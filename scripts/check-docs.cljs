#!/usr/bin/env nbb
(ns check-docs
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            ["fs" :as fs]
            ["path" :as path]))

(defn arg-value [flag]
  (let [args (vec (drop 2 (js->clj (.-argv js/process))))
        i (.indexOf args flag)]
    (when (and (<= 0 i) (< (inc i) (count args)))
      (nth args (inc i)))))

(def root (path/resolve (or (arg-value "--root") ".")))
(def map-path (path/join root "docs" "authority-map.edn"))

(defn exists? [p] (fs/existsSync p))
(defn read-text [p] (fs/readFileSync p "utf8"))
(defn read-edn [p] (reader/read-string (read-text p)))
(defn local-path [p] (path/resolve root p))

(defn markdown-targets [text]
  (let [re #"\[[^\]]*\]\(([^)]+)\)"]
    (map second (re-seq re text))))

(defn relative-target [source target]
  (let [target (-> target
                   (str/replace #"^<|>$" "")
                   (str/split #"#" 2)
                   first)]
    (when (and (seq target)
               (not (str/starts-with? target "#"))
               (not (re-find #"^[a-zA-Z][a-zA-Z0-9+.-]*:" target))
               (not (str/starts-with? target "/")))
      (path/resolve (path/dirname source) target))))

(defn profile-errors []
  (let [grammar-path (local-path "lang/guest-grammar.edn")
        surface-path (local-path "lang/surface-status.edn")
        pipeline-path (local-path "lang/elaboration-pipeline.edn")]
    (if-not (every? exists? [grammar-path surface-path pipeline-path])
      []
      (let [grammar (read-edn grammar-path)
            surface (read-edn surface-path)
            pipeline (read-edn pipeline-path)
            versions {:grammar (:kotoba.lang.guest-grammar/profile-version grammar)
                      :surface (:kotoba.lang.surface-status/profile-version surface)
                      :pipeline (get-in pipeline [:contract-versions :language-profile])}]
        (when-not (apply = (vals versions))
          [{:code :docs/profile-version-drift :versions versions}])))))

(defn main []
  (let [missing-map (when-not (exists? map-path)
                      [{:code :docs/authority-map-missing
                        :path (path/relative root map-path)}])
        authority-map (when-not missing-map (read-edn map-path))
        checked (:checked-documents authority-map [])
        missing-docs (for [doc checked :when (not (exists? (local-path doc)))]
                       {:code :docs/document-missing :path doc})
        broken-links
        (for [doc checked
              :let [source (local-path doc)]
              :when (exists? source)
              target (markdown-targets (read-text source))
              :let [resolved (relative-target source target)]
              :when (and resolved (not (exists? resolved)))]
          {:code :docs/link-missing
           :source doc
           :target target
           :resolved (path/relative root resolved)})
        route-errors
        (for [[audience {:keys [start next]}] (:audiences authority-map {})
              p (cons start next)
              :when (not (exists? (local-path p)))]
          {:code :docs/route-missing :audience audience :path p})
        authority-errors
        (for [[axis {:keys [owner path kind]}] (:authorities authority-map {})
              :when (or (not (re-matches #"[^/]+/[^/]+" (or owner "")))
                        (str/blank? path)
                        (nil? kind))]
          {:code :docs/authority-invalid :axis axis})
        errors (vec (concat missing-map missing-docs broken-links route-errors
                            authority-errors (profile-errors)))]
    (if (seq errors)
      (do (println "DOCS FAIL" (pr-str errors)) (js/process.exit 1))
      (do (println "DOCS PASS"
                   (pr-str {:documents (count checked)
                            :routes (count (:audiences authority-map))
                            :authorities (count (:authorities authority-map))}))
          (js/process.exit 0)))))

(main)
