#!/usr/bin/env bb
(ns check-q9-soak
  (:require [clojure.edn :as edn]
            [clojure.set :as set])
  (:import [java.time Duration Instant]))

(def evidence (edn/read-string (slurp "lang/q9-wave1-tranche-1-soak.edn")))

(defn fail! [message data]
  (binding [*out* *err*]
    (println "Q9 SOAK NOT READY:" message (pr-str data)))
  (System/exit 1))

(defn artifact-paths [dir]
  #{(str "src/" dir "/page_limit.kotoba")
    (str "test/" dir "/kotoba_qualification_test.clj")
    "kotoba-qualification.edn"
    "deps.edn"
    ".github/workflows/ci.yml"})

(let [{:keys [workflow event branch conclusion distinct-runs-per-repository
              minimum-soak-seconds same-qualification-artifacts]}
      (:requirements evidence)
      observed-at (Instant/parse (:as-of evidence))
      results
      (mapv
       (fn [{:keys [repository dir qualified-revision qualification-artifacts runs]}]
         (let [ids (map :run-id runs)
               shas (map :head-sha runs)
               expected-paths (artifact-paths dir)
               artifact-sets (map (comp set keys :artifacts) runs)
               artifact-identities (map :artifacts runs)
               oldest (when (seq runs)
                        (apply min-key #(.toEpochMilli ^Instant %)
                               (map (comp #(Instant/parse %) :completed-at) runs)))
               soak (if oldest (.getSeconds (Duration/between oldest observed-at)) 0)
               valid-shape?
               (every? (fn [run]
                         (and (integer? (:run-id run))
                              (re-matches #"[0-9a-f]{40}" (:head-sha run ""))
                              (= workflow (:workflow run))
                              (= event (:event run))
                              (= branch (:branch run))
                              (= conclusion (:conclusion run))
                              (string? (:url run))
                              (try (Instant/parse (:completed-at run)) true
                                   (catch Exception _ false))))
                       runs)
               ready? (and valid-shape?
                           (re-matches #"[0-9a-f]{40}" (or qualified-revision ""))
                           (= expected-paths (set (keys qualification-artifacts)))
                           (= (count ids) (count (set ids)))
                           (= (count shas) (count (set shas)))
                           (>= (count runs) distinct-runs-per-repository)
                           (every? #(= expected-paths %) artifact-sets)
                           (every? #(= qualification-artifacts %) artifact-identities)
                           (or (not same-qualification-artifacts)
                               (= 1 (count (set artifact-identities))))
                           (>= soak minimum-soak-seconds))]
           {:repository repository :runs (count runs) :soak-seconds soak
            :ready ready?}))
       (:repositories evidence))]
  (when-not (every? :ready results)
    (fail! "every repository needs distinct successful main-push runs over unchanged qualification artifacts and the full soak interval"
           {:requirements (:requirements evidence) :repositories results}))
  (when-not (and (true? (get-in evidence [:gate :ready]))
                 (true? (get-in evidence [:gate :consumer-cutover-authorized])))
    (fail! "evidence is sufficient but the explicit authority gate remains closed"
           {:gate (:gate evidence)}))
  (println "Q9 SOAK PASS:" (count results) "repositories satisfy CI and soak gates"))
