(ns kotoba.coll.walk-children
  "walk-children -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn walk-children [walk-one x depth]
  (cond
    (map? x) (into (empty x)
                    (map (fn [[k v]] [(walk-one k depth) (walk-one v depth)]))
                    x)
    (seq? x) (doall (map #(walk-one % depth) x))
    (coll? x) (into (empty x) (map #(walk-one % depth)) x)
    :else x))
