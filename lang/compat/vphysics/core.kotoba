(ns vphysics.core
  "Shared vehicle-physics primitives — the single source of the road-load and
  energy math that vehicle-design-actor (battery/H2 sizing) and aero-clj (range
  loop) both need. Previously duplicated in each; commonized here.

  Purpose: DOMAIN PHYSICS MATH only. SI internally (J, kg, m, W, N). No datoms,
  no solver dispatch — those are datom-clj and cae-solver-clj."
  (:require [clojure.string :as str]))

;; ─────────────────────────── SI constants ───────────────────────────

(def ^:const g 9.81)              ; m/s^2
(def ^:const rho-air 1.225)       ; kg/m^3
(def ^:const mu-air 1.81e-5)      ; Pa·s
(def ^:const J-per-kWh 3.6e6)
(def ^:const LHV-H2-J 1.20e8)     ; J/kg (hydrogen lower heating value)

;; Steady rolling+aero at one speed undercounts a real drive cycle (ignores
;; accel/transient energy braking only partly returns, plus high-speed aero of
;; mixed driving). This blends a single point up to ≈WLTP-class cycle energy.
(def ^:const cycle-factor 1.55)

;; ───────────────────────── road-load model ──────────────────────────

(defn road-load-J-per-km
  "Mechanical wheel energy to cover 1 km at `mass-kg` on a representative
  cycle. Rolling + aero, cycle-blended, less the regenerated share.
  `body`: {:crr :cd :frontal-area :avg-speed}."
  [{:keys [crr cd frontal-area avg-speed]} mass-kg regen-credit]
  (let [v      avg-speed
        f-roll (* crr mass-kg g)
        f-aero (* 0.5 rho-air cd frontal-area v v)
        e-mech (* (+ f-roll f-aero) 1000.0 cycle-factor)]
    (* e-mech (- 1.0 regen-credit))))

(defn aux-J-per-km
  "Auxiliary electrical energy per km — a function of TIME, so it scales
  inversely with speed."
  [p-aux-w avg-speed]
  (* p-aux-w (/ 1000.0 avg-speed)))

;; ───────────────────────────── aero ─────────────────────────────────

(defn aero-force-N
  "Aerodynamic drag force F = ½·ρ·Cd·A·v²."
  [cd area v]
  (* 0.5 rho-air cd area (* v v)))

(defn reynolds [rho v length-m mu]
  (/ (* rho v length-m) mu))

(defn range-effect
  "How a computed Cd vs an assumed prior moves range, holding non-aero road
  load fixed via `aero-share` (share of tractive energy that is aero at the
  design cruise — ~0.5 highway, ~0.35 mixed). Energy ∝ force; range ∝ 1/energy."
  [cd prior-cd & [{:keys [aero-share] :or {aero-share 0.40}}]]
  (let [e-ratio (+ (- 1.0 aero-share) (* aero-share (/ cd prior-cd)))]
    {:prior-cd     prior-cd
     :computed-cd  cd
     :energy-ratio e-ratio
     :range-mult   (/ 1.0 e-ratio)}))
