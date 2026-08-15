(ns kotoba.lang.organization-governance
  "Content-addressed organization governance with verified approvals.

  Policy, proposal, approval, and enactment records are inert incidences. A
  host verifier must bind every approval proof to the exact governor,
  proposal, constitution, and evidence CID. Only opaque admitted approvals can
  satisfy quorum and mint an opaque enacted decision."
  (:require [kotoba.lang.code-identity :as identity]
            [kotoba.lang.incidence :as incidence]))

(def version incidence/governance-version)

(defprotocol ^:private VerifiedApprovalValue
  (-approval-info [value]))

(defprotocol ^:private EnactedDecisionValue
  (-decision-info [value])
  (-decision-entry [value]))

(deftype ^:private VerifiedApproval [info]
  VerifiedApprovalValue
  (-approval-info [_] info))

(deftype ^:private EnactedDecision [info entry]
  EnactedDecisionValue
  (-decision-info [_] info)
  (-decision-entry [_] entry))

(def ^:private verifier-result-fields
  #{:approval/valid? :approval/cid :approval/proposal-cid
    :approval/constitution-cid :approval/governor :approval/evidence-cid})

(defn- addressed-kind-error
  [entry expected]
  (let [verified (when (map? entry) (incidence/verify-addressed entry))]
    (cond
      (not (:ok? verified))
      {:problem :governance/address-invalid :verification verified}

      (not= expected (get-in entry [:incidence/block :incidence/kind]))
      {:problem :governance/kind-invalid
       :expected expected
       :actual (get-in entry [:incidence/block :incidence/kind])})))

(defn policy
  "Create an immutable governance policy rooted in a constitution CID."
  [organization governors threshold actions]
  (incidence/addressed
   (incidence/incidence
    :organization/governance-policy
    {:governance/organization #{organization}
     :governance/governor (set governors)}
    {:parents #{(:ref/value organization)}
     :facts {:governance/version version
             :governance/threshold threshold
             :governance/actions (set actions)}})))

(defn proposal
  "Create a proposal bound to exact policy and payload CIDs. CONFLICT-KEY is
  application-defined; concurrent enacted proposals with the same key remain
  an explicit branch conflict."
  [policy-entry proposer action payload-entry conflict-key]
  (when-let [error (addressed-kind-error policy-entry
                                          :organization/governance-policy)]
    (throw (ex-info "invalid governance policy" error)))
  (when-let [error (addressed-kind-error payload-entry
                                          (get-in payload-entry
                                                  [:incidence/block
                                                   :incidence/kind]))]
    (throw (ex-info "invalid governance payload" error)))
  (let [policy-block (:incidence/block policy-entry)
        policy-cid (:incidence/cid policy-entry)
        payload-cid (:incidence/cid payload-entry)
        organization (first (get-in policy-block
                                    [:incidence/roles
                                     :governance/organization]))]
    (when-not (contains? (get-in policy-block
                                 [:incidence/facts :governance/actions])
                         action)
      (throw (ex-info "governance action is outside policy"
                      {:problem :governance/action-not-allowed})))
    (incidence/addressed
     (incidence/incidence
      :organization/proposal
      {:governance/organization #{organization}
       :governance/proposer #{proposer}}
      {:parents (set [policy-cid payload-cid])
       :policies #{policy-cid}
       :facts {:governance/version version
               :governance/policy-cid policy-cid
               :governance/action action
               :governance/payload-cid payload-cid
               :governance/conflict-key conflict-key}}))))

(defn approval
  "Create inert signed-approval subject data. EVIDENCE-CID names the external
  proof object; constructing this incidence does not verify it."
  [policy-entry proposal-entry governor decision evidence-cid]
  (when-let [error (addressed-kind-error policy-entry
                                          :organization/governance-policy)]
    (throw (ex-info "invalid governance policy" error)))
  (when-let [error (addressed-kind-error proposal-entry
                                          :organization/proposal)]
    (throw (ex-info "invalid governance proposal" error)))
  (let [policy-cid (:incidence/cid policy-entry)
        proposal-cid (:incidence/cid proposal-entry)
        policy-block (:incidence/block policy-entry)
        proposal-block (:incidence/block proposal-entry)
        organization (first (get-in policy-block
                                    [:incidence/roles
                                     :governance/organization]))]
    (when-not (= policy-cid (get-in proposal-block
                                    [:incidence/facts
                                     :governance/policy-cid]))
      (throw (ex-info "proposal uses another policy"
                      {:problem :governance/policy-mismatch})))
    (when-not (contains? (get-in policy-block
                                 [:incidence/roles :governance/governor])
                         governor)
      (throw (ex-info "approval governor is outside policy"
                      {:problem :governance/governor-not-authorized})))
    (incidence/addressed
     (incidence/incidence
      :organization/approval
      {:governance/organization #{organization}
       :governance/governor #{governor}}
      {:parents #{proposal-cid}
       :evidence #{evidence-cid}
       :policies #{policy-cid}
       :facts {:governance/version version
               :governance/proposal-cid proposal-cid
               :governance/decision decision}}))))

(defn- approval-structure-error
  [policy-entry proposal-entry approval-entry]
  (or
   (addressed-kind-error policy-entry :organization/governance-policy)
   (addressed-kind-error proposal-entry :organization/proposal)
   (addressed-kind-error approval-entry :organization/approval)
   (let [policy-cid (:incidence/cid policy-entry)
         proposal-cid (:incidence/cid proposal-entry)
         policy-block (:incidence/block policy-entry)
         proposal-block (:incidence/block proposal-entry)
         approval-block (:incidence/block approval-entry)
         organization (get-in policy-block
                              [:incidence/roles :governance/organization])
         governor (first (get-in approval-block
                                 [:incidence/roles :governance/governor]))]
     (cond
       (not= policy-cid (get-in proposal-block
                                [:incidence/facts :governance/policy-cid]))
       {:problem :governance/policy-mismatch}

       (not (contains? (get-in policy-block
                               [:incidence/facts :governance/actions])
                       (get-in proposal-block
                               [:incidence/facts :governance/action])))
       {:problem :governance/action-not-allowed}

       (not= organization (get-in proposal-block
                                  [:incidence/roles
                                   :governance/organization]))
       {:problem :governance/organization-mismatch}

       (not= organization (get-in approval-block
                                  [:incidence/roles
                                   :governance/organization]))
       {:problem :governance/organization-mismatch}

       (not= proposal-cid (get-in approval-block
                                  [:incidence/facts
                                   :governance/proposal-cid]))
       {:problem :governance/proposal-mismatch}

       (not= #{policy-cid} (:incidence/policies approval-block))
       {:problem :governance/policy-mismatch}

       (not (contains? (get-in policy-block
                               [:incidence/roles :governance/governor])
                       governor))
       {:problem :governance/governor-not-authorized}))))

(defn- verifier-result-error
  [proposal-entry approval-entry result]
  (let [approval-block (:incidence/block approval-entry)
        organization (first (get-in approval-block
                                    [:incidence/roles
                                     :governance/organization]))
        governor (first (get-in approval-block
                                [:incidence/roles :governance/governor]))
        evidence-cid (first (:incidence/evidence approval-block))]
    (cond
      (not (map? result))
      {:problem :governance/verifier-result-not-a-map}

      (not= verifier-result-fields (set (keys result)))
      {:problem :governance/verifier-result-fields}

      (not (true? (:approval/valid? result)))
      {:problem :governance/approval-not-verified}

      (not= (:incidence/cid approval-entry) (:approval/cid result))
      {:problem :governance/approval-cid-mismatch}

      (not= (:incidence/cid proposal-entry) (:approval/proposal-cid result))
      {:problem :governance/proposal-mismatch}

      (not= (:ref/value organization) (:approval/constitution-cid result))
      {:problem :governance/constitution-mismatch}

      (not= governor (:approval/governor result))
      {:problem :governance/governor-mismatch}

      (not= evidence-cid (:approval/evidence-cid result))
      {:problem :governance/evidence-mismatch}

      (not (identity/cid? (:approval/evidence-cid result)))
      {:problem :governance/evidence-invalid})))

(defn verify-approval!
  "Verify one approval proof and mint an opaque admission. VERIFY! owns DID/VC
  resolution, proof-purpose and cryptographic-suite policy."
  [verify! policy-entry proposal-entry approval-entry evidence]
  (when-not (fn? verify!)
    (throw (ex-info "governance verifier is not live"
                    {:problem :governance/verifier-invalid})))
  (when-let [error (approval-structure-error policy-entry proposal-entry
                                              approval-entry)]
    (throw (ex-info "approval structure was rejected" error)))
  (let [result (try
                 (verify! {:policy policy-entry
                           :proposal proposal-entry
                           :approval approval-entry
                           :evidence evidence
                           :payload (incidence/canonical-bytes
                                     (:incidence/block approval-entry))})
                 (catch #?(:clj Exception :cljs :default) _
                   ::verification-failed))]
    (when (= ::verification-failed result)
      (throw (ex-info "governance verification failed"
                      {:problem :governance/verification-failed})))
    (if-let [error (verifier-result-error proposal-entry approval-entry result)]
      (throw (ex-info "governance approval was not admitted" error))
      (VerifiedApproval. (dissoc result :approval/valid?)))))

(defn verified-approval?
  [x]
  (satisfies? VerifiedApprovalValue x))

(defn approval-description
  [approval]
  (when (verified-approval? approval) (-approval-info approval)))

(defn enact
  "Enact one proposal when distinct verified governors meet its immutable
  policy threshold. Returns an opaque decision containing the publishable
  addressed enactment incidence."
  [policy-entry proposal-entry approval-entries admissions]
  (when-let [error (or (addressed-kind-error policy-entry
                                              :organization/governance-policy)
                       (addressed-kind-error proposal-entry
                                             :organization/proposal))]
    (throw (ex-info "governance enactment inputs are invalid" error)))
  (when-not (and (vector? approval-entries) (seq approval-entries)
                 (vector? admissions)
                 (= (count approval-entries) (count admissions)))
    (throw (ex-info "approval entries and admissions must align"
                    {:problem :governance/approval-input-invalid})))
  (when-not (every? verified-approval? admissions)
    (throw (ex-info "verified approval admissions required"
                    {:problem :governance/approval-not-verified})))
  (doseq [approval-entry approval-entries]
    (when-let [error (approval-structure-error policy-entry proposal-entry
                                                approval-entry)]
      (throw (ex-info "approval structure was rejected" error))))
  (let [policy-block (:incidence/block policy-entry)
        proposal-block (:incidence/block proposal-entry)
        policy-cid (:incidence/cid policy-entry)
        proposal-cid (:incidence/cid proposal-entry)
        organization (first (get-in policy-block
                                    [:incidence/roles
                                     :governance/organization]))
        threshold (get-in policy-block
                          [:incidence/facts :governance/threshold])
        pairs (mapv vector approval-entries admissions)
        governors
        (mapv #(first (get-in (first %) [:incidence/block :incidence/roles
                                         :governance/governor])) pairs)
        approval-cids (set (map :incidence/cid approval-entries))]
    (when-not (every? #(= :approve
                          (get-in % [:incidence/block :incidence/facts
                                     :governance/decision]))
                      approval-entries)
      (throw (ex-info "only approvals satisfy enactment quorum"
                      {:problem :governance/rejection-present})))
    (when-not (= (count governors) (count (set governors)))
      (throw (ex-info "one governor cannot count more than once"
                      {:problem :governance/duplicate-governor})))
    (doseq [[approval-entry admission] pairs]
      (let [info (approval-description admission)
            governor (first (get-in approval-entry
                                    [:incidence/block :incidence/roles
                                     :governance/governor]))]
        (when-not (and (= (:incidence/cid approval-entry)
                          (:approval/cid info))
                       (= proposal-cid (:approval/proposal-cid info))
                       (= (:ref/value organization)
                          (:approval/constitution-cid info))
                       (= governor (:approval/governor info)))
          (throw (ex-info "approval admission is misbound"
                          {:problem :governance/admission-mismatch})))))
    (when (< (count governors) threshold)
      (throw (ex-info "governance quorum is below threshold"
                      {:problem :governance/below-quorum
                       :required threshold :actual (count governors)})))
    (let [evidence (into #{} (mapcat #(get-in % [:incidence/block
                                                  :incidence/evidence]))
                         approval-entries)
          entry
          (incidence/addressed
           (incidence/incidence
            :organization/enacted
            {:governance/organization #{organization}}
            {:parents (conj approval-cids proposal-cid)
             :evidence evidence
             :policies #{policy-cid}
             :facts {:governance/version version
                     :governance/proposal-cid proposal-cid
                     :governance/approval-cids approval-cids}}))
          info {:governance/enactment-cid (:incidence/cid entry)
                :governance/constitution-cid (:ref/value organization)
                :governance/policy-cid policy-cid
                :governance/proposal-cid proposal-cid
                :governance/action (get-in proposal-block
                                           [:incidence/facts
                                            :governance/action])
                :governance/payload-cid (get-in proposal-block
                                                [:incidence/facts
                                                 :governance/payload-cid])
                :governance/conflict-key (get-in proposal-block
                                                 [:incidence/facts
                                                  :governance/conflict-key])
                :governance/governors (set governors)}]
      (EnactedDecision. info entry))))

(defn enacted-decision?
  [x]
  (satisfies? EnactedDecisionValue x))

(defn decision-description
  [decision]
  (when (enacted-decision? decision) (-decision-info decision)))

(defn decision-entry
  "Return the inert addressed incidence suitable for capability-guarded
  publication. Re-reading it does not recreate the opaque decision."
  [decision]
  (when (enacted-decision? decision) (-decision-entry decision)))

(defn project-decisions
  "Project verified decisions without inventing a winner for concurrent
  proposals sharing a conflict key. Repeated certificates for one proposal
  collapse; distinct proposals remain an explicit conflict."
  [decisions]
  (when-not (and (sequential? decisions)
                 (every? enacted-decision? decisions))
    (throw (ex-info "opaque enacted decisions required"
                    {:problem :governance/decision-not-verified})))
  (let [infos (map decision-description decisions)
        grouped (group-by (juxt :governance/constitution-cid
                                :governance/conflict-key) infos)
        normalized (into {}
                         (map (fn [[k xs]]
                                [k
                                 (->> xs
                                      (group-by :governance/proposal-cid)
                                      (map (fn [[_ same-proposal]]
                                             (let [base (first
                                                         (sort-by
                                                          :governance/enactment-cid
                                                          same-proposal))]
                                               (assoc base
                                                      :governance/enactment-cids
                                                      (set (map :governance/enactment-cid
                                                                same-proposal))
                                                      :governance/governors
                                                      (into #{} (mapcat
                                                                 :governance/governors)
                                                            same-proposal)))))
                                      (sort-by :governance/proposal-cid)
                                      vec)]))
                         grouped)]
    {:governance/active
     (into {} (keep (fn [[k xs]] (when (= 1 (count xs)) [k (first xs)])))
           normalized)
     :governance/conflicts
     (into {} (keep (fn [[k xs]] (when (> (count xs) 1) [k (vec xs)])))
           normalized)}))
