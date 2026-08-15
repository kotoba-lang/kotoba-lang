(ns kotoba.lang.signed-readback
  "Fail-closed admission for organization-bound signed readback receipts.

  Receipt statements are inert content-addressed incidences. Only a lexical
  verifier containing live challenge, clock, and signature-verification
  capabilities can turn one into an opaque VerifiedReadback value."
  (:require [kotoba.lang.capability-values :as capabilities]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.trusted-admission :as trusted]))

(def version incidence/signed-readback-version)
(def kind incidence/signed-readback-kind)

(def ^:private envelope-fields #{:receipt/statement :receipt/proof})
(def ^:private proof-result-fields
  #{:proof/valid? :proof/payload-cid :proof/issuer
    :proof/verification-method :proof/relationship})

(defprotocol ^:private ReadbackVerifierValue
  (-verifier-info [value])
  (-issue-challenge! [value request])
  (-discard-challenge! [value challenge])
  (-verify-envelope! [value request challenge envelope]))

(defprotocol ^:private VerifiedReadbackValue
  (-readback-info [value]))

(deftype ^:private VerifiedReadback [info]
  VerifiedReadbackValue
  (-readback-info [_] info))

(defn- request-error
  [request]
  (let [entry (:entry request)
        verified (when (map? entry) (incidence/verify-addressed entry))]
    (cond
      (not (map? request))
      {:problem :readback/request-not-a-map}

      (not= #{:dataspace :entry :capability} (set (keys request)))
      {:problem :readback/request-fields}

      (not (capabilities/non-empty-string? (:dataspace request)))
      {:problem :readback/dataspace-invalid}

      (not (:ok? verified))
      {:problem :readback/incidence-invalid :verification verified})))

(defn- proof-result-error
  [expected result]
  (cond
    (not (map? result))
    {:problem :readback/proof-result-not-a-map}

    (not= proof-result-fields (set (keys result)))
    {:problem :readback/proof-result-fields}

    (not (true? (:proof/valid? result)))
    {:problem :readback/signature-invalid}

    (not= (:statement-cid expected) (:proof/payload-cid result))
    {:problem :readback/proof-payload-mismatch}

    (not= (:issuer expected) (:proof/issuer result))
    {:problem :readback/proof-issuer-mismatch}

    (not= (:verification-method expected)
          (:proof/verification-method result))
    {:problem :readback/proof-method-mismatch}

    (not= :assertionMethod (:proof/relationship result))
    {:problem :readback/proof-relationship-invalid}))

(defn- statement-error
  [binding-info session-info request challenge now-ms max-age-ms statement]
  (let [verified (when (map? statement) (incidence/verify-addressed statement))
        block (:incidence/block statement)
        facts (:incidence/facts block)
        roles (:incidence/roles block)
        subject-cid (get-in request [:entry :incidence/cid])
        issuer (:binding/did binding-info)
        method (:binding/verification-method binding-info)
        issued (:receipt/issued-at-ms facts)
        expires (:receipt/expires-at-ms facts)]
    (cond
      (not (:ok? verified))
      {:problem :readback/statement-address-invalid :verification verified}

      (not= kind (:incidence/kind block))
      {:problem :readback/statement-kind-invalid}

      (not= #{(incidence/typed-ref :cid subject-cid)}
            (:receipt/subject roles))
      {:problem :readback/subject-mismatch}

      (not= subject-cid (:receipt/readback-cid facts))
      {:problem :readback/cid-mismatch}

      (not= (:dataspace request) (:receipt/dataspace facts))
      {:problem :readback/dataspace-mismatch}

      (not= #{(incidence/typed-ref :cid
                                   (:binding/constitution-cid binding-info))}
            (:receipt/organization roles))
      {:problem :readback/constitution-mismatch}

      (not= #{(incidence/typed-ref :did issuer)} (:receipt/issuer roles))
      {:problem :readback/issuer-mismatch}

      (not= #{(:session/peer session-info)} (:receipt/peer roles))
      {:problem :readback/peer-mismatch}

      (not= (:session/transcript-cid session-info)
            (:receipt/session-transcript-cid facts))
      {:problem :readback/session-mismatch}

      (not= challenge (:receipt/challenge facts))
      {:problem :readback/challenge-mismatch}

      (not= method (:receipt/verification-method facts))
      {:problem :readback/verification-method-mismatch}

      (not= #{(:binding/evidence-cid binding-info)}
            (:incidence/evidence block))
      {:problem :readback/binding-evidence-mismatch}

      (not (and (integer? issued) (integer? expires)
                (<= issued now-ms) (< now-ms expires)
                (<= (- now-ms issued) max-age-ms)
                (<= (- expires issued) max-age-ms)))
      {:problem :readback/not-fresh}

      :else
      {:statement-cid (:incidence/cid statement)
       :issuer issuer
       :verification-method method
       :issued-at-ms issued
       :expires-at-ms expires})))

(deftype ^:private ReadbackVerifier
  [info challenge! clock! verify! max-age-ms pending]
  ReadbackVerifierValue
  (-verifier-info [_] info)
  (-issue-challenge! [_ request]
    (when-let [error (request-error request)]
      (throw (ex-info "invalid signed readback request" error)))
    (let [challenge (try
                      (challenge!)
                      (catch #?(:clj Exception :cljs :default) _
                        ::challenge-failed))]
      (when (= ::challenge-failed challenge)
        (throw (ex-info "readback challenge generation failed"
                        {:problem :readback/challenge-generation-failed})))
      (when-not (capabilities/non-empty-string? challenge)
        (throw (ex-info "readback challenge is invalid"
                        {:problem :readback/challenge-invalid})))
      (let [binding {:dataspace (:dataspace request)
                     :incidence/cid (get-in request [:entry :incidence/cid])}]
        (loop []
          (let [before @pending]
            (when (contains? before challenge)
              (throw (ex-info "readback challenge was reused"
                              {:problem :readback/challenge-duplicate})))
            (if (compare-and-set! pending before (assoc before challenge binding))
              challenge
              (recur)))))))
  (-discard-challenge! [_ challenge]
    (swap! pending dissoc challenge)
    nil)
  (-verify-envelope! [_ request challenge envelope]
    (when-let [error (request-error request)]
      (throw (ex-info "invalid signed readback request" error)))
    (let [expected-pending {:dataspace (:dataspace request)
                            :incidence/cid (get-in request
                                                   [:entry :incidence/cid])}
          registered (get @pending challenge)]
      ;; Consume before any parsing or host call: failed attempts cannot turn a
      ;; one-shot challenge into a reusable oracle.
      (swap! pending dissoc challenge)
      (when-not (= expected-pending registered)
        (throw (ex-info "readback challenge is absent, replayed, or misbound"
                        {:problem :readback/challenge-not-pending})))
      (when-not (and (map? envelope)
                     (= envelope-fields (set (keys envelope))))
        (throw (ex-info "signed readback envelope is invalid"
                        {:problem :readback/envelope-fields})))
      (let [now-ms (try
                     (clock!)
                     (catch #?(:clj Exception :cljs :default) _ ::clock-failed))]
        (when (or (= ::clock-failed now-ms) (not (integer? now-ms)))
          (throw (ex-info "readback clock failed"
                          {:problem :readback/clock-failed})))
        (let [statement (:receipt/statement envelope)
              checked (statement-error info (:session info) request challenge
                                       now-ms max-age-ms statement)]
          (when (:problem checked)
            (throw (ex-info "signed readback statement was rejected" checked)))
          (let [verification-input
                {:payload (incidence/canonical-bytes
                           (:incidence/block statement))
                 :payload-cid (:statement-cid checked)
                 :proof (:receipt/proof envelope)
                 :issuer (:issuer checked)
                 :verification-method (:verification-method checked)
                 :relationship :assertionMethod}
                result (try
                         (verify! verification-input)
                         (catch #?(:clj Exception :cljs :default) _
                           ::verification-failed))]
            (when (= ::verification-failed result)
              (throw (ex-info "readback signature verification failed"
                              {:problem :readback/signature-verification-failed})))
            (when-let [error (proof-result-error checked result)]
              (throw (ex-info "readback proof was rejected" error)))
            (VerifiedReadback.
             {:receipt/statement-cid (:statement-cid checked)
              :receipt/dataspace (:dataspace request)
              :receipt/incidence-cid (get-in request [:entry :incidence/cid])
              :receipt/constitution-cid (:binding/constitution-cid info)
              :receipt/issuer (:issuer checked)
              :receipt/peer (get-in info [:session :session/peer])
              :receipt/session-transcript-cid
              (get-in info [:session :session/transcript-cid])
              :receipt/issued-at-ms (:issued-at-ms checked)
              :receipt/expires-at-ms (:expires-at-ms checked)})))))))

(defn verifier
  "Create a lexical one-shot verifier bound to an admitted organization and
  authenticated session. CHALLENGE!, CLOCK!, and VERIFY! are live host
  capabilities and are never exposed or serialized."
  [{:keys [organization-binding session challenge! clock! verify! max-age-ms]}]
  (let [binding-info (trusted/organization-binding-description
                      organization-binding)
        session-info (trusted/session-description session)]
    (cond
      (nil? binding-info)
      (throw (ex-info "verified organization binding required"
                      {:problem :readback/organization-binding-required}))

      (nil? session-info)
      (throw (ex-info "authenticated session required"
                      {:problem :readback/session-required}))

      (not (contains? (:binding/authorized-peers binding-info)
                      (:session/peer session-info)))
      (throw (ex-info "session peer is not authorized by the constitution binding"
                      {:problem :readback/peer-not-authorized}))

      (not (every? fn? [challenge! clock! verify!]))
      (throw (ex-info "readback host capabilities are invalid"
                      {:problem :readback/host-capability-invalid}))

      (not (and (integer? max-age-ms) (pos? max-age-ms)))
      (throw (ex-info "readback freshness bound is invalid"
                      {:problem :readback/max-age-invalid}))

      :else
      (ReadbackVerifier.
       (assoc binding-info :session session-info)
       challenge! clock! verify! max-age-ms (atom {})))))

(defn verifier?
  [x]
  (satisfies? ReadbackVerifierValue x))

(defn verifier-description
  "Return inert binding/session metadata without live host capabilities."
  [value]
  (when (verifier? value)
    (-verifier-info value)))

(defn issue-challenge!
  [value request]
  (when-not (verifier? value)
    (throw (ex-info "signed readback verifier required"
                    {:problem :readback/verifier-required})))
  (-issue-challenge! value request))

(defn verify-envelope!
  [value request challenge envelope]
  (when-not (verifier? value)
    (throw (ex-info "signed readback verifier required"
                    {:problem :readback/verifier-required})))
  (-verify-envelope! value request challenge envelope))

(defn discard-challenge!
  "Forget a pending challenge after transport failure or broken settlement."
  [value challenge]
  (when-not (verifier? value)
    (throw (ex-info "signed readback verifier required"
                    {:problem :readback/verifier-required})))
  (-discard-challenge! value challenge))

(defn verified-readback?
  [x]
  (satisfies? VerifiedReadbackValue x))

(defn verified-readback-description
  "Return sanitized audit metadata; serialized lookalikes are never verified."
  [value]
  (when (verified-readback? value)
    (-readback-info value)))

(defn statement
  "Construct a statement a remote organization can sign for a challenge.
  This helper is inert and does not sign or admit anything."
  [verifier request challenge issued-at-ms expires-at-ms]
  (when-not (verifier? verifier)
    (throw (ex-info "signed readback verifier required"
                    {:problem :readback/verifier-required})))
  (when-let [error (request-error request)]
    (throw (ex-info "invalid signed readback request" error)))
  (let [info (-verifier-info verifier)
        session-info (:session info)
        subject-cid (get-in request [:entry :incidence/cid])]
    (incidence/signed-readback-statement
     {:dataspace (:dataspace request)
      :subject-cid subject-cid
      :readback-cid subject-cid
      :constitution-cid (:binding/constitution-cid info)
      :issuer (:binding/did info)
      :peer (:session/peer session-info)
      :challenge challenge
      :issued-at-ms issued-at-ms
      :expires-at-ms expires-at-ms
      :session-transcript-cid (:session/transcript-cid session-info)
      :verification-method (:binding/verification-method info)
      :binding-evidence-cid (:binding/evidence-cid info)})))
