(ns aero.model
  "Reduced-order aerodynamic drag model coefficients — the single edn-tunable
  table (mirrors vehicle-design-actor's `powertrain/tech`). These drive a
  component BUILD-UP estimate of Cd, the honest concept-stage method used
  before a full CFD run: total drag = Σ component contributions, each scaled
  by a shape descriptor in [0,1]. Pure potential-flow/panel methods give
  ZERO drag (d'Alembert), so drag must come from these viscous/separation
  correlations — not from a panel solve.

  Retune here as the body family or the reference data changes; a future
  high-fidelity `kami-cfd` (lattice-Boltzmann) backend plugs in behind the
  same case/datom interface and can re-fit these coefficients."
  (:require [clojure.string :as str]))

(def ^:const rho-air 1.225)   ; kg/m^3
(def ^:const mu-air  1.81e-5) ; Pa·s

(def default
  "Component build-up coefficients. `:max` is the bluff/worst contribution;
  the shape descriptor (round/taper/faired/smooth in [0,1]) removes a
  `*-k` fraction of it. Friction is a turbulent flat-plate Cf × wetted-area
  ratio; cooling scales with open frontal-area ratio; induced ∝ lift."
  {:forebody  {:max 0.100 :round-k  0.60}   ; nose rounding
   :afterbody {:max 0.160 :taper-k  0.72}   ; boat-tail / fastback (dominant)
   :wheels    {:max 0.055 :faired-k 0.60}   ; wheels + wheelhouses (~20-25%)
   :underbody {:max 0.060 :smooth-k 0.80}   ; flat floor vs rough underbody
   :cooling   {:per-open 0.040}             ; per unit open cooling area ratio
   :friction  {:cf-a 0.074 :cf-re-exp -0.2  ; turbulent flat plate
               :wet-over-frontal 12.0}      ; S_wet / A_frontal (car ≈ 12)
   :induced   {:k 0.012}})                  ; lift-induced drag coefficient
