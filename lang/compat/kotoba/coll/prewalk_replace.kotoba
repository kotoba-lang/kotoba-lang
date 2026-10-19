(ns kotoba.coll.prewalk-replace
  "prewalk-replace -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.coll.prewalk :refer [prewalk]])
)

(defn prewalk-replace
  "Recursively transform `form` by replacing every node that is a key in
  `replacements` with that key's value, top-down. Mirrors
  clojure.walk/prewalk-replace."
  [replacements form]
  (prewalk (fn [x] (if (contains? replacements x) (get replacements x) x)) form))
