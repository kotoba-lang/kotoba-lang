(ns tcp.send
  "What to send next, composed from the nine decisions the other namespaces
  make.

  Each of those answers one question in isolation. This answers the question a
  sender actually has — *is there something to put on the wire right now, and
  what* — and the interesting part is that the answers interact.

  ## The binding limit is the one worth reporting

  Three separate things cap how much may go: the congestion window (what the
  *network* will take), the receiver's advertised window (what the *peer* will
  take), and the path MTU (what a single packet may be). The amount that goes is
  the smallest of them, and **which one was smallest is the diagnostic**.

  \"This connection is slow\" is unanswerable without it. \"The receiver's window
  is the limit, not congestion\" tells an operator to look at the application on
  the other end rather than at the network. `decide` therefore returns
  `:limited-by` on every decision, including the ones that send.

  ## Retransmission comes before new data, and SACK changes what that means

  RFC 6675: during recovery a sender resends the holes before it sends anything
  new. Without SACK the hole is everything from the first unacknowledged byte;
  with it, the holes are named, and the difference is most of a window.

  ## Flight size is un-SACKed bytes, not queue bytes

  The congestion window bounds what is *in the network*. SACKed data has left
  it — the receiver said so — so counting it as in flight makes the sender hold
  back for the rest of the recovery by exactly the amount the receiver already
  has. `tcp.sack/pipe` is the number; using the raw queue size instead is a
  throughput bug that no test of any single namespace can catch, because both
  namespaces are individually correct.

  That is the whole reason this namespace has tests of its own."
  (:require [tcp.congestion :as cc]
            [tcp.sack :as sack]
            [tcp.retransmit :as rtx]
            [tcp.pacing :as pace]
            [tcp.pmtu :as pmtu]))

(def limits
  "Why a decision was the size it was."
  #{:congestion-window :receiver-window :path-mtu :application-data
    :nagle :nothing-to-send :retransmission})

(def actions #{:send-new :retransmit :wait})

(defn- effective-mss
  "The segment payload, bounded by the path and by what the peer said it would
  accept. The peer's MSS option is an upper bound on what it wants; the path is
  an upper bound on what will arrive."
  [discoverer peer-mss header-bytes]
  (min (pmtu/mss-for discoverer header-bytes) (or peer-mss 65535)))

(defn decide
  "One send decision.

  `ctx` is
    `{:congestion :queue :sender :discoverer
      :receiver-window n :peer-mss n :header-bytes n
      :bytes-available n :now-ms n :in-recovery? b}`

  Returns `{:action :send-new|:retransmit|:wait :bytes n :segment e
            :limited-by k}`."
  [{:keys [congestion queue sender discoverer receiver-window peer-mss
           header-bytes bytes-available now-ms in-recovery? rto-ms]
    :or {header-bytes 40 receiver-window 65535 bytes-available 0 rto-ms 1000}}]
  (let [mss (effective-mss discoverer peer-mss header-bytes)
        ;; What is really in the network. NOT the queue's byte count: SACKed
        ;; data has left it and the receiver has said so.
        flight (sack/pipe queue)
        cwnd-room (cc/may-send congestion flight)
        rwnd-room (max 0 (- receiver-window flight))
        room (min cwnd-room rwnd-room)
        binding (cond (and (<= cwnd-room rwnd-room) (< cwnd-room mss)) :congestion-window
                      (< rwnd-room mss) :receiver-window
                      :else :path-mtu)
        timed-out (first (rtx/due queue rto-ms now-ms))]
    (cond
      ;; 1. A timer fired: the oldest unacknowledged segment goes again. This
      ;; outranks everything, including the congestion window, because RFC 5681
      ;; permits one loss-recovery segment when cwnd has collapsed to it.
      timed-out
      {:action :retransmit :segment timed-out :bytes (:len timed-out)
       :limited-by :retransmission}

      ;; 2. In recovery, the named holes go before anything new (RFC 6675).
      (and in-recovery? (seq (sack/holes queue)))
      (if (>= room mss)
        (let [e (sack/next-to-retransmit queue)]
          {:action :retransmit :segment e :bytes (:len e) :limited-by :retransmission})
        {:action :wait :bytes 0 :limited-by binding})

      (zero? bytes-available)
      {:action :wait :bytes 0 :limited-by :nothing-to-send}

      (zero? room)
      {:action :wait :bytes 0 :limited-by (if (< cwnd-room rwnd-room)
                                            :congestion-window :receiver-window)}

      :else
      (let [n (min bytes-available room mss)
            nagle (pace/may-send? sender n flight)]
        (if (:send? nagle)
          {:action :send-new :bytes n
           :limited-by (cond (= n bytes-available) :application-data
                             (= n mss) :path-mtu
                             (= n cwnd-room) :congestion-window
                             :else :receiver-window)}
          {:action :wait :bytes 0 :limited-by :nagle :nagle-reason (:reason nagle)})))))

(defn why-slow
  "A one-word answer to \"why is this connection not going faster\", from a
  decision. Separate from `decide` because operators ask it about a *series* of
  decisions, and folding a series is the caller's job — but the vocabulary
  should not be reinvented at each call site."
  [{:keys [limited-by]}]
  (case limited-by
    :congestion-window :the-network
    :receiver-window :the-peer
    :path-mtu :packet-size
    :application-data :nothing-more-to-send
    :nagle :held-for-coalescing
    :nothing-to-send :idle
    :retransmission :recovering
    :unknown))
