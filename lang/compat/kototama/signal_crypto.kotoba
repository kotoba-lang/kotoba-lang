(ns kototama.signal-crypto
  "Signal Protocol cryptographic primitives: HKDF, epoch-key derivation, AEAD.

  Provides:
  - HKDF-Extract/Expand (RFC 5869)
  - Epoch-key derivation (per-message ephemeral nonce)
  - AEAD encryption/decryption wrapper (ChaCha20-Poly1305)"
    (:require [kotoba.security.hkdf :as hkdf]
                    [kotoba.security.aead :as aead]
                    [kotoba.security.ed25519 :as ed25519]))

(def AEAD-KEY-LEN 32)
(def AEAD-NONCE-LEN 12)
(def AEAD-TAG-LEN 16)

;; Epoch-key derivation: derive a unique (key, nonce) pair per message
;;
;; Each message in the Signal session gets a unique symmetric key to prevent
;; replay and limit key reuse. This is different from the chain-key in the
;; symmetric ratchet; it's the actual key material for AEAD encryption.
;;
;; Input:  chain-key (32 bytes, from symmetric-ratchet)
;;         message-count (u32, deterministic for replay resistance)
;; Output: {:message-key <32 bytes>, :nonce <12 bytes>, :next-chain-key <32>}
;;
;; The next-chain-key is already computed in signal-ratchet/symmetric-ratchet,
;; so this function mainly computes the nonce and formats for AEAD.
(defn derive-epoch-key
  [chain-key message-count]
  {:pre [(= (count chain-key) 32)
         (number? message-count)]}
  (let [;; Counter as 8-byte big-endian (u64 for longer sequences)
        counter-bytes (unchecked-long message-count)
        counter-be (byte-array 8)
        _ (doseq [i (range 8)]
            (aset-byte counter-be i
                       (unchecked-byte
                        (unsigned-bit-shift-right counter-bytes
                                                 (* (- 7 i) 8)))))

        ;; Nonce: HKDF-Expand(chain-key, "NonceKey" || counter, 12)
        nonce (hkdf/hkdf-expand chain-key
                                (byte-array (concat (byte-array (map byte "NonceKey"))
                                                    counter-be))
                                AEAD-NONCE-LEN)

        ;; Message key: HKDF-Expand(chain-key, "MessageKey" || counter, 32)
        message-key (hkdf/hkdf-expand chain-key
                                      (byte-array (concat (byte-array (map byte "MessageKey"))
                                                          counter-be))
                                      AEAD-KEY-LEN)

        ;; Chain key: HKDF-Expand(chain-key, "ChainKey" || counter, 32)
        next-chain-key (hkdf/hkdf-expand chain-key
                                         (byte-array (concat (byte-array (map byte "ChainKey"))
                                                             counter-be))
                                         32)]
    {:message-key message-key
     :nonce nonce
     :next-chain-key next-chain-key}))

;; AEAD encrypt: ChaCha20-Poly1305
;;
;; Input:  plaintext (bytes)
;;         key (32 bytes)
;;         nonce (12 bytes)
;;         aad (additional authenticated data, e.g., peer-id || timestamp)
;; Output: ciphertext || tag (nonce is sent in plaintext, not encrypted)
(defn encrypt-aead
  [plaintext key nonce aad]
  {:pre [(bytes? plaintext)
         (= (count key) AEAD-KEY-LEN)
         (= (count nonce) AEAD-NONCE-LEN)]}
  (let [aad-bytes (if (bytes? aad) aad (byte-array 0))
        {:keys [ciphertext tag]} (aead/chacha20-poly1305-encrypt
                                   plaintext key nonce aad-bytes)]
    (byte-array (concat ciphertext tag))))

;; AEAD decrypt: ChaCha20-Poly1305
;;
;; Input:  ciphertext || tag (ciphertext-tag as single byte array, last 16 bytes are tag)
;;         key (32 bytes)
;;         nonce (12 bytes)
;;         aad (additional authenticated data)
;; Output: plaintext (or nil if authentication fails)
(defn decrypt-aead
  [ciphertext-tag key nonce aad]
  {:pre [(bytes? ciphertext-tag)
         (= (count key) AEAD-KEY-LEN)
         (= (count nonce) AEAD-NONCE-LEN)]}
  (if (< (count ciphertext-tag) AEAD-TAG-LEN)
    nil  ;; ciphertext too short
    (let [ct-len (- (count ciphertext-tag) AEAD-TAG-LEN)
          ciphertext (byte-array (take ct-len ciphertext-tag))
          tag (byte-array (drop ct-len ciphertext-tag))
          aad-bytes (if (bytes? aad) aad (byte-array 0))]
      (try
        (aead/chacha20-poly1305-decrypt ciphertext tag key nonce aad-bytes)
        (catch Exception _ nil)))))  ;; Authentication failure

;; Request envelope: sign-request serialization with Signal encryption
;;
;; The canonical request format for signing is:
;;   request-bytes = serialize({:timestamp <u64> :nonce <32-bytes> :payload <bytes>})
;;
;; This is signed with the requester's long-term key (via kagi),
;; then encrypted under the session's current message key.
(defrecord SignalEnvelope
  [message-number       ;; u32, for nonce derivation and replay resistance
   sender-id            ;; CID of sender
   timestamp            ;; u64 milliseconds since epoch
   nonce                ;; 32 bytes, random for replay resistance
   encrypted-payload    ;; bytes: payload || signature (encrypted)
   signature            ;; 64 bytes, ed25519(timestamp || nonce || payload-hash)
   ])

;; Helper: pack u64 big-endian
(defn- u64-be [value]
  (byte-array 8)
  (let [arr (byte-array 8)]
    (doseq [i (range 8)]
      (aset-byte arr i
                 (unchecked-byte
                  (unsigned-bit-shift-right (unchecked-long value)
                                           (* (- 7 i) 8)))))
    arr))

;; Helper: pack u32 big-endian
(defn- u32-be [value]
  (let [arr (byte-array 4)]
    (doseq [i (range 4)]
      (aset-byte arr i
                 (unchecked-byte
                  (unsigned-bit-shift-right (unchecked-int value)
                                           (* (- 3 i) 8)))))
    arr))

;; Format request for signing: timestamp || nonce || payload-hash
(defn format-request-for-signing
  [timestamp nonce payload-hash]
  {:pre [(number? timestamp)
         (= (count nonce) 32)
         (= (count payload-hash) 32)]}
  (byte-array (concat (u64-be timestamp)
                      nonce
                      payload-hash)))

;; Verify request signature against epoch key (from Double Ratchet)
;;
;; Input:  request-bytes (canonical format: timestamp || nonce || payload-hash)
;;         signature (64 bytes, ed25519)
;;         epoch-key (32 bytes, derived from message key via HKDF)
;; Output: true if valid, false otherwise
(defn verify-request-signature
  [request-bytes signature epoch-key]
  {:pre [(bytes? request-bytes)
         (= (count signature) 64)
         (= (count epoch-key) 32)]}
  (try
    ;; epoch-key is the ed25519 public key for this message
    ;; signature is the ed25519 signature over request-bytes
    (ed25519/verify request-bytes signature epoch-key)
    (catch Exception _ false)))

;; Test: epoch-key derivation produces unique keys per message
(comment
  (let [chain-key (byte-array 32)
        k0 (derive-epoch-key chain-key 0)
        k1 (derive-epoch-key chain-key 1)]
    ;; Each message should have different key and nonce
    (not= (:message-key k0) (:message-key k1))
    (not= (:nonce k0) (:nonce k1))
    ;; Nonces should be deterministic (replay resistance)
    (= (:nonce (derive-epoch-key chain-key 0))
       (:nonce (derive-epoch-key chain-key 0)))))
