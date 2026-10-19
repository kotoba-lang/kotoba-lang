(ns vdesign.dc-bus
  "Time-domain DC-bus power split between the fuel cell and the traction
  buffer battery for an FCEV design — the missing dynamic half of
  `vdesign.powertrain/size-fcev`, which sizes the buffer battery only as
  a lumped mass (`:buffer-mass-kg`) and never checks that the buffer can
  actually load-level a mission.

  Boundary advanced: `:dc-dc-and-dc-bus` + `:buffer-battery` on the
  vehicle energy-flow plane (system-scope.edn architecture vector).

  The contract is pure conservation-of-power bookkeeping over a demand
  profile:

    per interval:  P-fc + P-batt = P-demand          (discharge)
    per interval:  P-fc - P-charge  = P-demand       (fc charging batt)
    battery energy = Σ P-batt · dt  bounded by capacity

  Dispatch policy (deterministic, disclosed):
    - the fuel cell carries min(P-demand, :fc-max-kw);
    - any residual capability (:fc-max-kw − P-fc, when the demand is
      below the fc rating) recharges the battery at that same rate —
      the load-leveling purpose of a buffer;
    - demand the fc cannot carry comes from the battery (discharge);
    - a demand that exceeds fc-max AND an empty battery is a hard
      deficit, reported per interval — never silently absorbed.

  Provenance and unmeasured quantities:
    - `:fc-max-kw` comes from the caller (normally the design concept's
      `:p-peak-kw`, the same number `size-fcev` uses for stack sizing).
    - `:buffer-capacity-kwh` comes from the caller; the existing
      `vdesign.powertrain/tech` table value (`:buffer-kWh` 1.5) is the
      workspace's declared production-technology figure. It is treated
      here as the FULL usable capacity; no separate usable-fraction
      (depth-of-discharge) is applied because none is recorded for the
      buffer in that table — applying an invented one would fabricate a
      measurement.
    - Round-trip battery efficiency is UNMEASURED and is deliberately
      NOT modeled: battery flows are treated as lossless at the bus.
      Every result carries `:unmeasured {:battery-round-trip-eff true
      :battery-dod true :dcdc-losses true}` so a consumer cannot read
      the lossless bookkeeping as a measured efficiency claim.
    - DC/DC converter losses (fc→bus, buffer→bus) are likewise
      unmeasured and not modeled.

  Refusals: non-numeric/negative demand, non-positive fc-max or
  capacity, non-positive dt, empty profile."
  (:require [vdesign.powertrain :as powertrain]))

(defn- require-positive-number [x k]
  (when-not (and (number? x) (pos? x))
    (throw (ex-info "non-physical input" {:field k :value x}))))

(defn- require-kw-list [xs]
  (when-not (and (sequential? xs) (seq xs))
    (throw (ex-info "empty or non-sequential demand profile" {:profile xs})))
  (doseq [[i p] (map-indexed vector xs)]
    (when-not (and (number? p) (not (neg? p)))
      (throw (ex-info "non-physical demand" {:index i :power-kw p})))))

(defn power-split
  "Split a per-interval DC-bus electrical power demand profile (kW,
  uniformly sampled every `dt` seconds) between a fuel cell limited to
  `:fc-max-kw` and a buffer battery of `:buffer-capacity-kwh`.

  Returns
    {:intervals [{:i :demand-kw :fc-kw :batt-discharge-kw :fc-charge-kw
                  :soc-end-kwh :deficit-kw} ...]
     :energy {:demand-kwh :fc-out-kwh :batt-discharge-kwh :batt-charge-kwh
              :fc-curtail-kwh :deficit-kwh}
     :soc {:start-kwh :min-kwh :end-kwh}
     :feasible? boolean        ; no deficit interval
     :provenance {:fc-max-kw .. :buffer-capacity-kwh .. :dt-s ..}
     :unmeasured {:battery-round-trip-eff true :battery-dod true
                  :dcdc-losses true}}"
  [{:keys [demand-profile-kw dt-s fc-max-kw buffer-capacity-kwh
           initial-soc-kwh]}]
  (require-kw-list demand-profile-kw)
  (require-positive-number dt-s :dt-s)
  (require-positive-number fc-max-kw :fc-max-kw)
  (require-positive-number buffer-capacity-kwh :buffer-capacity-kwh)
  (let [soc0 (cond
               (nil? initial-soc-kwh)   buffer-capacity-kwh ; default full
               (and (number? initial-soc-kwh)
                    (>= initial-soc-kwh 0.0)
                    (<= initial-soc-kwh buffer-capacity-kwh)) initial-soc-kwh
               :else (throw (ex-info "initial SoC outside capacity"
                                     {:initial-soc-kwh initial-soc-kwh
                                      :buffer-capacity-kwh buffer-capacity-kwh})))
        step (fn [{:keys [soc] :as acc} [i demand]]
               (let [;; fc base output serves min(demand, rating)
                     fc-base    (min demand fc-max-kw)
                     ;; battery covers what the fc cannot
                     deficit0   (- demand fc-base)
                     batt-dis   (if (pos? deficit0)
                                  (min deficit0 (* soc (/ 3600.0 dt-s))) ; kW deliverable this interval (soc kWh over dt s)
                                  0.0)
                     soc-after-dis (- soc (* batt-dis dt-s (/ 3600.0))) ; kWh
                     headroom   (- buffer-capacity-kwh soc-after-dis)
                     ;; load-leveling: fc runs ABOVE demand up to its rating,
                     ;; and that extra REAL output charges the battery —
                     ;; bounded by headroom; the rest of the rating simply
                     ;; stays unused (curtailed, no dump resistor modeled).
                     residual   (- fc-max-kw fc-base)
                     charge-kw  (min residual (* headroom (/ 3600.0 dt-s)))
                     curtail-kw (- residual charge-kw)
                     fc         (+ fc-base charge-kw)
                     soc-end    (+ soc-after-dis (* charge-kw dt-s (/ 3600.0)))
                     ;; unmet demand: fc short + battery exhausted
                     deficit    (max 0.0 (- deficit0 batt-dis))
                     row {:i i
                          :demand-kw demand
                          :fc-kw fc
                          :batt-discharge-kw batt-dis
                          :fc-charge-kw charge-kw
                          :fc-curtail-kw curtail-kw
                          :soc-end-kwh soc-end
                          :deficit-kw deficit}]
                 (-> acc
                     (update :intervals conj row)
                     (update :min-soc min soc-end)
                     (update :deficit-kwh + (* deficit dt-s (/ 3600.0)))
                     (update :batt-dis-kwh + (* batt-dis dt-s (/ 3600.0)))
                     (update :batt-chg-kwh + (* charge-kw dt-s (/ 3600.0)))
                     (update :curtail-kwh + (* curtail-kw dt-s (/ 3600.0)))
                     (assoc :soc soc-end))))
        init {:intervals [] :min-soc soc0 :soc soc0 :deficit-kwh 0.0
              :batt-dis-kwh 0.0 :batt-chg-kwh 0.0 :curtail-kwh 0.0}
        out  (reduce step init (map-indexed vector demand-profile-kw))
        rows (:intervals out)
        demand-kwh (reduce + (map #(* % dt-s (/ 3600.0)) demand-profile-kw))
        fc-out-kwh (reduce + (map #(* (:fc-kw %) dt-s (/ 3600.0)) rows))]
    {:intervals rows
     :energy {:demand-kwh demand-kwh
              :fc-out-kwh fc-out-kwh
              :batt-discharge-kwh (:batt-dis-kwh out)
              :batt-charge-kwh (:batt-chg-kwh out)
              :fc-curtail-kwh (:curtail-kwh out)
              :deficit-kwh (:deficit-kwh out)}
     :soc {:start-kwh soc0
           :min-kwh (:min-soc out)
           :end-kwh (:soc out)}
     :feasible? (zero? (:deficit-kwh out))
     :provenance {:fc-max-kw fc-max-kw
                  :buffer-capacity-kwh buffer-capacity-kwh
                  :dt-s dt-s
                  :initial-soc-kwh initial-soc-kwh}
     :unmeasured {:battery-round-trip-eff true
                  :battery-dod true
                  :dcdc-losses true}}))

(defn power-split-for-fcev
  "Compose with `vdesign.powertrain/size-fcev`: take the fuel-cell rating
  from the design concept's `:p-peak-kw` (the same number `size-fcev`
  uses to size the stack) and the buffer capacity from the workspace
  tech table (`vdesign.powertrain/tech` `:fcev` `:buffer-kWh`).
  REFUSES a `:bev` result (`:kind` guard) so a battery result can never
  silently flow into a hydrogen powertrain contract — the same boundary
  discipline `vdesign.cartridge/size-for-fcev-store` applies on the
  storage side. The source result is echoed into `:provenance`."
  [powertrain-result {:keys [demand-profile-kw dt-s initial-soc-kwh]}]
  (when-not (= :fcev (:kind powertrain-result))
    (throw (ex-info "not a FCEV powertrain result"
                    {:kind (:kind powertrain-result)})))
  (let [p-peak (:p-peak-kW powertrain-result)
        buffer-cap (get-in powertrain/tech [:fcev :buffer-kWh])]
    (when-not (and (number? p-peak) (pos? p-peak))
      (throw (ex-info "powertrain result lacks a usable :p-peak-kW"
                      {:p-peak-kW p-peak})))
    (assoc (power-split {:demand-profile-kw demand-profile-kw
                         :dt-s dt-s
                         :fc-max-kw p-peak
                         :buffer-capacity-kwh buffer-cap
                         :initial-soc-kwh initial-soc-kwh})
           :source {:powertrain-result-kind :fcev
                    :p-peak-kW p-peak
                    :buffer-capacity-source "vdesign.powertrain/tech :fcev :buffer-kWh"})))
