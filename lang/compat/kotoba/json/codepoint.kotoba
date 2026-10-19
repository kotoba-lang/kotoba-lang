(ns kotoba.json.codepoint
  "codepoint -- addressed on its own.

  Split out of json.core on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.json.hex-val :refer [hex-val]])
)

(defn codepoint [s i]
  (reduce (fn [n ch] (+ (* n 16) (hex-val ch))) 0 (subs s i (+ i 4))))
