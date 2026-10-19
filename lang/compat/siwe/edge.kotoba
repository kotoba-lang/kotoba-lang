(ns siwe.edge
  "EIP-4361 EOA signature verification for browser/edge runtimes.

  Message parsing and relying-party binding come from `siwe.core`. This file
  implements only the Ethereum cryptographic boundary: EIP-191 prefixing,
  Keccak-256, low-S secp256k1 public-key recovery, EIP-55 checksumming, and
  temporal validation. It uses noble's audited curve/hash primitives, but no
  SIWE package; the protocol and policy remain this library's code.

  Contract accounts are deliberately not guessed at. EIP-4361 says an
  implementation that does not use ERC-1271 must define its method clearly;
  this verifier is EOA-only and returns `:contract-account-unsupported` when
  recovered EOA control does not match the claimed address. Hosts may add an
  explicit chain-bound ERC-1271 verifier as a separate policy path."
  (:require [kotoba.lang.text :as str]
            [siwe.core :as core]
            ["@noble/curves/secp256k1.js" :refer [secp256k1]]
            ["@noble/hashes/sha3.js" :refer [keccak_256]]))

(defn- utf8 [s] (.encode (js/TextEncoder.) s))

(defn- concat-bytes [& arrays]
  (let [size (reduce + (map #(aget % "length") arrays))
        out (js/Uint8Array. size)]
    (loop [offset 0 xs arrays]
      (if-let [a (first xs)]
        (do (.set out a offset)
            (recur (+ offset (aget a "length")) (next xs)))
        out))))

(defn- bytes->hex [bytes]
  (apply str (map #(let [h (.toString % 16)] (if (= 1 (count h)) (str "0" h) h))
                  (array-seq bytes))))

(defn- hex->bytes [hex]
  (when-not (and (string? hex) (even? (count hex)) (re-matches #"^[0-9A-Fa-f]+$" hex))
    (throw (js/Error. "invalid hex")))
  (let [out (js/Uint8Array. (/ (count hex) 2))]
    (dotimes [i (aget out "length")]
      (aset out i (js/Number.parseInt (subs hex (* 2 i) (+ 2 (* 2 i))) 16)))
    out))

(defn checksum-address
  "Canonical EIP-55 address, or nil for a non-address input."
  [address]
  (when (core/ethereum-address? address)
    (let [lower (str/lower (subs address 2))
          hash (bytes->hex (keccak_256 (utf8 lower)))]
      (str "0x"
           (apply str
                  (map-indexed
                   (fn [i ch]
                     (if (and (re-matches #"[a-f]" (str ch))
                              (>= (js/Number.parseInt (str (nth hash i)) 16) 8))
                       (str/upper (str ch))
                       ch))
                   lower))))))

(defn eip191-digest
  "Keccak-256 of the exact ERC-191 personal-sign envelope for `message`."
  [message]
  (let [body (utf8 message)
        prefix (utf8 (str "\u0019Ethereum Signed Message:\n" (aget body "length")))]
    (keccak_256 (concat-bytes prefix body))))

(defn recover-address
  "Recover the EOA address from an Ethereum 65-byte r||s||v signature.
  Accepts v=27/28 and the normalized v=0/1 form; rejects high-S signatures."
  [message signature]
  (let [raw (hex->bytes (if (str/starts-with? signature "0x") (subs signature 2) signature))]
    (when-not (= 65 (aget raw "length"))
      (throw (js/Error. "signature must be 65 bytes")))
    (let [v (aget raw 64)
          recovery (cond (= v 27) 0 (= v 28) 1 (or (= v 0) (= v 1)) v :else nil)]
      (when (nil? recovery) (throw (js/Error. "invalid recovery id")))
      (let [compact (.slice raw 0 64)
            sig (.fromBytes (.-Signature secp256k1) compact "compact")]
        (when (.hasHighS sig) (throw (js/Error. "high-S signature rejected")))
        (let [point (.recoverPublicKey (.addRecoveryBit sig recovery)
                                       (eip191-digest message))
              public-key (.toBytes point false)
              digest (keccak_256 (.slice public-key 1))
              address (str "0x" (bytes->hex (.slice digest 12)))]
          (checksum-address address))))))

(defn- parse-seconds [value]
  (when (core/rfc3339? value)
    (let [ms (js/Date.parse value)]
      (when-not (js/Number.isNaN ms) (/ ms 1000)))))

(defn- temporal-problem
  [{:keys [issued-at expiration-time not-before]}
   {:keys [now-sec clock-skew-sec max-age-sec require-expiration?]
    :or {clock-skew-sec 60 max-age-sec 300 require-expiration? true}}]
  (let [now (or now-sec (/ (js/Date.now) 1000))
        issued (parse-seconds issued-at)
        expires (some-> expiration-time parse-seconds)
        starts (some-> not-before parse-seconds)]
    (cond
      (nil? issued) :invalid-issued-at
      (> issued (+ now clock-skew-sec)) :issued-in-future
      (> (- now issued) (+ max-age-sec clock-skew-sec)) :message-too-old
      (and require-expiration? (nil? expiration-time)) :expiration-required
      (and expiration-time (nil? expires)) :invalid-expiration-time
      (and expires (<= expires issued)) :invalid-expiration-window
      (and expires (>= now (+ expires clock-skew-sec))) :expired
      (and not-before (nil? starts)) :invalid-not-before
      (and starts (< (+ now clock-skew-sec) starts)) :not-yet-valid
      :else nil)))

(defn verify
  "Verify an EIP-4361 EOA message and return a non-throwing result map.

  `expected` may bind `:scheme`, `:domain`, `:uri`, `:nonce`, `:chain-ids`,
  `:now-sec`, `:clock-skew-sec`, `:max-age-sec`, and
  `:require-expiration?`. Successful sessions are bound to the address via a
  `did:pkh:eip155` principal."
  [message signature expected]
  (try
    (let [parsed (core/parse-message message)]
      (if-not (:ok? parsed)
        {:valid? false :problem (:problem parsed)}
        (let [fields (:message parsed)
              expected-error (core/expected-problem fields expected)
              time-error (temporal-problem fields expected)]
          (cond
            expected-error {:valid? false :problem expected-error}
            time-error {:valid? false :problem time-error}
            :else
            (let [recovered (recover-address message signature)
                  claimed (checksum-address (:address fields))]
              (if-not (= recovered claimed)
                {:valid? false :problem :signature-address-mismatch}
                {:valid? true
                 :address recovered
                 :chain-id (:chain-id fields)
                 :principal (core/principal-did (assoc fields :address recovered))
                 :message fields}))))))
    (catch :default error
      {:valid? false :problem :invalid-signature
       :detail (or (aget error "message") "signature verification failed")})))
