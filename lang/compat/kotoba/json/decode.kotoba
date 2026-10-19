(ns kotoba.json.decode
  "decode -- addressed on its own.

  Split out of json.core on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.json.read-value :refer [read-value]]
            [kotoba.json.skip-ws :refer [skip-ws]])
)

(defn decode
  "Parse JSON into Clojure data. Object keys are strings."
  [s]
  (let [[v i] (read-value s 0)
        i (skip-ws s i)]
    (when-not (= i (count s))
      (throw (ex-info "trailing JSON input" {:pos i})))
    v))
