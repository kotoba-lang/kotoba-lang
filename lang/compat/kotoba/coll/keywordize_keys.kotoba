(ns kotoba.coll.keywordize-keys
  "keywordize-keys -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.coll.postwalk :refer [postwalk]])
)

(defn keywordize-keys
  "Recursively transform all string map keys in `form` into keywords, leaving
  keys of every other type untouched. Mirrors clojure.walk/keywordize-keys,
  unbounded.

  `(keywordize-keys {\"a\" {\"b\" 1} 2 3}) => {:a {:b 1} 2 3}`"
  [form]
  (let [coerce (fn [[k v]] (if (string? k) [(keyword k) v] [k v]))]
    (postwalk (fn [x] (if (map? x) (into {} (map coerce) x) x)) form)))
