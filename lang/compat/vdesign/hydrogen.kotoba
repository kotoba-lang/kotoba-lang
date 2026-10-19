(ns vdesign.hydrogen
  "Per-interval hydrogen consumption and cartridge depletion on the FCEV
  energy-flow plane — the link between the DC-bus plane's fuel-cell output
  (`vdesign.dc-bus/power-split` → :intervals :fc-kw) and the replaceable
  Mg/MgH2 cartridge (`vdesign.cartridge/size-cartridge`).

  Boundary advanced: `:controlled-hydrogen-reactor` + `:replaceable-mg-or-mgh2-cartridge`
  on the vehicle energy-flow plane (system-scope.edn architecture vector).
  Before this namespace, the repo could size a cartridge from a range figure
  and split power in time, but could NOT answer \"after this drive cycle,
  how much H2 (and MgH2) has the cartridge delivered, and when does it run
  out?\" — a demonstrated downstream block for any reactor-control or
  cartridge-swap contract.

  The contract is pure bookkeeping with one physical conversion:

    per interval:  m-h2 = E-elec / (eta-fc · LHV-H2)      (LHV, energy basis)
    per interval:  m-MgH2 = m-h2 / w-h2(MgH2)             (exact desorption stoichiometry)
    cartridge:     remaining = store − Σ m-h2, floored at 0

  Provenance and unmeasured quantities:
    - `:fc-elec-eff` is REQUIRED from the caller and echoed in :provenance —
      no default is applied here, because an efficiency is an operating-point
      claim, not a constant. (`vdesign.powertrain/tech` :fc-elec-eff 0.53 is
      the workspace's declared production-technology figure; a caller may
      pass it, and then the provenance trail records that choice.)
    - LHV of H2 is the shared physics constant `vdesign.powertrain/LHV-H2-J`
      (single provenance point, re-exported below). Not re-declared.
    - w-h2(MgH2) is the exact stoichiometric mass fraction from the IUPAC
      2021 atomic weights in `vdesign.cartridge/molar-masses-g-per-mol` —
      identical arithmetic to the cartridge namespace, not a copy of it.
    - UNMEASURED and NOT modeled: operating-point dependence of eta-fc
      (polarization, BoP parasitics — see kami-engine-echem's rom-fc),
      desorption kinetics / heat of desorption / plateau pressure (the
      reactor is not simulated), minimum H2 recirculation/purge flows, and
      start-up/shutdown transients. Every result carries :unmeasured so a
      consumer cannot read the flat-efficiency bookkeeping as a measured
      reactor model.

  Refusals: non-numeric/negative fc power, non-positive dt, efficiency
  outside (0, 1], non-positive store, empty profile. A depleted cartridge
  is NOT an exception — it is a reported shortfall, mirroring
  `vdesign.dc-bus`'s deficit handling."

  (:require [vdesign.powertrain :as pt]
            [vdesign.cartridge :as cartridge]))

(def LHV-H2-J pt/LHV-H2-J)   ; J/kg, LHV — re-export for callers composing units

(defn- require-positive-number [x k]
  (when-not (and (number? x) (pos? x))
    (throw (ex-info "non-physical input" {:field k :value x}))))

(defn- require-kw-list [xs]
  (when-not (and (sequential? xs) (seq xs))
    (throw (ex-info "empty or non-sequential fc profile" {:profile xs})))
  (doseq [[i p] (map-indexed vector xs)]
    (when-not (and (number? p) (not (neg? p)))
      (throw (ex-info "non-physical fc power" {:index i :power-kw p})))))

(defn- require-eff [x]
  (when-not (and (number? x) (pos? x) (<= x 1.0))
    (throw (ex-info "fc-elec-eff must be in (0, 1]"
                    {:fc-elec-eff x}))))

(defn- w-h2-in-mgh2
  "Mass fraction of H2 in MgH2 — exact stoichiometric identity, computed
  from the cartridge namespace's public provenance table (same IUPAC 2021
  atomic weights the sizing path uses)."
  []
  (/ (* 2 (:h cartridge/molar-masses-g-per-mol))
     (:mgh2 cartridge/molar-masses-g-per-mol)))

(defn consumption-profile
  "Convert a fuel-cell electrical output profile into hydrogen and MgH2
  consumption, and (when a store is given) track cartridge depletion.

  Inputs:
    :fc-profile-kw  — per-interval fc electrical output, kW (≥ 0)
    :dt-s           — uniform interval length, s (> 0)
    :fc-elec-eff    — H2(LHV) → electric efficiency, in (0, 1]; caller-
                      supplied, echoed in provenance; no default
    :store-h2-kg    — optional; H2 capacity of the cartridge (e.g. the
                      :h2-kg a size-cartridge result was sized for)

  Returns:
    {:intervals  [{:i :fc-kw :e-kwh :h2-kg :mgh2-kg
                   :store-remaining-kg :h2-shortfall-kg} ...]
     :h2  {:total-kg :total-mgh2-kg :total-e-kwh :total-shortfall-kg}
     :store {:start-kg :end-kg :depleted-at-i}
     :provenance {:fc-elec-eff .. :lhv-h2-j-per-kg .. :dt-s ..
                  :w-h2-mgh2 .. :store-h2-kg ..}
     :unmeasured {:fc-eff-operating-point true
                  :desorption-kinetics true
                  :heat-of-desorption true
                  :plateau-pressure true
                  :min-h2-recirculation true}}"
  [{:keys [fc-profile-kw dt-s fc-elec-eff store-h2-kg]}]
  (require-kw-list fc-profile-kw)
  (require-positive-number dt-s :dt-s)
  (require-eff fc-elec-eff)
  (when (some? store-h2-kg) (require-positive-number store-h2-kg :store-h2-kg))
  (let [wh2  (w-h2-in-mgh2)
        lhv  LHV-H2-J
        jpk  pt/J-per-kWh
        step (fn [{:keys [remaining] :as acc} [i p-kw]]
               (let [e-kwh (* p-kw dt-s (/ 3600.0))
                     h2-demand (* e-kwh jpk (/ fc-elec-eff) (/ lhv))
                     ;; a depleted cartridge is a shortfall, not an exception
                     h2-used (if remaining (min h2-demand remaining) h2-demand)
                     shortfall (- h2-demand h2-used)
                     remaining' (when remaining (- remaining h2-used))
                     row {:i i
                          :fc-kw p-kw
                          :e-kwh e-kwh
                          :h2-kg h2-demand
                          :mgh2-kg (/ h2-demand wh2)
                          :store-remaining-kg remaining'
                          :h2-shortfall-kg shortfall}]
                 (-> acc
                     (update :intervals conj row)
                     (update :h2-total + h2-demand)
                     (update :mgh2-total + (/ h2-demand wh2))
                     (update :shortfall + shortfall)
                     (update :e-total + e-kwh)
                     (assoc :remaining remaining'))))
        init {:intervals [] :h2-total 0.0 :mgh2-total 0.0
              :shortfall 0.0 :e-total 0.0
              :remaining (some-> store-h2-kg double)}
        out  (reduce step init (map-indexed vector fc-profile-kw))
        rows (:intervals out)
        ;; first interval whose demand exceeded what the (then-)remaining
        ;; store could deliver — nil while the cartridge lasts
        depleted-at (some (fn [{:keys [h2-shortfall-kg i]}]
                            (when (pos? h2-shortfall-kg) i))
                          rows)]
    {:kind :hydrogen-consumption
     :intervals rows
     :h2 {:total-kg (:h2-total out)
          :total-mgh2-kg (:mgh2-total out)
          :total-e-kwh (:e-total out)
          :total-shortfall-kg (:shortfall out)}
     :store (when store-h2-kg
              {:start-kg store-h2-kg
               :end-kg (:remaining out)
               :depleted-at-i depleted-at})
     :provenance {:fc-elec-eff fc-elec-eff
                  :lhv-h2-j-per-kg lhv
                  :j-per-kwh jpk
                  :dt-s dt-s
                  :w-h2-mgh2 wh2
                  :store-h2-kg store-h2-kg}
     :unmeasured {:fc-eff-operating-point true
                  :desorption-kinetics true
                  :heat-of-desorption true
                  :plateau-pressure true
                  :min-h2-recirculation true}}))

(defn consumption-for-split
  "Compose with the DC-bus plane: take a `vdesign.dc-bus/power-split`
  result and produce the hydrogen/cartridge consumption its fuel-cell
  output implies. The fc electrical energy is taken per-interval from the
  split result (:fc-kw × dt), so curtailment and fc→battery charging are
  reflected exactly as the split contract reported them — this namespace
  does not re-derive the split.

  `:fc-elec-eff` and optional `:store-h2-kg` are as in consumption-profile."
  [split {:keys [fc-elec-eff store-h2-kg]}]
  (when-not (and (map? split) (sequential? (:intervals split))
                 (map? (:provenance split))
                 (number? (get-in split [:provenance :dt-s])))
    (throw (ex-info "expected a vdesign.dc-bus/power-split result"
                    {:received (select-keys split [:intervals :provenance])})))
  (let [dt (get-in split [:provenance :dt-s])
        fc-profile (mapv :fc-kw (:intervals split))]
    (consumption-profile {:fc-profile-kw fc-profile
                          :dt-s dt
                          :fc-elec-eff fc-elec-eff
                          :store-h2-kg store-h2-kg})))
