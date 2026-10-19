(ns noise.provider.noble
  "ClojureScript primitive ports — the first-class runtime for this library
  (browser / nbb / Worker), backed by the audited `@noble/*` packages: X25519 from
  `@noble/curves` and ChaCha20-Poly1305 from `@noble/ciphers`. Pure JS, no native
  bindings, so the same provider works in a Cloudflare Worker and under nbb.

  Same choice `kotoba.signal.x25519`'s CLJS sibling made, for the same reason:
  there is no JCA-equivalent on this platform and hand-rolled curve arithmetic in
  ClojureScript would be both slower and less safe than an audited library."
  (:require ["@noble/ciphers/chacha.js" :refer [chacha20poly1305]]
            ["@noble/curves/ed25519.js" :refer [x25519]]
            ["@noble/hashes/blake2s.js" :refer [blake2s]]
            ["@noble/hashes/sha256.js" :refer [sha256]]))

(defn ->u8
  "byte-vector (ints 0..255) -> Uint8Array"
  [bs]
  (js/Uint8Array.from (clj->js (vec bs))))

(defn ->vec
  "Uint8Array -> byte-vector (ints 0..255)"
  [u8]
  (vec (js/Array.from u8)))

(defn dh-generate []
  (let [priv (.randomPrivateKey (.-utils x25519))]
    {:priv (->vec priv)
     :pub (->vec (.getPublicKey x25519 priv))}))

(defn dh [priv pub]
  (->vec (.getSharedSecret x25519 (->u8 priv) (->u8 pub))))

(defn aead-encrypt [k nonce ad plaintext]
  (->vec (.encrypt (chacha20poly1305 (->u8 k) (->u8 nonce) (->u8 ad))
                   (->u8 plaintext))))

(defn aead-decrypt [k nonce ad ciphertext]
  (try
    (->vec (.decrypt (chacha20poly1305 (->u8 k) (->u8 nonce) (->u8 ad))
                     (->u8 ciphertext)))
    (catch :default _ nil)))

(defn hash-blake2s [bs] (->vec (blake2s (->u8 bs))))
(defn hash-sha256 [bs] (->vec (sha256 (->u8 bs))))

(defn ports
  "The injected ports, **including `:hash`**.

   Overriding the hash matters for more than tidiness. `noise.blake2s` is correct
   (it is what the RFC and OpenSSL cross-checks run against) but it is Clojure
   arithmetic, and under nbb that arithmetic is *interpreted* by SCI: measured on
   this workstation, one 96-byte BLAKE2s took ~16 ms, which made a single IK
   handshake take ~1.5 s and starved the agent's socket loop badly enough that
   relay handshakes visibly raced. With `@noble/hashes` the same handshake is
   milliseconds. So: the pure implementation stays the portable default and the
   reference for tests, and any runtime that has a real hash injects it here.

   `opts`: `:hash :blake2s` (default) | `:sha256` — must match the suite's hash."
  ([] (ports {}))
  ([{:keys [hash] :or {hash :blake2s}}]
   {:dh-generate dh-generate
    :dh dh
    :aead-encrypt aead-encrypt
    :aead-decrypt aead-decrypt
    :hash (case hash :sha256 hash-sha256 hash-blake2s)}))
