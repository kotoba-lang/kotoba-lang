(ns noise.handshake-state
  "HandshakeState (Noise rev 34 §5.3) — the token interpreter. Pure: every step
  returns a new state, and the handshake's completion is a value
  (`:done? true` plus the two transport CipherStates), not a side effect.

  Reading order for `write-message` / `read-message`: they are mirror images. If
  you change a DH token's key selection in one, change it in the other or the
  handshake fails with an authentication error one message later, which is a
  miserable thing to debug — hence `dh-for` being a single shared function
  parameterized by role."
  (:require [noise.patterns :as patterns]
            [noise.suite :as suite]
            [noise.symmetric-state :as ss]))

(defn initialize
  "Start a handshake.

   `:s`  our static keypair {:priv :pub} (required by IK on both sides)
   `:e`  our ephemeral keypair — normally omitted so it is generated per
         handshake; supplying it is for known-answer tests only
   `:rs` the peer's static public key (the initiator needs this for IK)
   `:prologue` bytes both sides must agree on; mixed into `h` before anything
         else, so a disagreement fails the handshake instead of being ignored.
         Use it to bind the session to the netmap version / tailnet id."
  [{:keys [suite pattern initiator? s e rs re prologue psk]}]
  (let [p (patterns/pattern pattern)
        state (-> (ss/initialize-symmetric suite (suite/protocol-name suite pattern))
                  (ss/mix-hash (vec prologue)))
        ;; pre-messages: MixHash each pre-shared public key, initiator's first
        ;; (Noise rev 34 §7.1). For IK that is the responder's static, which the
        ;; initiator knows as `rs` and the responder knows as its own `s`.
        init-pre (get-in p [:pre-messages :initiator] [])
        resp-pre (get-in p [:pre-messages :responder] [])
        pub-for (fn [role token]
                  (let [ours? (= role (if initiator? :initiator :responder))]
                    (case token
                      :s (if ours? (:pub s) rs)
                      :e (if ours? (:pub e) re))))
        state (reduce (fn [st t] (ss/mix-hash st (pub-for :initiator t))) state init-pre)
        state (reduce (fn [st t] (ss/mix-hash st (pub-for :responder t))) state resp-pre)]
    (when (and initiator? (patterns/requires-remote-static? pattern) (nil? rs))
      (throw (ex-info "pattern requires the responder's static public key up front"
                      {:pattern pattern})))
    {:suite suite
     :pattern pattern
     :initiator? initiator?
     :ss state
     :s s :e e :rs rs :re re :psk psk
     :messages (:messages p)
     :message-index 0
     :done? false}))

(defn- our-turn? [{:keys [initiator? message-index]}]
  (= initiator? (even? message-index)))

(defn- dh-for
  "The DH key pair selection for a token, from the perspective of `initiator?`.
   `:es` means \"initiator's ephemeral with responder's static\", `:se` the
   reverse — so which of our keys and which of theirs we use flips with role."
  [{:keys [suite initiator? s e rs re]} token]
  (let [dh (:dh suite)]
    (case token
      :ee (dh (:priv e) re)
      :ss (dh (:priv s) rs)
      :es (if initiator? (dh (:priv e) rs) (dh (:priv s) re))
      :se (if initiator? (dh (:priv s) re) (dh (:priv e) rs)))))

(defn- finish
  "After the last message pattern, split into transport keys. `:send-cs` /
   `:recv-cs` are already oriented for this side."
  [{:keys [initiator? ss] :as hs}]
  (let [[c1 c2] (ss/split ss)]
    (assoc hs
           :done? true
           :handshake-hash (:h ss)
           :send-cs (if initiator? c1 c2)
           :recv-cs (if initiator? c2 c1))))

(defn write-message
  "Write the next handshake message. -> [hs' message-bytes]"
  [hs payload]
  (when (:done? hs) (throw (ex-info "handshake already complete" {})))
  (when-not (our-turn? hs)
    (throw (ex-info "not this side's turn to write" {:message-index (:message-index hs)})))
  (let [tokens (nth (:messages hs) (:message-index hs))
        [hs out]
        (reduce
         (fn [[hs out] token]
           (case token
             :e (let [kp (or (:e hs) ((:dh-generate (:suite hs))))
                      pub (vec (:pub kp))]
                  [(-> hs (assoc :e kp) (update :ss ss/mix-hash pub))
                   (into out pub)])
             :s (let [[ss' ct] (ss/encrypt-and-hash (:ss hs) (vec (:pub (:s hs))))]
                  [(assoc hs :ss ss') (into out ct)])
             ;; DH tokens
             [(update hs :ss ss/mix-key (dh-for hs token)) out]))
         [hs []]
         tokens)
        [ss' ct] (ss/encrypt-and-hash (:ss hs) (vec payload))
        hs (-> hs (assoc :ss ss') (update :message-index inc))]
    [(if (= (:message-index hs) (count (:messages hs))) (finish hs) hs)
     (into out ct)]))

(defn read-message
  "Read the next handshake message. -> [hs' payload-bytes]. Throws on
   authentication failure, which for IK's first message is also how an unknown /
   unauthorized initiator is rejected: it cannot produce a valid `s` ciphertext
   under a chaining key derived from our static key."
  [hs message]
  (when (:done? hs) (throw (ex-info "handshake already complete" {})))
  (when (our-turn? hs)
    (throw (ex-info "not this side's turn to read" {:message-index (:message-index hs)})))
  (let [{:keys [suite]} hs
        dhlen (:dhlen suite)
        taglen 16
        tokens (nth (:messages hs) (:message-index hs))
        [hs rest-msg]
        (reduce
         (fn [[hs msg] token]
           (case token
             :e (let [pub (vec (take dhlen msg))]
                  (when (< (count pub) dhlen)
                    (throw (ex-info "truncated noise message (ephemeral)" {})))
                  [(-> hs (assoc :re pub) (update :ss ss/mix-hash pub))
                   (vec (drop dhlen msg))])
             :s (let [len (if (get-in hs [:ss :cs :k]) (+ dhlen taglen) dhlen)
                      ct (vec (take len msg))]
                  (when (< (count ct) len)
                    (throw (ex-info "truncated noise message (static)" {})))
                  (let [[ss' pt] (ss/decrypt-and-hash (:ss hs) ct)]
                    [(-> hs (assoc :ss ss') (assoc :rs (vec pt))) (vec (drop len msg))]))
             [(update hs :ss ss/mix-key (dh-for hs token)) msg]))
         [hs (vec message)]
         tokens)
        [ss' pt] (ss/decrypt-and-hash (:ss hs) rest-msg)
        hs (-> hs (assoc :ss ss') (update :message-index inc))]
    [(if (= (:message-index hs) (count (:messages hs))) (finish hs) hs)
     pt]))
