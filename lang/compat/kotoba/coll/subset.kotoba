(ns kotoba.coll.subset
  "subset? -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn subset?
  "True if every element of set1 is also in set2. Mirrors
  clojure.set/subset?. An empty set1 is a subset of any set2, including
  another empty set."
  [set1 set2]
  (and (<= (count set1) (count set2))
       (every? #(contains? set2 %) set1)))
