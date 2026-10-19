(ns kotoba.set.difference
  "difference -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn difference
  "Elements of s1 not present in any of the other sets. Mirrors
  clojure.set/difference."
  ([s1] s1)
  ([s1 s2] (into (empty s1) (remove #(contains? s2 %)) s1))
  ([s1 s2 & sets] (reduce difference (difference s1 s2) sets)))
