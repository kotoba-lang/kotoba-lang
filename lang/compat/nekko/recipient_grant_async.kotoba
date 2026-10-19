(ns nekko.recipient-grant-async
  "`nekko.recipient-grant` for hosts where the only crypto is asynchronous
  SubtleCrypto: browsers, Cloudflare Workers, and node's WebCrypto. That
  namespace says outright that 'Browser cljs (async SubtleCrypto) is out of
  scope', so this is a sibling rather than a branch in it — same wire format,
  different host contract. Every fn here returns a js/Promise.

  Wire-compatible with the sync namespace, byte for byte:

  - keypairs are `{:priv <hex 32B raw> :pub <hex 32B raw>}`;
  - raw 32-byte X25519 keys cross the platform boundary through the standard
    12-byte SPKI / 16-byte PKCS8 DER prefixes (identical constants);
  - the AES key is `SHA-256(ecdh-shared || \"kotoba-rad/recipient-grant/v1\")`
    -- a one-shot digest, NOT HKDF. Getting this wrong is invisible until a
    cross-host open fails on the GCM tag, so it is stated here explicitly;
  - AES-256-GCM with a 12-byte ZERO iv and `ct = ciphertext||tag`. The zero iv
    is safe for exactly the reason the sync namespace gives: the key is derived
    from a fresh ephemeral ECDH output and is used once. Do not lift this
    pattern to a key that gets reused.

  A grant sealed by a Cloudflare Worker therefore opens on the JVM and on nbb,
  and vice versa; `recipient_grant_async_test.cljs` proves both directions.

  Deliberately does NOT require `nekko.bytes`: that namespace's :cljs branch is
  documented as '(nbb / --target node)' and reaches for `js/Buffer` and
  node:crypto, neither of which exists in a browser or (without nodejs_compat)
  in a Worker. The handful of byte helpers below are duplicated on purpose so
  this namespace has no host assumptions. Making `nekko.bytes` Buffer-free
  would remove the duplication, but `sha256`/`random-bytes` cannot be both
  synchronous and portable, so that cleanup is a separate decision.

  Style note: algorithm objects use STRING keys and CryptoKey fields are read
  with `aget`, because this compiles under Closure `:advanced` in
  cloud-itonami's :edge-api build, which has already shipped one production
  outage from renamed untyped JS properties (cloud-itonami ADR-0030)."
  (:refer-clojure :exclude [cat]))

(def ^:private x25519-spki-prefix-hex "302a300506032b656e032100")
(def ^:private x25519-pkcs8-prefix-hex "302e020100300506032b656e04220420")
(def ^:private hkdf-info "kotoba-rad/recipient-grant/v1")

(defn- subtle [] (aget (aget js/globalThis "crypto") "subtle"))

;; ── byte helpers (Uint8Array only; no Buffer, no node:crypto) ────────────────

(defn hexify [b]
  (let [u (js/Uint8Array. b)]
    (loop [i 0 acc ""]
      (if (< i (.-length u))
        (recur (inc i) (str acc (.padStart (.toString (aget u i) 16) 2 "0")))
        acc))))

(defn unhex [s]
  (let [n (quot (count s) 2)
        out (js/Uint8Array. n)]
    (dotimes [i n]
      (aset out i (js/parseInt (subs s (* 2 i) (+ (* 2 i) 2)) 16)))
    out))

(defn- cat [a b]
  (let [a (js/Uint8Array. a) b (js/Uint8Array. b)
        out (js/Uint8Array. (+ (.-length a) (.-length b)))]
    (.set out a 0)
    (.set out b (.-length a))
    out))

(defn- utf8 [s] (.encode (js/TextEncoder.) s))

(defn- tail [b n]
  (let [u (js/Uint8Array. b)] (.slice u (- (.-length u) n))))

(defn random-bytes [n]
  (.getRandomValues (aget js/globalThis "crypto") (js/Uint8Array. n)))

;; ── X25519 key import/export ────────────────────────────────────────────────

(defn- import-priv [priv-hex]
  (.importKey (subtle) "pkcs8"
              (cat (unhex x25519-pkcs8-prefix-hex) (unhex priv-hex))
              #js {"name" "X25519"} false #js ["deriveBits"]))

(defn- import-pub [pub-hex]
  (.importKey (subtle) "spki"
              (cat (unhex x25519-spki-prefix-hex) (unhex pub-hex))
              #js {"name" "X25519"} true #js []))

(defn gen-recipient-keypair
  "-> Promise<{:priv <hex 32B> :pub <hex 32B raw>}>. Same shape as the sync
  namespace's, so either side can mint a key the other consumes."
  []
  (-> (.generateKey (subtle) #js {"name" "X25519"} true #js ["deriveBits"])
      (.then (fn [kp]
               (js/Promise.all
                #js [(.exportKey (subtle) "pkcs8" (aget kp "privateKey"))
                     (.exportKey (subtle) "spki" (aget kp "publicKey"))])))
      (.then (fn [[pkcs8 spki]]
               ;; PKCS8 for X25519 ends with the raw 32-byte private key, SPKI
               ;; with the raw 32-byte public key -- the same tail-32 trick the
               ;; sync namespace uses, verified against workerd's real output.
               {:priv (hexify (tail pkcs8 32))
                :pub (hexify (tail spki 32))}))))

;; ── ecdh -> aes key ─────────────────────────────────────────────────────────

(defn- ecdh
  "-> Promise<Uint8Array 32B> raw X25519 shared secret."
  [priv-hex pub-hex]
  (-> (js/Promise.all #js [(import-priv priv-hex) (import-pub pub-hex)])
      (.then (fn [[priv pub]]
               (.deriveBits (subtle) #js {"name" "X25519" "public" pub} priv 256)))
      (.then (fn [bits] (js/Uint8Array. bits)))))

(defn- wrap-key
  "-> Promise<CryptoKey> AES-256-GCM key = SHA-256(shared || info)."
  [shared]
  (-> (.digest (subtle) "SHA-256" (cat shared (utf8 hkdf-info)))
      (.then (fn [digest]
               (.importKey (subtle) "raw" digest #js {"name" "AES-GCM"} false
                           #js ["encrypt" "decrypt"])))))

;; ── grant / open ────────────────────────────────────────────────────────────

(def ^:private zero-iv (js/Uint8Array. 12))

(defn grant
  "Wrap `epoch-key` (Uint8Array, 32 raw bytes) to `recipient-pub-hex`.
  -> Promise<{:v 1 :epoch n :recipient hex :eph hex :iv hex :ct hex}>, openable
  by `nekko.recipient-grant/open` on the JVM or nbb."
  [epoch recipient-pub-hex epoch-key]
  (-> (gen-recipient-keypair)
      (.then (fn [{eph-priv :priv eph-pub :pub}]
               (-> (ecdh eph-priv recipient-pub-hex)
                   (.then wrap-key)
                   (.then (fn [aes]
                            (.encrypt (subtle)
                                      #js {"name" "AES-GCM" "iv" zero-iv "tagLength" 128}
                                      aes (js/Uint8Array. epoch-key))))
                   (.then (fn [ct]
                            {:v 1 :epoch epoch :recipient recipient-pub-hex
                             :eph eph-pub :iv (hexify zero-iv)
                             :ct (hexify ct)})))))))

(defn open
  "Unwrap `grant` with the recipient's X25519 private key (hex).
  -> Promise<Uint8Array 32B epoch key>. The returned promise REJECTS on a wrong
  key or a tampered grant (AES-GCM tag), mirroring the sync namespace's throw."
  [grant recipient-priv-hex]
  (-> (ecdh recipient-priv-hex (:eph grant))
      (.then wrap-key)
      (.then (fn [aes]
               (.decrypt (subtle)
                         #js {"name" "AES-GCM" "iv" (unhex (:iv grant)) "tagLength" 128}
                         aes (unhex (:ct grant)))))
      (.then (fn [pt] (js/Uint8Array. pt)))))

;; ── epoch key + rotation ────────────────────────────────────────────────────

(defn new-epoch-key
  "A fresh random 32-byte symmetric epoch key (hex)."
  []
  (hexify (random-bytes 32)))

(defn grant-epoch
  "Grant `epoch-key-hex` at `epoch` to every recipient pubkey.
  -> Promise<{recipient-pub-hex grant}>."
  [epoch recipient-pubs epoch-key-hex]
  (let [k (unhex epoch-key-hex)
        pubs (vec recipient-pubs)]
    (-> (js/Promise.all (clj->js (mapv #(grant epoch % k) pubs)))
        (.then (fn [grants] (zipmap pubs (js->clj grants :keywordize-keys true)))))))

(defn rotate
  "Mint a NEW epoch key and grant it to `next-recipients` only -- dropping a
  recipient revokes their FUTURE access while they keep any old-epoch
  ciphertext they already hold (revocation = epoch rotation, never deletion).
  -> Promise<{:epoch prev+1 :key hex :grants {pub grant}}>."
  [prev-epoch next-recipients]
  (let [epoch (inc prev-epoch)
        key-hex (new-epoch-key)]
    (-> (grant-epoch epoch next-recipients key-hex)
        (.then (fn [grants] {:epoch epoch :key key-hex :grants grants})))))
