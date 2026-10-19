(ns kotoba.coll.filter-keys
  "filter-keys -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn filter-keys
  "Return a map containing only the entries of `m` whose key satisfies `pred`."
  [pred m]
  (reduce-kv (fn [out k v]
               (if (pred k) (assoc out k v) out))
             {} m))
