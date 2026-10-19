(ns aero.solver
  "Reduced-order Cd solver — component build-up. Deterministic and explainable:
  every drag count is traceable to a body descriptor, so the result is a
  decomposition, not a black-box number. This is the `:rom-buildup` backend;
  a `kami-cfd` lattice-Boltzmann backend would implement the same
  `solve` contract and return the same shape (with a resolved field instead
  of correlations)."
  (:require [aero.model :as model]
            [cae.solver :as cae]))

(defn- reynolds [{:keys [rho mu v-inf]} length-m]
  (/ (* rho v-inf length-m) mu))

(defn- friction-cd [m fluid length-m]
  (let [{:keys [cf-a cf-re-exp wet-over-frontal]} (:friction m)
        re (reynolds fluid length-m)
        cf (* cf-a (Math/pow re cf-re-exp))]
    (* cf wet-over-frontal)))

(defn solve
  "Compute Cd (and CdA) for a case. Returns
  {:Cd .. :CdA .. :Re .. :breakdown {component → cd-count} :solver :rom-buildup}.

  `case` keys:
    :frontal-area A (m^2), :length L (m)
    :shape {:round :taper :underbody-smooth :wheels-faired :cooling-open :lift}
            each in [0,1] (lift is a downforce/lift factor)
    :fluid {:rho :mu :v-inf}   (defaults from model)"
  [{:keys [frontal-area length shape fluid]
    :or   {fluid {:rho model/rho-air :mu model/mu-air :v-inf 33.3}}}]
  (let [m     model/default
        f     (merge {:rho model/rho-air :mu model/mu-air :v-inf 33.3} fluid)
        {:keys [round taper underbody-smooth wheels-faired cooling-open lift]
         :or   {round 0.5 taper 0.5 underbody-smooth 0.5 wheels-faired 0.3
                cooling-open 0.2 lift 0.2}} shape
        comp  {:forebody  (* (get-in m [:forebody :max])
                             (- 1.0 (* (get-in m [:forebody :round-k]) round)))
               :afterbody (* (get-in m [:afterbody :max])
                             (- 1.0 (* (get-in m [:afterbody :taper-k]) taper)))
               :wheels    (* (get-in m [:wheels :max])
                             (- 1.0 (* (get-in m [:wheels :faired-k]) wheels-faired)))
               :underbody (* (get-in m [:underbody :max])
                             (- 1.0 (* (get-in m [:underbody :smooth-k]) underbody-smooth)))
               :cooling   (* (get-in m [:cooling :per-open]) cooling-open)
               :friction  (friction-cd m f length)
               :induced   (* (get-in m [:induced :k]) lift)}
        cd    (reduce + (vals comp))]
    {:Cd        cd
     :CdA       (* cd frontal-area)
     :Re        (reynolds f length)
     :breakdown (into {} (map (fn [[k v]] [k (/ (Math/round (* v 1.0e4)) 1.0e4)]) comp))
     :fluid     f
     :solver    :rom-buildup}))

;; Register on the shared CAE contract — callers do (cae.solver/solve case);
;; a kami-cfd :lbm backend registers alongside for the same case shape.
(defmethod cae/solve :rom-buildup [case] (solve case))
