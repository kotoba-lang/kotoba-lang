(ns aero.case
  "EDN aero-case construction. A case is the portable, datafied setup a solver
  consumes — geometry ref + body shape descriptors + fluid. Priors here turn a
  vehicle-design-actor spec (class + powertrain) into shape descriptors, so the
  design loop can ask 'what Cd does this actually have?' instead of trusting a
  fixed prior. The BEV/FCEV split is real: a BEV's flat battery floor and small
  cooling area give a smoother, lower-drag underbody than an FCEV/ICE that must
  feed a radiator and route exhaust/tanks."
  (:require [clojure.string :as str]))

;; Body geometry per class (frontal area A, ref length L, and an aero shape
;; template). Shape descriptors ∈ [0,1]; higher = more drag-favourable EXCEPT
;; :cooling-open and :lift where higher = MORE drag.
(def classes
  {:city  {:frontal-area 2.20 :length 3.9 :taper 0.45 :round 0.65}
   :sedan {:frontal-area 2.30 :length 4.8 :taper 0.62 :round 0.70}
   :suv   {:frontal-area 2.85 :length 4.7 :taper 0.40 :round 0.55}
   :truck {:frontal-area 9.50 :length 16.5 :taper 0.30 :round 0.45}})

;; Powertrain modifies the underbody smoothness, wheel fairing and cooling
;; open-area — the levers that actually differ BEV vs FCEV.
(def powertrain-mods
  {:bev  {:underbody-smooth 0.90 :wheels-faired 0.45 :cooling-open 0.15 :lift 0.30}
   :fcev {:underbody-smooth 0.70 :wheels-faired 0.40 :cooling-open 0.35 :lift 0.30}})

(defn for-vehicle
  "Build an aero case from a vehicle-design-actor-style spec
  {:class :powertrain [:frontal-area] [:v-inf-kmh]}. Returns an EDN case map."
  [{:keys [class powertrain frontal-area v-inf-kmh]
    :or   {class :sedan powertrain :bev v-inf-kmh 120}}]
  (let [g   (get classes class (:sedan classes))
        mod (get powertrain-mods powertrain (:bev powertrain-mods))]
    {:case/id      (str "veh-" (name class) "-" (name powertrain) "/aero-0")
     :geometry/ref (str "veh-" (name class) "-" (name powertrain))
     :frontal-area (or frontal-area (:frontal-area g))
     :length       (:length g)
     :shape        (merge {:taper (:taper g) :round (:round g)} mod)
     :fluid        {:v-inf (/ v-inf-kmh 3.6)}
     :solver       {:kind :rom-buildup}}))
