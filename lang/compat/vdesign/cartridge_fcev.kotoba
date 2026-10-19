(ns vdesign.cartridge-fcev
  "Cartridge-fed FCEV mass closure — the replaceable Mg/MgH2 hydride
  cartridge as the on-board H2 store INSIDE the PhysicsGovernor's mass
  spiral.

  What existed before this contract:

    - `vdesign.powertrain/size-fcev` sizes the 700-bar type-IV tank path
      (tech-table :grav-frac / :tank-overhead) — the only H2 store the
      mass closure (`vdesign.physics/close-mass`) could represent.
    - `vdesign.cartridge/size-cartridge` sizes the stoichiometric MgH2
      bed for an externally given H2 mass — but NOTHING fed it back into
      the vehicle: the cartridge could not participate in the mass
      spiral, so the system boundary this actor exists for
      (a REPLACEABLE cartridge, separate from load-bearing structure)
      was not representable at vehicle level.

  This namespace closes that gap by composition only:

    each spiral step = `pt/size-fcev` (energy, H2 mass, stack, buffer,
    motor — untouched) → discard the 700-bar tank figures → store mass =
    stoichiometric bed mass (`cart/size-cartridge`) + a DECLARED cartridge
    overhead (containment + heat exchange + insulation, a per-cartridge
    property the caller must declare with provenance).

  The overhead is NOT invented here. A MgH2 bed needs containment, HX and
  insulation; those masses are unmeasured in this workspace. Supplying no
  overhead would silently under-count store mass and bias the whole
  spiral light — so this contract refuses LOUDLY (ex-info) unless the
  caller declares both a positive `:overhead-kg` and a provenance map.
  `vdesign.cartridge/unmeasured` remains the honest gap list; the
  declared overhead moves ONE entry (bed-system-overhead) from
  unmeasured to declared-by-caller, and the declaration itself travels
  in the result.

  The store volume is :unmeasured (no cartridge volumetric figure exists
  in this workspace), so `vdesign.physics/check` reports the packaging
  gate as UNVERIFIED instead of silently passing or failing it.

  Replaceable unit, still never load-bearing (system rule 7): the result
  carries :load-bearing false and :tank-path-superseded true, so no
  downstream consumer can mistake bed+overhead for the 700-bar system
  the tech table describes."
  (:require [vdesign.powertrain :as pt]
            [vdesign.cartridge :as cart]
            [vdesign.physics :as physics]))

(defn sized-store
  "One mass-spiral step with the MgH2 cartridge as the H2 store. Same
  shape contract as `pt/size-fcev` (:kind :fcev-cartridge), so the
  governor's mass arithmetic needs no special-casing beyond the volume.

  `overhead` map (required):
    :overhead-kg         — positive number, the declared per-cartridge
                           containment+HX+insulation mass
    :overhead-provenance — map, WHO declared it and on what basis
                           (carried through verbatim)
    :label               — optional datom-log label"
  [glider concept mass-kg {:keys [overhead-kg overhead-provenance label]}]
  (when-not (and (number? overhead-kg) (pos? overhead-kg))
    (throw (ex-info
            (str "cartridge overhead-kg must be a positive DECLARED number — "
                 "containment + heat-exchange + insulation mass is unmeasured "
                 "in this workspace, and omitting it would silently bias the "
                 "mass spiral light. Declare it with provenance or use the "
                 "700-bar tank path.")
            {:overhead-kg overhead-kg})))
  (when-not (map? overhead-provenance)
    (throw (ex-info
            "overhead-provenance map is required — an unprovenanced overhead mass is indistinguishable from an invented constant"
            {:overhead-provenance overhead-provenance})))
  (let [tank (pt/size-fcev glider concept mass-kg)
        bed  (cart/size-cartridge (:h2-kg tank)
                                  {:source tank
                                   :label (or label "vehicle/fcev-cartridge")})
        store-mass (+ (:mgh2-mass-kg bed) overhead-kg)]
    {:kind                 :fcev-cartridge
     :h2-kg                (:h2-kg tank)
     :consumption-kWh-km   (:consumption-kWh-km tank)
     :b2w-eff              (:b2w-eff tank)
     :b2w-eff-source       (:b2w-eff-source tank)
     :bed-mass-kg          (:mgh2-mass-kg bed)
     :mg-mass-kg           (:mg-mass-kg bed)
     :h2-wt-frac           (:h2-wt-frac bed)
     :cartridge-overhead-kg overhead-kg
     :overhead-provenance  overhead-provenance
     :store-mass-kg        store-mass
     :stack-mass-kg        (:stack-mass-kg tank)
     :buffer-mass-kg       (:buffer-mass-kg tank)
     :propulsion-mass-kg   (:propulsion-mass-kg tank)
     :mass-kg              (+ store-mass (:propulsion-mass-kg tank))
     ;; no volumetric figure for a hydride cartridge exists here — the
     ;; governor reports the packaging gate as :unverified, never as pass
     :volume-L             :unmeasured
     :load-bearing         false   ; replaceable unit (system rule 7)
     :tank-path-superseded true   ; 700-bar grav-frac/tank-overhead NOT used
     :unmeasured           cart/unmeasured}))

(defn close-mass
  "Run the governor's bounded mass spiral with the cartridge store.
  Returns `vdesign.physics/close-mass`'s result with the cartridge-shaped
  :store. The spiral is the governor's own — same iteration budget,
  same tolerance, same divergence semantics."
  [glider concept overhead]
  (physics/close-mass :fcev glider concept
                      {:store-fn (fn [g c m] (sized-store g c m overhead))}))

(defn check
  "Full feasibility verdict with the cartridge store. Same gates as
  `vdesign.physics/check` (mass-closure, gross-mass, store-fraction),
  except the packaging gate is UNVERIFIABLE (store volume is
  :unmeasured): it is reported in :unverified-gates and does NOT count
  as a pass — a :closes? true from this contract is an energy/mass
  closure only, never a packaging clearance."
  [glider concept overhead]
  (physics/check :fcev glider concept
                 {:store-fn (fn [g c m] (sized-store g c m overhead))}))
