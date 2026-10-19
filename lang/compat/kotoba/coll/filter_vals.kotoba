(ns kotoba.coll.filter-vals
  "filter-vals -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn filter-vals
  "Return a map containing only the entries of `m` whose value satisfies
  `pred`."
  [pred m]
  (reduce-kv (fn [out k v]
               (if (pred v) (assoc out k v) out))
             {} m))
