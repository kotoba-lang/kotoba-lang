(ns kotoba.lang.interop-verifiers
  "Fail-closed adapters from W3C DID/VC Data Integrity and UCAN verifier
  results into Kotoba's closed trusted-admission result shapes.

  This namespace does not implement cryptography, network DID resolution, or
  JSON-LD processing. Those remain injected live capabilities. It does own the
  semantic checks that are easy to lose between libraries: DID verification
  relationship, proof purpose, cryptosuite allowlist, exact constitution
  binding, UCAN audience, attenuation, and normalized capability resources."
  (:require [clojure.string :as str]
            [kotoba.lang.capability-values :as capabilities]
            [kotoba.lang.code-identity :as identity]
            [kotoba.lang.incidence :as incidence]))

(defn- did? [x]
  (and (string? x) (boolean (re-matches #"did:[a-z0-9]+:[^\s]+" x))))

(defn- get-field [m k]
  (let [s (name k)
        keyword-present? (and (map? m) (contains? m k))
        string-present? (and (map? m) (contains? m s))]
    (when (and keyword-present? string-present?
               (not= (get m k) (get m s)))
      (throw (ex-info "external object contains ambiguous field aliases"
                      {:problem :interop/ambiguous-field :field k})))
    (if keyword-present? (get m k) (get m s))))

(defn- non-empty-string? [x]
  (capabilities/non-empty-string? x))

(defn- positive-int? [x]
  (and (int? x) (pos? x)))

(defn- bounded-vector? [limit x]
  (and (vector? x) (<= (count x) limit)))

(defn- method-id [x]
  (if (string? x) x (get-field x :id)))

(defn- relationship-method [document relationship method]
  (some #(when (= method (method-id %)) %) (get-field document relationship)))

(defn- verification-method [document method]
  (or (some #(when (= method (method-id %)) %) (get-field document :verificationMethod))
      (relationship-method document :assertionMethod method)))

(defn- method-error [did document method max-methods]
  (let [methods (get-field document :verificationMethod)
        assertions (get-field document :assertionMethod)]
    (cond
      (not (map? document)) {:problem :interop/did-document-not-a-map}
      (not= did (get-field document :id)) {:problem :interop/did-document-id-mismatch}
      (not (bounded-vector? max-methods methods))
      {:problem :interop/did-verification-methods-unbounded}
      (not (bounded-vector? max-methods assertions))
      {:problem :interop/did-assertion-methods-unbounded}
      :else
      (let [entry (verification-method document method)
            related (relationship-method document :assertionMethod method)
            controller (when (map? entry) (get-field entry :controller))
            material (when (map? entry)
                       (filter #(some? (get-field entry %))
                               [:publicKeyJwk :publicKeyMultibase]))]
        (cond
          (nil? related) {:problem :interop/did-assertion-method-missing}
          (not (map? entry)) {:problem :interop/did-verification-method-unresolved}
          (not= did controller) {:problem :interop/did-method-controller-mismatch}
          (not= 1 (count material))
          {:problem :interop/did-method-material-invalid})))))

(defn- issuer-id [credential]
  (let [issuer (get-field credential :issuer)]
    (if (string? issuer) issuer (get-field issuer :id))))

(defn- credential-subjects [credential]
  (let [subjects (get-field credential :credentialSubject)]
    (if (vector? subjects) subjects [subjects])))

(defn- proof-error [did method constitution-cid suites max-subjects secured-document]
  (let [proof (get-field secured-document :proof)
        subjects (credential-subjects secured-document)
        suite (get-field proof :cryptosuite)]
    (cond
      (not (map? secured-document)) {:problem :interop/vc-not-a-map}
      (not= did (issuer-id secured-document)) {:problem :interop/vc-issuer-mismatch}
      (not (bounded-vector? max-subjects subjects))
      {:problem :interop/vc-subjects-unbounded}
      (not (some #(and (map? %)
                       (= constitution-cid
                          (get-field % :kotobaConstitution)))
                 subjects))
      {:problem :interop/vc-constitution-mismatch}
      (not (map? proof)) {:problem :interop/data-integrity-proof-missing}
      (not= "DataIntegrityProof" (get-field proof :type))
      {:problem :interop/data-integrity-type-invalid}
      (not (contains? suites suite)) {:problem :interop/cryptosuite-not-allowed}
      (not= method (get-field proof :verificationMethod))
      {:problem :interop/proof-method-mismatch}
      (not= "assertionMethod" (get-field proof :proofPurpose))
      {:problem :interop/proof-purpose-mismatch}
      (not (non-empty-string? (get-field proof :proofValue)))
      {:problem :interop/proof-value-invalid})))

(defn did-vc-organization-binding-verifier
  "Build a verifier accepted by trusted/verify-organization-binding!.

  RESOLVE! accepts a DID and returns its DID document. VERIFY! accepts the
  secured VC and options and must return exactly
  {:verified true :verified-document <same document>}. Network, cache,
  revocation/status, JSON-LD canonicalization, and suite cryptography stay in
  those injected capabilities."
  [{:keys [resolve! verify! allowed-cryptosuites
           max-methods max-subjects max-peers]}]
  (when-not (and (fn? resolve!) (fn? verify!)
                 (set? allowed-cryptosuites) (seq allowed-cryptosuites)
                 (every? non-empty-string? allowed-cryptosuites)
                 (positive-int? max-methods)
                 (positive-int? max-subjects)
                 (positive-int? max-peers))
    (throw (ex-info "DID/VC adapter capabilities are invalid"
                    {:problem :interop/did-vc-adapter-invalid})))
  (fn [{:keys [constitution evidence]}]
    (let [cid (:incidence/cid constitution)
          kind (get-in constitution [:incidence/block :incidence/facts
                                     :organization/kind])
          did (:binding/did evidence)
          method (:binding/verification-method evidence)
          peers (:binding/authorized-peers evidence)
          evidence-cid (:binding/evidence-cid evidence)
          secured (:vc/secured-document evidence)]
      (when-not (= #{:binding/did :binding/verification-method
                     :binding/authorized-peers :binding/evidence-cid
                     :vc/secured-document}
                   (set (keys evidence)))
        (throw (ex-info "DID/VC evidence fields are invalid"
                        {:problem :interop/did-vc-evidence-fields})))
      (when-not (and (did? did) (non-empty-string? method)
                     (str/starts-with? method (str did "#"))
                     (set? peers) (seq peers) (<= (count peers) max-peers)
                     (every? incidence/ref? peers)
                     (identity/cid? evidence-cid))
        (throw (ex-info "DID/VC binding input is invalid"
                        {:problem :interop/did-vc-binding-invalid})))
      (let [document (resolve! did)]
        (when-let [error (method-error did document method max-methods)]
          (throw (ex-info "DID verification relationship was rejected" error)))
        (when-let [error (proof-error did method cid allowed-cryptosuites
                                      max-subjects secured)]
          (throw (ex-info "VC Data Integrity proof was rejected" error)))
        (let [result (verify! secured
                              {:expectedProofPurpose "assertionMethod"
                               :verificationMethod method})]
          (when-not (and (map? result)
                         (= #{:verified :verified-document} (set (keys result)))
                         (true? (:verified result))
                         (= secured (:verified-document result)))
            (throw (ex-info "VC cryptographic verification was rejected"
                            {:problem :interop/vc-verification-invalid})))
          {:binding/valid? true
           :binding/constitution-cid cid
           :binding/kind kind
           :binding/did did
           :binding/authorized-peers peers
           :binding/verification-relationship :assertionMethod
           :binding/verification-method method
           :binding/evidence-cid evidence-cid})))))

(def ^:private ucan-result-fields
  #{:ucan/valid? :ucan/problems :ucan/root-iss :ucan/audience
    :ucan/resources :ucan/expires :ucan/depth :ucan/attenuated?})

(defn ucan-delegation-verifier
  "Build a verifier accepted by trusted/verify-delegation!.

  VERIFY! owns envelope/CID/signature/proof-chain processing and returns the
  closed normalized UCAN result. This adapter requires successful attenuation,
  exact audience, valid cap URIs, and a bounded chain before translating to
  Kotoba's existing chain result."
  [{:keys [verify! holder max-depth max-resources]}]
  (when-not (and (fn? verify!) (did? holder)
                 (int? max-depth) (not (neg? max-depth))
                 (positive-int? max-resources))
    (throw (ex-info "UCAN adapter capabilities are invalid"
                    {:problem :interop/ucan-adapter-invalid})))
  (fn [evidence]
    (let [result (verify! evidence)]
      (when-not (and (map? result)
                     (= ucan-result-fields (set (keys result))))
        (throw (ex-info "UCAN verifier result fields are invalid"
                        {:problem :interop/ucan-result-fields})))
      (when-not (and (true? (:ucan/valid? result))
                     (vector? (:ucan/problems result))
                     (empty? (:ucan/problems result))
                     (true? (:ucan/attenuated? result))
                     (did? (:ucan/root-iss result))
                     (= holder (:ucan/audience result))
                     (set? (:ucan/resources result))
                     (seq (:ucan/resources result))
                     (<= (count (:ucan/resources result)) max-resources)
                     (every? #(and (non-empty-string? %)
                                   (str/starts-with? % "kotoba://cap/"))
                             (:ucan/resources result))
                     (int? (:ucan/depth result))
                     (<= 0 (:ucan/depth result) max-depth))
        (throw (ex-info "UCAN delegation was rejected"
                        {:problem :interop/ucan-delegation-invalid})))
      {:chain/valid? true
       :chain/problems []
       :chain/root-iss (:ucan/root-iss result)
       :chain/holder holder
       :chain/resources (:ucan/resources result)
       :chain/expires (:ucan/expires result)
       :chain/depth (:ucan/depth result)})))
