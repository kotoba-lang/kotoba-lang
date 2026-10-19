(ns kotoba.edn.refuse-imprecise
  "refuse-imprecise! -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.reject :refer [reject!]]))

(defn refuse-imprecise! [tok kind]
  (reject! (str "EDN number would not mean the same on every host: " tok)
           {:kotoba.lang.edn/reason kind :token tok}))
