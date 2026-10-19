(ns kotoba.walk.prewalk
  "prewalk -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.walk.walk :refer [walk]])
)

(defn prewalk
  "Like `walk`, but apply `f` to `form` and then to its children,
  recursively, top-down (f runs on a node before it runs on that node's
  children). Mirrors clojure.walk/prewalk, unbounded."
  [f form]
  (walk (partial prewalk f) identity (f form)))
