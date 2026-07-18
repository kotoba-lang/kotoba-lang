#!/usr/bin/env bb
(ns generate-q9-kotoba-extension-audit
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))

(def organizations ["etzhayyim" "kotoba-lang" "gftdcojp"
                    "cloud-itonami" "com-junkawasaki"])
(def workspace (-> "../../.." fs/canonicalize str))

(defn paths [org]
  (let [{:keys [exit out err]}
        @(process/process ["rg" "--files" (str "orgs/" org) "-g" "*.kotoba"]
                          {:dir workspace :out :string :err :string})]
    (when-not (contains? #{0 1} exit)
      (throw (ex-info "Kotoba extension audit failed" {:org org :exit exit :err err})))
    (sort (remove str/blank? (str/split-lines out)))))

(defn first-content-line [path]
  (with-open [reader (io/reader (str workspace "/" path))]
    (first (remove str/blank? (line-seq reader)))))

(let [all (vec (mapcat paths organizations))
      entries (mapv (fn [path]
                      (let [first-line (or (first-content-line path) "")
                            schema-dsl? (str/starts-with? (str/triml first-line) "//")]
                        {:path path
                         :classification (if schema-dsl?
                                           :legacy-schema-dsl-extension-collision
                                           :canonical-candidate-unverified)}))
                    all)
      counts (frequencies (map :classification entries))
      audit {:kotoba.lang.q9.extension-audit/version 1
             :generated-at "2026-07-18"
             :generator "scripts/generate-q9-kotoba-extension-audit.bb"
             :path-count (count entries)
             :counts counts
             :entries entries}]
  (spit "lang/q9-kotoba-extension-audit.edn"
        (with-out-str (pprint/pprint audit)))
  (println "Q9 KOTOBA EXTENSION AUDIT:" (count entries) "paths" counts))
