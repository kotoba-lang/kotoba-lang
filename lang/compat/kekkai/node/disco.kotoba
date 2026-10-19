(ns kekkai.node.disco
  "Endpoint discovery, NAT hole punching and path selection — pure state machine.
  Times are **milliseconds** here (latencies are the unit of the decision);
  `noise.session`'s policy is in seconds. The agent passes both explicitly rather
  than sharing one clock value, because mixing the two silently produces a
  session that rekeys every 120 ms.

  The design is Tailscale's disco, reduced to what an EDN state machine can own:

  1. Every peer starts on the **relay** path, which needs no discovery and works
     behind anything. Connectivity first, optimization second — a node is never
     unreachable *because* hole punching is still in progress.
  2. Candidates come from the netmap (stale hints), from our own STUN reflexive
     lookup, and from the peer's `call-me-maybe` over the relay (its current
     hints). All are probed; none are trusted.
  3. A **hole punch** is a simultaneous open: both sides send their candidate
     list through the relay and then ping each other's candidates on the *same
     burst schedule*. Each side's outgoing ping creates the NAT mapping its
     inbound counterpart needs. This is why the schedule is a fixed offset table
     rather than an adaptive backoff — both sides must be firing at once, and
     they only agree if the schedule is a constant.
  4. A direct path that answers becomes the active path. If it stops answering,
     the peer falls back to the relay. Switching between two *direct* paths needs
     a latency margin, or a jittery network makes the node flap.

  What this cannot do, stated plainly: two peers both behind **symmetric** NATs
  (a fresh outside port per destination) cannot be punched — the candidate we
  learn is wrong for us by construction. Those peers stay on the relay forever,
  which is correct behaviour, not a failure. Tailscale's numbers put that at a
  small but real fraction of paths; ours will be whatever this fleet's NATs are,
  and `path-report` is what tells you."
  (:require [kekkai.node.endpoint :as ep]))

(def defaults
  {:probe-timeout-ms 1500
   :max-probe-attempts 4
   :heartbeat-ms 5000        ; keeps a live path's NAT mapping open
   :path-death-ms 15000      ; no pong for this long -> dead, fall back
   :switch-margin-ms 20      ; direct→direct switch hysteresis
   :call-me-maybe-ms 5000    ; re-signal candidates while no direct path is live
   ;; Fixed simultaneous-open burst offsets, in ms from the punch start. Both
   ;; sides use the identical table; that is the mechanism, not a tuning knob.
   :punch-offsets-ms [0 100 300 700 1500 3000 5000]
   :latency-alpha 0.25})     ; EWMA weight for a new sample

(defn new-peer
  "Start tracking a peer. `relay-region` is the fallback path; `candidates` are
   the netmap's hints."
  [{:keys [peer-id relay-region candidates now config]}]
  {:peer-id peer-id
   :config (merge defaults config)
   :relay-region relay-region
   :paths (into {} (map (fn [c] [(ep/ekey c) {:endpoint c :state :unprobed
                                              :attempts 0 :latency-ms nil
                                              :last-ping nil :last-pong nil
                                              :tx-id nil}]))
                (filter ep/direct? (ep/normalize candidates)))
   :active :relay
   :punch nil
   :last-call-me-maybe nil
   :created-at now})

(defn add-candidates
  "Merge freshly learned candidates, keeping the probe state of ones we already
   know (a re-announced candidate we already proved dead should not restart its
   attempt counter, or a peer that spams call-me-maybe would keep us probing a
   dead address forever)."
  [st candidates]
  (update st :paths
          (fn [paths]
            (reduce (fn [ps c]
                      (let [k (ep/ekey c)]
                        (if (contains? ps k)
                          ps
                          (assoc ps k {:endpoint c :state :unprobed :attempts 0
                                       :latency-ms nil :last-ping nil
                                       :last-pong nil :tx-id nil}))))
                    paths
                    (filter ep/direct? (ep/normalize candidates))))))

(defn begin-punch
  "Arm the simultaneous-open schedule. Called when we send *or* receive a
   call-me-maybe, so both sides arm at nearly the same instant."
  [st now]
  (assoc st :punch {:started-at now :sent 0}))

(defn- punch-due?
  "Is a burst offset due that we have not sent yet?"
  [{:keys [punch config]} now]
  (when punch
    (let [offsets (:punch-offsets-ms config)
          elapsed (- now (:started-at punch))
          due (count (take-while #(<= % elapsed) offsets))]
      (when (> due (:sent punch)) due))))

(defn- probe-due? [{:keys [state attempts last-ping]} {:keys [probe-timeout-ms max-probe-attempts heartbeat-ms]} now]
  (case state
    :unprobed true
    :probing (and (< attempts max-probe-attempts)
                  (>= (- now (or last-ping 0)) probe-timeout-ms))
    :live (>= (- now (or last-ping 0)) heartbeat-ms)
    :dead false
    false))

(defn- tx-id [peer-id ekey now attempts]
  ;; Deterministic, unique per (peer, endpoint, attempt): a disco ping needs a
  ;; transaction id so a pong can be matched to the probe that caused it, but a
  ;; random one would make this namespace impure and untestable. Uniqueness, not
  ;; unpredictability, is the requirement — the pong is authenticated by the
  ;; Noise session, not by guessing the id.
  (str peer-id "/" ekey "/" now "/" attempts))

(defn pings-due
  "-> [{:endpoint … :tx-id … :reason :probe|:punch|:heartbeat}]"
  [{:keys [peer-id paths config punch] :as st} now]
  (let [punching (some? (punch-due? st now))]
    (into []
          (keep (fn [[k p]]
                  (when (or punching (probe-due? p config now))
                    {:endpoint (:endpoint p)
                     :tx-id (tx-id peer-id k now (:attempts p))
                     :reason (cond punching :punch
                                   (= :live (:state p)) :heartbeat
                                   :else :probe)})))
          paths)))

(defn record-pings
  "Fold the effect of actually having sent `pings` back into the state."
  [st pings now]
  (let [by-key (into {} (map (juxt (comp ep/ekey :endpoint) identity)) pings)]
    (cond-> (update st :paths
                    (fn [paths]
                      (reduce-kv (fn [ps k p]
                                   (if-let [sent (get by-key k)]
                                     (assoc ps k (-> p
                                                     (assoc :last-ping now
                                                            :tx-id (:tx-id sent))
                                                     (update :attempts inc)
                                                     (update :state #(if (= :live %) :live :probing))))
                                     ps))
                                 paths paths)))
      (punch-due? st now) (update :punch assoc :sent (punch-due? st now)))))

(defn on-pong
  "A peer answered a disco ping from `endpoint`. The pong must have arrived
   inside an authenticated session — an unauthenticated pong would let anyone
   move a peer's path, so the agent only calls this for frames that decrypted."
  [st endpoint now {:keys [tx-id]}]
  (let [k (ep/ekey endpoint)
        alpha (get-in st [:config :latency-alpha])]
    (update-in st [:paths k]
               (fn [p]
                 (let [p (or p {:endpoint endpoint :attempts 1})
                       rtt (max 0 (- now (or (:last-ping p) now)))
                       prev (:latency-ms p)]
                   (assoc p
                          :endpoint endpoint
                          :state :live
                          :last-pong now
                          :matched-tx (= tx-id (:tx-id p))
                          :latency-ms (if prev
                                        (+ (* (- 1 alpha) prev) (* alpha rtt))
                                        rtt)))))))

(defn- live-paths [{:keys [paths config]} now]
  (->> paths
       (filter (fn [[_ p]]
                 (and (= :live (:state p))
                      (< (- now (or (:last-pong p) 0)) (:path-death-ms config)))))
       (sort-by (fn [[_ p]] (or (:latency-ms p) 1e9)))))

(defn expire-dead
  "Mark live paths that stopped answering as dead. Separate from `advice` so the
   caller decides when state changes, and so a test can freeze time."
  [{:keys [config] :as st} now]
  (update st :paths
          (fn [paths]
            (reduce-kv (fn [ps k p]
                         (cond
                           (and (= :live (:state p))
                                (>= (- now (or (:last-pong p) 0)) (:path-death-ms config)))
                           (assoc ps k (assoc p :state :dead))

                           (and (= :probing (:state p))
                                (>= (:attempts p) (:max-probe-attempts config))
                                (>= (- now (or (:last-ping p) 0)) (:probe-timeout-ms config)))
                           (assoc ps k (assoc p :state :dead))

                           :else ps))
                       paths paths))))

(defn best-path
  "The path the agent should be using now: the lowest-latency live direct path,
   or `:relay`. Hysteresis applies only between two direct paths — falling back
   to the relay is immediate, because a dead path means packets are being lost
   right now."
  [{:keys [active config] :as st} now]
  (let [live (live-paths st now)]
    (if (empty? live)
      :relay
      (let [[best-k best] (first live)
            current (get-in st [:paths active])]
        (if (and (not= :relay active)
                 current
                 (= :live (:state current))
                 (< (- (or (:latency-ms current) 1e9) (:switch-margin-ms config))
                    (or (:latency-ms best) 1e9)))
          active            ; keep the current path; the candidate is not enough better
          best-k)))))

(defn call-me-maybe-due?
  "Should we (re)publish our candidates through the relay? While there is no live
   direct path, yes, on an interval — the peer may have restarted, changed
   network, or had its NAT mapping expire."
  [{:keys [config last-call-me-maybe] :as st} now]
  (and (= :relay (best-path st now))
       (>= (- now (or last-call-me-maybe 0)) (:call-me-maybe-ms config))))

(defn record-call-me-maybe [st now]
  (-> st (assoc :last-call-me-maybe now) (begin-punch now)))

(defn on-call-me-maybe
  "The peer told us where it thinks it is. Arm the punch so our bursts line up
   with theirs."
  [st candidates now]
  (-> st (add-candidates candidates) (begin-punch now)))

(defn advice
  "Everything the agent should do for this peer at `now`, as data:

     {:pings […]              disco pings to send
      :switch-to :relay|ekey  the path to use (compare with :active)
      :call-me-maybe? bool}

   The agent applies it with `record-pings` / `record-call-me-maybe` / `activate`
   so that every state transition is a value the tests can inspect."
  [st now]
  (let [st (expire-dead st now)
        best (best-path st now)]
    {:state st
     :pings (pings-due st now)
     :switch-to (when (not= best (:active st)) best)
     :call-me-maybe? (call-me-maybe-due? st now)}))

(defn activate [st path] (assoc st :active path))

(defn path-report
  "Human-facing summary — which peers got a direct path and which are stuck on a
   relay, with latencies. This is the number that says whether hole punching is
   working in this fleet, so it is part of the library rather than left to
   whoever writes the CLI."
  [peers now]
  (mapv (fn [st]
          (let [live (live-paths st now)]
            {:peer (:peer-id st)
              :active (:active st)
              :via (if (= :relay (:active st)) :relay :direct)
              :relay-region (:relay-region st)
              :direct-candidates (count (:paths st))
              :live-direct (count live)
              :best-latency-ms (some-> (first live) second :latency-ms)}))
        peers))
