(ns kotoba.set.index
  "index -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn index
  "Return a map from the distinct values of `ks` (as a map holding just
  those keys) to the set of maps in relation `xrel` that have those values.
  Mirrors clojure.set/index."
  [xrel ks]
  (reduce
   (fn [m x]
     (let [ik (select-keys x ks)]
       (assoc m ik (conj (get m ik #{}) x))))
   {} xrel))
