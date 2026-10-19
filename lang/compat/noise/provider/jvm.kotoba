(ns noise.provider.jvm
  "JVM primitive ports: X25519 and ChaCha20-Poly1305 from the JCA, no third-party
  dependency (JDK 11+ ships both; verified here on Temurin 21).

  Per this workspace's runtime ordering the JVM is the *compat* path — the
  first-class runtime is ClojureScript (`noise.provider.noble`). This provider
  exists because it is dependency-free, which makes it the honest place to run
  the RFC known-answer tests: nothing about the protocol core can be passing
  because of a favourable npm version.

  The DER wrapping below is the same technique `kotoba.signal.x25519`
  (kotoba-lang/org-signal) and kotoba-lang/ed25519 use: the JCA speaks X25519
  only through PKCS8/X.509-wrapped key objects, never raw 32-byte scalars, but
  every Noise message carries raw ones. It is duplicated here rather than taken
  as a dependency because pulling org-signal in would drag its Ed25519
  dependency into every consumer of this library for thirty lines of glue."
  (:import (java.security KeyFactory KeyPairGenerator SecureRandom)
           (java.security.spec PKCS8EncodedKeySpec X509EncodedKeySpec)
           (javax.crypto Cipher KeyAgreement)
           (javax.crypto.spec IvParameterSpec SecretKeySpec)))

(def ^:private pkcs8-prefix
  ;; OneAsymmetricKey, algorithm OID 1.3.101.110 (X25519 = DER 2b 65 6e)
  (mapv unchecked-byte [0x30 0x2e 0x02 0x01 0x00 0x30 0x05 0x06 0x03 0x2b 0x65 0x6e 0x04 0x22 0x04 0x20]))

(def ^:private spki-prefix
  (mapv unchecked-byte [0x30 0x2a 0x30 0x05 0x06 0x03 0x2b 0x65 0x6e 0x03 0x21 0x00]))

(defn ->bytes
  "byte-vector (ints 0..255) -> ^bytes"
  ^bytes [bs]
  (byte-array (map unchecked-byte bs)))

(defn ->vec
  "^bytes -> byte-vector (ints 0..255)"
  [^bytes ba]
  (mapv #(bit-and % 0xff) ba))

;; `Cipher/getInstance` and friends do a provider lookup on every call, which
;; measured at ~1 ms here — an order of magnitude more than the cipher itself.
;; They are stateful, so they are cached per thread and re-initialized per call
;; rather than shared.
(def ^:private ^ThreadLocal chacha-cipher
  (proxy [ThreadLocal] []
    (initialValue [] (Cipher/getInstance "ChaCha20-Poly1305"))))

(def ^:private ^ThreadLocal x25519-factory
  (proxy [ThreadLocal] []
    (initialValue [] (KeyFactory/getInstance "X25519"))))

(def ^:private ^ThreadLocal x25519-agreement
  (proxy [ThreadLocal] []
    (initialValue [] (KeyAgreement/getInstance "X25519"))))

(defn- private-key [priv]
  (.generatePrivate ^KeyFactory (.get x25519-factory)
                    (PKCS8EncodedKeySpec. (->bytes (into (vec pkcs8-prefix) priv)))))

(defn- public-key [pub]
  (.generatePublic ^KeyFactory (.get x25519-factory)
                   (X509EncodedKeySpec. (->bytes (into (vec spki-prefix) pub)))))

(defn dh-generate []
  (let [kp (.generateKeyPair (KeyPairGenerator/getInstance "X25519"))]
    {:priv (vec (drop (count pkcs8-prefix) (->vec (.getEncoded (.getPrivate kp)))))
     :pub (vec (drop (count spki-prefix) (->vec (.getEncoded (.getPublic kp)))))}))

(defn dh
  "X25519(priv, pub) -> 32-byte shared secret (RFC 7748)."
  [priv pub]
  (let [ka (doto ^KeyAgreement (.get x25519-agreement) (.init (private-key priv)))]
    (.doPhase ka (public-key pub) true)
    (->vec (.generateSecret ka))))

(defn- init-chacha [^Cipher c mode k nonce]
  (doto c
    (.init (int mode)
           (SecretKeySpec. (->bytes k) "ChaCha20")
           (IvParameterSpec. (->bytes nonce))
           (SecureRandom.))))

(defn- chacha
  "A cached, freshly-initialized ChaCha20-Poly1305 Cipher.

   The JCA refuses to re-initialize a ChaCha20 Cipher with the *same* key and
   nonce it last used ('Matching key and nonce from previous initialization') —
   a guard against keystream reuse, and a correct one. Noise never legitimately
   hits it (a nonce is used once per key), but a caller that does — a benchmark,
   a test encrypting the same thing twice — should not get an exception from a
   caching optimization it cannot see. So fall back to a fresh instance."
  [mode k nonce]
  (try
    (init-chacha (.get chacha-cipher) mode k nonce)
    (catch java.security.InvalidKeyException _
      (init-chacha (Cipher/getInstance "ChaCha20-Poly1305") mode k nonce))))

(defn aead-encrypt
  "ChaCha20-Poly1305 seal; the 16-byte tag is appended, as Noise expects."
  [k nonce ad plaintext]
  (let [c (chacha Cipher/ENCRYPT_MODE k nonce)]
    (when (seq ad) (.updateAAD c (->bytes ad)))
    (->vec (.doFinal c (->bytes plaintext)))))

(defn aead-decrypt
  "-> plaintext, or nil on authentication failure (the shape
   `noise.cipher-state` expects; it turns nil into a thrown error so a caller
   cannot accidentally use an unauthenticated plaintext)."
  [k nonce ad ciphertext]
  (try
    (let [c (chacha Cipher/DECRYPT_MODE k nonce)]
      (when (seq ad) (.updateAAD c (->bytes ad)))
      (->vec (.doFinal c (->bytes ciphertext))))
    (catch javax.crypto.AEADBadTagException _ nil)
    (catch javax.crypto.BadPaddingException _ nil)))

(defn sha256 [bs]
  (->vec (.digest (java.security.MessageDigest/getInstance "SHA-256") (->bytes bs))))

(defn ports
  "The injected ports. `opts` mirrors `noise.suite/suite`'s: pass the same
   `:hash` to both, or the suite and the primitives will disagree about which
   hash they are using and every handshake will fail against a correct peer.

   For `:sha256` the JCA's digest is injected (native, fast). For `:blake2s` no
   `:hash` is supplied and the suite falls back to `noise.blake2s` — the JCA has
   no BLAKE2s, and adding BouncyCastle to get one is not worth a dependency for a
   compat runtime. Note the consequence: the BLAKE2s suites hash in interpreted
   Clojure here. Fine for tests and for handshakes; if a JVM host ever carries
   real traffic on the BLAKE2s suite, inject a native hash."
  ([] (ports {}))
  ([{:keys [hash] :or {hash :blake2s}}]
   (cond-> {:dh-generate dh-generate
            :dh dh
            :aead-encrypt aead-encrypt
            :aead-decrypt aead-decrypt}
     (= :sha256 hash) (assoc :hash sha256))))
