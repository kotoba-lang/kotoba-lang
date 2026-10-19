(ns kotoba.coll.bounded-postwalk
  "bounded-postwalk -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.coll.default-max-walk-depth :refer [default-max-walk-depth]]
            [kotoba.coll.walk :refer [walk]]
            [kotoba.coll.walk-children :refer [walk-children]]
            [kotoba.coll.walk-depth-exceeded :refer [walk-depth-exceeded!]])
)

(defn bounded-postwalk
  "Like clojure.walk/postwalk: apply f to form's children first, then to
  form itself, bottom-up. Bounded by max-depth (default
  default-max-walk-depth); throws ex-info rather than recursing without
  limit once the ceiling is crossed."
  ([f form] (bounded-postwalk f default-max-walk-depth form))
  ([f max-depth form]
   (letfn [(walk [x depth]
             (when (> depth max-depth) (walk-depth-exceeded! max-depth))
             (f (walk-children walk x (inc depth))))]
     (walk form 0))))
