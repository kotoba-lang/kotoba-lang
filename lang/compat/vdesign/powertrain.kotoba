(ns vdesign.powertrain
  "Energy-system models for a clean-sheet vehicle — the ONLY part of the
  design that branches on powertrain. Everything upstream (glider, road
  load, package envelope) is propulsion-agnostic; the two functions here
  size the on-board energy store from the *same* mission energy.

  All sizing is first-principles, not a lookup table:

    road load → mechanical wheel energy per km
              → source energy per km   (÷ path efficiency, per powertrain)
              → store size for the required range
              → store MASS, which feeds back into road load (the mass
                spiral the PhysicsGovernor closes).

  Units are SI internally (J, kg, m, W); kWh / L surface in the result
  maps for human-readable specs. Constants reflect ~2026 production
  technology and are the single place to retune as cells/tanks improve.

  SI constants and the road-load model are commonized in `vphysics-clj`
  (shared with aero-clj); only the pack/tank/stack technology table is
  powertrain-specific and lives here."
  (:require [vphysics.core :as phys]))

(def J-per-kWh phys/J-per-kWh)
(def LHV-H2-J  phys/LHV-H2-J)

;; Pack / tank / stack technology — pack-LEVEL figures (not bare cell).
(def tech
  {:bev  {:b2w-eff      0.88      ; battery→wheel (inverter·motor·gearbox)
          :dcdc-eff     0.95      ; battery→12V/aux bus
          :regen-credit 0.15      ; share of mech demand recovered on a mixed cycle
          :dod          0.90      ; usable depth of discharge
          :pack-Wh-kg   175.0     ; pack-level gravimetric energy
          :pack-kWh-L   0.45      ; pack-level volumetric energy
          :motor-kW-kg  5.0}      ; traction motor+inverter specific power
   :fcev {:b2w-eff      0.88      ; buffer-battery/motor→wheel
          :fc-elec-eff  0.53      ; H2(LHV)→electric, system (stack+BoP)
          :regen-credit 0.15
          :grav-frac    0.055     ; usable H2 / tank-system mass (700 bar type-IV)
          :h2-kg-L      0.040     ; stored H2 density @700 bar (≈40 kg/m^3)
          :tank-overhead 2.2      ; external system vol / internal H2 vol
          :fc-kW-kg     2.0       ; fuel-cell system specific power (incl BoP)
          :motor-kW-kg  5.0
          :buffer-kWh   1.5       ; traction buffer battery
          :buffer-Wh-kg 150.0}})

;; Road load + aux energy come from the shared vphysics-clj.
(def road-load-J-per-km phys/road-load-J-per-km)
(def aux-J-per-km        phys/aux-J-per-km)

;; ───────────────── b2w path efficiency + motor provenance ──────────────────

(defn compose-b2w-eff
  "Battery→wheel path efficiency for energy sizing, with motor provenance.

   The tech-table :b2w-eff (0.88) folds inverter·motor·gearbox into one
   declared 2026-production assumption. When a SOLVED traction-motor
   efficiency is available (:rom-motor :eff-peak from kami-engine-motor),
   the path can be corrected for the actually-sized motor — but ONLY if the
   motor efficiency assumed inside the tech default is also declared
   (:b2w-ref-motor-eff): the inverter·gearbox share of 0.88 has never been
   separated out here, so without that reference the correction factor
   (motor-eff / ref-motor-eff) is unknowable.

   - no :motor-eff            → tech default, :b2w-eff-source :tech-default
   - :motor-eff, no reference → LOUD refusal (ex-info); never assume the split
   - both                     → b2w-eff × motor-eff / ref, :motor-corrected,
                                carrying both provenance values

   Unknown remains explicitly unmeasured; this contract never invents the
   inverter·gearbox split."
  [tech-default {:keys [motor-eff b2w-ref-motor-eff]}]
  (cond
    (nil? motor-eff)
    {:b2w-eff tech-default :b2w-eff-source :tech-default}

    (nil? b2w-ref-motor-eff)
    (throw (ex-info (str "motor-eff supplied without :b2w-ref-motor-eff — the "
                         "inverter·gearbox share of the b2w-eff default is "
                         "unmeasured, so no correction factor exists. Declare "
                         "the reference motor efficiency (:b2w-ref-motor-eff) "
                         "or omit :motor-eff.")
                    {:motor-eff motor-eff}))

    :else
    (let [m-eff (double motor-eff)
          r-eff (double b2w-ref-motor-eff)]
      (when-not (and (pos? m-eff) (pos? r-eff) (<= m-eff 1.0) (<= r-eff 1.0))
        (throw (ex-info "motor efficiencies must be in (0, 1]"
                        {:motor-eff m-eff :b2w-ref-motor-eff r-eff})))
      {:b2w-eff (* tech-default (/ m-eff r-eff))
       :b2w-eff-source :motor-corrected
       :b2w-motor-eff m-eff
       :b2w-ref-motor-eff r-eff})))

;; ───────────────────────── BEV energy system ─────────────────────────

(defn size-bev
  "Battery-electric energy system for `range-km` at operating `mass-kg`.
  Returns {:kind :bev :consumption-kWh-km .. :usable-kWh .. :nominal-kWh ..
           :mass-kg .. :volume-L .. :p-peak-kW ..}."
  [glider concept mass-kg]
  (let [{:keys [dcdc-eff regen-credit dod pack-Wh-kg pack-kWh-L motor-kW-kg]}
        (:bev tech)
        ;; b2w path efficiency: motor-corrected when a solved motor efficiency
        ;; AND its tech-default reference are both declared (provenance kept);
        ;; otherwise the tech default, flagged :tech-default.
        {:keys [b2w-eff b2w-eff-source b2w-motor-eff b2w-ref-motor-eff]}
        (compose-b2w-eff (:b2w-eff (:bev tech)) concept)
        {:keys [range-km]} concept
        e-mech   (road-load-J-per-km glider mass-kg regen-credit)
        e-aux    (aux-J-per-km (:p-aux-w concept) (:avg-speed glider))
        batt-J   (+ (/ e-mech b2w-eff) (/ e-aux dcdc-eff))   ; at the battery, per km
        cons-kWh (/ batt-J J-per-kWh)
        usable   (* cons-kWh range-km)
        nominal  (/ usable dod)
        mass     (/ (* nominal 1000.0) pack-Wh-kg)
        volume   (/ nominal pack-kWh-L)
        motor    (or (:motor-mass-kg concept)            ; computed by motor-clj (#3)
                     (/ (:p-peak-kw concept) motor-kW-kg))]
    {:kind :bev
     :b2w-eff b2w-eff
     :b2w-eff-source b2w-eff-source
     :b2w-motor-eff b2w-motor-eff
     :b2w-ref-motor-eff b2w-ref-motor-eff
     :consumption-kWh-km cons-kWh
     :usable-kWh usable
     :nominal-kWh nominal
     :propulsion-mass-kg motor
     :store-mass-kg mass
     :mass-kg (+ mass motor)
     :volume-L volume
     :p-peak-kW (:p-peak-kw concept)}))

;; ──────────────────────── FCEV energy system ─────────────────────────

(defn size-fcev
  "Hydrogen fuel-cell energy system for `range-km` at operating `mass-kg`.
  H2(LHV) → electric (stack) → wheel (buffer/motor). Returns the parallel
  shape to `size-bev`, with H2 mass + tank/stack/buffer breakdown."
  [glider concept mass-kg]
  (let [{:keys [regen-credit grav-frac h2-kg-L tank-overhead
                fc-kW-kg motor-kW-kg buffer-kWh buffer-Wh-kg]}
        (:fcev tech)
        ;; stack efficiency: a computed echem (:rom-fc) value injected by the
        ;; design graph overrides the tech default (#3 wiring).
        fc-elec-eff (or (:fc-elec-eff concept) (get-in tech [:fcev :fc-elec-eff]))
        ;; b2w path efficiency: same motor-provenance contract as :bev.
        {:keys [b2w-eff b2w-eff-source b2w-motor-eff b2w-ref-motor-eff]}
        (compose-b2w-eff (:b2w-eff (:fcev tech)) concept)
        {:keys [range-km]} concept
        e-mech   (road-load-J-per-km glider mass-kg regen-credit)
        e-aux    (aux-J-per-km (:p-aux-w concept) (:avg-speed glider))
        ;; electric energy at the bus per km, then up-convert through the stack
        elec-J   (+ (/ e-mech b2w-eff) e-aux)
        h2-J     (/ elec-J fc-elec-eff)                ; H2 LHV energy per km
        h2-kg    (/ (* h2-J range-km) LHV-H2-J)
        tank     (/ h2-kg grav-frac)                   ; full tank-system mass
        tank-vol (* (/ h2-kg h2-kg-L) tank-overhead)   ; external L
        stack    (/ (:p-peak-kw concept) fc-kW-kg)
        buffer   (/ (* buffer-kWh 1000.0) buffer-Wh-kg)
        motor    (or (:motor-mass-kg concept)            ; computed by motor-clj (#3)
                     (/ (:p-peak-kw concept) motor-kW-kg))]
    {:kind :fcev
     :b2w-eff b2w-eff
     :b2w-eff-source b2w-eff-source
     :b2w-motor-eff b2w-motor-eff
     :b2w-ref-motor-eff b2w-ref-motor-eff
     :h2-kg h2-kg
     :consumption-kWh-km (/ h2-J J-per-kWh)            ; H2 LHV basis
     :tank-mass-kg tank
     :stack-mass-kg stack
     :buffer-mass-kg buffer
     :propulsion-mass-kg (+ stack buffer motor)
     ;; `tank` (h2-kg / grav-frac) is already the FULL tank-system mass --
     ;; grav-frac is usable-H2 / tank-system-mass, so the H2 fuel is already
     ;; included in `tank`. Adding h2-kg again here double-counts the fuel.
     :store-mass-kg tank
     :mass-kg (+ tank stack buffer motor)
     :volume-L tank-vol
     :p-peak-kW (:p-peak-kw concept)}))

(defn size
  "Dispatch on powertrain. `mass-kg` is the current operating-mass estimate
  in the closure spiral (the PhysicsGovernor calls this repeatedly)."
  [powertrain glider concept mass-kg]
  (case powertrain
    :bev  (size-bev  glider concept mass-kg)
    :fcev (size-fcev glider concept mass-kg)
    (throw (ex-info "unknown powertrain" {:powertrain powertrain}))))
