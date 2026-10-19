(ns kagi.rotation-admission
  "Fail-closed admission boundary for authority-key rotation events."
  (:require [kagi.recovery :as recovery]
            [kagi.rotation :as rotation]
            [kagi.rotation-store :as rotation-store]
            [kagi.witness :as witness]))

(defn- reject! [reason data]
  (throw (ex-info "authority rotation rejected" (assoc data :reason reason))))

(defn- assert-continuity!
  [store event {:keys [subject purpose key-id epoch parent]}]
  (when-not (= subject (:rotation/subject event))
    (reject! :wrong-subject {:expected subject}))
  (when-not (= purpose (:rotation/purpose event))
    (reject! :wrong-purpose {:expected purpose}))
  (when-not (= key-id (:rotation/from-key event))
    (reject! :stale-from-key {:expected key-id}))
  (when-not (= epoch (:rotation/from-epoch event))
    (reject! :stale-epoch {:expected epoch}))
  (let [parents (set (:rotation/parents event))]
    (if parent
      (when-not (= #{parent} parents)
        (reject! :wrong-parent {:expected parent :actual parents}))
      (when (seq parents)
        (reject! :unexpected-parent {:actual parents}))))
  (when (contains? (rotation-store/quarantined store) parent)
    (reject! :quarantined-parent {:parent parent})))

(defn admit!
  "Verify current-state continuity and every proof before durable DAG mutation.

  opts requires :current, :new-public and, for normal rotation, :old-public.
  Compromise recovery additionally requires :recovery-policy and
  :witness-policy; approvals/checkpoints are read from the event."
  [store crypto event {:keys [current old-public new-public
                              recovery-policy witness-policy]}]
  (assert-continuity! store event current)
  (when (some #(and (= (:rotation/subject %) (:rotation/subject event))
                     (= (:rotation/purpose %) (:rotation/purpose event))
                     (= (:rotation/from-epoch %) (:rotation/from-epoch event))
                     (not= (:rotation/id %) (:rotation/id event)))
              (rotation-store/events store))
    (reject! :competing-child {:rotation/id (:rotation/id event)}))
  (let [valid?
        (if (= :compromise (:rotation/reason event))
          (and recovery-policy witness-policy
               (seq (:rotation/policy-cid event))
               (= (:rotation/policy-cid event) (:policy-cid recovery-policy))
               (= (:rotation/policy-cid event) (:policy-cid witness-policy))
               (rotation/valid-recovery?
                crypto event new-public
                #(recovery/threshold-authorized?
                  crypto % (:rotation/recovery-approvals %) recovery-policy)
                #(witness/witnesses-authorize?
                  crypto (:rotation/id %) (:rotation/witness-checkpoints %)
                  witness-policy)))
          (and old-public
               (rotation/valid-normal? crypto event old-public new-public)))]
    (when-not valid?
      (reject! :invalid-proof {:rotation/id (:rotation/id event)}))
    (rotation-store/put-event! store event)))

(defn admit-authorized!
  "Admission for a non-signing key epoch (KEM/DEK/etc.) certified by the
  current managed authority. KEM keys are never incorrectly treated as
  signing keys."
  [store crypto event {:keys [current authorizer-public]}]
  (assert-continuity! store event current)
  (when (some #(and (= (:rotation/subject %) (:rotation/subject event))
                     (= (:rotation/purpose %) (:rotation/purpose event))
                     (= (:rotation/from-epoch %) (:rotation/from-epoch event))
                     (not= (:rotation/id %) (:rotation/id event)))
              (rotation-store/events store))
    (reject! :competing-child {:rotation/id (:rotation/id event)}))
  (when-not (and authorizer-public
                 (rotation/valid-authorized? crypto event authorizer-public))
    (reject! :invalid-authority-proof {:rotation/id (:rotation/id event)}))
  (rotation-store/put-event! store event))
