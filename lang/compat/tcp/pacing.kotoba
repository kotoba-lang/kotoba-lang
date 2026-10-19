(ns tcp.pacing
  "Nagle's algorithm (RFC 896) and the delayed ACK (RFC 1122 §4.2.3.2), in one
  namespace because their interaction is the reason either is interesting.

  Both are pure decisions: `may-send?` says whether a small segment may go now,
  `ack-decision` says whether an ACK goes now or waits. Neither holds a timer;
  the caller passes `:now-ms`.

  ## Each one alone is reasonable

  Nagle stops a sender turning one keystroke into one 41-byte packet. It holds
  a small segment while any small segment is still unacknowledged, so a burst of
  tiny writes coalesces into one.

  The delayed ACK stops a receiver spending a packet on every acknowledgement.
  It waits a little, hoping either to piggyback the ACK on a reply or to
  acknowledge two segments at once.

  ## Together they deadlock, and the deadlock is the famous one

  A sender writes data that ends in a partial segment. Nagle holds that last
  piece because earlier data is still unacknowledged. The receiver has the
  earlier data and is waiting — for a second full segment that will never come,
  because the sender is holding it, or for its delay timer.

  Nothing is lost, nothing errors, and the transfer stalls for the length of the
  receiver's delay — typically 200 ms, on every request that ends in a partial
  segment. The two algorithms are each correct and their composition is not.

  `deadlock?` names the state so it can be asserted against rather than
  discovered in a latency graph. **This namespace does not silently disable one
  of them** to avoid it: which to disable is the application's decision (a
  request/response protocol wants Nagle off; a bulk transfer does not), and a
  library making that choice quietly is how the setting becomes impossible to
  find later.

  ## The delayed ACK has an exception that loss recovery depends on

  An out-of-order segment must be acknowledged **immediately**. Those duplicate
  ACKs are what drive `tcp.congestion`'s fast retransmit; delaying them delays
  the third duplicate, which delays the retransmission by the whole delay timer,
  on exactly the path that has just lost a segment. `ack-decision` treats
  out-of-order as an immediate case for that reason, not for symmetry."
  )

(def max-delay-ms
  "RFC 1122 §4.2.3.2: an ACK may be delayed, but not by more than 500 ms. Most
  stacks use 200. The RFC's ceiling is the value here because exceeding it is
  a violation, while choosing a smaller one is tuning."
  500)

(def default-delay-ms 200)

(defn sender
  [{:keys [mss nodelay?] :or {nodelay? false}}]
  {:tcp.pace/mss mss
   :tcp.pace/nodelay? nodelay?})

(defn receiver
  [{:keys [mss delay-ms] :or {delay-ms default-delay-ms}}]
  {:tcp.pace/mss mss
   :tcp.pace/delay-ms (min delay-ms max-delay-ms)
   :tcp.pace/unacked-segments 0
   :tcp.pace/first-unacked-ms nil})

;; ── Nagle ─────────────────────────────────────────────────────────────────

(def hold-reasons
  #{:small-and-unacked-outstanding})

(defn may-send?
  "May `bytes-available` go now?

  `unacked-bytes` is what is outstanding. Returns `{:send? b :reason r}`.

  Four ways to be allowed, and the fourth is the one people forget: when
  everything sent so far has been acknowledged there is nothing to coalesce
  with, so a small segment goes immediately. Without that clause Nagle would
  hold the *first* byte of a connection forever."
  [{:tcp.pace/keys [mss nodelay?]} bytes-available unacked-bytes]
  (cond
    (zero? (or bytes-available 0)) {:send? false :reason :nothing-to-send}
    nodelay? {:send? true :reason :nodelay}
    (>= bytes-available mss) {:send? true :reason :full-segment}
    (zero? (or unacked-bytes 0)) {:send? true :reason :nothing-outstanding}
    :else {:send? false :reason :small-and-unacked-outstanding}))

;; ── delayed ACK ───────────────────────────────────────────────────────────

(def immediate-reasons
  "Why an ACK cannot wait. `:out-of-order` is not here for symmetry — it is what
  fast retransmit is built on."
  #{:second-full-segment :out-of-order :window-update :timer-expired :closing})

(defn on-segment
  "Record an arriving in-window segment, and say whether the ACK goes now.

  Returns `{:receiver r :ack-now? b :reason r}`."
  [rcv {:keys [len in-order? window-opened? fin? now-ms]}]
  (let [{:tcp.pace/keys [mss delay-ms unacked-segments first-unacked-ms]} rcv
        full? (>= (or len 0) mss)
        n (inc (or unacked-segments 0))
        started (or first-unacked-ms now-ms)
        clear (fn [r] (assoc r :tcp.pace/unacked-segments 0
                             :tcp.pace/first-unacked-ms nil))
        held (assoc rcv :tcp.pace/unacked-segments n
                    :tcp.pace/first-unacked-ms started)]
    (cond
      (false? in-order?)
      ;; the duplicate ACK that fast retransmit counts. Delaying it delays the
      ;; retransmission by the whole timer, on the path that just lost a segment.
      {:receiver (clear rcv) :ack-now? true :reason :out-of-order}

      fin? {:receiver (clear rcv) :ack-now? true :reason :closing}

      window-opened? {:receiver (clear rcv) :ack-now? true :reason :window-update}

      (and full? (>= n 2))
      {:receiver (clear rcv) :ack-now? true :reason :second-full-segment}

      :else
      {:receiver held :ack-now? false :reason :delayed})))

(defn on-tick
  "Has the delay expired? The timer starts at the *first* unacknowledged
  segment, not the most recent — restarting it on each arrival would let a
  steady trickle of small segments defer the ACK indefinitely."
  [rcv now-ms]
  (let [{:tcp.pace/keys [delay-ms first-unacked-ms]} rcv]
    (if (and first-unacked-ms (>= (- now-ms first-unacked-ms) delay-ms))
      {:receiver (assoc rcv :tcp.pace/unacked-segments 0
                        :tcp.pace/first-unacked-ms nil)
       :ack-now? true :reason :timer-expired}
      {:receiver rcv :ack-now? false :reason :delayed})))

(defn ack-pending? [rcv] (some? (:tcp.pace/first-unacked-ms rcv)))

;; ── the interaction ───────────────────────────────────────────────────────

(defn deadlock?
  "Is this the Nagle/delayed-ACK stall?

  The sender is holding a small segment because earlier data is unacknowledged,
  and the receiver is holding the acknowledgement waiting for more data. Neither
  will move until the receiver's timer fires.

  Named so it can be asserted against. This namespace will not resolve it by
  quietly disabling one side — which one to disable belongs to the application,
  and a library choosing silently is how a setting becomes impossible to find."
  [send-decision rcv]
  (boolean (and (false? (:send? send-decision))
                (= :small-and-unacked-outstanding (:reason send-decision))
                (ack-pending? rcv))))

(defn stall-ms
  "How long the deadlock will last: whatever remains of the receiver's timer."
  [rcv now-ms]
  (when-let [started (:tcp.pace/first-unacked-ms rcv)]
    (max 0 (- (:tcp.pace/delay-ms rcv) (- now-ms started)))))
