(ns kotoba.set.join
  "join -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.set.index :refer [index]]
            [kotoba.set.intersection :refer [intersection]]
            [kotoba.set.map-invert :refer [map-invert]]
            [kotoba.set.rename-keys :refer [rename-keys]])
)

(defn join
  "Join two relations (sets of maps).

  2-arg form -- natural join: joins `xrel` and `yrel` on every key name
  they have in common, keeping rows where the values at those keys match.
  Returns the empty set if either relation is empty (there is nothing to
  match against, not an error).

  3-arg form -- explicit-mapping join: `km` is a map of {xrel-key
  yrel-key}, naming which key of `xrel` corresponds to which key of `yrel`
  when the two relations don't share key names (or share a name that
  doesn't mean the same column).

  Mirrors clojure.set/join exactly, including which relation gets indexed
  (the smaller one, by count) -- an implementation-detail optimization that
  does not affect the result, only performance."
  ([xrel yrel]
   (if (and (seq xrel) (seq yrel))
     (let [ks (intersection (set (keys (first xrel))) (set (keys (first yrel))))
           [r s] (if (<= (count xrel) (count yrel)) [xrel yrel] [yrel xrel])
           idx (index r ks)]
       (reduce (fn [ret x]
                 (if-let [found (idx (select-keys x ks))]
                   (reduce #(conj %1 (clojure.core/merge %2 x)) ret found)
                   ret))
               #{} s))
     #{}))
  ([xrel yrel km]
   (let [[r s k] (if (<= (count xrel) (count yrel))
                   [xrel yrel (map-invert km)]
                   [yrel xrel km])
         idx (index r (vals k))]
     (reduce (fn [ret x]
               (if-let [found (idx (rename-keys (select-keys x (keys k)) k))]
                 (reduce #(conj %1 (clojure.core/merge %2 x)) ret found)
                 ret))
             #{} s))))
