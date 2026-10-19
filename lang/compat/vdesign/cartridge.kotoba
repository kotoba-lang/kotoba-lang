(ns vdesign.cartridge
  "Replaceable Mg/MgH2 hydride cartridge sizing — STOICHIOMETRY ONLY.

  The magnesium-hydrogen system boundary needs the on-board H2 store to
  be representable as a REPLACEABLE cartridge, kept strictly separate from
  the load-bearing magnesium structure (system rule 7). This namespace
  sizes the cartridge's hydride bed from the H2 mass the energy-flow
  plane already computes (`vdesign.powertrain/size-fcev` → :h2-kg) using
  the EXACT desorption stoichiometry:

      MgH2 ⇌ Mg + H2

  What this module deliberately IS NOT:

    - NOT a thermodynamic model. Heat of desorption, equilibrium plateau
      pressure, kinetics, and thermal management are UNMEASURED here and
      are reported as such (:unmeasured), never as numbers. No Mg/MgH2
      performance constant is invented anywhere in this file.
    - NOT the tank-system model. `size-fcev`'s :grav-frac / :tank-overhead
      700-bar-type-IV tank figures remain the pressure-vessel path; this
      cartridge is a DIFFERENT storage option and is marked as such. It
      contributes NO mass/volume claims beyond the hydride bed itself —
      bed containment, heat exchange, and insulation are all part of
      :unmeasured system overhead.
    - NOT load-bearing structure. The cartridge is a replaceable unit;
      nothing here feeds the structural (crash/SF) path.

  The only numbers used are IUPAC standard atomic weights (2021):
  A_r(Mg) = 24.305, A_r(H) = 1.008 — provenance carried in
  `molar-masses-g-per-mol` and echoed in every result."
  (:require [vdesign.powertrain :as pt]))

(def molar-masses-g-per-mol
  "IUPAC standard atomic weights (2021), g/mol. Single provenance point."
  {:mg 24.305
   :h  1.008
   ;; derived compound molar mass: MgH2 = A_r(Mg) + 2·A_r(H)
   :mgh2 (+ 24.305 (* 2 1.008))})

;; stoichiometry: 1 mol MgH2 ⇌ 1 mol Mg + 1 mol H2
(defn- mole-frac-h2-in-mgh2
  "Mass fraction of H2 in MgH2 — exact stoichiometric identity:
  2·A_r(H) / (A_r(Mg) + 2·A_r(H))."
  []
  (/ (* 2 (:h molar-masses-g-per-mol))
     (:mgh2 molar-masses-g-per-mol)))

(defn- mole-frac-mg-in-mgh2
  "Mass fraction of Mg in MgH2 — exact stoichiometric identity."
  []
  (/ (:mg molar-masses-g-per-mol)
     (:mgh2 molar-masses-g-per-mol)))

(def unmeasured
  "Properties a real MgH2 cartridge needs that this contract does NOT
  claim. Presence by name; absence of any numeric claim is the contract."
  {:heat-of-desorption   :unmeasured
   :plateau-pressure     :unmeasured
   :kinetics             :unmeasured
   :thermal-management   :unmeasured
   :cycling-degradation  :unmeasured
   :bed-system-overhead  :unmeasured   ; containment + HX + insulation mass/volume
   :cartridge-volume-L   :unmeasured})

(defn size-cartridge
  "Size the replaceable MgH2 hydride bed for `h2-kg` of usable hydrogen —
  the H2 mass `vdesign.powertrain/size-fcev` computes for the mission
  (:h2-kg in its result). Returns the bed's stoichiometric composition
  with full provenance and explicit unmeasured gaps.

  opts (both optional, both provenance-preserving):
    :source      — map carried through into the result verbatim (e.g. the
                   full size-fcev result this demand came from)
    :label       — case/id-style label for the datom log

  Returns {:kind :mgmh2-cartridge :h2-kg :mg-mass-kg :mgh2-mass-kg
           :h2-wt-frac :provenance {...} :unmeasured unmeasured}."
  [h2-kg & [{:keys [source label] :as _opts}]]
  (when-not (and (number? h2-kg) (pos? h2-kg))
    (throw (ex-info "h2-kg must be a positive number of kg"
                    {:h2-kg h2-kg})))
  (let [w-h2  (mole-frac-h2-in-mgh2)
        w-mg  (mole-frac-mg-in-mgh2)
        mgh2  (/ h2-kg w-h2)               ; hydride bed mass holding h2-kg
        mg    (* mgh2 w-mg)]               ; Mg fraction of that bed
    {:kind         :mgmh2-cartridge
     :label        (or label "vehicle/mgmh2-cartridge")
     :h2-kg        h2-kg
     :mg-mass-kg   mg
     :mgh2-mass-kg mgh2
     :h2-wt-frac   w-h2
     ;; replaceable unit — NOT load-bearing structure (system rule 7)
     :load-bearing false
     :provenance   {:molar-masses molar-masses-g-per-mol
                    :basis        "exact stoichiometry MgH2 ⇌ Mg + H2"
                    :h2-demand    (if (map? source) (:h2-kg source) :direct)
                    :source       (or source :unspecified)}
     :unmeasured   unmeasured}))

(defn size-for-fcev-store
  "Convenience composition with the energy-flow plane: size the cartridge
  for the H2 demand of `fcev-store` (a `vdesign.powertrain/size-fcev`
  result). Requires that result's :kind to be :fcev so a BEV result can
  never silently flow into a hydrogen storage contract."
  [fcev-store & [opts]]
  (when-not (= :fcev (:kind fcev-store))
    (throw (ex-info "expected a size-fcev result"
                    {:kind (:kind fcev-store)})))
  (size-cartridge (:h2-kg fcev-store)
                  (assoc (or opts {})
                         :source fcev-store
                         :label  (or (:label opts) "vehicle/fcev-mgmh2-cartridge"))))

(defn charge
  "Reverse (absorption) direction: given the bed's current Mg mass and a
  supply of H2, return the bed mass after full hydrogenation — exact
  stoichiometry again, so charge/discharge round-trips close by identity
  (the test suite proves this, not a tolerance-based assertion)."
  [mg-mass-kg h2-supply-kg]
  (when-not (and (pos? mg-mass-kg) (pos? h2-supply-kg))
    (throw (ex-info "mg-mass-kg and h2-supply-kg must be positive"
                    {:mg-mass-kg mg-mass-kg :h2-supply-kg h2-supply-kg})))
  (let [w-h2 (mole-frac-h2-in-mgh2)
        ;; H2 the bed can take up: 1 mol Mg absorbs 1 mol H2
        h2-capable (* mg-mass-kg (/ w-h2 (- 1.0 w-h2)))
        h2-stored  (min h2-supply-kg h2-capable)]
    {:mg-mass-kg     (- mg-mass-kg (* (/ h2-stored w-h2) (- 1.0 w-h2)))
     :h2-consumed-kg h2-stored
     :mgh2-mass-kg   (/ h2-stored w-h2)
     ;; shortfall floored at 0.0: float round-trip error can leave ~1e-16,
     ;; which is arithmetic noise, not unreacted supply
     :h2-shortfall-kg (max 0.0 (- h2-capable h2-supply-kg))}))

(def J-per-kWh pt/J-per-kWh)   ; re-export for callers composing units
