(ns kotoba.coll.rename-keys
  "rename-keys -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn rename-keys
  "Return `m` with any key present in `kmap` renamed to that key's value in
  `kmap`; keys of `m` absent from `kmap` are left as-is. Mirrors
  clojure.set/rename-keys. Operates on a single map (not a relation) --
  `rename` below is the relation-wide form."
  [m kmap]
  (reduce
   (fn [out [old new]]
     (if (contains? m old)
       (assoc out new (get m old))
       out))
   (apply dissoc m (keys kmap))
   kmap))
