(ns vdesign.datom
  "Thin domain-bound view over the shared `datom-clj` — binds the :vdesign
  attribute prefix so the rest of the actor calls `(entity kind id attrs)`
  unchanged. The EAVT/Datomic-log logic itself is commonized in datom.core
  (used identically by aero-clj and the CAE seeds)."
  (:require [datom.core :as d]))

(defn entity [kind id attrs] (d/entity "vdesign" kind id attrs))
(def eavt d/eavt)
(defn log [entities] (d/log entities))
