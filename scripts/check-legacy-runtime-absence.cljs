#!/usr/bin/env nbb
;; --- nbb shims (auto, ADR-2607173000) ---------------------------------
(def ^:private __fs (js/require "node:fs"))
(def ^:private __path (js/require "node:path"))
(def ^:private __cp (js/require "node:child_process"))
(def ^:private __os (js/require "node:os"))
(def ^:private __crypto (js/require "node:crypto"))
(defn- __sh [& args]
  (let [opts (when (map? (last args)) (last args))
        cmd (if opts (butlast args) args)
        r (.spawnSync __cp (first cmd) (to-array (rest cmd))
                      (clj->js (merge {:encoding "utf8"} (when opts {:cwd (:dir opts)}))))]
    {:exit (or (.-status r) 1) :out (or (.-stdout r) "") :err (or (.-stderr r) "")}))
(defn- __shell [& args]
  (let [opts (when (map? (first args)) (first args))
        cmd (if opts (rest args) args)
        r (.spawnSync __cp (first cmd) (to-array (rest cmd))
                      (clj->js (merge {:stdio "inherit" :encoding "utf8"}
                                      (when opts {:cwd (:dir opts)}))))]
    (when-not (zero? (or (.-status r) 1))
      (throw (js/Error. (str "shell failed: " (pr-str cmd)))))
    {:exit (or (.-status r) 0) :out "" :err ""}))
;; -----------------------------------------------------------------------

(require '         '[clojure.string :as str])

(def root (__path.resolve "."))

(defn hidden-vcs-path? [^java.io.File file]
  (some #{" .git" ".git"} (.split (str file) java.io.File/separator)))

(defn legacy-runtime-file? [^java.io.File file]
  (let [path (str file)
        name (.getName file)]
    (or (= "Cargo.toml" name)
        (= "Cargo.lock" name)
        (str/starts-with? name "rust-toolchain")
        (str/ends-with? name ".rs")
        (str/includes? path (str java.io.File/separator ".cargo" java.io.File/separator)))))

(def offenders
  (->> (file-seq root)
       (remove hidden-vcs-path?)
       (filter #(.isFile ^java.io.File %))
       (filter legacy-runtime-file?)
       (map #(str ^java.io.File %))
       sort
       vec))

(when (seq offenders)
  (binding [*out* *err*]
    (println "legacy runtime files remain:")
    (doseq [path offenders]
      (println path)))
  (.exit js/process 1))

(println "ok legacy runtime absence")
