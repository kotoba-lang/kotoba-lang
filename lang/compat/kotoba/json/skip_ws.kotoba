(ns kotoba.json.skip-ws
  "skip-ws -- addressed on its own.

  Split out of json.core on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.json.ws :refer [ws?]])
)

(defn skip-ws [s i]
  (let [n (count s)]
    (loop [i i]
      (if (and (< i n) (ws? (nth s i)))
        (recur (inc i))
        i))))
