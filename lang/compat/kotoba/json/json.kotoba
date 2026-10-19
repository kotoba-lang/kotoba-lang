(ns kotoba.json.json
  "json -- addressed on its own.

  Split out of json.core on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.json.emit :refer [emit]])
)

(defn json
  "Serialize EDN to a 2-space pretty JSON string. Keyword keys/values become strings."
  [x]
  (emit x 0 true))
