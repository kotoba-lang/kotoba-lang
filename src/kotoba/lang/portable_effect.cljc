(ns kotoba.lang.portable-effect
  "Portable, data-only ability requests shared by CLJ, CLJS, workerd adapters,
  and Wasm component hosts.

  A request contains no callback and carries no ambient authority. Dispatch is
  always host-owned and goes through `guard-component-ability-call`, so the
  same effect row, grant, local policy, target, operation, limits, and receipt
  contract applies on every host."
  (:require [clojure.string :as str]
            [kotoba.lang.capability-host :as capability-host]
            [kotoba.lang.capability-values :as capability-values]))

(def version 1)

(def required-keys
  [:kotoba.effect/version
   :effect/id
   :effect/call
   :effect/effects
   :effect/ability
   :effect/input])

(defn request
  "Construct a portable effect request. `ability` must be a component-bound
  capability value; validation remains fail-closed at dispatch time."
  [{:keys [id call effects ability input]}]
  {:kotoba.effect/version version
   :effect/id id
   :effect/call call
   :effect/effects (set effects)
   :effect/ability ability
   :effect/input input})

(defn validate-request
  "Validate the closed portable request envelope. Provider callbacks, policy,
  grants, credentials, and host bindings are intentionally not representable
  in this value."
  [effect]
  (let [extra (when (map? effect)
                (seq (remove (set required-keys) (keys effect))))
        problems
        (cond-> []
          (not (map? effect))
          (conj {:problem :effect/not-a-map})

          (and (map? effect)
               (not= version (:kotoba.effect/version effect)))
          (conj {:problem :effect/version-unsupported})

          (and (map? effect)
               (not (and (string? (:effect/id effect))
                         (not (str/blank? (:effect/id effect)))
                         (<= (count (:effect/id effect)) 128))))
          (conj {:problem :effect/id-invalid})

          (and (map? effect)
               (not (or (keyword? (:effect/call effect))
                        (symbol? (:effect/call effect)))))
          (conj {:problem :effect/call-invalid})

          (and (map? effect) (not (set? (:effect/effects effect))))
          (conj {:problem :effect/row-invalid})

          (and (map? effect)
               (not (capability-values/component-capability?
                     (:effect/ability effect))))
          (conj {:problem :effect/ability-invalid})

          extra
          (conj {:problem :effect/unknown-keys :keys (vec extra)}))]
    {:ok? (empty? problems) :problems problems}))

(defn dispatch
  "Dispatch one portable request through the typed component ability gate.

  HOST:
  {:handlers     {[target operation] (fn [concrete-cap input] result)}
   :cacao-grants [...]
   :local-policy {...}
   :now          yyyy-mm-dd
   :record!      receipt-recorder, optional}

  Missing providers and malformed requests fail before any provider call.
  Provider selection uses the ability's bound target+operation, never
  guest-controlled executable code."
  [host effect]
  (let [validation (validate-request effect)]
    (if-not (:ok? validation)
      {:kotoba.host/ok? false
       :kotoba.host/denied :portable-effect-invalid
       :kotoba.effect/problems (:problems validation)}
      (let [ability (:effect/ability effect)
            provider-key [(:cap/target ability) (:cap/operation ability)]
            handler (get (:handlers host) provider-key)]
        (if-not (fn? handler)
          {:kotoba.host/ok? false
           :kotoba.host/denied :provider-absent
           :kotoba.effect/id (:effect/id effect)
           :kotoba.effect/provider provider-key}
          (capability-host/guard-component-ability-call
           {:call (:effect/call effect)
            :requested ability
            :effect-row (:effect/effects effect)
            :cacao-grants (:cacao-grants host)
            :local-policy (:local-policy host)
            :now (:now host)
            :record! (:record! host)
            :handler (fn [concrete]
                       (handler concrete (:effect/input effect)))}))))))

(defn dispatch-all
  "Sequentially dispatch requests and stop at the first denial. Sequential
  order is part of the portable semantics; hosts may not silently parallelize
  authority-bearing effects."
  [host effects]
  (loop [remaining (seq effects)
         outcomes []]
    (if-not remaining
      {:kotoba.host/ok? true :kotoba.host/outcomes outcomes}
      (let [outcome (dispatch host (first remaining))
            outcomes' (conj outcomes outcome)]
        (if (:kotoba.host/ok? outcome)
          (recur (next remaining) outcomes')
          {:kotoba.host/ok? false
           :kotoba.host/denied (:kotoba.host/denied outcome)
           :kotoba.host/outcomes outcomes'})))))
