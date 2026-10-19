(ns vdesign.dutycycle
  "Drive-cycle → per-interval DC-bus demand profile.

  The energy-flow plane on `origin/main` sizes stores from a single
  cycle-blended road-load number (`vphysics/road-load-J-per-km`, factor
  1.55) and the vehicle sim time-steps only the crash event
  (`vdesign.simphysics`). No contract anywhere converts a speed-vs-time
  drive cycle into the per-interval electrical demand a DC-bus
  load-leveling contract (cf. the open `agent/dc-bus-power-split`
  branch) needs as input. This namespace is that smallest upstream
  step: it produces the demand profile from first-principles forces,
  composing BY DATA ONLY — it requires nothing from any unlanded PR
  and changes no existing namespace.

  Physics per interval (uniform `:dt-s`), all SI:

    v-mid   = (v[i] + v[i+1]) / 2            ; trapezoid midpoint speed
    a       = (v[i+1] - v[i]) / dt           ; interval acceleration
    F-wheel = m·a + m·g·crr + ½·ρ·Cd·A·v-mid²
    P-mech  = F-wheel · v-mid

  - `P-mech > 0` (traction): electrical demand at the battery is
    `P-mech / :b2w-eff + :aux-w`.
  - `P-mech < 0` (deceleration): the caller-supplied fraction
    `:regen-frac` of `|P-mech|` is recovered as NEGATIVE electrical
    demand (battery charge); the rest is friction-brake heat.
    `:regen-frac` is an explicit caller input with provenance — this
    contract never assumes a recovery fraction, and the DC-bus
    load-leveling caller may equally pass 0.

  Everything is conservation-of-energy and Newton's second law on
  caller-supplied quantities; NO efficiency, drag, rolling, or
  regen constant is invented here. `:g`, `:rho-air` are the exact
  same physical constants `vphysics.core` already publishes and are
  re-declared here only to keep this namespace dependency-free.

  Fails closed: refuses non-positive dt/mass/eff, negative speeds,
  negative non-negative-quantities (crr, cd, frontal-area, aux), and
  `:regen-frac` outside [0, 1]. Negative electrical demand is REPORTED,
  never clipped or silently absorbed."
  (:require [kotoba.lang.text :as str]))

(def ^:const g 9.81)           ; m/s^2 — same value as vphysics.core/g
(def ^:const rho-air 1.225)    ; kg/m^3 — same value as vphysics.core/rho-air

(defn- num? [x] (number? x))

(defn- require-pos! [m k]
  (let [v (get m k)]
    (when-not (and (num? v) (pos? v))
      (throw (ex-info (str "dutycycle: :" (subs (str k) 1) " must be a positive number")
                      {:key k :value v})))
    v))

(defn- require-nonneg! [m k]
  (let [v (get m k)]
    (when-not (and (num? v) (not (neg? v)))
      (throw (ex-info (str "dutycycle: :" (subs (str k) 1) " must be a non-negative number")
                      {:key k :value v})))
    v))

(defn- require-frac! [m k]
  (let [v (get m k)]
    (when-not (and (num? v) (<= 0.0 v 1.0))
      (throw (ex-info (str "dutycycle: :" (subs (str k) 1) " must be in [0, 1]")
                      {:key k :value v})))
    v))

(defn demand-profile
  "Per-interval DC-bus electrical demand for `speeds-mps` sampled at a
  uniform `:dt-s` grid (n samples → n−1 intervals).

  Inputs:
    cycle   {:speeds-mps [v0 v1 ...] :dt-s seconds}
    glider  {:crr :cd :frontal-area}            ; rolling/aero inputs
    mass-kg vehicle test mass
    opts    {:b2w-eff battery→wheel efficiency (caller-provenance)
             :regen-frac recovered share of decel mech power [0,1]
             :aux-w    auxiliary electrical load}

  Returns
    {:interval-s n
     :v-mid-mps  [...]
     :accel-mps2 [...]
     :mech-kw    [...]   ; wheel power, signed
     :demand-kw  [...]   ; battery-side electrical power, signed (charge < 0)
     :regen-kw   [...]   ; recovered portion of decel power (≥ 0)
     :brake-heat-kw [...] ; dissipated portion of decel power (≥ 0)
     :energy-demand-kwh n      ; ∫ demand-kw dt (signed)
     :energy-traction-kwh n    ; ∫ max(0, mech-kw) dt
     :energy-regen-kwh n       ; ∫ regen-kw dt
     :energy-brake-heat-kwh n
     :aux-energy-kwh n
     :unmeasured {:tire-transients true :grade true :aero-yaw true
                  :drivetrain-inertia true :regen-efficiency true
                  :b2w-eff-source (caller's own provenance string, if given)}
     :provenance {:b2w-eff source :regen-frac source}}"
  [cycle glider mass-kg
   {:keys [b2w-eff-source regen-frac-source]
    :or {b2w-eff-source "caller-declared (unmeasured here)"
         regen-frac-source "caller-declared (unmeasured here)"}
    :as opts}]
  (let [dt        (require-pos! cycle :dt-s)
        speeds    (:speeds-mps cycle)
        _         (when-not (and (sequential? speeds) (>= (count speeds) 2))
                    (throw (ex-info "dutycycle: :speeds-mps must be a sequence of ≥ 2 numbers"
                                    {:speeds-mps speeds})))
        _         (doseq [v speeds]
                    (when-not (and (num? v) (not (neg? v)))
                      (throw (ex-info "dutycycle: every speed must be a non-negative number"
                                      {:speed v}))))
        mass      (require-pos! {:mass-kg mass-kg} :mass-kg)
        crr       (require-nonneg! glider :crr)
        cd        (require-nonneg! glider :cd)
        area      (require-nonneg! glider :frontal-area)
        eff       (require-pos! opts :b2w-eff)
        rf        (require-frac! opts :regen-frac)
        aux       (require-nonneg! opts :aux-w)
        vs        (mapv double speeds)
        n         (dec (count vs))
        row       (fn [i]
                    (let [v0  (nth vs i)
                          v1  (nth vs (inc i))
                          vm  (/ (+ v0 v1) 2.0)
                          a   (/ (- v1 v0) dt)
                          f-roll (* mass g crr)
                          f-aero  (* 0.5 rho-air cd area vm vm)
                          f-trac  (+ (* mass a) f-roll f-aero)
                          p-mech  (* f-trac vm)
                          aux-kw  (/ (* aux dt) 1000.0 dt)     ; aux W → kW (per-interval avg)
                          [d-kw regen-kw heat-kw]
                          (if (pos? p-mech)
                            [(+ (/ p-mech 1000.0 eff) aux-kw) 0.0 0.0]
                            (let [recov (* (- p-mech) rf)]     ; W recovered
                              ;; net demand = aux draw − regen charge (charge can net negative)
                              [(- aux-kw (/ recov 1000.0))
                               (/ recov 1000.0)
                               (/ (* (- p-mech) (- 1.0 rf)) 1000.0)]))]
                      {:v-mid vm :a a
                       :mech-kw (/ p-mech 1000.0)
                       :demand-kw d-kw :regen-kw regen-kw :brake-heat-kw heat-kw}))
        rows       (mapv row (range n))
        kwh        (fn [kw-key] (/ (* dt (reduce + 0.0 (map kw-key rows))) 3600.0))]  ; ΣkW·dt → kWh
      {:interval-s dt
       :v-mid-mps  (mapv :v-mid rows)
       :accel-mps2 (mapv :a rows)
       :mech-kw    (mapv :mech-kw rows)
       :demand-kw  (mapv :demand-kw rows)
       :regen-kw   (mapv :regen-kw rows)
       :brake-heat-kw (mapv :brake-heat-kw rows)
       :energy-demand-kwh      (kwh :demand-kw)
       :energy-traction-kwh    (/ (* dt (reduce + 0.0 (map (fn [r] (max 0.0 (:mech-kw r))) rows)))
                                  3600.0)
       :energy-regen-kwh       (kwh :regen-kw)
       :energy-brake-heat-kwh  (kwh :brake-heat-kw)
       :aux-energy-kwh         (/ (* aux n dt) 3.6e6)
       :unmeasured {:tire-transients true :grade true :aero-yaw true
                    :drivetrain-inertia true :regen-efficiency true
                    :b2w-eff-source (if (str/blank? (str b2w-eff-source))
                                      "unmeasured" b2w-eff-source)}
       :provenance {:b2w-eff eff :b2w-eff-source b2w-eff-source
                    :regen-frac rf :regen-frac-source regen-frac-source}}))
