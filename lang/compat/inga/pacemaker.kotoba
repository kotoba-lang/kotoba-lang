(ns inga.pacemaker
  "View change: how a chained-HotStuff replica makes progress when a leader
  does not.

  `inga.consensus` owns the SAFETY rules — quorum arithmetic, the shape of a
  quorum certificate, the three-chain commit rule. Those say what may never
  happen. They say nothing about what must eventually happen, and a protocol
  with only safety rules has a trivial implementation: never propose anything.

  This namespace is the other half, and it is the half that kills home-grown
  BFT. Safety is a handful of comparisons that are easy to get right and easy
  to test. Liveness is a timeout, a certificate, a lock, and an exponential
  backoff, interacting — and the failure mode is not a crash but a chain that
  stops, sometimes only under a partition that lasts long enough.

  ## The lock is the whole safety argument across a view change

  A replica keeps a LOCKED QC: the certificate of the block it has seen two
  consecutive certified descendants of. Having voted to commit on that basis,
  it must not later vote for a branch that excludes it.

  So the vote rule has two clauses, and both are needed:

  - SAFETY: vote for a block that extends the locked QC.
  - LIVENESS: also vote when the block's justification is from a LATER view
    than the lock.

  Without the first, a view change lets a new leader propose a branch that
  drops an already-committed block — two conflicting commits, which is exactly
  the thing the whole protocol exists to prevent. Without the second, a replica
  that locked on a branch which then lost can never vote again, and the chain
  halts with every replica individually safe and collectively stuck.

  The second clause is safe because a QC from a later view proves that a
  quorum — which intersects the quorum that formed the lock in at least one
  honest replica — has already moved on.

  ## Timeouts must grow

  A fixed timeout under a partition produces a view change per timeout
  forever: every view times out, nobody ever assembles a proposal, and the
  chain makes no progress while looking busy. Doubling means the views
  eventually become longer than the message delay, and the chain resumes on
  its own once the network does.

  ## Everything here is a pure function of data

  No clock, no sockets, no timers. `now` is passed in. That is what lets an
  entire n-replica view change, including a partition and its healing, be
  simulated as a value in a test."
  (:require [inga.quorum :as q]))

;; ── view state ──────────────────────────────────────────────────────────────

(defn initial
  "The state a replica starts in. `view` counts view changes, not heights: a
  view can end without producing a block, which is precisely the case this
  namespace exists to handle, so the two cannot be the same number."
  [witness]
  {:witness witness
   :view 0
   :locked-qc nil
   :high-qc nil
   :deadline 0})

(def default-params
  {;; the first view's budget, in the same logical units the caller uses
   :base-timeout 1000
   ;; views past this many consecutive failures stop doubling — an unbounded
   ;; backoff means a chain that recovers from a long partition takes as long
   ;; again to notice
   :max-doublings 6})

(defn timeout-for
  "The budget for `view`, doubling with each consecutive failed view and then
  flattening. Deterministic: two replicas computing a deadline for the same
  view must get the same answer, or they will time out at different points and
  never form a certificate together."
  [failures {:keys [base-timeout max-doublings]}]
  (* base-timeout (bit-shift-left 1 (min failures max-doublings))))

;; ── quorum certificate ordering ─────────────────────────────────────────────

(defn qc-view
  "The view a QC was formed in. A QC from `inga.consensus` carries a height;
  the pacemaker layer attaches the view, because two different views can
  certify blocks at the same height — that is what a view change means."
  [qc]
  (or (:inga.qc/view qc) 0))

(defn higher-qc
  "The later of two QCs, nil-safe. Ties by view are broken by height so the
  choice is total: two replicas that picked differently would carry different
  high QCs into the same view change."
  [a b]
  (cond
    (nil? a) b
    (nil? b) a
    (> (qc-view a) (qc-view b)) a
    (< (qc-view a) (qc-view b)) b
    (>= (or (:inga.qc/height a) 0) (or (:inga.qc/height b) 0)) a
    :else b))

;; ── the vote rule ───────────────────────────────────────────────────────────

(defn extends-locked?
  "Does `block` build on the locked QC? `ancestor?` is supplied by the caller
  — the pacemaker does not own the block store and will not pretend to."
  [state block ancestor?]
  (let [locked (:locked-qc state)]
    (or (nil? locked)
        (boolean (ancestor? (:inga.qc/block-hash locked) block)))))

(defn safe-to-vote?
  "The two-clause rule. See the namespace docstring for why both are needed
  and why the liveness clause does not break safety."
  [state block ancestor?]
  (let [locked (:locked-qc state)
        justify (:inga.block/justify block)]
    (boolean
     (or
      ;; safety: it extends what we are locked on
      (extends-locked? state block ancestor?)
      ;; liveness: a quorum has demonstrably moved past our lock
      (and justify locked (> (qc-view justify) (qc-view locked)))))))

;; ── new-view messages and the timeout certificate ───────────────────────────

(defn new-view
  "What a replica broadcasts when its view times out: the view it is
  abandoning and the highest QC it knows. The QC is the load-bearing part —
  it is how the next leader learns what it must extend."
  [state]
  {:inga.nv/witness (:witness state)
   :inga.nv/view (:view state)
   :inga.nv/high-qc (:high-qc state)})

(defn timeout-certificate
  "A TC from `messages`, or nil below quorum.

  Counts DISTINCT witnesses, for the same reason `inga.consensus/qc` does: a
  Byzantine replica that resends its own new-view must not be able to
  manufacture a view change alone.

  `quorum` is anything `inga.quorum/->predicate` accepts: an integer for a
  managed set, or a predicate — the stake-weighted one under permissionless
  admission, where counting heads is what a Sybil defeats.

  Throws when the messages are not all for the same view — routing two views
  into one certificate is a caller bug, not a Byzantine case, and silently
  accepting it would produce a certificate that means nothing."
  [messages quorum]
  (when (seq messages)
    (let [view (:inga.nv/view (first messages))]
      (when-not (every? #(= view (:inga.nv/view %)) messages)
        (throw (ex-info "pacemaker: all new-view messages must share a view"
                        {:messages messages})))
      (let [distinct-witnesses (set (map :inga.nv/witness messages))]
        (when (q/met? quorum distinct-witnesses)
          {:inga.tc/view view
           :inga.tc/witnesses distinct-witnesses
           ;; the highest QC anyone reported — what the next leader must build
           ;; on, and the reason a view change cannot drop a committed block
           :inga.tc/high-qc (reduce higher-qc nil (map :inga.nv/high-qc messages))})))))

;; ── transitions ─────────────────────────────────────────────────────────────

(defn on-qc
  "Fold a QC a replica has learned about. Advances the high QC always, and the
  LOCK only when the QC is genuinely later — a lock that could move backwards
  is not a lock."
  [state qc]
  (let [high (higher-qc (:high-qc state) qc)
        locked (:locked-qc state)]
    (cond-> (assoc state :high-qc high)
      ;; `nil` lock is distinct from a lock at view 0: view 0 is a legitimate
      ;; first view, and requiring strictly-greater-than against a nil lock
      ;; treated as 0 meant the very first certificate could never lock.
      (or (nil? locked) (> (qc-view qc) (qc-view locked)))
      (assoc :locked-qc qc))))

(defn on-timeout
  "The view expired without a decision. Returns `[state' new-view-message]`."
  [state now failures params]
  (let [state' (-> state
                   (update :view inc)
                   (update :failures (fnil inc 0))
                   (assoc :deadline (+ now (timeout-for failures params))))]
    [state' (new-view state)]))

(defn on-timeout-certificate
  "Enter the view a TC certifies. A replica that was behind jumps forward
  rather than working through the views it missed one timeout at a time —
  which is what a partitioned replica would otherwise have to do, taking as
  long to catch up as it was away."
  [state tc now params]
  (let [view (inc (:inga.tc/view tc))]
    (-> state
        (assoc :view (max (:view state) view))
        (on-qc (:inga.tc/high-qc tc))
        (assoc :failures 0)
        (assoc :deadline (+ now (timeout-for 0 params))))))

(defn enter-at-least
  "Raise the view to `view` if it is behind. Monotone, like every other view
  move here — a view that could go backwards is not a view.

  For the one caller that knows a view was reached without having a message
  that says so: a replica replaying its own chain. A block carries the round
  it was proposed in, and a certified block proves a quorum was at that view,
  so a replica holding it is behind if its own view is lower."
  [state view]
  (cond-> state (> view (:view state)) (assoc :view view :failures 0)))

(defn on-progress
  "A round produced a certified block. The failure count resets, which is what
  makes the backoff track CONSECUTIVE failures rather than total ones — a
  chain that hiccups once an hour should not end up with hour-long views.

  ## Takes the certified block's ROUND, not the certificate's view

  It used to read `(inc (qc-view qc))`, which derives a view from a view: the
  QC's view is the view its VOTES were cast in, so the counter was anchored to
  itself and only moved when the pacemaker moved. Anchoring it to
  `:inga.block/round` — carried in the block the quorum certified — means
  **agreeing on the chain is agreeing on the counter** (superproject
  ADR-2608680000). The caller folds the QC separately; doing it here as well
  was doing it twice."
  [state round now params]
  (-> state
      (update :view max (inc round))
      (assoc :failures 0)
      (assoc :deadline (+ now (timeout-for 0 params)))))

(defn expired?
  [state now]
  (>= now (:deadline state 0)))

(defn leader-for-view
  "Round-robin over views rather than heights, so a failed view moves the
  leadership on. Keyed by view for the same reason the timeout is: a view that
  produced nothing still has to hand over, or a crashed leader keeps being
  re-elected and the chain stops."
  [witnesses view]
  (nth witnesses (mod view (count witnesses))))
