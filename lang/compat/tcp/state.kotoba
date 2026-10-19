(ns tcp.state
  "RFC 9293 §3.3.2 connection state machine, as a pure step function.

  `step` takes a connection and one segment (or one call from the user, or one
  timer tick) and returns the next connection plus the segments to send. It
  opens no sockets, allocates no buffers and reads no clock — the caller passes
  `:now-ms` on every event, which is what makes TIME-WAIT testable without
  waiting two minutes for it.

  ## What this is and is not

  It is the **connection** half of TCP: the eleven states, the handshake, the
  acceptability test, and the close sequence with its four ways of ending. It is
  the part where a hand-written stack gets a state wrong and only finds out
  under a peer it did not expect.

  It is **not** a transmission stack. No retransmission timers, no congestion
  control, no Nagle, no options negotiation. Those are separable and are named
  here as absent rather than half-built — `kotoba-lang/aiueos`'s existing probe
  says the same thing about itself, and the honest thing is to keep saying it
  until the code is there.

  ## Simultaneous open, and why the table has an arm for it

  Two peers can SYN each other at once. The RFC handles it: SYN-SENT that
  receives a SYN with no ACK goes to SYN-RECEIVED rather than treating the
  segment as garbage. It is rare in practice and it is exactly the arm that gets
  dropped when someone writes the table from the common path — so it has a test
  whose name says what it is."
  (:require [tcp.seq :as s]))

(def states
  "RFC 9293 §3.3.2. CLOSED is both the start and the end."
  #{:closed :listen :syn-sent :syn-received :established
    :fin-wait-1 :fin-wait-2 :close-wait :closing :last-ack :time-wait})

(def synchronised
  "States in which both sides have agreed on sequence numbers, so the
  acceptability test applies."
  #{:established :fin-wait-1 :fin-wait-2 :close-wait :closing :last-ack :time-wait})

(def msl-ms
  "Maximum segment lifetime. TIME-WAIT is 2×MSL. 30 s rather than the RFC's
  2 min because this is the value a caller most often wants to shorten; it is
  data, and `step` takes it from the connection."
  30000)

(defn connection
  "A fresh connection. `iss` is the initial send sequence — the caller picks it
  (RFC 9293 §3.4.1 wants it unpredictable, and randomness is not this
  namespace's business)."
  [{:keys [iss local-port remote-port msl-ms*]
    :or {iss 0 msl-ms* msl-ms}}]
  {:tcp/state :closed
   :tcp/iss iss
   :tcp/snd-una iss :tcp/snd-nxt iss :tcp/snd-wnd 65535
   :tcp/rcv-nxt 0 :tcp/rcv-wnd 65535
   :tcp/local-port local-port :tcp/remote-port remote-port
   :tcp/msl-ms msl-ms*})

;; ── segments ───────────────────────────────────────────────────────────────

(defn- seg [{:keys [seq ack flags len] :or {len 0}}]
  (cond-> {:tcp/seq seq :tcp/flags (set flags) :tcp/len len}
    ack (assoc :tcp/ack ack)))

(defn- ack-of [c] (seg {:seq (:tcp/snd-nxt c) :ack (:tcp/rcv-nxt c) :flags [:ack]}))
(defn- rst-of [c ack?] (seg (cond-> {:seq (:tcp/snd-nxt c) :flags [:rst]}
                              ack? (assoc :ack (:tcp/rcv-nxt c) :flags #{:rst :ack}))))

(defn- flags [sg] (or (:tcp/flags sg) #{}))
(defn- has? [sg f] (contains? (flags sg) f))

(def drop-reasons
  "Why a segment changed nothing. Named rather than swallowed: a stack that
  silently drops cannot be debugged from its own state."
  #{:unacceptable-sequence :bad-ack :rst-out-of-window :closed :no-listener
    :wrong-state-for-flag})

(defn- out [c segs & {:keys [dropped]}]
  (cond-> {:tcp/connection c :tcp/send (vec (remove nil? segs))}
    dropped (assoc :tcp/dropped dropped)))

;; ── the step function ──────────────────────────────────────────────────────

(defn- on-syn-sent [c sg]
  (let [ack-ok (and (has? sg :ack)
                    (= (:tcp/ack sg) (s/+seq (:tcp/iss c) 1)))]
    (cond
      ;; RST is only believed when it acknowledges our SYN (§3.10.7.3).
      (has? sg :rst)
      (if ack-ok (out (assoc c :tcp/state :closed) nil)
          (out c nil :dropped :rst-out-of-window))

      (and (has? sg :syn) (has? sg :ack) ack-ok)
      (let [c (assoc c :tcp/state :established
                     :tcp/snd-una (:tcp/ack sg)
                     :tcp/rcv-nxt (s/+seq (:tcp/seq sg) 1))]
        (out c [(ack-of c)]))

      ;; simultaneous open: a SYN with no ACK.
      (and (has? sg :syn) (not (has? sg :ack)))
      (let [c (assoc c :tcp/state :syn-received
                     :tcp/rcv-nxt (s/+seq (:tcp/seq sg) 1))]
        (out c [(seg {:seq (:tcp/iss c) :ack (:tcp/rcv-nxt c) :flags [:syn :ack]})]))

      (has? sg :ack) (out c [(rst-of c false)] :dropped :bad-ack)
      :else (out c nil :dropped :wrong-state-for-flag))))

(defn- on-listen [c sg]
  (cond
    (has? sg :rst) (out c nil :dropped :rst-out-of-window)
    (has? sg :ack) (out c [(rst-of c false)] :dropped :bad-ack)
    (has? sg :syn)
    (let [c (assoc c :tcp/state :syn-received
                   :tcp/rcv-nxt (s/+seq (:tcp/seq sg) 1)
                   :tcp/snd-nxt (:tcp/iss c) :tcp/snd-una (:tcp/iss c))]
      (out c [(seg {:seq (:tcp/iss c) :ack (:tcp/rcv-nxt c) :flags [:syn :ack]})]))
    :else (out c nil :dropped :wrong-state-for-flag)))

(defn- advance-on-fin
  "The close sequence: which state a FIN moves us to depends on where we are."
  [c sg]
  (let [c (assoc c :tcp/rcv-nxt (s/+seq (:tcp/seq sg) (inc (:tcp/len sg 0))))]
    (case (:tcp/state c)
      :established (assoc c :tcp/state :close-wait)
      :fin-wait-1  (assoc c :tcp/state :closing)
      :fin-wait-2  (assoc c :tcp/state :time-wait)
      c)))

(defn- on-synchronised [c sg now]
  (let [len (:tcp/len sg 0)]
    (cond
      (not (s/acceptable? (:tcp/seq sg) len (:tcp/rcv-nxt c) (:tcp/rcv-wnd c)))
      ;; an unacceptable segment is answered with an ACK, not silence: the peer
      ;; needs to learn our window. RST is the one exception.
      (if (has? sg :rst)
        (out c nil :dropped :rst-out-of-window)
        (out c [(ack-of c)] :dropped :unacceptable-sequence))

      (has? sg :rst) (out (assoc c :tcp/state :closed) nil)

      (not (has? sg :ack)) (out c nil :dropped :wrong-state-for-flag)

      :else
      (let [acked (:tcp/ack sg)
            c (cond-> c
                (s/geq? acked (:tcp/snd-una c)) (assoc :tcp/snd-una acked))
            our-fin-acked? (and (#{:fin-wait-1 :closing :last-ack} (:tcp/state c))
                                (= (s/wrap acked) (s/wrap (:tcp/snd-nxt c))))
            c (cond
                (and our-fin-acked? (= :fin-wait-1 (:tcp/state c)))
                (assoc c :tcp/state :fin-wait-2)
                (and our-fin-acked? (= :closing (:tcp/state c)))
                (assoc c :tcp/state :time-wait :tcp/time-wait-started-ms now)
                (and our-fin-acked? (= :last-ack (:tcp/state c)))
                (assoc c :tcp/state :closed)
                :else c)]
        (if (has? sg :fin)
          (let [c (advance-on-fin c sg)
                c (cond-> c (= :time-wait (:tcp/state c))
                          (assoc :tcp/time-wait-started-ms now))]
            (out c [(ack-of c)]))
          (let [c (cond-> c (pos? len)
                          (assoc :tcp/rcv-nxt (s/+seq (:tcp/seq sg) len)))]
            (out c (when (pos? len) [(ack-of c)]))))))))

(defn step
  "One event -> `{:tcp/connection <next> :tcp/send [segments] :tcp/dropped <why>}`.

  `event` is one of
    `{:kind :listen}` / `{:kind :connect}` / `{:kind :close}`
    `{:kind :segment :segment {...} :now-ms n}`
    `{:kind :tick :now-ms n}`"
  [c {:keys [kind segment now-ms]}]
  (case kind
    :listen (out (assoc c :tcp/state :listen) nil)

    :connect
    (let [c (assoc c :tcp/state :syn-sent :tcp/snd-nxt (s/+seq (:tcp/iss c) 1))]
      (out c [(seg {:seq (:tcp/iss c) :flags [:syn]})]))

    :close
    (case (:tcp/state c)
      :established (let [c (assoc c :tcp/state :fin-wait-1
                                  :tcp/snd-nxt (s/+seq (:tcp/snd-nxt c) 1))]
                     (out c [(seg {:seq (s/-seq (:tcp/snd-nxt c) 1)
                                   :ack (:tcp/rcv-nxt c) :flags [:fin :ack]})]))
      :close-wait (let [c (assoc c :tcp/state :last-ack
                                 :tcp/snd-nxt (s/+seq (:tcp/snd-nxt c) 1))]
                    (out c [(seg {:seq (s/-seq (:tcp/snd-nxt c) 1)
                                  :ack (:tcp/rcv-nxt c) :flags [:fin :ack]})]))
      :listen (out (assoc c :tcp/state :closed) nil)
      :syn-sent (out (assoc c :tcp/state :closed) nil)
      (out c nil :dropped :wrong-state-for-flag))

    :tick
    ;; TIME-WAIT is the only timer this namespace owns. Retransmission is not
    ;; here, and pretending otherwise by adding a second timer would suggest it.
    (if (and (= :time-wait (:tcp/state c))
             (:tcp/time-wait-started-ms c)
             (>= (- now-ms (:tcp/time-wait-started-ms c)) (* 2 (:tcp/msl-ms c))))
      (out (assoc c :tcp/state :closed) nil)
      (out c nil))

    :segment
    (case (:tcp/state c)
      :closed (out c (when-not (has? segment :rst) [(rst-of c true)]) :dropped :closed)
      :listen (on-listen c segment)
      :syn-sent (on-syn-sent c segment)
      :syn-received
      (cond
        (has? segment :rst) (out (assoc c :tcp/state :closed) nil)
        (and (has? segment :ack)
             (= (:tcp/ack segment) (s/+seq (:tcp/iss c) 1)))
        (out (assoc c :tcp/state :established :tcp/snd-una (:tcp/ack segment)
                    :tcp/snd-nxt (s/+seq (:tcp/iss c) 1)) nil)
        :else (out c nil :dropped :bad-ack))
      (on-synchronised c segment now-ms))

    (out c nil :dropped :wrong-state-for-flag)))

(defn established? [c] (= :established (:tcp/state c)))
(defn closed? [c] (= :closed (:tcp/state c)))
