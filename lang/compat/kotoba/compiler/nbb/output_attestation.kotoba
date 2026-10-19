(ns kotoba.compiler.nbb.output-attestation
  "Ed25519 publisher authentication for one admitted output set.

  The signature binds the committed marker, sealed provenance, primary
  artifact identity, and target.  It intentionally does not replace the
  independent output admission that callers must run first."
  (:require [kotoba.artifact.core :as artifact]
            [kotoba.kir.cljs-i64 :as i64]
            ["node:crypto" :as crypto]))

(def format :kotoba.output-attestation/v1)
(def statement-format :kotoba.output-attestation-statement/v1)

(def ^:private envelope-keys #{:format :statement :signature})
(def ^:private statement-keys
  #{:format :output-set-sha256 :provenance-sha256 :artifact-sha256
    :target :signer :public-key :not-before :expires})
(def ^:private signing-key-keys
  #{:format :algorithm :signer :public-key :private-key})
(def ^:private trust-required-keys
  #{:format :trusted-signers :revoked-signers :revoked-artifacts})
(def ^:private trust-optional-keys
  #{:trusted-runtime-sha256 :revoked-runtime-sha256})

(defn- reject! [phase reason message]
  (throw (ex-info message {:phase phase :reason reason})))

(defn- digest? [value]
  (and (string? value) (boolean (re-matches #"[0-9a-f]{64}" value))))

(defn- strict-base64 [value]
  (when (and (string? value)
             (pos? (count value))
             (zero? (mod (count value) 4))
             (boolean (re-matches #"[A-Za-z0-9+/]+={0,2}" value)))
    (let [decoded (.from js/Buffer value "base64")]
      (when (= value (.toString decoded "base64")) decoded))))

(defn- public-key [encoded]
  (when-let [bytes (strict-base64 encoded)]
    (try
      (let [key (.createPublicKey crypto #js {:key bytes :format "der" :type "spki"})]
        (when (= "ed25519" (.-asymmetricKeyType key)) key))
      (catch :default _ nil))))

(defn- private-key [encoded]
  (when-let [bytes (strict-base64 encoded)]
    (try
      (let [key (.createPrivateKey crypto #js {:key bytes :format "der" :type "pkcs8"})]
        (when (= "ed25519" (.-asymmetricKeyType key)) key))
      (catch :default _ nil))))

(defn- signature-bytes [encoded]
  (when-let [bytes (strict-base64 encoded)]
    (when (= 64 (.-length bytes)) bytes)))

(defn- verifies? [public statement signature]
  (try
    (and public signature
         (.verify crypto nil (artifact/canonical-bytes statement)
                  public signature))
    (catch :default _ false)))

(defn signer-id [encoded-public-key]
  (artifact/sha256 {:algorithm :ed25519 :public-key encoded-public-key}))

(defn- keypair-matches? [encoded-public encoded-private]
  (try
    (let [public (public-key encoded-public)
          private (private-key encoded-private)
          challenge (.from js/Buffer "kotoba:key-consistency:v1" "utf8")
          signature (when private (.sign crypto nil challenge private))]
      (and public signature (.verify crypto nil challenge public signature)))
    (catch :default _ false)))

(defn- signing-key! [key]
  (when-not (and (map? key)
                 (= signing-key-keys (set (keys key)))
                 (= :kotoba.signing-key/v1 (:format key))
                 (= :ed25519 (:algorithm key))
                 (string? (:public-key key))
                 (string? (:private-key key))
                 (= (:signer key) (signer-id (:public-key key)))
                 (keypair-matches? (:public-key key) (:private-key key)))
    (reject! :signature :invalid-key "output-set signing key is invalid"))
  key)

(defn- trust! [trust]
  (let [keys (set (keys trust))]
    (when-not (and (map? trust)
                   (= :kotoba.trust/v1 (:format trust))
                   (every? (into trust-required-keys trust-optional-keys) keys)
                   (every? keys trust-required-keys)
                   (every? #(and (set? (get trust %))
                                 (every? digest? (get trust %)))
                           (disj trust-required-keys :format))
                   (every? #(or (not (contains? trust %))
                                (and (set? (get trust %))
                                     (every? digest? (get trust %))))
                           trust-optional-keys))
      (reject! :trust :malformed-trust "output-set trust policy is malformed")))
  trust)

(defn- epoch! [value reason]
  (when-not (and (i64/bigint-value? value)
                 (<= 0N value 9223372036854775807N))
    (reject! :signature reason "output-set signature time is invalid"))
  value)

(defn parse-epoch! [value]
  (try
    (epoch! (js/BigInt value) :invalid-time)
    (catch :default _
      (reject! :usage :invalid-time "output-set signature time must be a non-negative i64"))))

(defn- identities! [marker provenance]
  (let [marker-sha (:sha256 marker)
        provenance-sha (:sha256 provenance)
        artifact-sha (get-in provenance [:outputs :primary :sha256])
        target (:target provenance)]
    (when-not (and (every? digest? [marker-sha provenance-sha artifact-sha])
                   (keyword? target))
      (reject! :signature :invalid-output-identity
               "output-set identity cannot be attested"))
    {:output-set-sha256 marker-sha
     :provenance-sha256 provenance-sha
     :artifact-sha256 artifact-sha
     :target target}))

(defn- statement [marker provenance key not-before expires]
  (merge {:format statement-format}
         (identities! marker provenance)
         {:signer (:signer key)
          :public-key (:public-key key)
          :not-before not-before
          :expires expires}))

(defn sign [marker provenance key not-before expires]
  (let [key (signing-key! key)
        not-before (epoch! not-before :invalid-validity)
        expires (epoch! expires :invalid-validity)]
    (when-not (< not-before expires)
      (reject! :signature :invalid-validity
               "output-set signature validity interval is invalid"))
    (let [statement (statement marker provenance key not-before expires)
          private (private-key (:private-key key))]
      {:format format
       :statement statement
       :signature (.toString
                   (.sign crypto nil (artifact/canonical-bytes statement) private)
                   "base64")})))

(defn verify!
  [envelope trust marker provenance now]
  (let [statement (:statement envelope)
        signature (:signature envelope)
        expected (identities! marker provenance)
        now (epoch! now :invalid-now)]
    (when-not (and (map? envelope)
                   (= envelope-keys (set (keys envelope)))
                   (= format (:format envelope))
                   (map? statement)
                   (= statement-keys (set (keys statement)))
                   (= statement-format (:format statement))
                   (every? digest? ((juxt :output-set-sha256
                                          :provenance-sha256
                                          :artifact-sha256) statement))
                   (keyword? (:target statement))
                   (string? (:signer statement))
                   (string? (:public-key statement))
                   (= (:signer statement) (signer-id (:public-key statement)))
                   (signature-bytes signature))
      (reject! :signature :malformed-attestation
               "output-set attestation schema is invalid"))
    (let [not-before (epoch! (:not-before statement) :invalid-validity)
          expires (epoch! (:expires statement) :invalid-validity)
          trust (trust! trust)
          public (public-key (:public-key statement))
          signature (signature-bytes signature)]
      (when-not (< not-before expires)
        (reject! :signature :invalid-validity
                 "output-set signature validity interval is invalid"))
      (when-not (verifies? public statement signature)
        (reject! :signature :invalid-signature
                 "output-set Ed25519 signature is invalid"))
      (when-not (contains? (:trusted-signers trust) (:signer statement))
        (reject! :trust :untrusted-signer "output-set signer is not trusted"))
      (when (contains? (:revoked-signers trust) (:signer statement))
        (reject! :trust :revoked-signer "output-set signer is revoked"))
      (when (some (:revoked-artifacts trust) (vals expected))
        (reject! :trust :revoked-output "output-set identity is revoked"))
      (when (< now not-before)
        (reject! :trust :not-yet-valid "output-set signature is not yet valid"))
      (when (>= now expires)
        (reject! :trust :expired "output-set signature is expired"))
      (when-not (= expected (select-keys statement (keys expected)))
        (reject! :signature :identity-mismatch
                 "output-set attestation does not name the admitted output"))
      {:publisher-authenticated true
       :publisher (:signer statement)
       :publisher-attestation format
       :publisher-validity {:not-before not-before :expires expires}})))
