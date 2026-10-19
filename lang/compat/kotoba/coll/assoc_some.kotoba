(ns kotoba.coll.assoc-some
  "assoc-some -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn assoc-some
  "Assoc `k` to `v` in `m` only when `v` is not nil. Useful for building option
  maps without (when ...) scaffolding at every call site."
  ([m k v]
   (if (nil? v) m (assoc m k v)))
  ([m k v & kvs]
   (let [m (assoc-some m k v)]
     (if (seq kvs)
       (recur m (first kvs) (second kvs) (nnext kvs))
       m))))
