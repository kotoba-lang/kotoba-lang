(ns noise.suite
  "The cipher-suite descriptor: which primitives a Noise handshake runs on, and
  which of them this library implements versus injects.

  **Implemented here** (pure `.cljc`): BLAKE2s / SHA-256-shaped hashing via
  `noise.blake2s`, HMAC + Noise HKDF via `noise.kdf`, and every piece of
  protocol logic.

  **Injected** (host-supplied ports): `:dh-generate`, `:dh`, `:aead-encrypt`,
  `:aead-decrypt`. This is deliberate and is the security argument for the whole
  library:

  - X25519 scalar multiplication and ChaCha20-Poly1305 are the two places where a
    hand-rolled portable implementation would be *actively worse* than the
    platform's — constant-time field arithmetic and constant-time tag comparison
    are exactly what a naive `.cljc` port loses. So we take them from the JCA
    (`noise.provider.jvm`, JDK 11+ has both) or from audited `@noble/*`
    (`noise.provider.noble`, first-class runtime per this workspace's
    ClojureScript-before-JVM ordering).
  - It also means the same protocol core runs under a WASM host that grants a
    crypto capability, with no reader conditionals in the protocol layer.

  A suite map:

      {:name         \"25519_ChaChaPoly_BLAKE2s\"   ; the tail of the protocol name
       :dhlen 32 :hashlen 32 :blocklen 64
       :hash         (fn [bytes] -> bytes)
       :nonce-bytes  (fn [n] -> 12 bytes)          ; AEAD nonce encoding
       :dh-generate  (fn [] -> {:priv bytes :pub bytes})
       :dh           (fn [priv pub] -> bytes)
       :aead-encrypt (fn [k nonce-bytes ad plaintext] -> ciphertext)
       :aead-decrypt (fn [k nonce-bytes ad ciphertext] -> plaintext, throws/nil on
                                                          authentication failure)}

  All byte values are vectors of ints 0..255 (see `noise.blake2s`)."
  (:require [kotoba.bytes.sha256 :as sha256]
            [noise.blake2s :as blake2s]))

(defn chachapoly-nonce
  "ChaCha20-Poly1305 nonce for Noise: 4 zero bytes then the 64-bit counter
   little-endian (Noise rev 34 §12.3). AES-GCM suites would use 4 zero bytes then
   big-endian — the difference is why this is a suite field and not a constant."
  [n]
  (loop [i 0 acc [0 0 0 0] n n]
    (if (= i 8)
      acc
      (recur (inc i) (conj acc (bit-and n 0xff)) (quot n 256)))))

(def hashes
  "The two hash choices, both implemented purely here: BLAKE2s (WireGuard's, via
   `noise.blake2s`) and SHA-256 (via `kotoba.bytes.sha256`). Having both is not
   just completeness — the SHA-256 suites let the protocol core be
   known-answer-tested against the official vectors *independently* of our own
   BLAKE2s, so a bug in one cannot hide a bug in the other."
  {:blake2s {:name "25519_ChaChaPoly_BLAKE2s" :hash blake2s/hash :hashlen 32 :blocklen 64}
   :sha256 {:name "25519_ChaChaPoly_SHA256" :hash sha256/sha256-bytes :hashlen 32 :blocklen 64}})

(def basepoint
  "The X25519 base point u=9 (RFC 7748 §4.1). X25519(priv, basepoint) is the
   public key, which is how `dh-public` is derived when a provider does not
   offer a dedicated one (the JCA cannot derive a public key from a raw scalar,
   but it will happily agree with the base point)."
  (into [9] (repeat 31 0)))

(def base
  "Suite fields that do not depend on the injected primitives (BLAKE2s suite)."
  (merge {:dhlen 32 :nonce-bytes chachapoly-nonce} (:blake2s hashes)))

(def required-ports [:dh-generate :dh :aead-encrypt :aead-decrypt])

(defn suite
  "Build a suite from the injected `ports`. `opts`:

     :hash  :blake2s (default, WireGuard's suite) | :sha256

   Fails loudly on a missing port rather than at the first handshake message — a
   half-wired suite otherwise surfaces as an authentication failure on the
   remote side, which is the worst possible place to debug it."
  ([ports] (suite ports {}))
  ([ports {:keys [hash] :or {hash :blake2s}}]
   (let [h (or (get hashes hash)
               (throw (ex-info "unknown noise hash" {:hash hash :known (vec (keys hashes))})))
         s (merge base h ports)
         missing (remove #(ifn? (get s %)) required-ports)]
     (when (seq missing)
       (throw (ex-info "noise suite is missing injected primitive ports"
                       {:missing (vec missing)
                        :hint "see noise.provider.jvm / noise.provider.noble"})))
     (cond-> s
       (not (ifn? (:dh-public s)))
       (assoc :dh-public (fn [priv] ((:dh s) priv basepoint)))))))

(defn keypair-from-private
  "Recover the full keypair from a raw 32-byte X25519 scalar. Needed to run the
   official known-answer vectors, which fix both sides' ephemerals."
  [suite priv]
  {:priv (vec priv) :pub (vec ((:dh-public suite) (vec priv)))})

(defn protocol-name
  "Full Noise protocol name, e.g. \"Noise_IK_25519_ChaChaPoly_BLAKE2s\"."
  [suite pattern-name]
  (str "Noise_" (name pattern-name) "_" (:name suite)))
