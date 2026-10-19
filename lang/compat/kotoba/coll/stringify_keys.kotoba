(ns kotoba.coll.stringify-keys
  "stringify-keys -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.coll.postwalk :refer [postwalk]])
)

(defn stringify-keys
  "Recursively transform all keyword map keys in `form` into strings, leaving
  keys of every other type untouched. Mirrors clojure.walk/stringify-keys,
  unbounded.

  Uses `name`, so a namespaced keyword loses its namespace: `:a/b` => `\"b\"`.
  See the section comment above -- that loss is clojure.walk's behaviour and
  is deliberate here.

  `(stringify-keys {:a {:b 1} 2 3}) => {\"a\" {\"b\" 1} 2 3}`"
  [form]
  (let [coerce (fn [[k v]] (if (keyword? k) [(name k) v] [k v]))]
    (postwalk (fn [x] (if (map? x) (into {} (map coerce) x) x)) form)))
