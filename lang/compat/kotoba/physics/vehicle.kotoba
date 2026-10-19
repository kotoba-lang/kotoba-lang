(ns kotoba.physics.vehicle
  "Backend-neutral vehicle document shared by Studio, games and engineering solvers."
  (:require [kotoba.physics.contract :as contract]))
(def document-version 1)
(defn document [{:keys [id name preset spec systems structure materials collision provenance] :as input}]
  (when-not (and id (or (keyword? id) (string? id)) (map? (or spec {}))
                 (map? (or systems {})) (map? (or structure {})))
    (throw (ex-info "invalid shared vehicle document" {:input input})))
  {:physics/version contract/contract-version :vehicle/version document-version
   :vehicle/kind :document :vehicle/id id :vehicle/name (or name (clojure.core/name id))
   :vehicle/preset preset :vehicle/spec (or spec {}) :vehicle/systems (or systems {})
   :vehicle/structure (merge {:nodes [] :beams [] :triangles [] :wheels []} structure)
   :vehicle/materials (vec materials) :vehicle/collision (or collision {})
   :vehicle/provenance (or provenance {})})
(defn document? [x] (and (= :document (:vehicle/kind x)) (= document-version (:vehicle/version x))
                         (contains? x :vehicle/id) (map? (:vehicle/structure x))))
(defn entity
  ([id doc] (entity id doc {}))
  ([id doc {:keys [state controls ground transform]}]
   (when-not (document? doc) (throw (ex-info "vehicle entity requires a vehicle document" {:document doc})))
   (cond-> {:entity/id id :entity/domain :vehicle :vehicle/document doc
            :vehicle/controls (or controls {}) :entity/transform (or transform {})}
     state (assoc :vehicle/state state) ground (assoc :vehicle/ground ground))))
(defn scene [id entities]
  (contract/make-scene {:id id :dimensions 3 :entities (vec entities) :collision {:domain :vehicle}}))
(defn registry [& backends]
  (into {} (map (fn [backend] [(:id (contract/descriptor backend)) backend]) backends)))
(defn select-backend [backends fidelity capabilities]
  (some (fn [[_ backend]] (when (contract/supports? backend fidelity capabilities) backend)) backends))
