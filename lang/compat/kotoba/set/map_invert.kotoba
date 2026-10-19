(ns kotoba.set.map-invert
  "map-invert -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn map-invert
  "Return `m` with each value mapped back to its key. Later entries win on a
  value collision. Private: mirrors clojure.set/map-invert but is not
  exported, since it is only needed here to implement the 3-arg `join`
  below and this namespace does not otherwise claim clojure.set/map-invert
  coverage."
  [m]
  (reduce (fn [out [k v]] (assoc out v k)) {} m))
