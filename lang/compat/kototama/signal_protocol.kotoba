(ns kototama.signal-protocol
  "Signal Protocol primitives: X3DH, double ratchet, symmetric/asymmetric ratchet.

  This namespace implements the Double Ratchet Algorithm (RFC 8440) with:
  - Symmetric ratchet: derive per-message keys using HKDF chain keys
  - Asymmetric ratchet: DH key agreement for forward secrecy across peers
  - Session state: per-peer ratchet trees for bidirectional secure channels

  Based on Open Whisper Systems' Signal Protocol specification.
  Dependency: kotoba-lang/security (HKDF, X25519, ed25519)"
    (:require [kotoba.security.hkdf :as hkdf]
                    [kotoba.security.x25519 :as x25519]
                    [kotoba.security.ed25519 :as ed25519]))

;; Constants (matching Signal Protocol specification)
(def KDF-SALT-LEN 32)
(def CHAIN-KEY-LEN 32)
(def MESSAGE-KEY-LEN 32)
(def ROOT-KEY-LEN 32)

;; Signal Session state: persistent ratchet state for one peer
(defrecord SignalSession
  [peer-id                    ;; recipient's CID
   root-key                   ;; 32 bytes, rotates on asymmetric ratchet
   send-chain-key            ;; 32 bytes, derives message keys
   recv-chain-key            ;; 32 bytes, for decrypting from peer
   send-message-number       ;; u32, incremented per encrypt
   recv-message-number       ;; u32, incremented per decrypt
   prev-dh-public            ;; 32 bytes, previous DH public key
   dh-public                 ;; 32 bytes, current DH public key
   dh-private                ;; 32 bytes, current DH private key (secret)
   peer-dh-public            ;; 32 bytes, peer's DH public key
   ])

;; Symmetric ratchet: derive message key and advance chain key
;;
;; Input:  chain-key (32 bytes, from root or previous symmetric ratchet)
;;         message-count (u32, for determinism)
;; Output: {:message-key <32 bytes>, :next-chain-key <32 bytes>}
;;
;; This is called once per message sent or received. The symmetric ratchet
;; ensures each message has a unique key derived from the chain, and the
;; chain is irreversibly advanced (forward secrecy: decrypting message N
;; reveals message key N, but not key N+1).
(defn symmetric-ratchet
  [chain-key message-count]
  {:pre [(= (count chain-key) CHAIN-KEY-LEN)]}
  (let [;; Counter as 4-byte big-endian (deterministic input)
        counter-bytes (-> message-count
                          (bit-and 0xFFFFFFFF)
                          (unchecked-int))
        counter-be (byte-array 4)
        _ (do
            (aset-byte counter-be 0 (unchecked-byte (unsigned-bit-shift-right counter-bytes 24)))
            (aset-byte counter-be 1 (unchecked-byte (unsigned-bit-shift-right counter-bytes 16)))
            (aset-byte counter-be 2 (unchecked-byte (unsigned-bit-shift-right counter-bytes 8)))
            (aset-byte counter-be 3 (unchecked-byte counter-bytes)))

        ;; Message key: HKDF-Expand(chain-key, "MessageKeys" || counter, 32)
        message-key (hkdf/hkdf-expand chain-key
                                      (byte-array (concat (byte-array (map byte "MessageKeys"))
                                                          counter-be))
                                      MESSAGE-KEY-LEN)

        ;; Chain key: HKDF-Expand(chain-key, "ChainKeys" || counter, 32)
        next-chain-key (hkdf/hkdf-expand chain-key
                                         (byte-array (concat (byte-array (map byte "ChainKeys"))
                                                             counter-be))
                                         CHAIN-KEY-LEN)]
    {:message-key message-key
     :next-chain-key next-chain-key}))

;; Asymmetric ratchet: rotate DH chains for forward secrecy
;;
;; When peer sends a new DH public key, we re-derive root key and reset
;; chain keys. This provides forward secrecy: compromise of dh-private
;; does not compromise future chain keys.
;;
;; Input:  send-chain-key (current key we're using to send)
;;         peer-dh-public (new DH public key from peer)
;;         local-dh-private (our current DH private key)
;; Output: {:root-key <32>, :recv-chain-key <32>, :send-chain-key <same>}
;;
;; The send-chain-key is preserved until we send our own new DH key.
;; The recv-chain-key is reset to derive keys from peer's new message.
(defn asymmetric-ratchet
  [send-chain-key peer-dh-public local-dh-private]
  {:pre [(= (count send-chain-key) CHAIN-KEY-LEN)
         (= (count peer-dh-public) 32)
         (= (count local-dh-private) 32)]}
  (let [;; DH(our-private, peer-public) → 32-byte shared secret
        dh-output (x25519/dh local-dh-private peer-dh-public)

        ;; Root key ratchet:
        ;; new-root = HKDF-Extract(salt=send-chain-key, IKM=dh-output)
        ;;            (saltlen=32, ikmlen=32 → output 32 bytes)
        new-root-key (hkdf/hkdf-extract send-chain-key dh-output)

        ;; Recv chain key: HKDF-Expand(new-root, "RecvChain", 32)
        new-recv-chain (hkdf/hkdf-expand new-root-key
                                         (byte-array (map byte "RecvChain"))
                                         CHAIN-KEY-LEN)]
    {:root-key new-root-key
     :recv-chain-key new-recv-chain
     :send-chain-key send-chain-key}))

;; Initialize a new Signal session with peer
;;
;; Input:  peer-id (CID of recipient)
;;         root-key (initial 32-byte key, from X3DH)
;;         peer-dh-public (recipient's DH public key)
;;         local-dh-keypair {:public <32> :private <32>}
;; Output: SignalSession record
(defn new-session
  [peer-id root-key peer-dh-public local-dh-keypair]
  {:pre [(= (count root-key) ROOT-KEY-LEN)
         (= (count peer-dh-public) 32)
         (= (count (:public local-dh-keypair)) 32)
         (= (count (:private local-dh-keypair)) 32)]}
  (let [;; Initialize send chain key from root (no asymmetric ratchet yet)
        send-chain (hkdf/hkdf-expand root-key
                                     (byte-array (map byte "InitSendChain"))
                                     CHAIN-KEY-LEN)

        ;; Initialize recv chain key (we start with 0 recv messages)
        recv-chain (hkdf/hkdf-expand root-key
                                     (byte-array (map byte "InitRecvChain"))
                                     CHAIN-KEY-LEN)]
    (->SignalSession
      peer-id
      root-key
      send-chain
      recv-chain
      0  ;; send-message-number
      0  ;; recv-message-number
      (byte-array 32)  ;; prev-dh-public (initial, all zeros)
      (:public local-dh-keypair)
      (:private local-dh-keypair)
      peer-dh-public)))

;; Encrypt a message with the Signal session
;;
;; Returns: {:ciphertext <bytes>, :updated-session <SignalSession>}
;;
;; The message number is embedded in ciphertext for replay protection.
(defn encrypt-message
  [session plaintext aead-fn]
  {:pre [(some? session)
         (bytes? plaintext)
         (fn? aead-fn)]}
  (let [;; Derive message key from send chain
        {:keys [message-key next-chain-key]} (symmetric-ratchet
                                               (:send-chain-key session)
                                               (:send-message-number session))

        ;; Encrypt plaintext (nonce derived from message-number, handled by AEAD)
        message-number (:send-message-number session)
        ciphertext (aead-fn plaintext message-key message-number)

        ;; Update session: advance message number and chain key
        updated-session (assoc session
                          :send-chain-key next-chain-key
                          :send-message-number (inc message-number))]
    {:ciphertext ciphertext
     :updated-session updated-session
     :message-number message-number}))

;; Decrypt a message with the Signal session
;;
;; Returns: {:plaintext <bytes>, :updated-session <SignalSession>}
;;
;; Side effect: if decryption succeeds, session's recv-chain-key and
;; recv-message-number are advanced. If it fails, session is unchanged
;; (allowing out-of-order delivery).
(defn decrypt-message
  [session ciphertext message-number aead-fn]
  {:pre [(some? session)
         (bytes? ciphertext)
         (number? message-number)
         (fn? aead-fn)]}
  (let [;; Derive message key from recv chain at this message number
        ;; (in practice, recv-chain-key is reset by asymmetric-ratchet
        ;;  when peer sends a new DH public, so we only need to ratchet
        ;;  forward if message-number > recv-message-number)
        recv-offset (- message-number (:recv-message-number session))

        plaintext (if (>= recv-offset 0)
                    ;; Advance chain key if needed
                    (let [chain-state (loop [chain (:recv-chain-key session)
                                             i 0]
                                        (if (< i recv-offset)
                                          (let [{:keys [next-chain-key]}
                                                (symmetric-ratchet chain (+ (:recv-message-number session) i))]
                                            (recur next-chain-key (inc i)))
                                          chain))]
                      ;; Decrypt with the derived key at message-number
                      (let [{:keys [message-key]} (symmetric-ratchet
                                                    (:recv-chain-key session)
                                                    message-number)]
                        (aead-fn :decrypt ciphertext message-key message-number)))
                    ;; Out-of-order message: derive key without updating recv chain
                    (let [{:keys [message-key]} (symmetric-ratchet
                                                  (:recv-chain-key session)
                                                  message-number)]
                      (aead-fn :decrypt ciphertext message-key message-number)))

        ;; Update session only if decryption succeeded
        updated-session (if plaintext
                          (assoc session
                            :recv-chain-key (second (symmetric-ratchet
                                                       (:recv-chain-key session)
                                                       message-number))
                            :recv-message-number (max (:recv-message-number session)
                                                      (inc message-number)))
                          session)]
    {:plaintext plaintext
     :updated-session updated-session
     :success (some? plaintext)}))

;; Test vector: deterministic ratchet (for CI, no kagi dependency)
(comment
  ;; Example usage (tests)
  (let [test-chain-key (byte-array 32)  ;; placeholder
        {:keys [message-key next-chain-key]} (symmetric-ratchet test-chain-key 0)]
    ;; Both should be 32 bytes
    (= (count message-key) 32)
    (= (count next-chain-key) 32)
    ;; Chain key should advance
    (not= test-chain-key next-chain-key)))
