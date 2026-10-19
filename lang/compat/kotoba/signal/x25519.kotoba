;; kotoba.signal.x25519 — raw 32-byte X25519 (RFC 7748) Diffie-Hellman via the
;; JDK 11+ "XDH" provider (java.security), JVM-only.
;;
;; The JCA speaks X25519 only through PKCS8/X.509-wrapped Key objects, never raw
;; 32-byte scalars/u-coordinates. Signal-style bundles (X3DH IK/SPK/OPK, Double
;; Ratchet DH keys) need to travel as plain bytes in EDN maps, so this wraps/
;; unwraps through the minimal DER `OneAsymmetricKey`/`SubjectPublicKeyInfo`
;; prefixes for the X25519 OID (1.3.101.110 = DER bytes 2b 65 6e) — the SAME
;; technique com-junkawasaki/ed25519-clj uses for Ed25519 (OID 1.3.101.112,
;; 2b 65 70); the two curves share an identical minimal-encoding shape, differing
;; only in that one OID byte. Round-tripped and cross-checked at test time
;; (generate → unwrap → rewrap → dh agreement on both sides matches).
;;
;;   (require '[kotoba.signal.x25519 :as x25519])
;;   (x25519/generate-keypair)          ;=> {:priv bytes32 :pub bytes32}
;;   (x25519/dh my-priv their-pub)      ;=> bytes32 shared secret (RFC 7748 X25519())
(ns kotoba.signal.x25519
  (:import (java.security KeyFactory KeyPairGenerator)
           (java.security.spec PKCS8EncodedKeySpec X509EncodedKeySpec)
           (javax.crypto KeyAgreement)))

(def ^:private pkcs8-x25519-prefix
  (byte-array (map unchecked-byte [0x30 0x2e 0x02 0x01 0x00 0x30 0x05 0x06 0x03 0x2b 0x65 0x6e 0x04 0x22 0x04 0x20])))
(def ^:private spki-x25519-prefix
  (byte-array (map unchecked-byte [0x30 0x2a 0x30 0x05 0x06 0x03 0x2b 0x65 0x6e 0x03 0x21 0x00])))

(defn- private-key
  "Load a JCA X25519 PrivateKey from a raw 32-byte scalar (PKCS8 wrap)."
  [^bytes raw-priv]
  (when (not= 32 (alength raw-priv))
    (throw (ex-info "x25519 private key must be exactly 32 bytes" {:got (alength raw-priv)})))
  (.generatePrivate (KeyFactory/getInstance "X25519")
                    (PKCS8EncodedKeySpec. (byte-array (concat (seq pkcs8-x25519-prefix) (seq raw-priv))))))

(defn- public-key
  "Load a JCA X25519 PublicKey from a raw 32-byte u-coordinate (X.509 SPKI wrap)."
  [^bytes raw-pub]
  (when (not= 32 (alength raw-pub))
    (throw (ex-info "x25519 public key must be exactly 32 bytes" {:got (alength raw-pub)})))
  (.generatePublic (KeyFactory/getInstance "X25519")
                   (X509EncodedKeySpec. (byte-array (concat (seq spki-x25519-prefix) (seq raw-pub))))))

(defn generate-keypair
  "Fresh X25519 keypair -> {:priv ^bytes 32 :pub ^bytes 32} (both raw, unwrapped)."
  []
  (let [kp (.generateKeyPair (KeyPairGenerator/getInstance "X25519"))
        priv-enc (.getEncoded (.getPrivate kp))
        pub-enc (.getEncoded (.getPublic kp))]
    {:priv (byte-array (drop (alength ^bytes pkcs8-x25519-prefix) (seq priv-enc)))
     :pub (byte-array (drop (alength ^bytes spki-x25519-prefix) (seq pub-enc)))}))

(defn dh
  "X25519(my-raw-priv, their-raw-pub) -> 32-byte raw shared secret (RFC 7748).
   Commutative: dh(a-priv, b-pub) == dh(b-priv, a-pub) for a matching keypair
   (this is the property X3DH / Double Ratchet rely on for both sides to derive
   the same secret without ever exchanging it directly)."
  ^bytes [^bytes my-priv ^bytes their-pub]
  (let [ka (doto (KeyAgreement/getInstance "X25519") (.init (private-key my-priv)))]
    (.doPhase ka (public-key their-pub) true)
    (.generateSecret ka)))
