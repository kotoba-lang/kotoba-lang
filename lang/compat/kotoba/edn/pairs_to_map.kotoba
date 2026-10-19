(ns kotoba.edn.pairs-to-map
  "pairs->map -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.reject :refer [reject!]]))

(defn pairs->map
  "Build the map, small ones as an array-map so SOURCE ORDER SURVIVES.

  Not cosmetic. Both host readers do this -- `clojure.edn` hands the pairs to
  `RT/map`, which returns a PersistentArrayMap at eight entries or fewer -- and
  `write-string` prints a map in iteration order, so a hash-map here makes
  read->write reorder the keys of every small map. That breaks textual
  idempotence, which is exactly the property a `--check` gate over generated
  text depends on: the file would differ from itself on every regeneration.

  Above eight entries both hosts promote to a hash-map and order is not
  preserved by anyone, so this matches that too rather than inventing a
  stronger guarantee than the thing it replaced."
  [items]
  (when (odd? (count items))
    (reject! "EDN map has an odd number of forms" {:count (count items)}))
  (let [ks (take-nth 2 items)]
    (when (not= (count ks) (count (set ks)))
      (reject! "EDN map has a duplicate key" {}))
    (if (<= (count ks) 8)
      (apply array-map items)
      (apply hash-map items))))
