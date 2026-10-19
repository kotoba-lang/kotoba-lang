(ns kotoba.coll.map-vals
  "map-vals -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn map-vals
  "Return a map with the same keys as `m` and `f` applied to each value.
  Preserves the key's identity (objects/keywords/strings)."
  [f m]
  (reduce-kv (fn [out k v] (assoc out k (f v))) {} m))
