(ns kotoba.edn.write-string
  "write-string -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.escape-controls :refer [escape-controls]]
            [kotoba.edn.max-edn-bytes :refer [max-edn-bytes]]
            [kotoba.edn.reject :refer [reject!]]
            [kotoba.edn.utf8-size :refer [utf8-size]]
            [kotoba.edn.validate-shape :refer [validate-shape!]]))

(defn write-string
  "Canonical EDN text for `value`.

  The byte limit is checked **after** escaping, not before: one control
  character becomes six characters, so a value that passed a pre-escape check
  could still produce oversized output. The limit is on what is written."
  [value]
  (let [text (escape-controls (pr-str (validate-shape! value)))]
    (when (> (utf8-size text) max-edn-bytes)
      (reject! "EDN output exceeds byte limit" {:limit max-edn-bytes}))
    text))
