(ns kotoba.edn.qualify-map
  "qualify-map -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.reject :refer [reject!]]))

(defn qualify-map
  "Apply a `#:ns{...}` prefix to a map's keys, as clojure.edn does: an
  UNQUALIFIED keyword or symbol key gains the namespace, a key that already has
  one keeps it, and any other key type is left alone.

  This form is admitted, unlike every other `#` dispatch, because it names no
  reader -- it is a shorthand for qualification and cannot run anything. Three
  of this workspace's own resource files use it, so refusing it meant they
  could never be read by this namespace at all."
  [ns m]
  (when (= ns "_") (reject! "EDN namespaced map with _ namespace is forbidden" {}))
  (reduce-kv
   (fn [out k v]
     (assoc out
            (cond
              (and (keyword? k) (nil? (namespace k))) (keyword ns (name k))
              (and (symbol? k) (nil? (namespace k)))  (symbol ns (name k))
              :else k)
            v))
   (empty m) m))
