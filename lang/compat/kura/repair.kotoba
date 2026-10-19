(ns kura.repair
  "When to rebuild, in what order, and reading from where.

  The shard-level question — *given these erasures, what do I read* — belongs
  to `erasure.lrc` and is not re-answered here. What this namespace owns is
  everything the code cannot know: which losses are real rather than
  transient, how close a group is to the cliff, and which group to fix first
  when there are more than there is bandwidth for.

  **Grace before repair.** A node that stopped answering thirty seconds ago
  has usually not lost anything. Repairing on first miss turns a rolling
  restart into a network-wide rebuild, and rebuild traffic is the cost the
  whole local-parity design exists to minimise — spending it on nodes that
  are about to come back is the most expensive possible mistake. So a missing
  shard becomes a repairable shard only after `:grace-seconds`, and until then
  the group is *degraded*, which is a different state with a different
  response (serve reads around it, do not move bytes).

  **Margin, not count, sets priority.** Two groups each missing three shards
  are not equally urgent if one of them lost three shards from one local group
  and the other lost three spread out. What matters is how many further losses
  the group survives, which is a question for the code, so `margin` asks it
  by search rather than assuming the worst case applies uniformly."
  (:require [erasure.codec :as codec]
            [erasure.lrc :as lrc]))

(defn policy
  "Repair policy.

  `:grace-seconds` — how long a shard may be unreachable before it counts as
  lost. `:repair-threshold` — margin at or below which a group is queued for
  repair; a group with more margin than this is healthy enough to wait.
  `:parallel-repairs` — how many groups may be in flight at once."
  [{:keys [grace-seconds repair-threshold parallel-repairs]
    :or {grace-seconds 900 repair-threshold 3 parallel-repairs 8}}]
  {:grace-seconds grace-seconds
   :repair-threshold repair-threshold
   :parallel-repairs parallel-repairs})

(defn- lost?
  "Whether a shard's holder has been unreachable long enough to count."
  [{:keys [grace-seconds]} now {:keys [state unreachable-since]}]
  (case state
    :lost true
    :live false
    :unreachable (and (some? unreachable-since)
                      (>= (- now unreachable-since) grace-seconds))
    true))

(defn assess
  "The state of one placement group.

  `shard-states` maps shard index -> `{:state :live|:unreachable|:lost
  :unreachable-since ts}`. Returns the erased set (post-grace), the degraded
  set (unreachable but still inside grace), whether the group is currently
  readable, and its margin."
  [layout pol now shard-states]
  (let [erased (into (sorted-set)
                     (keep (fn [[i s]] (when (lost? pol now s) i)))
                     shard-states)
        degraded (into (sorted-set)
                       (keep (fn [[i s]]
                               (when (and (= :unreachable (:state s))
                                          (not (lost? pol now s)))
                                 i))
                             shard-states))
        plan (lrc/recovery-plan layout erased)]
    {:erased erased
     :degraded degraded
     :readable? (:recoverable? plan)
     ;; Readable right now counts the degraded shards as gone too: a read
     ;; that has to wait out a grace period is not a read that succeeded.
     :readable-now? (:recoverable?
                     (lrc/recovery-plan layout
                                        (into erased degraded)))
     :plan plan}))

(defn margin
  "How many FURTHER arbitrary losses this group is GUARANTEED to survive.

  `tolerated - |erased|`, floored at zero. This is a lower bound, and it is
  the bound scheduling should use: the code tolerates any `tolerated`
  erasures, so with `e` already gone any further `tolerated - e` are still
  decodable regardless of which shards they hit.

  The true margin of a particular pattern can be higher — `erasure`'s measured
  distance says 1,464 of the 1,562,275 eight-shard patterns fail, so most
  eight-shard losses are still fine. Scheduling on the optimistic number would
  mean betting a group's durability on which specific shards die next.
  `exact-margin` computes the real figure when a diagnostic wants it."
  [layout erased]
  (max 0 (- (lrc/max-tolerated-erasures layout) (count erased))))

(defn- subsets
  "All `t`-subsets of `xs`."
  [xs t]
  (cond
    (zero? t) [[]]
    (< (count xs) t) []
    :else (let [[h & more] xs]
            (concat (map #(cons h %) (subsets more (dec t)))
                    (subsets more t)))))

(defn exact-margin
  "The true number of further losses this exact pattern survives, by search.

  Exponential in `limit` — a diagnostic and a test oracle, not something the
  scheduler calls per group per tick. `limit` caps the search depth and is
  returned as-is when the group survives everything up to it."
  ([layout erased] (exact-margin layout erased 3))
  ([layout erased limit]
   (let [alive (vec (remove (set erased) (range (:n layout))))]
     (loop [t 0]
       (if (or (> t limit) (> t (count alive)))
         limit
         (if (every? #(codec/decodable? layout (into (set erased) %))
                     (subsets alive t))
           (recur (inc t))
           (dec t)))))))

(defn urgency
  "Sort key for the repair queue: smaller is more urgent.

  Unreadable groups come first (they are already a durability event, not a
  risk), then by margin, then by how many of the losses are repairable
  locally — those are cheap to fix, and clearing them frees the bandwidth the
  expensive global rebuilds need."
  [layout assessment]
  (let [local-steps (count (filter #(= :local (:op %)) (:steps (:plan assessment))))]
    [(if (:readable? assessment) 1 0)
     (margin layout (:erased assessment))
     (- local-steps)]))

(defn queue
  "Groups needing repair, most urgent first, capped at `:parallel-repairs`.

  `assessments` maps group id -> the map `assess` returned."
  [layout pol assessments]
  (->> assessments
       (filter (fn [[_ a]]
                 (and (seq (:erased a))
                      (<= (margin layout (:erased a)) (:repair-threshold pol)))))
       (sort-by (fn [[g a]] (conj (urgency layout a) g)))
       (take (:parallel-repairs pol))
       (mapv (fn [[g a]] {:group g
                          :erased (:erased a)
                          :margin (margin layout (:erased a))
                          :readable? (:readable? a)
                          :steps (:steps (:plan a))}))))

(defn read-sources
  "Turn a plan's shard read set into the nodes to actually fetch from.

  `shard->node` is `kura.placement/assign`'s output for the group."
  [plan shard->node]
  (into (sorted-map)
        (keep (fn [i] (when-let [n (get shard->node i)] [i n])))
        (:reads plan)))

(defn policy-headroom
  "Whether a placement policy's domain caps are survivable under `layout`.

  `kura.placement/policy` deliberately does not enforce this — the honest
  answer needs the code, and the code is a parameter. Reports, per domain
  kind, the cap and whether losing one whole domain value stays inside the
  code's tolerance. A cap ABOVE tolerance means a single rack or operator can
  take the object out, which is precisely the correlated failure the storage
  multiplier in ADR-2607299200 section 1 assumes away."
  [layout {:keys [caps max-intermittent]}]
  (let [tol (lrc/max-tolerated-erasures layout)
        report (fn [cap] {:cap cap
                          :tolerated tol
                          :survivable? (<= cap tol)
                          ;; A cap equal to tolerance survives, with zero
                          ;; margin for anything else failing at the same time.
                          :margin-after-domain-loss (- tol cap)})]
    (cond-> (into (sorted-map) (map (fn [[kind cap]] [kind (report cap)])) caps)
      ;; Availability is reported alongside the domain caps because it is the
      ;; same arithmetic, but it answers a different question: the domains ask
      ;; *can the object survive losing a rack*, and this asks *can it be read
      ;; right now, with every laptop in the fleet asleep*. A fleet can pass
      ;; every domain cap and still be unreadable on a Sunday.
      (some? max-intermittent)
      (assoc :availability
             (assoc (report max-intermittent)
                    :readable-from-always-on-alone? (<= max-intermittent tol)
                    :note (if (<= max-intermittent tol)
                            (str "at most " max-intermittent " shards may sleep, "
                                 "within the code's tolerance of " tol
                                 " — always-on nodes alone can serve a read")
                            (str max-intermittent " shards may sleep but the code "
                                 "tolerates only " tol ": a read can require "
                                 "waking somebody's laptop, which is not a "
                                 "durability property and cannot be promised")))))))
