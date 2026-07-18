#!/usr/bin/env bb

(require '[clojure.java.io :as io]
         '[clojure.string :as str])

(def root (io/file "."))

(defn hidden-vcs-path? [^java.io.File file]
  (some #{" .git" ".git"} (.split (.getPath file) java.io.File/separator)))

(defn legacy-runtime-file? [^java.io.File file]
  (let [path (.getPath file)
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
       (map #(.getPath ^java.io.File %))
       sort
       vec))

(when (seq offenders)
  (binding [*out* *err*]
    (println "legacy runtime files remain:")
    (doseq [path offenders]
      (println path)))
  (System/exit 1))

(println "ok legacy runtime absence")
