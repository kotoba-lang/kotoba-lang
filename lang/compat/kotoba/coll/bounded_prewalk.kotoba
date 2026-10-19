(ns kotoba.coll.bounded-prewalk
  "bounded-prewalk -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.coll.default-max-walk-depth :refer [default-max-walk-depth]]
            [kotoba.coll.walk :refer [walk]]
            [kotoba.coll.walk-children :refer [walk-children]]
            [kotoba.coll.walk-depth-exceeded :refer [walk-depth-exceeded!]])
)

(defn bounded-prewalk
  "Like clojure.walk/prewalk: apply f to form and then to its children,
  top-down. Bounded by max-depth (default default-max-walk-depth); throws
  ex-info rather than recursing without limit once the ceiling is crossed."
  ([f form] (bounded-prewalk f default-max-walk-depth form))
  ([f max-depth form]
   (letfn [(walk [x depth]
             (when (> depth max-depth) (walk-depth-exceeded! max-depth))
             (walk-children walk (f x) (inc depth)))]
     (walk form 0))))
