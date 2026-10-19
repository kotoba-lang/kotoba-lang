(ns kotoba.spec.explain
  "explain -- addressed on its own.

  Split out of kotoba.lang.spec on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.spec.explain-1 :refer [explain-1]]))

(defn explain
  "Return a seq of problem maps for `x` against `spec`. Empty seq means valid."
  [spec x]
  (explain-1 spec x [] []))
