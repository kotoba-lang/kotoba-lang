(ns tcp.congestion
  "RFC 5681 — when to speed up and when to slow down.

  Pure arithmetic over a small state: the congestion window, the slow-start
  threshold, and where in the cycle we are. Nothing here sends, times, or
  measures; the caller reports what happened and this says how much may be in
  flight afterwards.

  ## The window is a guess about a path nobody can see

  A sender cannot observe the bottleneck. It can observe that its segments
  arrived, and that some did not, and RFC 5681 turns those two observations into
  a window: grow while things arrive, halve when they do not. Every constant
  below is a bet on what a loss meant, and the ones that matter are named rather
  than inlined.

  ## Slow start is exponential and that is the point

  Below `ssthresh` the window grows by a full segment per acknowledged segment —
  doubling every round trip. It is called slow because it starts small, not
  because it climbs slowly, and it exists so a new connection finds the path's
  capacity in log time instead of probing linearly from one segment.

  Above `ssthresh` growth becomes one segment per **round trip**, which is the
  additive half of AIMD.

  ## Three duplicate ACKs is a heuristic, not a proof

  A duplicate ACK means the receiver got something out of order. That happens
  when a segment is lost and also when the network reorders. Three is the
  threshold RFC 5681 picks as the point where loss is more likely than
  reordering — **it is a bet**, and a path that reorders heavily will make this
  sender halve its window for no reason. Recorded here because the alternative
  is a reader assuming the number was derived.

  ## A timeout is a stronger signal than duplicate ACKs

  Duplicate ACKs mean the receiver is still hearing something. A timeout means
  it is not. So a timeout drops the window to one segment while fast recovery
  only halves it — the two paths are different because the evidence is."
  )

(def default-mss 1460)

(defn- loss-window [mss] mss)

(defn initial-window
  "RFC 5681 §3.1: min(4·MSS, max(2·MSS, 4380)).

  RFC 6928 permits 10·MSS and most stacks use it. This keeps the 5681 value as
  the default because a larger initial burst is a decision about how much an
  unmeasured path can absorb, and it belongs to whoever deploys this, not to
  the library. `initial-window-10` is here for callers who make that choice."
  [mss] (min (* 4 mss) (max (* 2 mss) 4380)))

(defn initial-window-10 [mss] (* 10 mss))

(def states
  "Where in the cycle the sender is. `:fast-recovery` is separate from
  `:congestion-avoidance` because the window is temporarily inflated there and
  the inflation has to be undone — a sender that forgets to deflate keeps a
  window it never earned."
  #{:slow-start :congestion-avoidance :fast-recovery})

(def dup-ack-threshold 3)

(def unbounded-ssthresh
  "§3.1 says ssthresh \"may be arbitrarily high\" at the start. A number rather
  than nil so `<` works without a special case, and the largest integer both
  hosts represent exactly rather than an arbitrary large one that a long
  connection could actually reach."
  9007199254740991)

(defn controller
  ([] (controller {}))
  ([{:keys [mss initial-cwnd] :or {mss default-mss}}]
   {:tcp.cc/mss mss
    :tcp.cc/cwnd (or initial-cwnd (initial-window mss))
    ;; §3.1: ssthresh starts arbitrarily high -- the sender has no reason yet to
    ;; believe any particular capacity, so it stays in slow start until a loss
    ;; tells it something.
    :tcp.cc/ssthresh unbounded-ssthresh
    :tcp.cc/state :slow-start
    :tcp.cc/dup-acks 0}))

(defn phase
  "Which growth rule applies. Derived rather than stored, so it cannot disagree
  with the numbers it is derived from."
  [{:tcp.cc/keys [cwnd ssthresh state]}]
  (cond (= :fast-recovery state) :fast-recovery
        (< cwnd ssthresh) :slow-start
        :else :congestion-avoidance))

(defn on-ack
  "A new (non-duplicate) ACK covering `bytes-acked`.

  Slow start adds one segment per acknowledged segment; congestion avoidance
  adds roughly one segment per round trip (MSS²/cwnd per ACK, the standard
  approximation from §3.1). Leaving fast recovery deflates the window back to
  ssthresh — the inflation was there only to keep data flowing while the
  retransmission was in the air."
  [{:tcp.cc/keys [mss cwnd ssthresh state] :as cc} bytes-acked]
  (let [cc (assoc cc :tcp.cc/dup-acks 0)]
    (case state
      :fast-recovery
      ;; §3.2 step 6: deflate. The window returns to what the halving decided,
      ;; not to what the inflation grew it to.
      (assoc cc :tcp.cc/cwnd ssthresh :tcp.cc/state :congestion-avoidance)

      (if (< cwnd ssthresh)
        (assoc cc :tcp.cc/cwnd (+ cwnd (min (or bytes-acked mss) mss))
               :tcp.cc/state :slow-start)
        (assoc cc :tcp.cc/cwnd (+ cwnd (max 1 (quot (* mss mss) cwnd)))
               :tcp.cc/state :congestion-avoidance)))))

(defn on-duplicate-ack
  "One duplicate ACK. `flight-size` is the bytes currently unacknowledged.

  Below the threshold this only counts. At the threshold it performs fast
  retransmit and enters fast recovery: ssthresh halves, and cwnd becomes
  ssthresh + 3·MSS — the three counting the segments that have left the network,
  which is what the duplicates told us. Beyond the threshold each further
  duplicate inflates by one more segment, for the same reason.

  Returns the controller with `:tcp.cc/retransmit?` true on the transition, so a
  caller knows to resend without having to compare counters itself."
  [{:tcp.cc/keys [mss cwnd ssthresh state dup-acks] :as cc} flight-size]
  (let [n (inc (or dup-acks 0))
        cc (assoc cc :tcp.cc/dup-acks n :tcp.cc/retransmit? false)]
    (cond
      (= :fast-recovery state)
      (assoc cc :tcp.cc/cwnd (+ cwnd mss))

      (< n dup-ack-threshold) cc

      :else
      (let [ssthresh' (max (quot (or flight-size cwnd) 2) (* 2 mss))]
        (assoc cc :tcp.cc/ssthresh ssthresh'
               :tcp.cc/cwnd (+ ssthresh' (* dup-ack-threshold mss))
               :tcp.cc/state :fast-recovery
               :tcp.cc/retransmit? true)))))

(defn on-timeout
  "§3.1: a retransmission timeout.

  ssthresh halves as it does for a fast retransmit, but cwnd goes to **one
  segment** rather than to ssthresh — a timeout means the receiver stopped
  hearing anything, which is stronger evidence than duplicates, and the sender
  restarts slow start from the bottom."
  [{:tcp.cc/keys [mss cwnd] :as cc} flight-size]
  (assoc cc
         :tcp.cc/ssthresh (max (quot (or flight-size cwnd) 2) (* 2 mss))
         :tcp.cc/cwnd (loss-window mss)
         :tcp.cc/state :slow-start
         :tcp.cc/dup-acks 0
         :tcp.cc/retransmit? true))

(defn may-send
  "How many bytes may go now: the window minus what is already in flight, never
  negative. The receiver's advertised window is the other limit and belongs to
  the caller — this namespace only knows what the *network* will take."
  [{:tcp.cc/keys [cwnd]} flight-size]
  (max 0 (- cwnd (or flight-size 0))))
