(ns kotoba.edn.check-opts
  "check-opts! -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.option-keys :refer [option-keys]]
            [kotoba.edn.reject :refer [reject!]]))

(defn check-opts! [opts]
  (when-not (map? opts) (reject! "EDN options must be a map" {}))
  (let [unknown (remove option-keys (keys opts))]
    (when (seq unknown)
      (reject! "EDN option is not recognised" {:unknown (vec unknown)})))
  (when (and (contains? opts :default) (not (fn? (:default opts))))
    (reject! "EDN :default must be a function of [tag value]" {}))
  (when (and (contains? opts :readers) (not (map? (:readers opts))))
    (reject! "EDN :readers must be a map of tag symbol to function" {}))
  opts)
