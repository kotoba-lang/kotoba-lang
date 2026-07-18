#!/usr/bin/env bb
(ns generate-q9-inventory
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))

(def organizations ["etzhayyim" "kotoba-lang" "gftdcojp"
                    "cloud-itonami" "com-junkawasaki"])
(def workspace (-> "../../.." fs/canonicalize str))

(defn cljc-paths [org]
  (let [{:keys [exit out err]}
        @(process/process ["rg" "--files" (str "orgs/" org) "-g" "*.cljc"]
                          {:dir workspace :out :string :err :string})]
    (when-not (zero? exit)
      (throw (ex-info "Q9 inventory failed" {:organization org :exit exit :err err})))
    (sort (remove str/blank? (str/split-lines out)))))

(defn repo-of [path]
  (str/join "/" (take 3 (str/split path #"/"))))

(let [paths (mapcat cljc-paths organizations)
      repositories
      (->> paths
           (group-by repo-of)
           (sort-by key)
           (mapv (fn [[repository baseline-paths]]
                   {:repository repository
                    :baseline-paths (vec baseline-paths)
                    :baseline-count (count baseline-paths)
                    :classification-status :unclassified
                    :migration-record nil})))
      inventory {:kotoba.lang.q9.inventory/version 1
                 :generated-at "2026-07-18"
                 :generator "scripts/generate-q9-inventory.bb"
                 :organizations organizations
                 :repository-count (count repositories)
                 :path-count (count paths)
                 :repositories repositories}]
  (spit "lang/q9-inventory.edn" (with-out-str (pprint/pprint inventory)))
  (println "Q9 INVENTORY GENERATED:" (count paths) "paths in"
           (count repositories) "repositories"))
