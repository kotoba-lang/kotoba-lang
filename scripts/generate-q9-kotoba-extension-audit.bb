#!/usr/bin/env bb
(ns generate-q9-kotoba-extension-audit
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]))

(def organizations ["etzhayyim" "kotoba-lang" "gftdcojp"
                    "cloud-itonami" "com-junkawasaki"])
(def workspace (-> "../../.." fs/canonicalize str))
(def verification-path "lang/q9-kotoba-candidate-verification.edn")

(defn sha256 [text]
  (let [bytes (.digest (MessageDigest/getInstance "SHA-256")
                       (.getBytes text StandardCharsets/UTF_8))]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) bytes))))

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
      verification (when (fs/exists? verification-path)
                     (read-string (slurp verification-path)))
      verified-by-path (into {} (map (juxt :path identity) (:entries verification)))
      entries (mapv (fn [path]
                      (let [text (slurp (str workspace "/" path))
                            first-line (or (first-content-line path) "")
                            schema-dsl? (str/starts-with? (str/triml first-line) "//")
                            archived-language? (str/includes? path "/kotoba-v2025/_archive/")
                            proof (get verified-by-path path)
                            proof-current? (= (sha256 text) (:sha256 proof))]
                        (let [classification
                              (cond
                                schema-dsl? :legacy-schema-dsl-extension-collision
                                archived-language? :legacy-language-extension-collision
                                (and proof-current? (= :canonical-verified (:status proof)))
                                :canonical-verified
                                (and proof-current? (= :canonical-fixture-verified (:status proof)))
                                :canonical-fixture-verified
                                (and proof-current? (= :canonical-rejected (:status proof)))
                                :canonical-rejected
                                :else :canonical-candidate-unverified)
                              repository (str/join "/" (take 3 (str/split path #"/")))
                              exception
                              (case classification
                                :legacy-schema-dsl-extension-collision
                                {:exception/type :extension-collision
                                 :artifact-kind :legacy-schema-dsl
                                 :owner repository
                                 :canonical-admission :deny
                                 :replacement-extension ".kotoba-schema"
                                 :migration-track :q9-schema-extension-remediation}
                                :legacy-language-extension-collision
                                {:exception/type :extension-collision
                                 :artifact-kind :archived-legacy-language
                                 :owner repository
                                 :canonical-admission :deny
                                 :replacement-extension ".cljc"
                                 :migration-track :q9-archive-retirement}
                                :canonical-candidate-unverified
                                {:exception/type :verification-required
                                 :artifact-kind :unverified-kotoba-source
                                 :owner repository
                                 :canonical-admission :deny
                                 :replacement-extension ".kotoba.quarantine"
                                 :migration-track :q9-candidate-verification}
                                :canonical-rejected
                                {:exception/type :verification-rejected
                                 :artifact-kind :rejected-kotoba-source
                                 :owner repository
                                 :canonical-admission :deny
                                 :replacement-extension ".kotoba.quarantine"
                                 :migration-track :q9-candidate-remediation}
                                nil)]
                          (cond-> {:path path
                                   :sha256 (sha256 text)
                                   :classification classification}
                            exception (assoc :typed-exception exception)))))
                    all)
      counts (frequencies (map :classification entries))
      audit {:kotoba.lang.q9.extension-audit/version 3
             :generated-at "2026-07-19"
             :generator "scripts/generate-q9-kotoba-extension-audit.bb"
             :verification "lang/q9-kotoba-candidate-verification.edn"
             :path-count (count entries)
             :counts counts
             :entries entries}]
  (spit "lang/q9-kotoba-extension-audit.edn"
        (with-out-str (pprint/pprint audit)))
  (println "Q9 KOTOBA EXTENSION AUDIT:" (count entries) "paths" counts))
