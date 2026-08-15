(ns kotoba.lang.interop-profiles
  "Concrete profiles that connect external DID/VC/UCAN implementations to the
  closed adapters in `kotoba.lang.interop-verifiers`.

  These functions verify inert evidence. Only the existing trusted-admission
  layer may turn a successful result into an opaque runtime authority value."
  (:require [clojure.string :as str]
            [data-integrity.core :as di]
            [data-integrity.eddsa :as eddsa]
            [data-integrity.eddsa-rdfc :as rdfc]
            [did.core :as did]
            [kotoba.lang.interop-verifiers :as interop]
            [status-list.core :as status-list]
            [ucan.core :as ucan]))

(def accepted-ed25519-suites
  "Explicit W3C Data Integrity Ed25519 allowlist. A proof never selects an
  arbitrary implementation by naming its own cryptosuite."
  {(:cryptosuite eddsa/suite) eddsa/suite
   (:cryptosuite rdfc/suite) rdfc/suite})

(defn- fail! [problem message data]
  (throw (ex-info message (assoc data :problem problem))))

(defn- field [m k]
  (let [s (name k)]
    (cond
      (and (contains? m k) (contains? m s) (not= (get m k) (get m s)))
      (fail! :interop/ambiguous-field "external object has ambiguous aliases"
             {:field k})
      (contains? m k) (get m k)
      :else (get m s))))

(defn- did-key? [x]
  (and (string? x) (str/starts-with? x "did:key:")
       (try (did/did-key->public-key (:did (did/parse x))) true
            (catch #?(:clj Exception :cljs :default) _ false))))

(defn resolve-did-key!
  "Offline did:key resolution only. did:web and every network method are an
  explicit capability outside this profile."
  [identifier]
  (when-not (did-key? identifier)
    (fail! :interop/unsupported-did-profile
           "the concrete profile accepts only Ed25519 did:key" {}))
  (did/did-key-document (:did (did/parse identifier))))

(defn- issuer-id [credential]
  (let [issuer (field credential :issuer)]
    (if (string? issuer) issuer (field issuer :id))))

(defn- verify-data-integrity!
  [secured {:keys [suite-opts]}]
  (let [result (di/verify-credential
                secured
                {:accept-suites accepted-ed25519-suites
                 :suite-opts suite-opts})]
    (when-not (and (map? result) (true? (:verified result)))
      (fail! :interop/vc-cryptographic-verification-failed
             "W3C Data Integrity verification failed"
             {:reason (:reason result)}))
    (let [issuer (issuer-id (:document result))
          signer (:did (did/parse (:verification-method result)))]
      (when-not (= issuer signer)
        (fail! :interop/vc-issuer-proof-mismatch
               "credential issuer does not control the verified did:key proof"
               {:issuer issuer :signer signer})))
    result))

(defn- verify-status!
  [secured unsecured {:keys [resolve-status-list! suite-opts]}]
  (when-let [entry (field unsecured :credentialStatus)]
    (when-not (and (map? entry) (fn? resolve-status-list!))
      (fail! :interop/status-verifier-required
             "credentialStatus requires one bounded resolver capability" {}))
    (let [url (field entry :statusListCredential)]
      (when-not (string? url)
        (fail! :interop/status-list-url-invalid
               "statusListCredential must be a URL string" {}))
      (let [status-secured (resolve-status-list! url)]
        (when-not (map? status-secured)
          (fail! :interop/status-list-unresolved
                 "status list resolver did not return a credential" {:url url}))
        (let [status-result (verify-data-integrity! status-secured
                                                    {:suite-opts suite-opts})
              status-document (:document status-result)]
          (when-not (and (= url (field status-document :id))
                         (= (issuer-id unsecured) (issuer-id status-document)))
            (fail! :interop/status-list-binding-mismatch
                   "status list id or issuer does not bind to the credential"
                   {:url url}))
          (let [checked (status-list/check-status
                         (di/stringify entry) status-document)]
            (when-not (true? (:valid? checked))
              (fail! :interop/credential-not-active
                     "credential is revoked, suspended, or otherwise inactive"
                     {:status (:status checked)
                      :purpose (:purpose checked)})))))))
  secured)

(defn did-key-vc-verify!
  "Concrete `verify!` capability for `did-vc-organization-binding-verifier`.

  It performs real Data Integrity Ed25519 verification, binds the exact
  verification method requested by the adapter, and, when credentialStatus is
  present, requires and verifies a signed Bitstring Status List credential.
  The returned shape is intentionally the adapter's closed two-field result."
  ([secured options] (did-key-vc-verify! secured options {}))
  ([secured options profile]
   (let [result (verify-data-integrity! secured profile)
         unsecured (:document result)
         expected-method (:verificationMethod options)]
     (when-not (and (= "assertionMethod" (:expectedProofPurpose options))
                    (= expected-method (:verification-method result)))
       (fail! :interop/verification-method-mismatch
              "verified proof did not use the admitted assertion method" {}))
     (verify-status! secured unsecured profile)
     {:verified true :verified-document secured})))

(defn did-key-vc-organization-binding-verifier
  "Ready-to-use organization-binding verifier with offline did:key resolution
  and the explicit Ed25519 Data Integrity suite allowlist."
  [{:keys [suite-opts resolve-status-list! max-methods max-subjects max-peers]
    :or {max-methods 8 max-subjects 4 max-peers 32}}]
  (interop/did-vc-organization-binding-verifier
   {:resolve! resolve-did-key!
    :verify! #(did-key-vc-verify!
               %1 %2 {:suite-opts suite-opts
                       :resolve-status-list! resolve-status-list!})
    :allowed-cryptosuites (set (keys accepted-ed25519-suites))
    :max-methods max-methods
    :max-subjects max-subjects
    :max-peers max-peers}))

(defn ucan-result-verifier
  "Concrete UCAN verifier capability. Evidence is exactly
  `{:ucan/chain [root-wire ... leaf-wire]}`. Clock and revocation knowledge are
  injected capabilities and are consulted on every admission."
  [{:keys [now! revoked? max-depth max-bytes]
    :or {max-depth 16 max-bytes 1048576}}]
  (when-not (and (fn? now!) (fn? revoked?))
    (fail! :interop/ucan-profile-invalid
           "UCAN profile requires clock and revocation capabilities" {}))
  (fn [evidence]
    (when-not (and (map? evidence)
                   (= #{:ucan/chain} (set (keys evidence))))
      (fail! :interop/ucan-evidence-fields
             "UCAN evidence must contain only :ucan/chain" {}))
    (ucan/verify-chain
     (:ucan/chain evidence)
     {:now (now!)
      :revoked? revoked?
      :resource! ucan/exact-resource
      :max-depth max-depth
      :max-bytes max-bytes})))

(defn did-key-ucan-delegation-verifier
  "Ready-to-use trusted-admission delegation verifier. The external chain may
  only yield `kotoba://cap/...` resources, which the existing adapter then
  intersects into typed Kotoba grants."
  [{:keys [holder max-resources max-depth] :as opts
    :or {max-resources 32 max-depth 16}}]
  (interop/ucan-delegation-verifier
   {:verify! (ucan-result-verifier (assoc opts :max-depth max-depth))
    :holder holder
    :max-depth max-depth
    :max-resources max-resources}))
