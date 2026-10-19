(ns kotoba.coll.index-by
  "index-by -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn index-by
  "Return a map from `(keyfn item)` to `item` for each `item` in `coll`. Later
  items win on key collision. Nil keys are dropped (a nil key would collapse
  entries)."
  [keyfn coll]
  (reduce (fn [out item]
            (let [k (keyfn item)]
              (if (nil? k) out (assoc out k item))))
          {} coll))
