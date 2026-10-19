(ns kotoba.coll.postwalk
  "postwalk -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.coll.walk :refer [walk]])
)

(defn postwalk
  "Like `walk`, but apply `f` to `form`'s children first, then to `form`
  itself, recursively, bottom-up (f runs on a node's children before it
  runs on that node). Mirrors clojure.walk/postwalk, unbounded."
  [f form]
  (walk (partial postwalk f) f form))
