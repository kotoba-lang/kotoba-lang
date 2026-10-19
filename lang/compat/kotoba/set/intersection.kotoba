(ns kotoba.set.intersection
  "intersection -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn intersection
  "Intersection of one or more sets. Mirrors clojure.set/intersection."
  ([s1] s1)
  ([s1 s2] (into (empty s1) (filter #(contains? s2 %)) s1))
  ([s1 s2 & sets] (reduce intersection (intersection s1 s2) sets)))
