(ns kotoba.compiler.project-files
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [kotoba.sema :as sema]
            [kotoba.compiler.project :as project])
  (:import [java.nio.file Files LinkOption Path]))

(def ^:private extensions [".kotoba" ".cljk" ".cljc"])

(defn- reject! [message data]
  (throw (ex-info message (assoc data :phase :project-link))))

(defn- real-path ^Path [value]
  (try
    (.toRealPath (.toPath (io/file value)) (make-array LinkOption 0))
    (catch java.io.IOException _
      (reject! "project path is not readable" {}))))

(defn- namespace-relative [namespace]
  (-> (str namespace) (str/replace "." "/") (str/replace "-" "_")))

(defn- module-path [source-roots namespace]
  (let [relative (namespace-relative namespace)
        existing (keep (fn [^Path source-root]
                         ;; Extension priority remains local to one package
                         ;; root. The same namespace in two explicit roots is
                         ;; ambiguous and must never be shadowed by argument order.
                         (first (filter #(Files/isRegularFile % (make-array LinkOption 0))
                                        (map #(.resolve source-root (str relative %)) extensions))))
                       source-roots)]
    (cond
      (empty? existing)
      (reject! "required module is missing from the explicit source paths"
               {:module namespace})
      (> (count existing) 1)
      (reject! "namespace resolves from multiple explicit source paths"
               {:module namespace})
      :else (first existing))))

(defn load-closed-graph
  "Load only the transitive closure rooted at input from one explicit source
  directories. The explicitly selected root may live beside those directories;
  dependency real-path checks reject symlink escape and cross-root namespace
  ambiguity. project/link-source owns the aggregate graph and source bounds."
  [input source-paths]
  (let [source-roots (mapv real-path (if (sequential? source-paths)
                                       source-paths [source-paths]))
        root-path (real-path input)]
    (when (empty? source-roots)
      (reject! "at least one source path is required" {}))
    (doseq [^Path source-root source-roots]
      (when-not (Files/isDirectory source-root (make-array LinkOption 0))
        (reject! "source path must be a readable directory" {})))
    (let [sources (volatile! {})
          paths (volatile! {})]
      (letfn [(visit [^Path file expected]
                (let [real (.toRealPath file (make-array LinkOption 0))]
                  ;; The root is explicitly selected, not discovered by
                  ;; namespace. Only discovered dependencies are confined.
                  (when (and expected
                             (not-any? (fn [^Path source-root]
                                         (.startsWith real source-root))
                                       source-roots))
                    (reject! "project module escapes the explicit source paths"
                             {:module expected}))
                  (let [source (slurp (.toFile real))
                        info (project/module-info (sema/read-forms source))
                        declared (:namespace info)]
                    (when (and expected (not= expected declared))
                      (reject! "resolved path namespace does not match requirement"
                               {:module expected :declared declared}))
                    (when-let [previous (get @paths declared)]
                      (when-not (= previous real)
                        (reject! "namespace resolves to multiple project files"
                                 {:module declared})))
                    (when-not (contains? @sources declared)
                      (when (>= (count @sources) project/max-project-modules)
                        (reject! "project module count exceeds limit"
                                 {:limit project/max-project-modules}))
                      (vswap! sources assoc declared source)
                      (vswap! paths assoc declared real)
                      (doseq [{dependency :namespace} (:requires info)]
                        (visit (module-path source-roots dependency) dependency)))
                    declared)))]
        (let [root-namespace (visit root-path nil)]
          {:sources @sources :root root-namespace
           :paths (into (sorted-map) (map (fn [[k v]] [k (str v)])) @paths)})))))
