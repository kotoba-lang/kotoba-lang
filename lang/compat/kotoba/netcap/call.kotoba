(ns kotoba.netcap.call
  "call -- addressed on its own.

  Split out of kotoba.lang.netcap on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.netcap.net :refer [Net close-net connect read-net recv-udp resolve send-udp write-net]]
            [kotoba.netcap.denied :refer [denied]]
            [kotoba.netcap.gate :refer [gate]])
)

(defn call [mgr surface method args]
  (if-let [net (gate mgr surface)]
    (case [surface method]
      [:tcp :connect]  (connect net (:host args) (:port args))
      [:tcp :read]     (read-net net (:handle args))
      [:tcp :write]    (write-net net (:handle args) (:data args))
      [:tcp :close]    (close-net net (:handle args))
      [:udp :send]     (send-udp net (:host args) (:port args) (:data args))
      [:udp :recv]     (recv-udp net (:handle args) (or (:timeout args) 0))
      [:dns :resolve]  (resolve net (:hostname args))
      :kotoba.lang.netcap/unknown-method)
    denied))
