(ns vdesign.vehicle
  "Bridge a released design into the shared backend-neutral Vehicle Document."
  (:require [kotoba.physics.vehicle :as vehicle]))

(defn document
  [id design]
  (when-not (= :released (:status design))
    (throw (ex-info "only a released design can become a vehicle document"
                    {:status (:status design)})))
  (let [{:keys [curb-mass-kg powertrain geometry physics]} design]
    (vehicle/document
     {:id id
      :name (str (name (:class design)) " " (name powertrain) " design")
      :preset (:class design)
      :spec (merge {:mass-kg (+ 150.0 curb-mass-kg)} physics)
      :systems {:powertrain powertrain
                :energy (:energy design)}
      :collision {:envelope geometry}
      :provenance {:authority :kotoba/vehicle-designer
                   :design-status :released}})))
