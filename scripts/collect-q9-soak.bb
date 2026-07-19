#!/usr/bin/env bb
(ns collect-q9-soak
  (:require [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.string :as str])
  (:import [java.time Instant]))

(def path "lang/q9-wave1-tranche-1-soak.edn")
(def evidence (edn/read-string (slurp path)))

(defn command! [args dir]
  (let [{:keys [exit out err]} @(process/process args {:dir dir :out :string :err :string})]
    (when-not (zero? exit)
      (throw (ex-info "Q9 soak collection command failed"
                      {:args args :dir dir :exit exit :err err})))
    (str/trim out)))

(defn artifact-paths [dir]
  [(str "src/" dir "/page_limit.kotoba")
   (str "test/" dir "/kotoba_qualification_test.clj")
   "kotoba-qualification.edn" "deps.edn" ".github/workflows/ci.yml"])

(defn remote-tree [github sha]
  (let [raw (command! ["gh" "api" (str "repos/" github "/git/trees/" sha "?recursive=1")] ".")]
    (into {} (map (juxt :path :sha) (:tree (json/parse-string raw true))))))

(defn collect-repository [{:keys [repository github dir] :as row}]
  (let [local-dir (str "../../../" repository)]
    (when-not (str/blank? (command! ["git" "status" "--porcelain"] local-dir))
      (throw (ex-info "Q9 soak collection requires a clean committed repository"
                      {:repository repository})))
    (let [required (artifact-paths dir)
        local-head (command! ["git" "rev-parse" "HEAD"] local-dir)
        local-artifacts
        (into {} (map (fn [path]
                        [path (command! ["git" "rev-parse" (str "HEAD:" path)] local-dir)]))
              required)
        _ (command! ["gh" "api" (str "repos/" github "/commits/" local-head)] ".")
        api (str "repos/" github "/actions/workflows/ci.yml/runs"
                 "?status=success&branch=main&event=push&per_page=100")
        response (json/parse-string (command! ["gh" "api" api] ".") true)
        runs (->> (:workflow_runs response)
                  (filter #(and (= "success" (:conclusion %))
                                (= "push" (:event %))
                                (= "main" (:head_branch %))))
                  (map (fn [run]
                         (let [sha (:head_sha run)
                               tree (remote-tree github sha)
                               artifacts (select-keys tree required)]
                           (when (= local-artifacts artifacts)
                             {:run-id (:id run)
                              :head-sha sha
                              :workflow "ci.yml"
                              :event (:event run)
                              :branch (:head_branch run)
                              :conclusion (:conclusion run)
                              :completed-at (:updated_at run)
                              :url (:html_url run)
                              :artifacts artifacts}))))
                  (remove nil?)
                  (sort-by :completed-at)
                  vec)]
      (assoc row :qualified-revision local-head
                 :qualification-artifacts local-artifacts
                 :runs runs))))

(let [repositories (mapv collect-repository (:repositories evidence))
      updated (-> evidence
                  (assoc :as-of (str (Instant/now)))
                  (assoc :repositories repositories)
                  (assoc :gate {:actual-green-ci-runs-per-repository
                                (apply min (map (comp count :runs) repositories))
                                :soak-seconds 0
                                :ready false
                                :consumer-cutover-authorized false}))]
  (spit path (with-out-str (pprint/pprint updated)))
  (println "Q9 SOAK EVIDENCE COLLECTED; authority gate remains closed"))
