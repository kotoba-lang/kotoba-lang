(ns kotoba.coll.map-keys
  "map-keys -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn map-keys
  "Return a map with `f` applied to each key and the same values. If `f`
  produces duplicate keys, later entries win."
  [f m]
  (reduce-kv (fn [out k v] (assoc out (f k) v)) {} m))
