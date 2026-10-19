(ns kotoba.compiler.source-path
  (:require [clojure.string :as str]))

(def extensions
  "Closed source-discovery contract. Extensions select discovery intent only;
  they never select a runtime, weaken the Kotoba grammar, or imply JVM use."
  {".kotoba" :kotoba
   ".cljk" :clj-kotoba
   ".cljc" :portable-common})

(defn source-kind [path]
  (when (string? path)
    (some (fn [[extension kind]]
            (when (str/ends-with? path extension) kind))
          extensions)))

(defn admit! [path]
  (or (source-kind path)
      (throw (ex-info "source input must use .kotoba, .cljk, or .cljc"
                      {:phase :usage
                       :path path
                       :extensions (vec (sort (keys extensions)))})))
  path)
