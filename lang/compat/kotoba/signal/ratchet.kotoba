;; kotoba.signal.ratchet — Double Ratchet (Marlinspike & Perrin, "The Double
;; Ratchet Algorithm", 2016) for ClojureScript: a DH ratchet (fresh X25519
;; keypair per direction turn) driving a symmetric-key ratchet (HMAC chain)
;; per message, encrypting with AES-256-GCM (Web Crypto). CLJS sibling of
;; kotoba.signal.ratchet (JVM) — same state shape and pure-transition design
;; ([state, result] tuples), but every step that touches a crypto primitive
;; returns a Promise, since Web Crypto has no synchronous API (unlike JCA).
;;
;; PORTING NOTE (found while porting, not present in the JVM version): the
;; JVM `decrypt-message` compares `(:dh-remote state)` against the incoming
;; header's `:dh-pub` with plain `=`, which for `byte[]` is REFERENCE equality
;; in Clojure — that happens to work there only because the JVM test suite
;; keeps everything as live, non-serialized data (the same array object flows
;; unchanged through the pure functions within one chain). Once a header
;; round-trips through JSON/base64 on the wire — the real deployment case —
;; the receiver always gets a freshly-allocated Uint8Array, never the same
;; object, so plain `=` would spuriously DH-ratchet on every single message.
;; `bytes=` below does byte-CONTENT comparison instead; see ratchet_test.cljs
;; `decrypt-message-compares-dh-pub-by-content-not-identity`.
;;
;; SCOPE NOTE (matches the JVM version): no skipped-message-key store — this
;; assumes in-order delivery within a chain.
;;
;;   (require '[kotoba.signal.ratchet :as ratchet])
;;   (ratchet/kdf-root-key root-key dh-out)    ;=> Promise<{:root-key :chain-key}>
;;   (ratchet/kdf-chain-key chain-key)         ;=> Promise<{:chain-key :message-key}>
;;   (ratchet/ratchet-encrypt message-key pt)  ;=> Promise<{:iv :ciphertext}>
;;   (ratchet/ratchet-decrypt message-key env) ;=> Promise<plaintext Uint8Array>
;;   ;; full session, after X3DH produced `shared-secret`:
;;   (-> (ratchet/init-sender shared-secret bobs-initial-dh-pub)   ;=> Promise<state>
;;       (.then (fn [alice] (ratchet/encrypt-message alice (utf8 "hi")))))
;;   ;;=> Promise<[state' {:header {:dh-pub :n} :iv :ciphertext}]>
(ns kotoba.signal.ratchet
  (:require [kotoba.signal.x25519 :as dh]
            [kotoba.signal.hkdf :as hkdf]))

;; ── KDFs (Double Ratchet spec §5.2) ────────────────────────────────────────────
(def ^:private root-kdf-info (.encode (js/TextEncoder.) "kotoba-signal DR root"))
(def ^:private chain-const-msg (js/Uint8Array. #js [1]))
(def ^:private chain-const-chain (js/Uint8Array. #js [2]))

(defn bytes=
  "Byte-CONTENT equality for two Uint8Arrays — see the porting note above for
   why this is NOT plain `=`."
  [^js a ^js b]
  (and (some? a) (some? b)
       (= (.-length a) (.-length b))
       (loop [i 0]
         (cond
           (= i (.-length a)) true
           (not= (aget a i) (aget b i)) false
           :else (recur (inc i))))))

(defn kdf-root-key
  "Root KDF: (root-key, DH output) -> Promise<{:root-key Uint8Array(32)
   :chain-key Uint8Array(32)}>. HKDF over the DH output, keyed/salted by the
   current root key, split into a fresh root key + a fresh chain key. Called
   on every DH ratchet turn."
  [root-key dh-out]
  (-> (hkdf/hkdf root-key dh-out root-kdf-info 64)
      (.then (fn [^js okm] {:root-key (.slice okm 0 32) :chain-key (.slice okm 32 64)}))))

(defn kdf-chain-key
  "Chain KDF: chain-key -> Promise<{:chain-key :message-key}>, via two HMACs
   of the SAME chain key under different constant inputs (Signal's §5.2
   scheme): message-key = HMAC(ck, 0x01), next chain-key = HMAC(ck, 0x02).
   One-way: HMAC is not invertible, so neither the message-key nor the next
   chain-key can be used to recover this chain-key (forward secrecy per step)."
  [chain-key]
  (-> (js/Promise.all #js [(hkdf/hmac-sha256 chain-key chain-const-msg)
                           (hkdf/hmac-sha256 chain-key chain-const-chain)])
      (.then (fn [^js pair] {:message-key (aget pair 0) :chain-key (aget pair 1)}))))

;; ── AES-256-GCM message encryption ────────────────────────────────────────────
(defn- import-aes-key [^js raw]
  (js/crypto.subtle.importKey "raw" raw #js {:name "AES-GCM"} false #js ["encrypt" "decrypt"]))

(defn- header-aad
  "Canonical byte encoding of a ratchet header {:dh-pub :n} -- dh-pub (32
   bytes, fixed length, so no framing/length-prefix is needed for the
   concatenation to be unambiguous) followed by n as 8 big-endian bytes.
   Bound into the AEAD's authenticated (not encrypted) data by encrypt-
   message/decrypt-message below (Web Crypto's :additionalData), so an
   active party on the delivery path can't tamper with :dh-pub/:n without
   the receiver's AEAD tag check failing. Headers still travel in the
   CLEAR (a different, narrower property -- confidentiality, see ns
   docstring's PORTING NOTE area / README's Header encryption scope
   note) -- this only adds the INTEGRITY binding the Double Ratchet spec
   expects a header to have, previously missing entirely (JVM sibling
   has the identical fix, same rationale)."
  [^js dh-pub n]
  (let [out (js/Uint8Array. (+ (.-length dh-pub) 8))]
    (.set out dh-pub 0)
    ;; division/modulo, NOT bit-shift -- JS's `>>>`/`bit-and` coerce their
    ;; operand to a 32-bit int first (ToUint32/ToInt32), which would
    ;; silently truncate/misbehave for a shift >= 32 on an n that ever grew
    ;; past 2^31; div/mod stays correct across JS's full safe-integer range.
    (loop [i 7 v n]
      (when (>= i 0)
        (aset out (+ (.-length dh-pub) i) (mod v 256))
        (recur (dec i) (js/Math.floor (/ v 256)))))
    out))

(defn ratchet-encrypt
  "message-key (32 raw bytes, used directly as the AES-256 key) + plaintext (+
   optional AAD) -> Promise<{:iv Uint8Array(12) :ciphertext Uint8Array}> via
   AES-256-GCM with a fresh random 96-bit IV. Message keys are ONE-SHOT by
   construction — every message gets its own key from `kdf-chain-key`, so IV/
   key reuse across messages never happens."
  ([message-key plaintext] (ratchet-encrypt message-key plaintext nil))
  ([message-key ^js plaintext aad]
   (let [iv (doto (js/Uint8Array. 12) js/crypto.getRandomValues)]
     (-> (import-aes-key message-key)
         (.then (fn [k] (js/crypto.subtle.encrypt
                         (clj->js (cond-> {:name "AES-GCM" :iv iv} aad (assoc :additionalData aad)))
                         k plaintext)))
         (.then (fn [ct] {:iv iv :ciphertext (js/Uint8Array. ct)}))))))

(defn ratchet-decrypt
  "Inverse of `ratchet-encrypt`. Rejects (OperationError) on tamper/wrong key."
  ([message-key envelope] (ratchet-decrypt message-key envelope nil))
  ([message-key {:keys [iv ciphertext]} aad]
   (-> (import-aes-key message-key)
       (.then (fn [k] (js/crypto.subtle.decrypt
                       (clj->js (cond-> {:name "AES-GCM" :iv iv} aad (assoc :additionalData aad)))
                       k ciphertext)))
       (.then #(js/Uint8Array. %)))))

;; ── full session (DH ratchet + per-message symmetric ratchet) ────────────────
(defn init-sender
  "Alice's side: `shared-secret` from X3DH + Bob's current ratchet pubkey
   (conventionally his SPK pub, reused as the bootstrap DR key so no extra
   round-trip is needed before the first message). Alice immediately generates
   her own first ratchet keypair and derives a send-chain from it.
   -> Promise<state>."
  [shared-secret their-initial-dh-pub]
  (let [my-dh (dh/generate-keypair)
        dh-out (dh/dh (:priv my-dh) their-initial-dh-pub)]
    (-> (kdf-root-key shared-secret dh-out)
        (.then (fn [{:keys [root-key chain-key]}]
                 {:dh-priv (:priv my-dh) :dh-pub (:pub my-dh) :dh-remote their-initial-dh-pub
                  :root-key root-key
                  :send-chain-key chain-key :send-chain-remote their-initial-dh-pub
                  :recv-chain-key nil :recv-chain-remote nil
                  :send-n 0 :recv-n 0})))))

(defn init-receiver
  "Bob's side: `shared-secret` from X3DH (same value Alice has) + the keypair
   whose PUBLIC half Alice used as `their-initial-dh-pub` in `init-sender`
   (typically Bob's SPK keypair). Bob has no send-chain yet — he only gets one
   once Alice's first message tells him her ratchet pubkey (see
   `encrypt-message`, which auto-ratchets the sender side forward when
   :dh-remote has moved). -> Promise<state> (for API symmetry with
   init-sender, even though this step needs no crypto op)."
  [shared-secret my-initial-dh-keypair]
  (js/Promise.resolve
   {:dh-priv (:priv my-initial-dh-keypair) :dh-pub (:pub my-initial-dh-keypair) :dh-remote nil
    :root-key shared-secret
    :send-chain-key nil :send-chain-remote nil
    :recv-chain-key nil :recv-chain-remote nil
    :send-n 0 :recv-n 0}))

(defn- dh-ratchet-recv
  "Receiving a message with a NEW peer ratchet pubkey: DH-ratchet the root key
   forward and derive a fresh receiving chain from it. -> Promise<state>."
  [state their-new-dh-pub]
  (let [dh-out (dh/dh (:dh-priv state) their-new-dh-pub)]
    (-> (kdf-root-key (:root-key state) dh-out)
        (.then (fn [{:keys [root-key chain-key]}]
                 (assoc state :dh-remote their-new-dh-pub :root-key root-key
                        :recv-chain-key chain-key :recv-chain-remote their-new-dh-pub :recv-n 0))))))

(defn- dh-ratchet-send
  "About to send after the peer's ratchet pubkey moved: generate a NEW own
   ratchet keypair, DH-ratchet the root key forward, derive a fresh sending
   chain from it. -> Promise<state>. This is the step that gives post-
   compromise security: even a chain-key leak stops mattering the moment
   either side ratchets to a fresh DH keypair the attacker doesn't hold."
  [state]
  (let [my-dh (dh/generate-keypair)
        dh-out (dh/dh (:priv my-dh) (:dh-remote state))]
    (-> (kdf-root-key (:root-key state) dh-out)
        (.then (fn [{:keys [root-key chain-key]}]
                 (assoc state :dh-priv (:priv my-dh) :dh-pub (:pub my-dh)
                        :root-key root-key :send-chain-key chain-key :send-n 0))))))

(defn encrypt-message
  "Advance the sending chain by one message, DH-ratcheting first if the peer's
   ratchet pubkey has moved since the send-chain was last (re)derived.
   -> Promise<[new-state {:header {:dh-pub :n} :iv :ciphertext}]> — :header
   MUST travel with the ciphertext so the receiver can follow the same
   ratchet."
  [state plaintext]
  (-> (if (and (:dh-remote state) (not (bytes= (:send-chain-remote state) (:dh-remote state))))
        (-> (dh-ratchet-send state) (.then (fn [s] (assoc s :send-chain-remote (:dh-remote s)))))
        (js/Promise.resolve state))
      (.then (fn [state]
               (let [header {:dh-pub (:dh-pub state) :n (:send-n state)}]
                 (-> (kdf-chain-key (:send-chain-key state))
                     (.then (fn [{:keys [chain-key message-key]}]
                              (-> (ratchet-encrypt message-key plaintext (header-aad (:dh-pub header) (:n header)))
                                  (.then (fn [env]
                                           [(-> state (assoc :send-chain-key chain-key) (update :send-n inc))
                                            (assoc env :header header)])))))))))))

(defn decrypt-message
  "Advance the receiving chain by one message, DH-ratcheting first if the
   sender's header :dh-pub is new. -> Promise<[new-state plaintext]>.
   NOTE: assumes in-order, non-dropped delivery within a chain (see ns scope
   note)."
  [state {:keys [header] :as envelope}]
  (-> (if (bytes= (:dh-remote state) (:dh-pub header))
        (js/Promise.resolve state)
        (dh-ratchet-recv state (:dh-pub header)))
      (.then (fn [state]
               (-> (kdf-chain-key (:recv-chain-key state))
                   (.then (fn [{:keys [chain-key message-key]}]
                            (-> (ratchet-decrypt message-key envelope (header-aad (:dh-pub header) (:n header)))
                                (.then (fn [pt]
                                         [(-> state (assoc :recv-chain-key chain-key) (update :recv-n inc)) pt]))))))))))
