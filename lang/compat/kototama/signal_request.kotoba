(ns kototama.signal-request
  "kototama-request signing and verification with Signal protocol.

  Request structure (for signing):
  {
    :requester-id <peer-id>       ;; CID of requester identity
    :timestamp <u64>              ;; milliseconds since epoch
    :nonce <32-byte-array>        ;; cryptographically random
    :payload <bytes>              ;; the actual request (JSON/msgpack/EDN)
    :signature <64-byte-array>    ;; ed25519(timestamp || nonce || payload-hash)
  }

  Signing flow:
  1. Client has cached kagi Keychain key (ed25519)
  2. Client formats (timestamp || nonce || SHA256(payload))
  3. Client signs with ed25519 key
  4. Client sends request with signature

  Verification flow:
  1. Service receives request + signature
  2. Service derives epoch-key from Signal session
  3. Service verifies ed25519 signature with epoch-key as public key
  4. Service validates timestamp (not > now + clock-skew-tolerance)
  5. Service checks nonce against bloom filter (replay prevention)"
    (:require [kotoba.security.ed25519 :as ed25519]
                    [kotoba.security.sha256 :as sha256]
                    [kotoba.security.hkdf :as hkdf]))

;; Request structure: canonical format for verification
(defrecord SignalRequest
  [requester-id        ;; CID (string representation)
   timestamp           ;; u64 milliseconds since Unix epoch
   nonce               ;; 32 random bytes
   payload             ;; bytes (any format: JSON, msgpack, EDN)
   signature           ;; 64 bytes ed25519 signature
   ])

;; Clock skew tolerance (5 minutes)
(def ^:const CLOCK-SKEW-TOLERANCE-MS 300000)

;; Helper: serialize request for signature
;;
;; Canonical format: timestamp (8 bytes BE) || nonce (32 bytes) || payload-hash (32 bytes SHA256)
;; This ensures the signature commits to the exact request payload without including
;; the payload in plaintext within the signature (compact, efficient).
(defn- serialize-request-for-signing
  [timestamp nonce payload-hash]
  {:pre [(number? timestamp)
         (= (count nonce) 32)
         (= (count payload-hash) 32)]}
  (let [arr (byte-array (+ 8 32 32))]
    ;; timestamp as u64 big-endian
    (doseq [i (range 8)]
      (aset-byte arr i
                 (unchecked-byte
                  (unsigned-bit-shift-right (unchecked-long timestamp)
                                           (* (- 7 i) 8)))))
    ;; nonce (32 bytes)
    (System/arraycopy nonce 0 arr 8 32)
    ;; payload-hash (32 bytes SHA256)
    (System/arraycopy payload-hash 0 arr 40 32)
    arr))

;; Helper: SHA256 hash of payload
(defn- hash-payload [payload]
  (sha256/sha256 payload))

;; Sign a request with the requester's long-term key
;;
;; Input:  request-id (string, CID of requester)
;;         timestamp (u64 ms since epoch)
;;         nonce (32 random bytes)
;;         payload (bytes)
;;         signing-key (32 bytes, ed25519 private key from kagi)
;; Output: SignalRequest record with signature
(defn sign-request
  [request-id timestamp nonce payload signing-key]
  {:pre [(string? request-id)
         (number? timestamp)
         (= (count nonce) 32)
         (bytes? payload)
         (= (count signing-key) 32)]}
  (let [payload-hash (hash-payload payload)
        message-to-sign (serialize-request-for-signing timestamp nonce payload-hash)
        signature (ed25519/sign message-to-sign signing-key)]
    (->SignalRequest
      request-id
      timestamp
      nonce
      payload
      signature)))

;; Verify a request signature against epoch-key (from Signal session)
;;
;; Input:  signal-request (SignalRequest record)
;;         epoch-key (32 bytes ed25519 public key, derived from message-key)
;;         now-ms (current time in milliseconds)
;; Output: {:valid? true|false, :requester-id <id>, :reason (if not valid)}
;;
;; Verification checks:
;; 1. Signature verification (ed25519)
;; 2. Timestamp freshness (not in future, not too old)
;; 3. Nonce uniqueness (caller's responsibility: bloom filter check)
(defn verify-request
  [signal-request epoch-key now-ms]
  {:pre [(some? signal-request)
         (= (count epoch-key) 32)
         (number? now-ms)]}
  (let [request-id (:requester-id signal-request)
        timestamp (:timestamp signal-request)
        nonce (:nonce signal-request)
        payload (:payload signal-request)
        signature (:signature signal-request)

        ;; Step 1: Check timestamp freshness
        time-delta (- now-ms timestamp)
        too-old? (> time-delta (* 15 CLOCK-SKEW-TOLERANCE-MS))  ;; 75 min TTL
        too-future? (< time-delta (- CLOCK-SKEW-TOLERANCE-MS))  ;; future by > 5 min

        ;; Step 2: Verify ed25519 signature
        payload-hash (hash-payload payload)
        message-to-verify (serialize-request-for-signing timestamp nonce payload-hash)
        signature-valid? (try
                           (ed25519/verify message-to-verify signature epoch-key)
                           (catch Exception _ false))]

    (cond
      too-future?
      {:valid? false
       :requester-id request-id
       :reason :timestamp-too-far-future}

      too-old?
      {:valid? false
       :requester-id request-id
       :reason :timestamp-too-old}

      (not signature-valid?)
      {:valid? false
       :requester-id request-id
       :reason :signature-verification-failed}

      :else
      {:valid? true
       :requester-id request-id
       :timestamp timestamp
       :nonce nonce
       :payload-hash (vec payload-hash)})))

;; Derive epoch-key from message-key (for signature verification)
;;
;; In Wave 4b, the epoch-key used for request signatures is derived from
;; the Signal message-key via HKDF. This ties request signatures to the
;; cryptographic channel, preventing signature reuse across channels.
;;
;; Input:  message-key (32 bytes, from symmetric-ratchet)
;; Output: epoch-key (32 bytes, ed25519 public key)
(defn derive-epoch-key-for-signing
  [message-key]
  {:pre [(= (count message-key) 32)]}
  ;; HKDF-Expand(message-key, "SignatureEpoch", 32)
  (hkdf/hkdf-expand message-key
                    (byte-array (map byte "SignatureEpoch"))
                    32))

;; Batch verification: verify multiple requests from the same peer
;;
;; Optimization: verify requests in a single superstep to reduce
;; HKDF/derivation overhead when multiple messages arrive.
;;
;; Input:  requests (vec of SignalRequest)
;;         epoch-key (32 bytes)
;;         now-ms (current time)
;; Output: vec of verification results
(defn verify-requests-batch
  [requests epoch-key now-ms]
  {:pre [(vector? requests)
         (= (count epoch-key) 32)]}
  (mapv (fn [req]
          (verify-request req epoch-key now-ms))
        requests))

;; Test vector: deterministic signing/verification
(comment
  ;; Example (would use actual kagi key in production)
  (let [test-key (ed25519/generate-keypair)
        private-key (:private test-key)
        public-key (:public test-key)

        ;; Sign a test request
        request-id "did:key:z6MkhaXgBZDvotKL5sKFiTKtzjjDpvHwikMxWDRDfbFvmqYq"
        timestamp 1721559600000  ;; some timestamp
        nonce (byte-array 32)    ;; placeholder
        payload (byte-array (map byte "Hello, Signal!"))

        signed (sign-request request-id timestamp nonce payload private-key)

        ;; Verify signature
        epoch-key (derive-epoch-key-for-signing (:message-key {}))  ;; placeholder
        result (verify-request signed public-key (+ timestamp 1000))]
    (:valid? result)))
