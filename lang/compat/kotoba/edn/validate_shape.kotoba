(ns kotoba.edn.validate-shape
  "validate-shape! -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.max-depth :refer [max-depth]]
            [kotoba.edn.max-nodes :refer [max-nodes]]
            [kotoba.edn.max-string-chars :refer [max-string-chars]]
            [kotoba.edn.reject :refer [reject!]]))

(defn validate-shape! [value]
  (let [nodes (volatile! 0)]
    (letfn [(walk [x depth]
              (when (> depth max-depth)
                (reject! "EDN value nesting exceeds limit" {:limit max-depth}))
              (when (> (vswap! nodes inc) max-nodes)
                (reject! "EDN value contains too many nodes" {:limit max-nodes}))
              (when (and (string? x) (> (count x) max-string-chars))
                (reject! "EDN string exceeds limit" {:limit max-string-chars}))
              (cond
                (map? x) (doseq [[k v] x]
                           (walk k (inc depth))
                           (walk v (inc depth)))
                (coll? x) (doseq [item x] (walk item (inc depth)))))]
      (walk value 0)
      value)))
