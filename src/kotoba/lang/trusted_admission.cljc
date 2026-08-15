(ns kotoba.lang.trusted-admission
  "Opaque host-admitted values at cryptographic trust boundaries.

  Inert EDN evidence never becomes authority by its shape. A trusted host must
  possess a verifier function, successfully verify the evidence, and mint one
  of the private runtime types in this namespace. Consumers accept those types
  rather than booleans or caller-constructed grant maps."
  (:require [clojure.string :as str]
            [kotoba.lang.capability-cacao :as cacao]
            [kotoba.lang.capability-values :as capabilities]
            [kotoba.lang.code-identity :as identity]
            [kotoba.lang.incidence :as incidence]))

(def captp-version "1.0")

(def ^:private delegation-result-fields
  #{:chain/valid? :chain/problems :chain/root-iss :chain/holder
    :chain/resources :chain/expires :chain/depth})

(def ^:private session-result-fields
  #{:session/valid? :session/id :session/version :session/peer
    :session/transcript-cid})

(def ^:private organization-binding-result-fields
  #{:binding/valid? :binding/constitution-cid :binding/kind :binding/did
    :binding/authorized-peers :binding/verification-relationship
    :binding/verification-method :binding/evidence-cid})

(def ^:private send-transport-fields #{:send!})
(def ^:private request-transport-fields #{:send! :request!})

(defprotocol ^:private VerifiedDelegationValue
  (-delegation-info [value])
  (-delegation-grants [value]))

(defprotocol ^:private AuthenticatedSessionValue
  (-session-info [value])
  (-session-send! [value message]))

(defprotocol ^:private RequestSessionValue
  (-session-request! [value call]))

(defprotocol ^:private VerifiedOrganizationBindingValue
  (-organization-binding-info [value]))

(deftype ^:private VerifiedDelegation [info grants]
  VerifiedDelegationValue
  (-delegation-info [_] info)
  (-delegation-grants [_] grants))

(deftype ^:private AuthenticatedSendSession [info send!]
  AuthenticatedSessionValue
  (-session-info [_] info)
  (-session-send! [_ message] (send! message)))

(deftype ^:private AuthenticatedRequestSession [info send! request!]
  AuthenticatedSessionValue
  (-session-info [_] info)
  (-session-send! [_ message] (send! message))
  RequestSessionValue
  (-session-request! [_ call] (request! call)))

(deftype ^:private VerifiedOrganizationBinding [info]
  VerifiedOrganizationBindingValue
  (-organization-binding-info [_] info))

(defn- did?
  [x]
  (and (string? x) (boolean (re-matches #"did:[a-z0-9]+:[^\s]+" x))))

(defn delegation-result-error
  "Return a closed diagnostic for a delegation verifier result."
  [result]
  (cond
    (not (map? result))
    {:problem :trusted/delegation-result-not-a-map}

    (not= delegation-result-fields (set (keys result)))
    {:problem :trusted/delegation-result-fields}

    (not (true? (:chain/valid? result)))
    {:problem :trusted/delegation-not-verified}

    (not (and (vector? (:chain/problems result))
              (empty? (:chain/problems result))))
    {:problem :trusted/delegation-has-problems}

    (not (did? (:chain/root-iss result)))
    {:problem :trusted/delegation-root-invalid}

    (not (did? (:chain/holder result)))
    {:problem :trusted/delegation-holder-invalid}

    (not (and (set? (:chain/resources result))
              (every? capabilities/non-empty-string?
                      (:chain/resources result))))
    {:problem :trusted/delegation-resources-invalid}

    (not (and (int? (:chain/depth result))
              (not (neg? (:chain/depth result)))))
    {:problem :trusted/delegation-depth-invalid}))

(defn verify-delegation!
  "Run VERIFY! over inert EVIDENCE and mint an opaque verified delegation.

  VERIFY! is a host capability and is never stored in or exposed by the
  returned value. Verification exceptions are reduced to a stable diagnostic
  so remote debug or secret data does not cross the trust boundary."
  [verify! evidence]
  (when-not (fn? verify!)
    (throw (ex-info "delegation verifier is not live"
                    {:problem :trusted/delegation-verifier-invalid})))
  (let [result (try
                 (verify! evidence)
                 (catch #?(:clj Exception :cljs :default) _
                   ::verification-failed))]
    (when (= ::verification-failed result)
      (throw (ex-info "delegation verification failed"
                      {:problem :trusted/delegation-verification-failed})))
    (if-let [error (delegation-result-error result)]
      (throw (ex-info "delegation was not admitted" error))
      (let [mapped (cacao/grants-from-chain result)]
        (when (seq (:problems mapped))
          (throw (ex-info "verified delegation could not be mapped"
                          {:problem :trusted/delegation-mapping-failed})))
        (VerifiedDelegation.
         (select-keys result [:chain/root-iss :chain/holder :chain/resources
                              :chain/expires :chain/depth])
         (:grants mapped))))))

(defn- addressed-constitution-error
  [entry]
  (let [verified (when (map? entry) (incidence/verify-addressed entry))
        block (:incidence/block entry)]
    (cond
      (not (:ok? verified))
      {:problem :trusted/constitution-address-invalid
       :verification verified}

      (not= :organization/constitution (:incidence/kind block))
      {:problem :trusted/constitution-kind-invalid})))

(defn organization-binding-result-error
  "Return a closed diagnostic for a DID/constitution verifier result."
  [constitution result]
  (let [constitution-cid (:incidence/cid constitution)
        constitution-block (:incidence/block constitution)
        constitution-kind (get-in constitution-block
                                  [:incidence/facts :organization/kind])
        constituents (get-in constitution-block
                             [:incidence/roles :organization/constituent])
        did-ref (when (did? (:binding/did result))
                  (incidence/typed-ref :did (:binding/did result)))]
    (cond
      (not (map? result))
      {:problem :trusted/organization-binding-result-not-a-map}

      (not= organization-binding-result-fields (set (keys result)))
      {:problem :trusted/organization-binding-result-fields}

      (not (true? (:binding/valid? result)))
      {:problem :trusted/organization-binding-not-verified}

      (not= constitution-cid (:binding/constitution-cid result))
      {:problem :trusted/organization-binding-constitution-mismatch}

      (not= constitution-kind (:binding/kind result))
      {:problem :trusted/organization-binding-kind-mismatch}

      (not (did? (:binding/did result)))
      {:problem :trusted/organization-binding-did-invalid}

      (not (contains? constituents did-ref))
      {:problem :trusted/organization-binding-did-not-constituent}

      (not (and (set? (:binding/authorized-peers result))
                (seq (:binding/authorized-peers result))
                (every? incidence/ref? (:binding/authorized-peers result))))
      {:problem :trusted/organization-binding-peers-invalid}

      (not= :assertionMethod (:binding/verification-relationship result))
      {:problem :trusted/organization-binding-relationship-invalid}

      (not (and (capabilities/non-empty-string?
                 (:binding/verification-method result))
                (str/starts-with? (:binding/verification-method result)
                                  (str (:binding/did result) "#"))))
      {:problem :trusted/organization-binding-method-invalid}

      (not (identity/cid? (:binding/evidence-cid result)))
      {:problem :trusted/organization-binding-evidence-invalid})))

(defn verify-organization-binding!
  "Verify an external DID binding for one addressed constitution and mint an
  opaque admitted value. DID documents, VCs, and proof objects remain inert;
  VERIFY! owns resolution, method-specific cryptography, revocation, and trust
  policy. The kernel independently binds the admitted result to the exact
  constitution CID and one of its DID constituents."
  [verify! constitution evidence]
  (when-not (fn? verify!)
    (throw (ex-info "organization binding verifier is not live"
                    {:problem :trusted/organization-binding-verifier-invalid})))
  (if-let [error (addressed-constitution-error constitution)]
    (throw (ex-info "organization constitution was not admitted" error))
    (let [result (try
                   (verify! {:constitution constitution :evidence evidence})
                   (catch #?(:clj Exception :cljs :default) _
                     ::verification-failed))]
      (when (= ::verification-failed result)
        (throw (ex-info "organization binding verification failed"
                        {:problem :trusted/organization-binding-verification-failed})))
      (if-let [error (organization-binding-result-error constitution result)]
        (throw (ex-info "organization binding was not admitted" error))
        (VerifiedOrganizationBinding. (dissoc result :binding/valid?))))))

(defn verified-organization-binding?
  [x]
  (satisfies? VerifiedOrganizationBindingValue x))

(defn organization-binding-description
  "Return inert admitted binding metadata, never the resolver/verifier."
  [binding]
  (when (verified-organization-binding? binding)
    (-organization-binding-info binding)))

(defn verified-delegation?
  [x]
  (satisfies? VerifiedDelegationValue x))

(defn delegation-description
  "Return inert audit metadata, never the verifier capability."
  [value]
  (when (verified-delegation? value)
    (-delegation-info value)))

(defn delegation-grants
  "Extract sanitized grants for a host guard. Serialized grant maps alone do
  not satisfy verified-delegation?."
  [value]
  (when (verified-delegation? value)
    (-delegation-grants value)))

(defn session-result-error
  "Return a closed diagnostic for an authenticated CapTP verifier result."
  [result]
  (cond
    (not (map? result))
    {:problem :trusted/session-result-not-a-map}

    (not= session-result-fields (set (keys result)))
    {:problem :trusted/session-result-fields}

    (not (true? (:session/valid? result)))
    {:problem :trusted/session-not-verified}

    (not (capabilities/non-empty-string? (:session/id result)))
    {:problem :trusted/session-id-invalid}

    (not= captp-version (:session/version result))
    {:problem :trusted/session-version-unsupported
     :expected captp-version
     :actual (:session/version result)}

    (not (incidence/ref? (:session/peer result)))
    {:problem :trusted/session-peer-invalid}

    (not (identity/cid? (:session/transcript-cid result)))
    {:problem :trusted/session-transcript-invalid}))

(defn- transport-error
  [transport]
  (cond
    (not (map? transport))
    {:problem :trusted/session-transport-not-a-map}

    (not (contains? #{send-transport-fields request-transport-fields}
                    (set (keys transport))))
    {:problem :trusted/session-transport-fields}

    (not (fn? (:send! transport)))
    {:problem :trusted/session-send-invalid}

    (and (contains? transport :request!)
         (not (fn? (:request! transport))))
    {:problem :trusted/session-request-invalid}))

(defn authenticate-session!
  "Run VERIFY! over inert handshake EVIDENCE and bind the verified result to
  live transport functions in an opaque session value."
  [verify! evidence transport]
  (when-not (fn? verify!)
    (throw (ex-info "session verifier is not live"
                    {:problem :trusted/session-verifier-invalid})))
  (let [result (try
                 (verify! evidence)
                 (catch #?(:clj Exception :cljs :default) _
                   ::verification-failed))]
    (when (= ::verification-failed result)
      (throw (ex-info "session verification failed"
                      {:problem :trusted/session-verification-failed})))
    (if-let [error (or (session-result-error result)
                       (transport-error transport))]
      (throw (ex-info "session was not admitted" error))
      (let [info (dissoc result :session/valid?)]
        (if (contains? transport :request!)
          (AuthenticatedRequestSession. info (:send! transport)
                                        (:request! transport))
          (AuthenticatedSendSession. info (:send! transport)))))))

(defn authenticated-session?
  [x]
  (satisfies? AuthenticatedSessionValue x))

(defn request-capable-session?
  [x]
  (satisfies? RequestSessionValue x))

(defn session-description
  "Return inert authenticated peer/transcript metadata without transport
  authority."
  [session]
  (when (authenticated-session? session)
    (-session-info session)))

(defn session-send!
  [session message]
  (when-not (authenticated-session? session)
    (throw (ex-info "session is not authenticated"
                    {:problem :trusted/session-required})))
  (-session-send! session message))

(defn session-request!
  [session call]
  (when-not (request-capable-session? session)
    (throw (ex-info "session cannot request a result"
                    {:problem :trusted/session-request-not-supported})))
  (-session-request! session call))
