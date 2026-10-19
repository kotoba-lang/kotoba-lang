(ns kotoba.edn.items-to-set
  "items->set -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.reject :refer [reject!]]))

(defn items->set [items]
  (when (not= (count items) (count (set items)))
    (reject! "EDN set has a duplicate element" {}))
  (set items))
