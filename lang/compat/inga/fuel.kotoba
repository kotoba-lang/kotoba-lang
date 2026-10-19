(ns inga.fuel
  "F2 — metered execution, where running out is a STATE TRANSITION and not an
  exception.

  ## The bug class this closes

  A consensus machine seam that takes an arbitrary `apply-fn` has no bound on
  the work a block can cause and no guarantee two replicas agree on what that
  work was. engi hit the second half of this for real (its ADR-2608022600): a
  machine map holding a ready-made order book handed every replica the same
  mutable structure, and four replicas that agreed on 123 committed blocks
  disagreed by 200 resting orders. That was found by a test. The class it
  belongs to is not.

  Filecoin's FVM answers this by making actor code WASM with every operation
  metered by gas, so determinism is a property of the VM rather than of each
  actor's care. Kotoba already has the same primitive: the compiler's native
  backends (`backend/x86_64.cljc`, `backend/aarch64.cljc`) implement fuel
  accounting, and capabilities are deny-by-default (superproject
  ADR-2607198300, ADR-2608010930 D4).

  ## The one rule that makes metering safe for consensus

  **Exhaustion must be a value in the state, never a thrown exception.**

  A replica that throws has left the protocol: it produces no state and no
  root, while its peers produce both. So `apply-metered` never throws on
  running out — it stops, records where it stopped, and that record is part
  of the state the root commits to. Two replicas that exhaust at the same op
  agree; a replica that somehow exhausts elsewhere produces a DIFFERENT root
  and is visibly wrong, which is the whole point of committing to a root.

  Determinism here depends on exactly three things — the budget, the cost
  function, and the order of ops — and on nothing else. No clock, no
  allocation counting, no host measurement. Anything that a second
  implementation could reasonably compute differently is not allowed to
  influence the result.

  ## What this namespace is NOT

  It is not a `.kotoba` machine yet. ADR-2608038000 F2's endpoint is the
  machine body compiled to fuel-metered Kotoba; today the compiler's
  capability kits are `:reference :implemented` with `:wasm-aot`/`:native-aot`
  pending, and there is no fs/process capability or Kotoba script host to run
  a build from. So this establishes the metering CONTRACT and the determinism
  property at the seam, which is what consensus actually needs, and the
  remaining step is named rather than implied.")

(defn fixed-cost
  "The simplest honest cost function: every op costs the same. A caller with
  a real cost model passes its own; what must not happen is a cost that
  depends on anything a peer cannot recompute."
  [n]
  (fn [_op] n))

(defn by-op-kind
  "Cost from a table keyed by `:op`, with a required default so an unknown op
  can never cost zero by accident — a free op is an unmetered op, which is
  the hole metering exists to close."
  [table default]
  (when-not (and (map? table) (nat-int? default))
    (throw (ex-info "inga.fuel/by-op-kind: table must be a map and default a non-negative int"
                    {:type :inga.fuel/invalid-schedule})))
  (fn [op] (get table (:op op) default)))

(defn apply-metered
  "Apply `ops` under `budget`, charging `cost-fn` per op before it runs.

  Returns `{:state s' :spent n :applied n :exhausted-at idx-or-nil :dropped n}`.

  `step` is `(fn [state op] -> state)`. It is called ONLY for ops that were
  paid for, so a caller cannot accidentally observe a half-charged effect.

  Charging BEFORE the op, and refusing an op whose cost does not fit, is what
  keeps the boundary crisp: `spent` never exceeds `budget`, so `budget` is a
  real ceiling rather than an approximate one. The alternative — run, then
  charge, then notice — lets one expensive op overrun by an unbounded amount,
  and 'unbounded' is not a quantity two replicas can agree on.

  ## `:expansion-cost-fn` — for an op that is not the whole of what it does

  Optional `(fn [state op] -> nat-int)`, added to `cost-fn`'s answer for the
  same op. It exists because an op can expand into more work whose size is a
  function of the state it lands on: `inga.state`'s actor ops emit datoms, and
  how many depends on whether that address already held a record.

  It is charged as ONE unit with the op rather than as separate ops on their
  own. That is the whole point — the expansion has to be atomic with what
  produced it, and metering them separately would let a block run out of fuel
  BETWEEN an actor mutation and its projection, which is a state no replica
  should be able to commit.

  `cost-fn` keeps its one-argument shape. A cost that depends on state is the
  exception, and making every caller take an argument it ignores would spread
  that exception over all of them."
  [{:keys [state ops budget cost-fn step expansion-cost-fn]}]
  (when-not (and (nat-int? budget) (ifn? cost-fn) (ifn? step))
    (throw (ex-info "inga.fuel/apply-metered: budget must be a non-negative int, cost-fn and step callable"
                    {:type :inga.fuel/invalid-args})))
  (when (and (some? expansion-cost-fn) (not (ifn? expansion-cost-fn)))
    (throw (ex-info "inga.fuel/apply-metered: :expansion-cost-fn is not callable"
                    {:type :inga.fuel/invalid-args})))
  (loop [s state, remaining (seq ops), spent 0, applied 0, idx 0]
    (if-not remaining
      {:state s :spent spent :applied applied :exhausted-at nil :dropped 0}
      (let [op (first remaining)
            expansion (if expansion-cost-fn (expansion-cost-fn s op) 0)
            _ (when-not (nat-int? expansion)
                (throw (ex-info "inga.fuel: expansion-cost-fn returned a non-integer cost"
                                {:type :inga.fuel/invalid-cost :op op :cost expansion})))
            cost (+ (cost-fn op) expansion)]
        (when-not (nat-int? cost)
          (throw (ex-info "inga.fuel: cost-fn returned a non-integer cost"
                          {:type :inga.fuel/invalid-cost :op op :cost cost})))
        (if (> (+ spent cost) budget)
          ;; Stop. Not an error — a fact about this block, and one that every
          ;; honest replica computes identically from the same three inputs.
          {:state s :spent spent :applied applied
           :exhausted-at idx :dropped (count remaining)}
          (recur (step s op) (next remaining)
                 (+ spent cost) (inc applied) (inc idx)))))))

(defn record
  "Fold one `apply-metered` result into the state's fuel ledger, so the state
  ROOT commits to it.

  Without this the exhaustion point is not covered by the root, and two
  replicas that stopped at different ops could still produce identical roots
  — which would make the root a worse check than no root at all, because it
  would look like agreement."
  [state height {:keys [spent applied exhausted-at dropped]}]
  (cond-> (assoc state :inga.fuel/last
                 {:height height :spent spent :applied applied})
    exhausted-at
    (update :inga.fuel/exhausted (fnil conj [])
            {:height height :op-index exhausted-at :dropped dropped})))

(defn exhausted?
  [state] (boolean (seq (:inga.fuel/exhausted state))))
