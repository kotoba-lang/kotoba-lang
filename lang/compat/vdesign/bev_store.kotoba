(ns vdesign.bev-store
  "Time-domain traction-battery state-of-charge over a signed electrical
  demand profile for a BEV — the missing dynamic half of
  `vdesign.powertrain/size-bev`, which sizes the pack only from a
  cycle-blended road-load number and never checks that the pack can
  actually carry a mission (regen included).

  Boundary advanced: `:traction-battery` (BEV store) on the vehicle
  energy-flow plane — the BEV counterpart of `vdesign.dc-bus/power-split`,
  which load-levels the FCEV's buffer battery against a fuel cell. Here
  there is no fc: the battery is the only source, so the whole signed
  demand (regen as negative) falls on the pack.

  Pure conservation-of-energy bookkeeping per interval (uniform `dt-s`):

    discharge (demand > 0): delivered = min(energy-needed, soc)
                            deficit  = energy-needed − delivered
    charge    (demand < 0): accepted  = min(energy-in, capacity − soc)
                            curtail  = energy-in − accepted
    soc' = soc + accepted − delivered

  Deficits and curtailed regen are REPORTED per interval — never clipped,
  never silently absorbed (same deficit discipline as `dc-bus`).

  Composability: `soc-for-cycle` takes a `vdesign.dutycycle/demand-profile`
  result directly (its own `:demand-kw` + `:interval-s`), so regen
  reporting, aux load, and b2w efficiency flow through exactly as the
  duty cycle reported them.

  Provenance and unmeasured quantities:
    - `:usable-capacity-kwh` is required from the caller. The tech table
      (`vdesign.powertrain/tech :bev`) records `:dod 0.90`; passing
      `(* nominal-kwh (:dod tech))` keeps that provenance with the caller.
      This contract never applies an invented usable fraction of its own.
    - Round-trip battery efficiency, DoD derating dynamics, pack aging,
      and C-rate limits are UNMEASURED and deliberately NOT modeled:
      battery flows are lossless at the bus. Every result carries
      `:unmeasured {:battery-round-trip-eff true :pack-aging true
      :crate-limits true}` so a consumer cannot read the lossless
      bookkeeping as a measured efficiency claim.

  Refusals: non-numeric demand entries, empty profile, non-positive
  `:dt-s` or `:usable-capacity-kwh`, initial soc outside [0, capacity]."
  (:require [kotoba.lang.text :as str]))

(defn- require-positive-number [x k]
  (when-not (and (number? x) (pos? x))
    (throw (ex-info "non-physical input" {:field k :value x}))))

(defn- require-signed-kw-list [xs]
  (when-not (and (sequential? xs) (seq xs))
    (throw (ex-info "empty or non-sequential demand profile" {:profile xs})))
  (doseq [[i p] (map-indexed vector xs)]
    (when-not (number? p)
      (throw (ex-info "non-numeric demand entry" {:index i :power-kw p})))))

(defn soc-profile
  "Per-interval traction-battery SoC bookkeeping for a signed demand
  profile (kW; charge/regen < 0), uniformly sampled every `dt-s` seconds.

  Inputs:
    {:demand-kw           [signed kW per interval]
     :dt-s                seconds per interval
     :usable-capacity-kwh usable (DoD-applied) pack energy — caller-provenance
     :initial-soc-kwh     optional; default = full usable capacity}

  Returns
    {:intervals [{:i :demand-kw :soc-start-kwh :soc-end-kwh
                  :deficit-kw :curtail-kw} ...]
     :energy {:demand-kwh        signed ∫ demand dt
              :discharge-kwh     ∫ delivered discharge dt
              :charge-kwh        ∫ accepted charge dt
              :deficit-kwh       unmet traction energy
              :curtail-kwh       regen energy the full pack could not take}
     :soc {:start-kwh :min-kwh :end-kwh}
     :feasible? boolean        ; no deficit interval
     :provenance {:usable-capacity-kwh .. :dt-s .. :initial-soc-kwh ..}
     :unmeasured {:battery-round-trip-eff true :pack-aging true
                  :crate-limits true}}"
  [{:keys [demand-kw dt-s usable-capacity-kwh initial-soc-kwh]}]
  (require-signed-kw-list demand-kw)
  (require-positive-number dt-s :dt-s)
  (require-positive-number usable-capacity-kwh :usable-capacity-kwh)
  (let [soc0 (cond
               (nil? initial-soc-kwh)   usable-capacity-kwh ; default full
               (and (number? initial-soc-kwh)
                    (>= initial-soc-kwh 0.0)
                    (<= initial-soc-kwh usable-capacity-kwh)) initial-soc-kwh
               :else (throw (ex-info "initial SoC outside usable capacity"
                                     {:initial-soc-kwh initial-soc-kwh
                                      :usable-capacity-kwh usable-capacity-kwh})))
        step (fn [{:keys [soc] :as acc} [i demand]]
               (let [soc-start soc
                     ;; discharge: the pack is the only source
                     needed   (if (pos? demand) (* demand dt-s (/ 3600.0)) 0.0)
                     delivered (min needed soc)
                     ;; charge: bounded by usable headroom; the rest is
                     ;; regen the pack cannot take (reported, not clipped)
                     incoming (if (neg? demand) (* (- demand) dt-s (/ 3600.0)) 0.0)
                     accepted  (min incoming (- usable-capacity-kwh soc-start))
                     soc-end   (+ soc-start accepted (- delivered))
                     deficit-kw  (if (pos? needed) (* (- needed delivered) (/ 3600.0 dt-s)) 0.0)
                     curtail-kw  (if (pos? incoming) (* (- incoming accepted) (/ 3600.0 dt-s)) 0.0)
                     row {:i i
                          :demand-kw demand
                          :soc-start-kwh soc-start
                          :soc-end-kwh soc-end
                          :deficit-kw deficit-kw
                          :curtail-kw curtail-kw}]
                 (-> acc
                     (update :intervals conj row)
                     (update :min-soc min soc-end)
                     (update :dis-kwh + delivered)
                     (update :chg-kwh + accepted)
                     (update :deficit-kwh + (* deficit-kw dt-s (/ 3600.0)))
                     (update :curtail-kwh + (* curtail-kw dt-s (/ 3600.0)))
                     (assoc :soc soc-end))))
        init {:intervals [] :min-soc soc0 :soc soc0 :dis-kwh 0.0
              :chg-kwh 0.0 :deficit-kwh 0.0 :curtail-kwh 0.0}
        out (reduce step init (map-indexed vector demand-kw))
        rows (:intervals out)
        demand-kwh (reduce + (map #(* % dt-s (/ 3600.0)) demand-kw))]
    {:intervals rows
     :energy {:demand-kwh demand-kwh
              :discharge-kwh (:dis-kwh out)
              :charge-kwh (:chg-kwh out)
              :deficit-kwh (:deficit-kwh out)
              :curtail-kwh (:curtail-kwh out)}
     :soc {:start-kwh soc0
           :min-kwh (:min-soc out)
           :end-kwh (:soc out)}
     :feasible? (every? #(zero? (:deficit-kw %)) rows)
     :provenance {:usable-capacity-kwh usable-capacity-kwh
                  :dt-s dt-s
                  :initial-soc-kwh soc0}
     :unmeasured {:battery-round-trip-eff true
                  :pack-aging true
                  :crate-limits true}}))

(defn soc-for-cycle
  "Compose directly with a `vdesign.dutycycle/demand-profile` result:
  takes the cycle's own `:demand-kw` and `:interval-s`, plus the
  caller-sized usable pack energy, and returns `soc-profile`'s result.
  Validates the input shape (rejects non-cycle maps) so a mis-composed
  call fails loudly instead of producing a fabricated profile."
  [cycle-result usable-capacity-kwh & [{:keys [initial-soc-kwh]}]]
  (when-not (and (map? cycle-result)
                 (sequential? (:demand-kw cycle-result))
                 (number? (:interval-s cycle-result)))
    (throw (ex-info "soc-for-cycle expects a vdesign.dutycycle/demand-profile result"
                    {:received (if (map? cycle-result) (vec (keys cycle-result)) :not-a-map)})))
  (soc-profile {:demand-kw (:demand-kw cycle-result)
                :dt-s (:interval-s cycle-result)
                :usable-capacity-kwh usable-capacity-kwh
                :initial-soc-kwh initial-soc-kwh}))
