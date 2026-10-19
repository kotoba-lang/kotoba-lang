(ns kotoba.coll.set-union
  "set-union -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn set-union
  "Union of zero or more sets. Mirrors clojure.set/union without the
  clojure.set dependency."
  ([] #{})
  ([s1] (or s1 #{}))
  ([s1 s2] (into (or s1 #{}) (or s2 #{})))
  ([s1 s2 & sets] (reduce set-union (set-union s1 s2) sets)))
