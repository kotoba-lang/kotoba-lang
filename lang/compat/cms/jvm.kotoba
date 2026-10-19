(ns cms.jvm
  "Ready-made `:digest-fn` and `:verify-fn` for the JVM.

  `cms.core` takes both as arguments and holds no keys, for the reason
  `x509.core` and `data-integrity.core` both record. That is right for the
  library and tedious for every caller, so the JVM implementations live here —
  in a `.clj` file, so a `.cljs` consumer gets a missing namespace rather than a
  namespace that pretends.

  `verify` refuses an algorithm it cannot name. An algorithm registry that fell
  back to a default is how a signature made with one algorithm gets checked with
  another."
  (:require [asn1.core :as asn1]
            [asn1.oid :as oid])
  (:import [java.security KeyFactory MessageDigest Signature]
           [java.security.spec X509EncodedKeySpec]))

(defn digest
  "`(digest :sha256 ints) -> ints`."
  [algorithm-name data]
  (let [spec (get oid/digest-algorithms algorithm-name)]
    (when-not spec
      (throw (ex-info (str "unsupported digest algorithm: " algorithm-name)
                      {:type :cms/unsupported-digest :algorithm algorithm-name})))
    (asn1/->ints (.digest (MessageDigest/getInstance (:jca spec))
                          (asn1/ints->bytes (asn1/->ints data))))))

(defn public-key
  "A `java.security.PublicKey` from a parsed `:x509/public-key`.

  Built from the SubjectPublicKeyInfo DER as it arrived, rather than from the
  curve and point read out of it. `X509EncodedKeySpec` takes exactly that
  structure, so there is nothing to reassemble and therefore nothing to
  reassemble wrongly."
  [{:keys [algorithm spki-der]}]
  (let [family (condp = (oid/named algorithm)
                 :ec-public-key "EC"
                 :rsa-encryption "RSA"
                 :ed25519 "Ed25519"
                 (throw (ex-info (str "unsupported key algorithm: " (oid/describe algorithm))
                                 {:type :cms/unsupported-key-algorithm
                                  :algorithm algorithm})))]
    (.generatePublic (KeyFactory/getInstance family)
                     (X509EncodedKeySpec. (asn1/ints->bytes (asn1/->ints spki-der))))))

(defn verify
  "A `:verify-fn`. Returns a boolean; throws only for an algorithm it will not
  honour, which a caller must not read as a failed signature."
  [{:keys [algorithm public-key signed signature] :as request}]
  (let [spec (get oid/signature-algorithms algorithm)]
    (when-not spec
      (throw (ex-info (str "unsupported signature algorithm: "
                           (oid/describe (:algorithm-oid request))
                           (when-let [note (oid/retirement-note algorithm)]
                             (str " — " note)))
                      {:type :cms/unsupported-signature-algorithm
                       :algorithm algorithm})))
    (let [verifier (Signature/getInstance (:jca spec))]
      (.initVerify verifier (cms.jvm/public-key public-key))
      (.update verifier (asn1/ints->bytes (asn1/->ints signed)))
      (.verify verifier (asn1/ints->bytes (asn1/->ints signature))))))

(defn signer
  "A `:sign-fn` from a `java.security.PrivateKey` and an algorithm name."
  [private-key algorithm-name]
  (let [spec (get oid/signature-algorithms algorithm-name)]
    (when-not spec
      (throw (ex-info (str "unsupported signature algorithm: " algorithm-name)
                      {:type :cms/unsupported-signature-algorithm
                       :algorithm algorithm-name})))
    (fn [data]
      (let [s (Signature/getInstance (:jca spec))]
        (.initSign s private-key)
        (.update s (asn1/ints->bytes (asn1/->ints data)))
        (asn1/->ints (.sign s))))))
