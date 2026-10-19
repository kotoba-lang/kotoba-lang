(ns kototama.runtime-lifecycle
  "Runtime-neutral lifecycle for a restartable Kototama guest.

  This is the state contract implemented by AIUEOS's native supervisor.  It
  deliberately contains no kernel reboot transition: a runtime fault clears
  the volatile guest workspace and advances the runtime epoch while the host,
  network and update authority remain alive.")

(def schema "kototama.runtime-lifecycle/v1")
(def states #{:empty :staged :starting :ready :running :degraded :stopped})
(def required-bundle-keys
  #{:bundle-id :component-cid :guest-cid :model-cid :runtime-abi})

(defn initial-state []
  {:schema schema :state :empty :epoch 0 :restarts 0 :invocations 0
   :failures 0 :bundle nil :kernel-reboot? false})

(defn- valid-bundle? [bundle]
  (and (map? bundle)
       (every? #(some? (get bundle %)) required-bundle-keys)
       (pos-int? (:runtime-abi bundle))))

(defn stage
  "Admit a signed bundle into the inactive runtime slot."
  [runtime bundle publisher-admitted?]
  (cond
    (not (true? publisher-admitted?))
    (assoc runtime :state :degraded :reason :publisher-not-admitted)

    (not (valid-bundle? bundle))
    (assoc runtime :state :degraded :reason :invalid-runtime-bundle)

    :else
    (assoc runtime :state :staged :candidate bundle :reason nil)))

(defn start-candidate [runtime]
  (if (and (= :staged (:state runtime)) (:candidate runtime))
    (assoc runtime :state :starting :kernel-reboot? false)
    (assoc runtime :state :degraded :reason :candidate-not-staged)))

(defn mark-ready
  "Commit the candidate only after AIUEOS reports all three runtime probes."
  [runtime {:keys [runtime-admission model-bind murakumo-result]}]
  (if (and (= :starting (:state runtime))
           (true? runtime-admission) (true? model-bind)
           (true? murakumo-result))
    (-> runtime
        (assoc :state :ready :bundle (:candidate runtime)
               :candidate nil :reason nil :kernel-reboot? false)
        (update :epoch inc))
    (-> runtime
        (assoc :state :degraded :reason :runtime-health-refused
               :candidate nil :kernel-reboot? false)
        (update :failures inc))))

(defn begin-invocation
  "Enter :running from :ready. Any other state refuses the invocation, and the
   refusal is a real transition: the runtime goes :degraded with
   :reason :runtime-not-ready and the failure is counted.

   Until 2026-09-07 the not-ready branch only stamped :reason and left
   :state, :failures and everything else untouched, so a caller that kept
   invoking a stopped or degraded runtime produced receipts whose :from and
   :to were identical and whose failure count never moved -- a refused call
   was indistinguishable from no call at all."
  [runtime]
  (if (= :ready (:state runtime))
    (-> runtime (assoc :state :running :reason nil)
        (update :invocations inc))
    (-> runtime
        (assoc :state :degraded :reason :runtime-not-ready :kernel-reboot? false)
        (update :failures inc))))

(defn invocation-complete [runtime]
  (if (= :running (:state runtime))
    (assoc runtime :state :ready :reason nil)
    runtime))

(defn invocation-failed [runtime reason]
  (-> runtime
      (assoc :state :degraded :reason reason :kernel-reboot? false)
      (update :failures inc)))

(defn restart
  "Restart only the admitted runtime. AIUEOS and its network stay untouched."
  [runtime]
  (if (:bundle runtime)
    (-> runtime
        (assoc :state :ready :reason nil :kernel-reboot? false)
        (update :epoch inc)
        (update :restarts inc))
    (assoc runtime :state :degraded :reason :no-admitted-runtime
           :kernel-reboot? false)))

(defn stop [runtime]
  (assoc runtime :state :stopped :reason nil :kernel-reboot? false))

(defn receipt
  "The record of one transition. Every field is read from AFTER (or, for
   :from, from BEFORE); none is a literal.

   Until 2026-09-07 :kernel-reboot? was the literal `false` and :reason was
   dropped, so a receipt said \"no kernel reboot\" regardless of what the
   transition had actually recorded, and a degraded outcome carried no
   reason. `:kernel-reboot?` is deliberately NOT wrapped in `boolean` -- a
   transition that forgets to set it yields nil here, which is a visible
   defect rather than a silent `false`."
  [before after action]
  {:schema schema
   :action action
   :from (:state before)
   :to (:state after)
   :reason (:reason after)
   :bundle-id (get-in after [:bundle :bundle-id])
   :epoch (:epoch after)
   :restarts (:restarts after)
   :failures (:failures after)
   :kernel-reboot? (:kernel-reboot? after)})
