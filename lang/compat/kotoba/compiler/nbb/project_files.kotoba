(ns kotoba.compiler.nbb.project-files
  "Node port of `kotoba.compiler.project-files`: resolve the transitive closure
  of a multi-module guest from explicit source roots, without a JVM.

  The JVM twin of this namespace is the reason a `.cljc` component could be
  read but not built on the JDK-free route. `bin/amu` refused every invocation
  carrying `--source-path`, so a guest that declares `(:require ...)` -- which
  is nearly every component ported from `.cljc` -- had to be flattened into one
  namespace before it could be compiled at all. That flattening is not a
  migration step, it is a tax on having modules.

  What is deliberately identical to the JVM path, because these are the
  properties the path resolver is FOR rather than incidental behaviour:

  - Extension priority (`.kotoba`, `.cljk`, `.cljc`) is local to ONE root. The
    same namespace present under two explicit roots is ambiguous and rejected;
    it must never be settled by argument order.
  - Every discovered path is resolved through the real path before use, and
    must lie under one of the source roots. The root module is exempt because
    it was named explicitly rather than discovered by namespace.
  - The declared namespace must equal the one that was required, and one
    namespace may not resolve to two files.

  Containment is compared segment-wise. A raw string prefix would accept
  `/src-evil` as living under `/src`, which is the whole class of bug this
  check exists to stop, and it would do so silently."
  (:require ["node:fs" :as fs]
            ["node:path" :as path]
            [clojure.string :as str]
            [kotoba.sema :as sema]
            [kotoba.compiler.project :as project]))

(def ^:private extensions [".kotoba" ".cljk" ".cljc"])

(defn- reject! [message data]
  (throw (ex-info message (assoc data :phase :project-link))))

(defn- real-path [value]
  (try
    (.realpathSync fs value)
    (catch :default _
      (reject! "project path is not readable" {}))))

(defn- directory? [p]
  (try
    (.isDirectory (.statSync fs p))
    (catch :default _ false)))

(defn- regular-file? [p]
  (try
    (.isFile (.statSync fs p))
    (catch :default _ false)))

(defn- under-root?
  "Segment-wise containment, the property `java.nio.file.Path/startsWith` has
  and `String/startsWith` does not."
  [candidate root]
  (or (= candidate root)
      (str/starts-with? candidate (str root (.-sep path)))))

(defn- namespace-relative [namespace]
  (-> (str namespace) (str/replace "." "/") (str/replace "-" "_")))

(defn- module-path [source-roots namespace]
  (let [relative (namespace-relative namespace)
        existing (keep (fn [source-root]
                         ;; Extension priority remains local to one package
                         ;; root. The same namespace in two explicit roots is
                         ;; ambiguous and must never be shadowed by argument
                         ;; order.
                         (first (filter regular-file?
                                        (map #(.join path source-root (str relative %))
                                             extensions))))
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
  "Load only the transitive closure rooted at input from explicit source
  directories. The explicitly selected root may live beside those directories;
  dependency real-path checks reject symlink escape and cross-root namespace
  ambiguity. project/link-source owns the aggregate graph and source bounds."
  [input source-paths]
  (let [source-roots (mapv real-path (if (sequential? source-paths)
                                       source-paths [source-paths]))
        root-path (real-path input)]
    (when (empty? source-roots)
      (reject! "at least one source path is required" {}))
    (doseq [source-root source-roots]
      (when-not (directory? source-root)
        (reject! "source path must be a readable directory" {})))
    (let [sources (volatile! {})
          paths (volatile! {})]
      (letfn [(visit [file expected]
                (let [real (real-path file)]
                  ;; The root is explicitly selected, not discovered by
                  ;; namespace. Only discovered dependencies are confined.
                  (when (and expected
                             (not-any? #(under-root? real %) source-roots))
                    (reject! "project module escapes the explicit source paths"
                             {:module expected}))
                  (let [source (.readFileSync fs real "utf8")
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
