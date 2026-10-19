(ns kagi.native-key
  "Non-exportable signing handles backed by a configured JCA KeyStore/HSM.

  This module never calls PrivateKey.getEncoded. The token/provider owns private
  material; kagi persists aliases and public encodings only."
  (:require [cbor.core :as cbor]
            [kagi.crypto :as crypto])
  (:import [java.security KeyStore Signature PublicKey MessageDigest Security KeyFactory]
           [java.security.spec X509EncodedKeySpec]
           [javax.crypto KeyAgreement KEM]
           [java.util Arrays]))

(defrecord HybridSigningHandle [keystore-id ed-alias mldsa-alias])
(defrecord HybridKemHandle [keystore-id x25519-alias mlkem-alias])

(declare sign require-native-keystore! sign-with key!)

(defrecord NativeSigner [keystore handle]
  crypto/SigningHandle
  (sign-hybrid [_ _provider message]
    (sign keystore handle message))
  (sign-ed25519 [_ message]
    (require-native-keystore! keystore)
    (sign-with "Ed25519" (key! keystore (:ed-alias handle)) message)))

(defrecord NativeKemRecipient [keystore handle public]
  crypto/DecapsulationHandle
  (decapsulate-hybrid [_ _provider {ephemeral-x :x pq-ciphertext :pq}]
    (require-native-keystore! keystore)
    (let [ephemeral-public (.generatePublic (KeyFactory/getInstance "X25519")
                                            (X509EncodedKeySpec. ephemeral-x))
          agreement (doto (KeyAgreement/getInstance "X25519")
                      (.init (key! keystore (:x25519-alias handle))))
          _ (.doPhase agreement ephemeral-public true)
          ss-x (.generateSecret agreement)
          decapsulator (.newDecapsulator
                        (KEM/getInstance "ML-KEM")
                        (key! keystore (:mlkem-alias handle)))
          ss-pq (.getEncoded (.decapsulate decapsulator pq-ciphertext))]
      (try
        (crypto/hybrid-kem-shared ss-x ss-pq (:x public) ephemeral-x
                                  (:pq public) pq-ciphertext)
        (finally
          (Arrays/fill ^bytes ss-x (byte 0))
          (Arrays/fill ^bytes ss-pq (byte 0)))))))

(def ^:private native-keystore-types
  #{"PKCS11" "KeychainStore" "Windows-MY" "Windows-ROOT" "AndroidKeyStore"})

(defn native-keystore?
  "True only for keystore types whose private operations are delegated to an
  OS or hardware provider. File-backed JKS/JCEKS/PKCS12 stores are explicitly
  excluded even if a caller labels their aliases as an HSM handle."
  [^KeyStore ks]
  (contains? native-keystore-types (.getType ks)))

(defn- require-native-keystore! [^KeyStore ks]
  (when-not (native-keystore? ks)
    (throw (ex-info "software keystore cannot satisfy non-exportable custody"
                    {:keystore-type (.getType ks)
                     :allowed native-keystore-types})))
  ks)

(defn handle [keystore-id ed-alias mldsa-alias]
  (when-not (every? seq [keystore-id ed-alias mldsa-alias])
    (throw (ex-info "native key aliases are required" {})))
  (->HybridSigningHandle keystore-id ed-alias mldsa-alias))

(defn kem-handle [keystore-id x25519-alias mlkem-alias]
  (when-not (every? seq [keystore-id x25519-alias mlkem-alias])
    (throw (ex-info "native KEM aliases are required" {})))
  (->HybridKemHandle keystore-id x25519-alias mlkem-alias))

(defn signer
  "Create an opaque runtime signer after validating that the backing KeyStore
  is an OS/HSM-native type. Neither this value nor its metadata contains a key."
  [^KeyStore ks ^HybridSigningHandle h]
  (require-native-keystore! ks)
  (->NativeSigner ks h))

(defn- key! [^KeyStore ks alias]
  (or (.getKey ks alias nil)
      (throw (ex-info "native private key handle not found" {:alias alias}))))

(defn- public! ^PublicKey [^KeyStore ks alias]
  (or (some-> (.getCertificate ks alias) (.getPublicKey))
      (throw (ex-info "native public certificate not found" {:alias alias}))))

(defn- sign-with [algorithm private-key ^bytes message]
  (let [s (doto (Signature/getInstance algorithm) (.initSign private-key))]
    (.update s message)
    (.sign s)))

(defn sign
  "Hybrid-sign through token aliases without exporting either private key."
  [^KeyStore ks ^HybridSigningHandle h ^bytes message]
  (require-native-keystore! ks)
  {:ed (sign-with "Ed25519" (key! ks (:ed-alias h)) message)
   :mldsa (sign-with "ML-DSA-65" (key! ks (:mldsa-alias h)) message)})

(defn sign-encoded
  "Canonical wire representation of the hybrid signature produced entirely
  inside the configured token/provider."
  [ks h message]
  (cbor/encode (sign ks h message)))

(defn public-bundle [^KeyStore ks ^HybridSigningHandle h]
  (require-native-keystore! ks)
  {:ed (.getEncoded (public! ks (:ed-alias h)))
   :mldsa (.getEncoded (public! ks (:mldsa-alias h)))})

(defn kem-public-bundle [^KeyStore ks ^HybridKemHandle h]
  (require-native-keystore! ks)
  {:x (.getEncoded (public! ks (:x25519-alias h)))
   :pq (.getEncoded (public! ks (:mlkem-alias h)))})

(defn assert-public-bundle!
  "Bind configured aliases to the public identity before any signature is
  accepted. Constant-time byte comparison prevents an operator typo or token
  substitution from silently changing authority."
  [^KeyStore ks ^HybridSigningHandle h expected]
  (let [actual (public-bundle ks h)]
    (when-not (and (map? expected)
                   (MessageDigest/isEqual ^bytes (:ed actual) ^bytes (:ed expected))
                   (MessageDigest/isEqual ^bytes (:mldsa actual) ^bytes (:mldsa expected)))
      (throw (ex-info "native signing aliases do not match identity public keys"
                      {:keystore-id (:keystore-id h)
                       :ed-alias (:ed-alias h)
                       :mldsa-alias (:mldsa-alias h)})))
    true))

(defn bound-signer
  "Create a native signer only after both certificate public keys match the
  persisted hybrid identity."
  [^KeyStore ks ^HybridSigningHandle h expected-public]
  (assert-public-bundle! ks h expected-public)
  (signer ks h))

(defn bound-kem-recipient
  "Bind both native decapsulation aliases to the persisted hybrid KEM public keys."
  [^KeyStore ks ^HybridKemHandle h expected-public]
  (let [actual (kem-public-bundle ks h)]
    (when-not (and (map? expected-public)
                   (MessageDigest/isEqual ^bytes (:x actual) ^bytes (:x expected-public))
                   (MessageDigest/isEqual ^bytes (:pq actual) ^bytes (:pq expected-public)))
      (throw (ex-info "native KEM aliases do not match identity public keys"
                      {:keystore-id (:keystore-id h)
                       :x25519-alias (:x25519-alias h)
                       :mlkem-alias (:mlkem-alias h)})))
    (->NativeKemRecipient ks h actual)))

(defn metadata [^HybridSigningHandle h]
  {:custody :non-exportable-native-handle
   :keystore-id (:keystore-id h)
   :aliases {:ed (:ed-alias h) :mldsa (:mldsa-alias h)}
   :private-exported? false})

(defn kem-metadata [^HybridKemHandle h]
  {:custody :non-exportable-native-handle
   :keystore-id (:keystore-id h)
   :aliases {:x25519 (:x25519-alias h) :mlkem (:mlkem-alias h)}
   :private-exported? false})

(defn load-keystore
  "Load an already configured OS/HSM KeyStore. PKCS11 configuration and PIN
  acquisition remain outside EDN and must be supplied by the process bootstrap."
  [type provider load-stream password]
  (let [ks (if provider (KeyStore/getInstance type provider) (KeyStore/getInstance type))]
    (require-native-keystore! ks)
    (.load ks load-stream password)
    ks))

(defn bootstrap
  "Resolve and load an explicitly configured native KeyStore and aliases.
  Password/PIN characters are erased before return. Provider lookup and all
  required fields fail closed; no software fallback is attempted."
  [{:keys [type provider-name password keystore-id ed-alias mldsa-alias
           x25519-alias mlkem-alias]}]
  (when-not (every? seq [type keystore-id ed-alias mldsa-alias])
    (throw (ex-info "incomplete native signing configuration"
                    {:required [:type :keystore-id :ed-alias :mldsa-alias]})))
  (let [provider (when (seq provider-name)
                   (or (Security/getProvider provider-name)
                       (throw (ex-info "configured JCA provider is unavailable"
                                       {:provider provider-name}))))
        pin (when password (char-array password))]
    (try
      (let [ks (load-keystore type provider nil pin)
            h (handle keystore-id ed-alias mldsa-alias)]
        (cond-> {:keystore ks :handle h}
          (or x25519-alias mlkem-alias)
          (assoc :kem-handle (kem-handle keystore-id x25519-alias mlkem-alias))))
      (finally
        (when pin (Arrays/fill ^chars pin (char 0)))))))
