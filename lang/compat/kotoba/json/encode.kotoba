(ns kotoba.json.encode
  "encode -- addressed on its own.

  Split out of json.core on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.json.emit :refer [emit]])
)

(defn encode
  "Serialize EDN to compact JSON with deterministic map key order."
  [x]
  (emit x 0 false))
