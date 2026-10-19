(ns vdesign.endurance
  "H2 inventory ENDURANCE from a MEASURED initial inventory and a
  per-interval electrical demand schedule — the vehicle-plane answer to
  *how far / how long does this H2 inventory take me*.

  System boundary advanced: `:replaceable-mg-or-mgh2-cartridge` +
  `:controlled-hydrogen-reactor` + `:pem-fuel-cell` on the vehicle
  energy-flow plane (scripts/hermes-magnesium-systems-bots/system-scope.edn
  on origin/main).

  Where the inputs come from (composition BY DATA ONLY — no unlanded
  dependency, no new constant):
    - `:h2-inventory-kg` — a MEASURED inventory. The natural upstream is
      the CAE plane's `:h2-tank-storage` result (uniform tank state from
      measured absolute pressure and temperature, kotoba-lang/
      kami-engine-cae-solver). This contract performs NO measurement and
      asserts none; it requires the caller to carry inventory provenance
      (`:h2-inventory-source`, mandatory non-blank, fail-closed).
    - `:demand-kw` / `:dt-s` — a per-interval fuel-cell electrical demand
      schedule, normally the `:fc-kw` column of
      `vdesign.dc-bus/power-split` (landed on main).
    - `:fc-elec-eff` — REQUIRED from the caller with `:eff-source`
      provenance. An efficiency is an operating-point claim, not a
      constant; the workspace's declared tech figure
      (`vdesign.powertrain/tech :fc-elec-eff 0.53`) may be passed and is
      then echoed verbatim in provenance.
    - `:speeds-mps` — OPTIONAL (n = intervals + 1), a speed-vs-time grid
      on the same `:dt-s` (e.g. from `vdesign.dutycycle`). Present ⇒
      `:range-km` is reported as the distance covered until depletion.

  Physics per interval (exact energy accounting, SI):
      E-elec [J]   = P [kW] · dt [s] · 1000
      m-H2  [kg]   = E-elec / (η-fc · LHV-H2)          (LHV energy basis)
      remaining    = inventory − Σ m-H2, floored at 0
      shortfall    = max(0, m-H2 − available)           (REPORTED, never
                                                          thrown — the
                                                          deficit discipline
                                                          dc-bus already uses)
      range-km     = Σ v-mid · dt up to and including the depletion
                     interval (absent speeds ⇒ :range-km :unmeasured)

  Constants used: `vdesign.powertrain/LHV-H2-J` (H2 lower heating value,
  the SAME single provenance point `size-fcev` uses) and its
  `J-per-kWh`. Nothing else. No Mg/MgH2, tank, or efficiency constant is
  invented anywhere in this file.

  Explicitly unmeasured, carried on every result: tank thermal derating,
  pressure-dependent delivery (regulator minimum pressure), leakage /
  boiloff integral, efficiency variation across the operating envelope,
  start-stop consumption. Refusals: non-positive inventory/dt, negative
  demand, efficiency outside (0, 1], blank provenance strings, speeds
  length mismatch, empty demand."
  (:require [vdesign.powertrain :as pt]))

(defn- finite? [x]
  (and (number? x) (not (or (Double/isNaN (double x))
                            (Double/isInfinite (double x))))))

(defn- demand->h2-kg
  "Exact LHV energy-basis conversion of one interval's electrical demand."
  [kw dt-s fc-elec-eff]
  (let [energy-j (* (double kw) (double dt-s) 1000.0)]
    (/ energy-j (* (double fc-elec-eff) pt/LHV-H2-J))))

(defn endurance
  "Per-interval H2 draw, depletion, and (optional) range for a measured
  initial H2 inventory under a demand schedule.

  Case keys:
    :h2-inventory-kg      measured initial H2 mass (positive number)
    :h2-inventory-source  provenance string for that measurement
                          (mandatory non-blank, fail-closed)
    :demand-kw            per-interval fuel-cell electrical demand [kW],
                          n ≥ 1, each ≥ 0 (a NEGATIVE demand is a refusal:
                          regen is handled upstream by the DC-bus split)
    :dt-s                 uniform interval seconds (positive)
    :fc-elec-eff          fuel-cell electrical efficiency on the LHV basis,
                          caller-supplied operating-point claim in (0, 1]
    :eff-source           provenance string for the efficiency (mandatory
                          non-blank, fail-closed)
    :speeds-mps           optional speed grid, (n+1) samples, each ≥ 0 —
                          enables :range-km
    :label / :case/id     optional echo for the datom log

  Returns {:kind :h2-endurance :h2-inventory-kg :intervals [...]
           :h2-used-kg :depleted-at :endurance-h :endurance-s
           :range-km (or :unmeasured) :provenance {...} :unmeasured {...}}
  where each interval is {:index :fc-kw :h2-kg :remaining-kg
  :shortfall-kg} and :depleted-at is the index of the FIRST shortfall
  interval (nil when the schedule completes with inventory to spare)."
  [{:keys [h2-inventory-kg h2-inventory-source demand-kw dt-s
           fc-elec-eff eff-source speeds-mps label case/id]}]
  (when-not (and (finite? h2-inventory-kg) (pos? (double h2-inventory-kg)))
    (throw (ex-info "endurance: :h2-inventory-kg must be a positive number"
                    {:field :h2-inventory-kg :value h2-inventory-kg})))
  (when-not (and (string? h2-inventory-source) (re-find #"\S" h2-inventory-source))
    (throw (ex-info "endurance: :h2-inventory-source must be a non-blank provenance string"
                    {:field :h2-inventory-source :value h2-inventory-source})))
  (when-not (and (string? eff-source) (re-find #"\S" eff-source))
    (throw (ex-info "endurance: :eff-source must be a non-blank provenance string"
                    {:field :eff-source :value eff-source})))
  (when-not (and (finite? fc-elec-eff) (pos? (double fc-elec-eff))
                 (<= (double fc-elec-eff) 1.0))
    (throw (ex-info "endurance: :fc-elec-eff must be in (0, 1]"
                    {:field :fc-elec-eff :value fc-elec-eff})))
  (when-not (and (finite? dt-s) (pos? (double dt-s)))
    (throw (ex-info "endurance: :dt-s must be a positive number"
                    {:field :dt-s :value dt-s})))
  (when-not (and (sequential? demand-kw) (seq demand-kw))
    (throw (ex-info "endurance: :demand-kw must be a non-empty sequence"
                    {:demand-kw demand-kw})))
  (doseq [[i p] (map-indexed vector demand-kw)]
    (when-not (and (finite? p) (not (neg? (double p))))
      (throw (ex-info "endurance: demand must be a non-negative number (regen is handled upstream by the DC-bus split)"
                      {:index i :power-kw p}))))
  (when speeds-mps
    (when-not (sequential? speeds-mps)
      (throw (ex-info "endurance: :speeds-mps must be a sequence of speeds"
                      {:speeds-mps speeds-mps})))
    (when-not (= (count speeds-mps) (inc (count demand-kw)))
      (throw (ex-info "endurance: :speeds-mps must have (count demand + 1) samples on the same :dt-s grid"
                      {:speeds-count (count speeds-mps)
                       :intervals (count demand-kw)})))
    (doseq [[i v] (map-indexed vector speeds-mps)]
      (when-not (and (finite? v) (not (neg? (double v))))
        (throw (ex-info "endurance: speeds must be non-negative"
                        {:index i :speed-mps v})))))
  (let [n        (count demand-kw)
        intervals
        (loop [remaining (double h2-inventory-kg), i 0, acc []]
          (if (= i n)
            acc
            (let [kw        (nth demand-kw i)
                  demand-kg (demand->h2-kg kw dt-s fc-elec-eff)
                  avail     (max 0.0 remaining)
                  delivered (min demand-kg avail)
                  short     (- demand-kg delivered)]
              (recur (- avail delivered)
                     (inc i)
                     (conj acc {:index i
                                :fc-kw kw
                                :h2-kg demand-kg
                                :remaining-kg (- avail delivered)
                                :shortfall-kg short})))))
        v-mids   (when speeds-mps
                   (mapv (fn [[a b]] (/ (+ (double a) (double b)) 2.0))
                         (partition 2 1 speeds-mps)))
        depleted  (some (fn [{:keys [index shortfall-kg]}]
                          (when (pos? shortfall-kg) index))
                        intervals)
        used      (reduce + (map :h2-kg intervals))
        ;; distance covered until (and including) the depletion interval;
        ;; beyond depletion the vehicle is out of H2 and no distance is
        ;; credited — an unclipped schedule never truncates.
        upto      (if depleted depleted n)
        range-m   (when v-mids
                    (reduce + 0.0 (map (fn [v] (* v (double dt-s)))
                                       (take upto v-mids))))
        endurance-s (* upto (double dt-s))]
    (cond-> {:kind :h2-endurance
             :h2-inventory-kg h2-inventory-kg
             :intervals intervals
             :h2-used-kg used
             :depleted-at depleted
             :endurance-s endurance-s
             :endurance-h (/ endurance-s 3600.0)
             :range-km (if (some? range-m) (/ range-m 1000.0) :unmeasured)
             :provenance {:h2-inventory-source h2-inventory-source
                          :fc-elec-eff fc-elec-eff
                          :eff-source eff-source
                          :lhv-h2-j pt/LHV-H2-J
                          :lhv-source "vdesign.powertrain/LHV-H2-J (same single provenance point as vdesign.powertrain/size-fcev)"}
             :unmeasured {:tank-thermal-derating true
                          :pressure-dependent-delivery true
                          :leakage-or-boiloff true
                          :efficiency-operating-point-variation true
                          :start-stop-consumption true}}
      label (assoc :label label)
      id (assoc :case/id id))))
