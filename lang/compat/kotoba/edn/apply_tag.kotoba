(ns kotoba.edn.apply-tag
  "apply-tag -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.reject :refer [reject!]]))

(defn apply-tag
  "Dispatch one `#tag value`, following clojure.edn: a `:readers` entry wins,
  then `:default`, and with neither the tag is refused. The strict default --
  no options at all -- therefore behaves exactly as it did before options
  existed."
  [opts tag value]
  (if-let [reader (get (:readers opts) tag)]
    (reader value)
    (if-let [d (:default opts)]
      (d tag value)
      (reject! "EDN tagged literal has no reader" {:tag tag}))))
