;; kotoba.signal.group — group ratchet via the "sender-keys" pattern (Signal
;; group messaging: https://signal.org/docs/specifications/xeddsa/ background,
;; sender-keys design as shipped in libsignal's GroupCipher). ONE sender holds a
;; chain key and advances it forward one step per message/segment; every member
;; who received that sender's chain key (via a one-time, pairwise X3DH-secured
;; distribution — see kotoba.signal.x3dh) can independently derive each
;; subsequent segment key by walking the SAME chain forward in lockstep,
;; without a fresh per-message/per-segment DH round with every member.
;;
;; This is the primitive ADR 2606271500 (kotoba-stage-obs-live-aozora) points at
;; for private live-streaming: one broadcaster ratchets a chain key forward once
;; per media segment; every viewer who was bootstrapped into the group can
;; derive that segment's AES key from the chain position alone.
;;
;; SCOPE NOTE — deliberately NOT implemented here (left for a follow-up, not a
;; correctness gap in what IS implemented):
;;   - group membership management (who's in/out, admin add/remove)
;;   - key revocation / forced chain reset on member removal
;;   - out-of-order segment recovery (member-derive assumes the member walks
;;     the SAME number of steps as the sender, in order — like ratchet.clj's
;;     scope note, no skipped-key cache)
;; What IS covered by group_test.clj: sender/member chain agreement (both derive
;; the same message-key at the same chain position), forward secrecy of the
;; chain (same one-way HMAC KDF as ratchet.clj), and distribution-message
;; signature authentication (a forged distribution is rejected).
;;
;;   (require '[kotoba.signal.group :as group])
;;   (def sender-key (group/create-sender-key))
;;   (def dist (group/distribution-message sender-key 0))   ; send to each member via X3DH
;;   (group/verify-distribution dist)                       ;=> true (member checks first)
;;   (let [[sender-key' mk] (group/sender-advance sender-key)]
;;     (group/encrypt-segment mk "segment bytes"))
;;   (let [[member-ck' mk] (group/member-derive (:chain-key dist))]
;;     (group/decrypt-segment mk envelope))
(ns kotoba.signal.group
  (:require [kotoba.signal.ratchet :as ratchet]
            [ed25519.core :as ed])
  (:import (java.security SecureRandom)))

(defn- rand-bytes ^bytes [^long n]
  (let [b (byte-array n)]
    (.nextBytes (SecureRandom.) b)
    b))

(defn create-sender-key
  "New group, as its sender: {:chain-key bytes32 :sig-seed bytes32 :sig-pub
   did:key}. :sig-seed is an Ed25519 identity used ONLY to sign distribution
   messages, so members can authenticate that a chain-key genuinely came from
   this sender (not injected by a MITM on the distribution channel)."
  []
  (let [chain-key (rand-bytes 32)
        sig-seed (rand-bytes 32)]
    {:chain-key chain-key
     :sig-seed sig-seed
     :sig-pub (ed/did-key-from-seed sig-seed)}))

(defn- distribution-payload ^bytes [chain-key iteration]
  (byte-array (concat (seq chain-key) (seq (.getBytes (str iteration) "UTF-8")))))

(defn distribution-message
  "What the sender sends to each member, over a pairwise X3DH-encrypted channel,
   to bootstrap them into the group: the CURRENT chain key + its iteration
   number + a signature binding both to the sender's identity.
   {:chain-key bytes32 :iteration int :sig-pub did:key :sig bytes64}."
  [{:keys [chain-key sig-seed sig-pub]} iteration]
  {:chain-key chain-key
   :iteration iteration
   :sig-pub sig-pub
   :sig (ed/sign sig-seed (distribution-payload chain-key iteration))})

(defn verify-distribution
  "Members MUST call this (and refuse on false) before trusting a distribution
   message's :chain-key."
  [{:keys [chain-key iteration sig-pub sig]}]
  (boolean (ed/verify-did sig-pub (distribution-payload chain-key iteration) sig)))

(defn sender-advance
  "Sender-side: advance the chain key one step. Returns [new-sender-key
   message-key]. Only the sender ever calls this — sender-keys' defining
   property is that only the sender advances the authoritative chain."
  [sender-key]
  (let [{:keys [chain-key message-key]} (ratchet/kdf-chain-key (:chain-key sender-key))]
    [(assoc sender-key :chain-key chain-key) message-key]))

(defn member-derive
  "Member-side: from a chain-key it holds (from a distribution-message, or the
   result of a prior member-derive), derive the NEXT message-key and the next
   chain-key. Returns [next-chain-key message-key]. Members never invent chain
   state — they only step the copy they were given forward, in lockstep with
   the sender's own `sender-advance` calls."
  [chain-key]
  (let [{:keys [chain-key message-key]} (ratchet/kdf-chain-key chain-key)]
    [chain-key message-key]))

(defn encrypt-segment
  "message-key + plaintext segment bytes -> {:iv :ciphertext} (AES-256-GCM)."
  ([message-key plaintext] (ratchet/ratchet-encrypt message-key plaintext))
  ([message-key plaintext aad] (ratchet/ratchet-encrypt message-key plaintext aad)))

(defn decrypt-segment
  ([message-key envelope] (ratchet/ratchet-decrypt message-key envelope))
  ([message-key envelope aad] (ratchet/ratchet-decrypt message-key envelope aad)))
