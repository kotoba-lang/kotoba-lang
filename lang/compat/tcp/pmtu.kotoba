(ns tcp.pmtu
  "Finding the largest packet the path will carry, without trusting ICMP to
  tell us.

  Pure decisions over a small search state. The caller sends the probes and
  reports what happened; this says what size to try next and what the current
  answer is.

  ## Why the classic method fails on the modern internet

  RFC 1191 discovers the path MTU by sending large packets with don't-fragment
  set and waiting for a router to send back ICMP \"fragmentation needed\" naming
  the smaller size. It is exact, it is cheap, and it depends on an ICMP message
  arriving.

  Firewalls filter ICMP. When they do, the too-large packets are dropped and
  nothing comes back — a **PMTU black hole**. The connection completes its
  handshake perfectly, because SYNs are small, and then hangs the moment it
  sends a full-sized segment. \"Connects fine, stalls on the first real
  transfer\" is that failure, and it is invisible to every check that only asks
  whether the connection came up.

  ## So the search is driven by the transport, not by ICMP

  RFC 8899 (PLPMTUD) probes upward with packets the transport itself
  acknowledges. An acknowledged probe *is* the confirmation; no ICMP is
  required, and a filtered network is no longer silent — it simply fails to
  confirm, which is a signal this namespace can act on.

  ICMP is still used, as a **hint that shortens the search**, never as an
  authority. `on-icmp` validates and clamps it, and a rejected message leaves
  the search exactly where it was.

  ## An unvalidated ICMP message is a denial of service

  Anyone able to send an ICMP packet can claim the path MTU is tiny. A stack
  that believes it drops to minimum-sized segments and stays there. So a message
  must quote enough of the original packet to prove the sender saw it, must name
  a size below the current one to be news at all, and is floored — a claim below
  `min-mtu` is refused rather than clamped, because a router that genuinely
  cannot carry 1200 bytes is not a path this transport can use anyway."
  )

(def min-mtu
  "RFC 8899 §5.1.1 / RFC 8201: the floor. IPv6 requires 1280 end to end, and
  a claim below this is either broken or hostile."
  1200)

(def base-mtu
  "The size assumed to work without probing. RFC 8899 calls it BASE_PMTU."
  1200)

(def max-probes
  "§5.1.3 MAX_PROBES: how many times one size is tried before it is treated as
  unreachable. Three, because a single lost probe is ordinary."
  3)

(def states
  "RFC 8899 §5.2. `:search-complete` is not an end — `:base` is re-entered when
  the path changes under the connection."
  #{:base :searching :search-complete :error})

(defn discoverer
  [{:keys [link-mtu] :or {link-mtu 1500}}]
  {:tcp.pmtu/state :base
   :tcp.pmtu/current base-mtu
   :tcp.pmtu/probe-size nil
   :tcp.pmtu/ceiling link-mtu
   :tcp.pmtu/floor base-mtu
   :tcp.pmtu/probes 0})

(defn current-mtu [d] (:tcp.pmtu/current d))

(defn- midpoint [lo hi] (+ lo (quot (- hi lo) 2)))

(defn next-probe
  "The size to try next, or nil when there is nothing left to learn.

  A binary search between what is known to work and what is known not to. The
  search stops when the gap is smaller than a segment's worth of gain, because
  probing for the last few bytes costs more round trips than the bytes are
  worth."
  [{:tcp.pmtu/keys [state floor ceiling]}]
  (when (not= :search-complete state)
    (let [mid (midpoint floor ceiling)]
      (when (> (- ceiling floor) 8) mid))))

(defn start-probe
  [d]
  (if-let [size (next-probe d)]
    (assoc d :tcp.pmtu/state :searching :tcp.pmtu/probe-size size :tcp.pmtu/probes 1)
    (assoc d :tcp.pmtu/state :search-complete :tcp.pmtu/probe-size nil)))

(defn on-probe-acked
  "The probe of `size` was acknowledged: the path carries at least that much.

  This is the whole reason the search does not need ICMP — an acknowledgement
  from the transport is proof the packet arrived, and no router had to volunteer
  anything."
  [d size]
  (let [d (assoc d :tcp.pmtu/current (max (:tcp.pmtu/current d) size)
                 :tcp.pmtu/floor (max (:tcp.pmtu/floor d) size)
                 :tcp.pmtu/probes 0)]
    (start-probe d)))

(defn on-probe-lost
  "A probe went unanswered. Ordinary once; `max-probes` times means the size is
  not carried, and the ceiling comes down to just below it.

  Losing a probe is **not** a congestion signal and must not be reported to
  `tcp.congestion` as one — probes are deliberately oversized and their loss
  says something about size, not about queueing. A stack that halves cwnd on
  every probe loss makes the search cost throughput it never needed to spend."
  [d]
  ;; `:probes` counts ATTEMPTS at this size, not losses -- `start-probe` sets it
  ;; to 1 for the transmission it just authorised. Comparing losses against
  ;; MAX_PROBES instead gives up after two attempts, one short of the RFC.
  (let [n (inc (:tcp.pmtu/probes d 0))]
    (if (<= n max-probes)
      (assoc d :tcp.pmtu/probes n)
      (-> d
          (assoc :tcp.pmtu/ceiling (dec (:tcp.pmtu/probe-size d))
                 :tcp.pmtu/probes 0)
          start-probe))))

;; ── ICMP, as a hint ───────────────────────────────────────────────────────

(def icmp-reject-reasons
  #{:not-quoting-our-packet :not-smaller :below-floor :no-size
    :contradicts-confirmed})

(defn on-icmp
  "An ICMP \"fragmentation needed\" claiming the path MTU is `claimed`.

  `quote-matches?` is the caller's verified answer to \"does the quoted packet
  match something we actually sent\" — RFC 8201 requires that check, and without
  it anyone who can send an ICMP packet can shrink this connection's segments to
  the floor and leave them there.

  A valid message **shortens the search**: it lowers the ceiling and re-probes.
  It never sets the current MTU directly, because the size a router names is
  still a claim, and the probe that follows is what turns it into knowledge."
  [d {:keys [claimed quote-matches?]}]
  (cond
    (not (true? quote-matches?))
    (assoc d :tcp.pmtu/icmp-rejected :not-quoting-our-packet)

    (nil? claimed) (assoc d :tcp.pmtu/icmp-rejected :no-size)

    (< claimed min-mtu)
    ;; refused, not clamped: a path that cannot carry the floor is not one this
    ;; transport can use, and clamping would hide that behind tiny segments.
    (assoc d :tcp.pmtu/icmp-rejected :below-floor)

    ;; News is measured against the CEILING -- what we currently believe the
    ;; path cannot exceed -- not against the confirmed size. A router naming
    ;; 1400 when a probe has confirmed 1350 is telling us something: the
    ;; ceiling comes down from 1500 to 1400 and the search narrows.
    (>= claimed (:tcp.pmtu/ceiling d))
    (assoc d :tcp.pmtu/icmp-rejected :not-smaller)

    ;; A claim at or below a size a probe already carried contradicts evidence
    ;; this connection gathered itself. It may be a changed path and it may be
    ;; a forgery, and the two are indistinguishable from here -- so the
    ;; confirmed floor stands and the claim is refused. The black-hole detector
    ;; is what catches a genuinely changed path, using timeouts rather than
    ;; anyone's assertion.
    (<= claimed (:tcp.pmtu/current d))
    (assoc d :tcp.pmtu/icmp-rejected :contradicts-confirmed)

    :else
    (-> d
        (dissoc :tcp.pmtu/icmp-rejected)
        (assoc :tcp.pmtu/ceiling claimed
               :tcp.pmtu/probes 0)
        start-probe)))

;; ── black holes ───────────────────────────────────────────────────────────

(def black-hole-timeouts
  "How many retransmission timeouts at the current size before the path is
  treated as a black hole. Two, because one is ordinary loss."
  2)

(defn on-retransmission-timeout
  "A timeout on ordinary data — not on a probe.

  Repeated timeouts at a size that used to work are the black-hole signature:
  the packets are being dropped by something that will not say so. Falling back
  to the base MTU is what turns a hung connection into a slow one, and a slow
  connection is one somebody can debug.

  This is the failure the ICMP-based method cannot see at all, and it is the
  reason the fallback lives here rather than being left to the caller."
  [d]
  (let [n (inc (:tcp.pmtu/data-timeouts d 0))]
    (if (or (< n black-hole-timeouts) (= base-mtu (:tcp.pmtu/current d)))
      (assoc d :tcp.pmtu/data-timeouts n)
      (assoc d :tcp.pmtu/state :base
             :tcp.pmtu/current base-mtu
             :tcp.pmtu/floor base-mtu
             :tcp.pmtu/ceiling (:tcp.pmtu/current d)
             :tcp.pmtu/data-timeouts 0
             :tcp.pmtu/black-hole? true))))

(defn on-data-acked
  "Ordinary data got through: whatever was accumulating was not a black hole."
  [d]
  (assoc d :tcp.pmtu/data-timeouts 0))

(defn mss-for
  "The largest segment payload at the current MTU, given the header overhead the
  caller knows about (20 IPv4 + 20 TCP is the usual minimum; options cost more)."
  ([d] (mss-for d 40))
  ([d header-bytes] (max 1 (- (current-mtu d) header-bytes))))
