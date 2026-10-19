(ns bpmn.execute
  "A pure, token-based interpreter for a BPMN-as-EDN model. State is plain data so a
  run is fully inspectable, replayable and testable offline with fixture ports.

  Tokens are advanced one at a time (`advance`); `run` folds `advance` to quiescence.
  A token is `{:bpmn/at node-id :bpmn/via flow-id}` (the flow it arrived on, so an
  AND-join can tell which incoming branches have delivered). Process variables live
  in one global `:bpmn/vars` map, mutated only by `IActivity/perform`.

  Gateway semantics:
    exclusive    — first outgoing whose condition is truthy, else the `:bpmn/default`
                   flow, else the first unconditioned flow (XOR-join just passes each
                   token through its single outgoing).
    parallel     — AND-join + AND-split: wait until a token has arrived on *every*
                   incoming flow, then emit one on *every* outgoing.
    inclusive    — OR-split: emit on every outgoing whose condition is truthy (or
                   every outgoing if none are, so a token is never silently lost).
                   NOTE: OR-*join* is treated as pass-through.
    event-based  — exactly one outgoing flow is taken, never a fan-out (per spec, an
                   event-based gateway's competing outgoing branches are mutually
                   exclusive — the first to trigger wins and the rest are discarded).
                   This interpreter has no event-triggering/racing model, so it
                   deterministically takes the first outgoing flow; this at least
                   preserves the single-token cardinality the spec requires.
    complex      — same condition-evaluation as inclusive (truthy conditions, else
                   default, else every outgoing), since no dedicated activation-
                   condition data is modeled for this gateway type.
  A non-gateway node performs its activity then fans a token onto each outgoing flow
  (0 → the token ends, 1 → moves, >1 → uncontrolled parallel split). An end event
  performs then consumes its token."
  (:require [kotoba.lang.text :as str]
            [bpmn.model :as m]
            [bpmn.ports :as p]))

(defn start
  "Initial run state: one token at each start event."
  ([model] (start model {}))
  ([model vars]
   {:bpmn/vars (or vars {})
    :bpmn/tokens (mapv (fn [se] {:bpmn/at (:bpmn/id se) :bpmn/via nil})
                       (m/start-events model))
    :bpmn/arrived {}                       ; gateway-id → #{arrived-flow-ids}
    :bpmn/trace []
    :bpmn/done? false}))

(defn completed? [state]
  (and (:bpmn/done? state) (empty? (:bpmn/tokens state))))

(defn- emit [outs] (mapv (fn [f] {:bpmn/at (:bpmn/target f) :bpmn/via (:bpmn/id f)}) outs))

(defn- inclusive-taken
  "Flows an OR-style gateway (:inclusive-gateway/:complex-gateway) takes: every
  outgoing whose condition is truthy, else the :bpmn/default flow, else every
  outgoing (never silently drop the token)."
  [ports model node outs vars]
  (let [taken (filter #(or (nil? (:bpmn/condition %))
                           (p/truthy? (:condition ports) (:bpmn/condition %) vars))
                      outs)]
    (cond
      (seq taken)           taken
      (:bpmn/default node)  (some->> (:bpmn/default node) (m/flow model) vector)
      :else                 outs)))

(defn advance
  "Advance exactly one token by one node. Returns the next state."
  [ports model state]
  (let [tokens (:bpmn/tokens state)]
    (if (empty? tokens)
      (assoc state :bpmn/done? true)
      (let [tok    (nth tokens 0)
            others (subvec tokens 1)
            nid    (:bpmn/at tok)
            node   (m/node model nid)
            t      (:bpmn/type node)
            outs   (m/outgoing model nid)
            vars   (:bpmn/vars state)
            log    (fn [st ev] (update st :bpmn/trace conj (assoc ev :bpmn/at nid)))]
        (cond
          (= t :end-event)
          (-> state
              (assoc :bpmn/tokens others
                     :bpmn/vars (p/perform (:activity ports) node vars))
              (log {:bpmn/event :end}))

          (= t :parallel-gateway)
          (let [ins      (m/incoming model nid)
                arrived' (conj (get-in state [:bpmn/arrived nid] #{}) (:bpmn/via tok))]
            (if (>= (count arrived') (max 1 (count ins)))
              (-> state
                  (assoc :bpmn/tokens (into others (emit outs)))
                  (assoc-in [:bpmn/arrived nid] #{})
                  (log {:bpmn/event :parallel-fire}))
              (-> state
                  (assoc :bpmn/tokens others)
                  (assoc-in [:bpmn/arrived nid] arrived')
                  (log {:bpmn/event :parallel-wait}))))

          (= t :exclusive-gateway)
          (let [chosen (or (first (filter #(and (:bpmn/condition %)
                                                (p/truthy? (:condition ports)
                                                           (:bpmn/condition %) vars))
                                          outs))
                           (some->> (:bpmn/default node) (m/flow model))
                           (first (remove :bpmn/condition outs))
                           (first outs))]
            (-> state
                (assoc :bpmn/tokens (into others (emit (when chosen [chosen]))))
                (log {:bpmn/event :exclusive :bpmn/flow (:bpmn/id chosen)})))

          (= t :inclusive-gateway)
          (-> state
              (assoc :bpmn/tokens (into others (emit (inclusive-taken ports model node outs vars))))
              (log {:bpmn/event :inclusive}))

          (= t :complex-gateway)
          (-> state
              (assoc :bpmn/tokens (into others (emit (inclusive-taken ports model node outs vars))))
              (log {:bpmn/event :complex}))

          (= t :event-based-gateway)
          ;; Exactly one outgoing flow, never a fan-out -- see the namespace
          ;; docstring's "event-based" note. Falling through to the generic
          ;; :else clause here would emit(outs) onto EVERY competing branch,
          ;; duplicating the token across mutually-exclusive paths.
          (-> state
              (assoc :bpmn/tokens (into others (emit (when (seq outs) [(first outs)]))))
              (log {:bpmn/event :event-based :bpmn/flow (:bpmn/id (first outs))}))

          :else                                    ; start / activity / event
          (-> state
              (assoc :bpmn/vars (p/perform (:activity ports) node vars)
                     :bpmn/tokens (into others (emit outs)))
              (log {:bpmn/event (if (seq outs) :pass :implicit-end)})))))))

(defn run
  "Fold `advance` until no tokens remain (or `max-steps`, a runaway guard, is hit;
  then `:bpmn/error :step-limit` is set)."
  ([ports model] (run ports model (start model) 10000))
  ([ports model state] (run ports model state 10000))
  ([ports model state max-steps]
   (loop [st state n 0]
     (cond
       (empty? (:bpmn/tokens st)) (assoc st :bpmn/done? true)
       (>= n max-steps)           (assoc st :bpmn/error :step-limit :bpmn/done? false)
       :else                      (recur (advance ports model st) (inc n))))))

;; --- a host-free default so any model is runnable out of the box ---

(defn default-ports
  "Activities are no-ops (vars unchanged); a condition is read as the boolean process
  variable it names — `${approved}`, `approved`, `= approved` all look up
  `(:approved vars)`. Enough to exercise control flow; replace for real work."
  []
  {:activity  (reify p/IActivity (perform [_ _ vars] vars))
   :condition (reify p/ICondition
                (truthy? [_ condition vars]
                  (let [k (-> (str condition) (str/replace #"[${}=\s]" ""))]
                    (boolean (get vars (keyword k))))))})
