(ns kotoba.coll.rename
  "rename -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.coll.rename-keys :refer [rename-keys]])
)

(defn rename
  "Return a relation (set of maps) built from `xrel` by applying
  `rename-keys` with `kmap` to every map in it. Mirrors clojure.set/rename."
  [xrel kmap]
  (set (map #(rename-keys % kmap) xrel)))
