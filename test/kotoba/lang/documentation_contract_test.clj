(ns kotoba.lang.documentation-contract-test
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]])
  (:import [java.io File]))

(defn file-at [root path]
  (File. ^File root ^String path))

(defn read-edn [root path]
  (edn/read-string (slurp (file-at root path))))

(defn markdown-targets [text]
  (map second (re-seq #"\[[^\]]*\]\(([^)]+)\)" text)))

(defn relative-target [^File source target]
  (let [target (-> target
                   (str/replace #"^<|>$" "")
                   (str/split #"#" 2)
                   first)]
    (when (and (seq target)
               (not (re-find #"^[a-zA-Z][a-zA-Z0-9+.-]*:" target))
               (not (str/starts-with? target "/")))
      (File. (.getParentFile source) target))))

(defn problems [root]
  (let [root (.getCanonicalFile (File. root))
        authority-map (read-edn root "docs/authority-map.edn")
        checked (:checked-documents authority-map)
        missing (for [path checked
                      :when (not (.isFile (file-at root path)))]
                  {:code :docs/document-missing :path path})
        broken (for [path checked
                     :let [source (file-at root path)]
                     :when (.isFile source)
                     target (markdown-targets (slurp source))
                     :let [resolved (relative-target source target)]
                     :when (and resolved (not (.exists resolved)))]
                 {:code :docs/link-missing :source path :target target})
        routes (for [[audience {:keys [start next]}] (:audiences authority-map)
                     path (cons start next)
                     :when (not (.isFile (file-at root path)))]
                 {:code :docs/route-missing :audience audience :path path})
        grammar (when (.isFile (file-at root "lang/guest-grammar.edn"))
                  (read-edn root "lang/guest-grammar.edn"))
        surface (when (.isFile (file-at root "lang/surface-status.edn"))
                  (read-edn root "lang/surface-status.edn"))
        pipeline (when (.isFile (file-at root "lang/elaboration-pipeline.edn"))
                   (read-edn root "lang/elaboration-pipeline.edn"))
        versions (when (and grammar surface pipeline)
                   {:grammar (:kotoba.lang.guest-grammar/profile-version grammar)
                    :surface (:kotoba.lang.surface-status/profile-version surface)
                    :pipeline (get-in pipeline [:contract-versions :language-profile])})
        drift (when (and versions (not (apply = (vals versions))))
                [{:code :docs/profile-version-drift :versions versions}])]
    (vec (concat missing broken routes drift))))

(deftest checked-documentation-contract-is-consistent
  (is (empty? (problems "."))))

(deftest broken-link-fixture-proves-the-negative-path
  (let [found (problems "test/fixtures/docs-negative")]
    (is (= [:docs/link-missing] (mapv :code found)))
    (is (= "missing.md" (:target (first found))))))
