(ns noise.provider.node
  "ClojureScript primitive ports backed by **Node's own crypto** (OpenSSL):
  X25519 via `crypto.diffieHellman`, ChaCha20-Poly1305 via `createCipheriv`,
  BLAKE2s/SHA-256 via `createHash`. Synchronous, no npm dependency.

  Why this exists next to `noise.provider.noble`, measured rather than assumed
  (this workstation, Node 26, Apple silicon):

  | | X25519 DH | keygen | hash (96 B) |
  |---|---|---|---|
  | `@noble/curves` 1.8 | **27 ms** | 25 ms | 0.3 ms |
  | Node crypto (here) | ~0.05 ms | ~0.05 ms | 0.3 ms |

  27 ms per DH is not a rounding error: a Noise IK handshake does two of them, and
  with a hundred peers the agent's event loop is blocked for seconds — which is
  exactly how the relay handshake race in this stack's E2E first showed up. The
  27 ms is `@noble`'s own cost, confirmed by timing it directly in `node -e`
  outside ClojureScript, so it is not an artefact of nbb's interpreter.

  Use this provider in a **Node** process (the resident agent, a relay). Use
  `noise.provider.noble` in a **browser or Worker**, where this module does not
  exist and WebCrypto's X25519 is Promise-based (which would make the whole
  protocol core async for one primitive).

  The DER wrapping below is the same shape as `noise.provider.jvm`'s, and for the
  same reason: OpenSSL, like the JCA, will not accept a raw 32-byte scalar."
  (:require ["node:crypto" :as crypto]))

(def ^:private pkcs8-prefix
  [0x30 0x2e 0x02 0x01 0x00 0x30 0x05 0x06 0x03 0x2b 0x65 0x6e 0x04 0x22 0x04 0x20])
(def ^:private spki-prefix
  [0x30 0x2a 0x30 0x05 0x06 0x03 0x2b 0x65 0x6e 0x03 0x21 0x00])

;; `(vec some-uint8array)` looks equivalent to this and is ~4x slower: ClojureScript's
;; `vec` has a fast path only for a real JS Array (`Array.isArray`), and a
;; Buffer/Uint8Array falls through to the generic seq path. Converting to a JS
;; Array first hits the fast path. At 512-byte datagrams this was most of the
;; per-packet cost — more than the cipher.
(defn- ->buf [bs] (.from js/Buffer (js/Uint8Array.from (clj->js (vec bs)))))
(defn- ->vec [buf] (vec (js/Array.from buf)))

;; DER-parsing a key costs more than the scalar multiplication itself here
;; (measured: 3.2 ms per DH with parsing, ~0.4 ms without). The same two keys — our
;; static/ephemeral and the peer's — are used for every DH of a session, so they
;; are cached. The cache is bounded and dropped wholesale when it grows: a peer
;; churn of thousands of keys should not leak, and an LRU is not worth the code
;; for a lookup this cheap.
(def ^:private key-cache (atom {}))
(def ^:private key-cache-limit 512)

(defn- cached [k f]
  (if-let [v (get @key-cache k)]
    v
    (let [v (f)]
      (swap! key-cache (fn [m] (assoc (if (> (count m) key-cache-limit) {} m) k v)))
      v)))

(defn- private-key [priv]
  (cached [:priv (vec priv)]
          #(.createPrivateKey crypto #js {:key (->buf (into (vec pkcs8-prefix) priv))
                                          :format "der" :type "pkcs8"})))

(defn- public-key [pub]
  (cached [:pub (vec pub)]
          #(.createPublicKey crypto #js {:key (->buf (into (vec spki-prefix) pub))
                                         :format "der" :type "spki"})))

(defn dh-generate []
  (let [kp (.generateKeyPairSync crypto "x25519")]
    {:priv (vec (drop (count pkcs8-prefix)
                      (->vec (.export (.-privateKey kp) #js {:type "pkcs8" :format "der"}))))
     :pub (vec (drop (count spki-prefix)
                     (->vec (.export (.-publicKey kp) #js {:type "spki" :format "der"}))))}))

(defn dh [priv pub]
  (->vec (.diffieHellman crypto #js {:privateKey (private-key priv)
                                     :publicKey (public-key pub)})))

(defn aead-encrypt [k nonce ad plaintext]
  (let [c (.createCipheriv crypto "chacha20-poly1305" (->buf k) (->buf nonce)
                           #js {:authTagLength 16})]
    (when (seq ad) (.setAAD c (->buf ad) #js {:plaintextLength (count plaintext)}))
    (let [body (.concat js/Buffer #js [(.update c (->buf plaintext)) (.final c)])]
      (into (->vec body) (->vec (.getAuthTag c))))))

(defn aead-decrypt
  "-> plaintext, or nil on authentication failure."
  [k nonce ad ciphertext]
  (try
    (let [ct (vec ciphertext)
          n (count ct)
          body (subvec ct 0 (- n 16))
          tag (subvec ct (- n 16))
          c (.createDecipheriv crypto "chacha20-poly1305" (->buf k) (->buf nonce)
                               #js {:authTagLength 16})]
      (.setAuthTag c (->buf tag))
      (when (seq ad) (.setAAD c (->buf ad) #js {:plaintextLength (count body)}))
      (->vec (.concat js/Buffer #js [(.update c (->buf body)) (.final c)])))
    (catch :default _ nil)))

(defn- digest [algo bs]
  (->vec (.digest (.update (.createHash crypto algo) (->buf bs)))))

(defn hash-blake2s [bs] (digest "blake2s256" bs))
(defn hash-sha256 [bs] (digest "sha256" bs))

(defn ports
  "`opts` mirrors `noise.suite/suite`'s — pass the same `:hash` to both."
  ([] (ports {}))
  ([{:keys [hash] :or {hash :blake2s}}]
   {:dh-generate dh-generate
    :dh dh
    :aead-encrypt aead-encrypt
    :aead-decrypt aead-decrypt
    :hash (case hash :sha256 hash-sha256 hash-blake2s)}))
