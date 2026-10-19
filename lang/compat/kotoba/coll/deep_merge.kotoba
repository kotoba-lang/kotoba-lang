(ns kotoba.coll.deep-merge
  "deep-merge -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn deep-merge
  "Recursively merge maps: when both values at a key are maps, they are merged
  recursively; otherwise the rightmost value wins. Scalars and collections that
  are not both maps are replaced (not concatenated) — this is merge semantics,
  not conj semantics."
  ([a] a)
  ([a b]
   (if (and (map? a) (map? b))
     (merge-with deep-merge a b)
     b))
  ([a b & more]
   (reduce deep-merge (deep-merge a b) more)))
