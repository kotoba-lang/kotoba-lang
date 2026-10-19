(ns kotoba.lang.pqh.pq-bc
  "JVM BouncyCastle host implementation of kotoba.lang.pqh.pq/IPq.

   This is the production JVM provider for X25519, ML-KEM-768 (FIPS 203),
   HKDF-SHA256, and ML-DSA-65 (FIPS 204). The portable orchestration remains
   in pq.cljc; this namespace is deliberately JVM-only."
  (:require [kotoba.lang.pqh.pq :as pq])
  (:import (org.bouncycastle.crypto.params X25519PrivateKeyParameters X25519PublicKeyParameters
                                            HKDFParameters)
           (org.bouncycastle.crypto.generators HKDFBytesGenerator)
           (org.bouncycastle.crypto.digests SHA256Digest)
           (org.bouncycastle.pqc.crypto.mlkem MLKEMParameters MLKEMKeyPairGenerator
                                               MLKEMKeyGenerationParameters
                                               MLKEMPublicKeyParameters MLKEMPrivateKeyParameters
                                               MLKEMGenerator MLKEMExtractor)
           (org.bouncycastle.pqc.crypto.mldsa MLDSAParameters MLDSAPrivateKeyParameters
                                               MLDSAPublicKeyParameters MLDSASigner)
           (java.security SecureRandom)))

(def ^:private mlkem-params MLKEMParameters/ml_kem_768)
(def ^:private mldsa-params MLDSAParameters/ml_dsa_65)

(defn- secure-random
  "Create the CSPRNG at operation time so native-image never captures a
   build-host seed in its image heap."
  []
  (SecureRandom.))

(defn bc-pq
  "Return a fresh IPq adapter backed by BouncyCastle."
  []
  (reify pq/IPq
    (-x25519-generate [_]
      (let [priv (X25519PrivateKeyParameters. (secure-random))]
        [(.getEncoded priv) (.getEncoded (.generatePublicKey priv))]))
    (-x25519-dh [_ secret public]
      (let [priv (X25519PrivateKeyParameters. ^bytes secret)
            pub (X25519PublicKeyParameters. ^bytes public)
            out (byte-array 32)]
        (.generateSecret priv pub out 0)
        out))
    (-mlkem-generate [_]
      (let [kpg (doto (MLKEMKeyPairGenerator.)
                  (.init (MLKEMKeyGenerationParameters. (secure-random) mlkem-params)))
            kp (.generateKeyPair kpg)]
        [(.getEncoded ^MLKEMPrivateKeyParameters (.getPrivate kp))
         (.getEncoded ^MLKEMPublicKeyParameters (.getPublic kp))]))
    (-mlkem-encapsulate [_ public]
      (let [pub (MLKEMPublicKeyParameters. mlkem-params ^bytes public)
            gen (MLKEMGenerator. (secure-random))
            encap (.generateEncapsulated gen pub)]
        [(.getEncapsulation encap) (.getSecret encap)]))
    (-mlkem-decapsulate [_ secret ciphertext]
      (let [priv (MLKEMPrivateKeyParameters. mlkem-params ^bytes secret)
            extractor (MLKEMExtractor. priv)]
        (.extractSecret extractor ^bytes ciphertext)))
    (-hkdf-sha256 [_ ikm salt info length]
      (let [gen (doto (HKDFBytesGenerator. (SHA256Digest.))
                  (.init (HKDFParameters. ^bytes ikm ^bytes salt ^bytes info)))
            out (byte-array length)]
        (.generateBytes gen out 0 length)
        out))
    (-mldsa-keygen-from-seed [_ seed]
      (let [priv (MLDSAPrivateKeyParameters. mldsa-params ^bytes seed)]
        (.getEncoded (.getPublicKeyParameters priv))))
    (-mldsa-sign [_ secret-seed message]
      (let [priv (MLDSAPrivateKeyParameters. mldsa-params ^bytes secret-seed)
            signer (doto (MLDSASigner.) (.init true priv))]
        (.update signer ^bytes message 0 (alength ^bytes message))
        (.generateSignature signer)))
    (-mldsa-verify [_ public message signature]
      (let [pub (MLDSAPublicKeyParameters. mldsa-params ^bytes public)
            verifier (doto (MLDSASigner.) (.init false pub))]
        (.update verifier ^bytes message 0 (alength ^bytes message))
        (.verifySignature verifier ^bytes signature)))))
