(ns kotoba.coll.postwalk-replace
  "postwalk-replace -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.coll.postwalk :refer [postwalk]])
)

(defn postwalk-replace
  "Recursively transform `form` by replacing every node that is a key in
  `replacements` with that key's value, bottom-up. Mirrors
  clojure.walk/postwalk-replace."
  [replacements form]
  (postwalk (fn [x] (if (contains? replacements x) (get replacements x) x)) form))
