(ns kotoba.compiler.signing
  (:require [clojure.set :as set]
            [kotoba.compiler.artifact :as artifact]
            [kotoba.compiler.verifier :as verifier])
  (:import [java.security KeyFactory KeyPairGenerator Signature]
           [java.security.spec PKCS8EncodedKeySpec X509EncodedKeySpec]
           [java.nio.charset StandardCharsets]
           [java.util Base64]))

(def encoder (Base64/getEncoder))
(def decoder (Base64/getDecoder))
(defn- b64 [bytes] (.encodeToString encoder bytes))
(defn- unb64 [s] (.decode decoder ^String s))
(defn- sha256? [value]
  (and (string? value) (boolean (re-matches #"[0-9a-f]{64}" value))))

(defn validate-trust! [trust]
  (let [allowed-fields #{:format :trusted-signers :revoked-signers :revoked-artifacts
                         :trusted-runtime-sha256 :revoked-runtime-sha256}]
    (when-not (and (map? trust)
                 (every? allowed-fields (keys trust))
                 (= #{:format :trusted-signers :revoked-signers :revoked-artifacts}
                    (set/intersection (set (keys trust))
                                      #{:format :trusted-signers :revoked-signers :revoked-artifacts}))
                 (= :kotoba.trust/v1 (:format trust))
                 (set? (:trusted-signers trust))
                 (every? sha256? (:trusted-signers trust))
                 (set? (:revoked-signers trust))
                 (every? sha256? (:revoked-signers trust))
                 (set? (:revoked-artifacts trust))
                 (every? sha256? (:revoked-artifacts trust))
                 (or (not (contains? trust :trusted-runtime-sha256))
                     (and (set? (:trusted-runtime-sha256 trust))
                          (every? sha256? (:trusted-runtime-sha256 trust))))
                 (or (not (contains? trust :revoked-runtime-sha256))
                     (and (set? (:revoked-runtime-sha256 trust))
                          (every? sha256? (:revoked-runtime-sha256 trust)))))
      (throw (ex-info "malformed trust policy" {:phase :trust}))))
  trust)

(defn signer-id [public-key]
  (artifact/sha256 {:algorithm :ed25519 :public-key public-key}))

(defn- decode-public [encoded]
  (.generatePublic (KeyFactory/getInstance "Ed25519")
                   (X509EncodedKeySpec. (unb64 encoded))))

(defn- decode-private [encoded]
  (.generatePrivate (KeyFactory/getInstance "Ed25519")
                    (PKCS8EncodedKeySpec. (unb64 encoded))))

(defn- keypair-matches? [public-key private-key]
  (try
    (let [challenge (.getBytes "kotoba:key-consistency:v1" StandardCharsets/UTF_8)
          signature (doto (Signature/getInstance "Ed25519")
                      (.initSign (decode-private private-key))
                      (.update challenge))
          proof (.sign signature)
          verifier (doto (Signature/getInstance "Ed25519")
                     (.initVerify (decode-public public-key))
                     (.update challenge))]
      (.verify verifier proof))
    (catch Exception _ false)))

(defn valid-key? [key]
  (and (map? key)
       (= #{:format :algorithm :signer :public-key :private-key} (set (keys key)))
       (= :kotoba.signing-key/v1 (:format key))
       (= :ed25519 (:algorithm key))
       (string? (:public-key key))
       (string? (:private-key key))
       (= (:signer key) (signer-id (:public-key key)))
       (keypair-matches? (:public-key key) (:private-key key))))

(defn valid-verification-key? [key]
  (and (map? key)
       (= #{:format :algorithm :signer :public-key} (set (keys key)))
       (= :kotoba.verification-key/v1 (:format key))
       (= :ed25519 (:algorithm key))
       (string? (:public-key key))
       (= (:signer key) (signer-id (:public-key key)))
       (try (decode-public (:public-key key)) true (catch Exception _ false))))

(defn verification-key [signing-key]
  (when-not (valid-key? signing-key)
    (throw (ex-info "malformed Ed25519 signing key" {:phase :sign})))
  {:format :kotoba.verification-key/v1 :algorithm :ed25519
   :signer (:signer signing-key) :public-key (:public-key signing-key)})

(defn trusted-signer-id! [key]
  (cond
    (valid-verification-key? key) (:signer key)
    (valid-key? key) (:signer key)
    :else (throw (ex-info "malformed Ed25519 verification key" {:phase :trust}))))

(defn sign-value [key value]
  (when-not (valid-key? key)
    (throw (ex-info "malformed Ed25519 signing key" {:phase :sign})))
  (let [private-key (decode-private (:private-key key))
        signer (doto (Signature/getInstance "Ed25519") (.initSign private-key)
                 (.update (artifact/canonical-bytes value)))]
    (b64 (.sign signer))))

(defn verify-value [public-key value signature]
  (try
    (let [public (decode-public public-key)
          checker (doto (Signature/getInstance "Ed25519") (.initVerify public)
                    (.update (artifact/canonical-bytes value)))]
      (.verify checker (unb64 signature)))
    (catch Exception _ false)))

(defn generate-keypair []
  (let [pair (.generateKeyPair (KeyPairGenerator/getInstance "Ed25519"))
        public (b64 (.getEncoded (.getPublic pair)))
        private (b64 (.getEncoded (.getPrivate pair)))]
    {:format :kotoba.signing-key/v1 :algorithm :ed25519
     :signer (signer-id public) :public-key public :private-key private}))

(defn- statement [artifact key not-before expires]
  {:format :kotoba.signature-statement/v1
   :artifact-sha256 (:sha256 artifact)
   :signer (:signer key)
   :public-key (:public-key key)
   :not-before not-before
   :expires expires})

(defn sign [kexe key {:keys [not-before expires]}]
  (verifier/verify-artifact! kexe)
  (when-not (valid-key? key)
    (throw (ex-info "malformed Ed25519 signing key" {:phase :sign})))
  (when-not (and (integer? not-before) (integer? expires) (< not-before expires))
    (throw (ex-info "invalid signature validity interval" {:phase :sign})))
  (let [statement (statement kexe key not-before expires)
        signature (sign-value key statement)]
    {:format :kotoba.signed-kexe/v1 :artifact kexe :statement statement
     :signature signature}))

(defn verify
  [envelope trust now]
  (when-not (= :kotoba.signed-kexe/v1 (:format envelope))
    (throw (ex-info "unknown signed artifact envelope" {:phase :signature})))
  (when-not (and (map? envelope)
                 (= #{:format :artifact :statement :signature} (set (keys envelope))))
    (throw (ex-info "signed artifact envelope schema mismatch" {:phase :signature})))
  (validate-trust! trust)
  (let [{:keys [artifact statement signature]} envelope
        {:keys [signer public-key not-before expires artifact-sha256]} statement]
    (when-not (and (map? statement)
                   (= #{:format :artifact-sha256 :signer :public-key :not-before :expires}
                      (set (keys statement)))
                   (= :kotoba.signature-statement/v1 (:format statement))
                   (= signer (signer-id public-key))
                   (= artifact-sha256 (:sha256 artifact))
                   (integer? not-before) (integer? expires) (< not-before expires)
                   (string? signature))
      (throw (ex-info "signature statement mismatch" {:phase :signature})))
    (when-not (verify-value public-key statement signature)
      (throw (ex-info "Ed25519 signature verification failed" {:phase :signature})))
    (when-not (contains? (set (:trusted-signers trust)) signer)
      (throw (ex-info "signer is not trusted" {:phase :trust :signer signer})))
    (when (contains? (set (:revoked-signers trust)) signer)
      (throw (ex-info "signer is revoked" {:phase :trust :signer signer})))
    (when (contains? (set (:revoked-artifacts trust)) artifact-sha256)
      (throw (ex-info "artifact is revoked" {:phase :trust :artifact artifact-sha256})))
    (when (< now not-before)
      (throw (ex-info "signature is not yet valid" {:phase :trust :not-before not-before :now now})))
    (when (>= now expires)
      (throw (ex-info "signature is expired" {:phase :trust :expires expires :now now})))
    (verifier/verify-artifact! artifact)
    {:verified? true :signer signer :artifact artifact
     :validity {:not-before not-before :expires expires}}))
