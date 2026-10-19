(ns inga.net
  "Peer sessions: when to connect, what to queue, and when to give up on a
  peer — as pure data.

  `inga.wire` says what a message is. This says how a replica treats the peer
  sending them. Everything a real socket does — opening, closing, bytes — is
  in a driver; everything that decides POLICY is here, because policy is the
  part with failure modes worth testing and a socket is not.

  ## The three things a naive peer loop gets wrong

  1. **Reconnecting immediately.** A peer that is down stays down for a while,
     and a replica that retries in a tight loop spends its CPU on a dead host
     and its bandwidth on SYN packets. The backoff is the same shape
     `inga.pacemaker` uses for views and for the same reason: it must grow,
     and it must stop growing so recovery is not proportional to downtime.

  2. **An unbounded outbound queue.** A slow or dead peer that is not yet
     detected as dead accumulates every message the replica wanted to send.
     Consensus is a broadcast protocol, so that is every proposal and every
     vote, forever, until the process dies of memory — with no invalid data
     involved anywhere. The queue is bounded and drops the OLDEST, because in
     consensus a stale vote is worthless and the newest message is the one
     that matters.

  3. **Treating malformed messages as noise.** A peer sending garbage is
     either broken or hostile, and either way the replica should stop
     spending on it. Malformed messages are counted, and past a threshold the
     peer is dropped — but the count decays on good messages, so a peer that
     glitched once is not condemned forever.

  ## What is deliberately NOT here

  No authentication and no rate limiting by identity. A peer's messages are
  still only as trustworthy as the certificates inside them: this layer
  decides who to spend bandwidth on, not who to believe. `inga.consensus` and
  `inga.sync` decide that, and they do not consult this namespace.")

(def default-params
  {:base-backoff 500          ; first reconnect delay, logical ms
   :max-doublings 6           ; caps the backoff — recovery must not scale with downtime
   :max-queue 512             ; outbound messages held for one peer
   :max-strikes 16            ; malformed messages before dropping a peer
   :strike-decay 8})          ; good messages that clear one strike

;; ── a session ───────────────────────────────────────────────────────────────

(defn session
  [peer]
  {:peer peer
   :state :disconnected      ; :disconnected | :connecting | :open | :dropped
   :failures 0
   :strikes 0
   :good-run 0
   :queue []
   :dropped-messages 0
   :next-attempt 0})

(defn backoff-for
  [failures {:keys [base-backoff max-doublings]}]
  (* base-backoff (bit-shift-left 1 (min failures max-doublings))))

(defn may-attempt?
  [s now]
  (and (= :disconnected (:state s)) (>= now (:next-attempt s))))

(defn on-connecting [s] (assoc s :state :connecting))

(defn on-open
  "A connection came up. Failures reset, so the backoff measures CONSECUTIVE
  failures — a peer that flaps once an hour must not end up on an hour-long
  retry interval."
  [s]
  (assoc s :state :open :failures 0))

(defn on-closed
  [s now params]
  (if (= :dropped (:state s))
    s
    ;; The delay uses the count BEFORE this failure, so the first retry waits
    ;; `base-backoff` — which is what that parameter is documented to mean.
    ;; Using the incremented count made the first retry wait twice the stated
    ;; base, which is the kind of drift that turns a tuned constant into a
    ;; misleading one.
    (let [failures (:failures s)]
      (assoc s :state :disconnected
             :failures (inc failures)
             :next-attempt (+ now (backoff-for failures params))
             ;; a closed socket cannot deliver what was queued for it; holding
             ;; it would mean replaying a stale view's votes on reconnect
             :queue []))))

;; ── sending ─────────────────────────────────────────────────────────────────

(defn enqueue
  "Queue an encoded message. Drops the OLDEST when full: in consensus a stale
  vote is worthless and the newest message is the one that matters, so a
  bounded queue that drops the newest would be strictly worse than one that
  drops nothing at all."
  [s msg {:keys [max-queue] :as _params}]
  (if (= :dropped (:state s))
    s
    (let [q (conj (:queue s) msg)]
      (if (> (count q) max-queue)
        (-> s
            (assoc :queue (vec (drop (- (count q) max-queue) q)))
            (update :dropped-messages + (- (count q) max-queue)))
        (assoc s :queue q)))))

(defn drain
  "Everything queued, and a session with an empty queue. Returns
  `[messages session]`. Only an open session drains — draining a connecting
  one would send into a socket that is not there yet."
  [s]
  (if (= :open (:state s))
    [(:queue s) (assoc s :queue [])]
    [[] s]))

;; ── misbehaviour ────────────────────────────────────────────────────────────

(defn on-bad-message
  "A message that `inga.wire/decode` refused. Past the threshold the peer is
  dropped: a peer sending garbage is broken or hostile, and either way the
  replica should stop spending on it."
  [s {:keys [max-strikes] :as _params}]
  (let [strikes (inc (:strikes s))]
    (cond-> (assoc s :strikes strikes :good-run 0)
      (>= strikes max-strikes) (assoc :state :dropped :queue []))))

(defn on-good-message
  "Strikes decay on sustained good behaviour, so a peer that glitched once —
  a truncated frame, a version skew during a deploy — is not condemned
  forever. Requiring a RUN of good messages rather than a single one is what
  stops a peer from alternating garbage and greetings indefinitely."
  [s {:keys [strike-decay] :as _params}]
  (let [run (inc (:good-run s))]
    (if (and (>= run strike-decay) (pos? (:strikes s)))
      (assoc s :strikes (dec (:strikes s)) :good-run 0)
      (assoc s :good-run run))))

(defn dropped? [s] (= :dropped (:state s)))

;; ── a peer set ──────────────────────────────────────────────────────────────

(defn peer-set
  [peers]
  (into {} (map (juxt identity session)) peers))

(defn due-for-attempt
  "Peers to dial now, in a deterministic order. Sorted because two replicas
  dialling in different orders is harmless but two REPLAYS of the same replica
  doing so is not — the same reason every fold over accounts in torihiki
  sorts first."
  [peers now]
  (->> peers
       (filter (fn [[_ s]] (may-attempt? s now)))
       (map key)
       sort
       vec))

(defn live-peers
  [peers]
  (->> peers (filter (fn [[_ s]] (= :open (:state s)))) (map key) sort vec))

(defn broadcast
  "Queue `msg` for every live peer. Returns the updated peer map.

  Queues rather than sends: what a driver does with a queued message is its
  business, and a policy layer that called a socket would be untestable
  without one."
  [peers msg params]
  (reduce-kv (fn [acc p s]
               (assoc acc p (if (= :open (:state s)) (enqueue s msg params) s)))
             {}
             peers))
