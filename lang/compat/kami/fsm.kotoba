(ns kami.fsm
  "L3 — data-first finite-state machine: a transition table + a pure `advance`
  walker. State is a keyword; transitions are `{:from :event :to :guard? :on-enter?}`
  rows. `advance` reduces over pending events and returns the new state + any
  emitted actions (from :on-enter hooks).

  This is the CLJC mirror of the per-frame FSM walkers the Rust `kami-game`
  crate used to hard-code. It runs on small config-shaped data (a transition
  table + a handful of pending events per entity per frame), never per-element,
  so it is wasmi-safe to compile via kotoba-clj and run as CLJ→WASM on
  iOS/console. The hot per-entity loop is the *host's* job (a `defsystem` that
  declares the query and dispatches `advance` once per entity, not an inner
  CLJ loop). See ADR-0040 / clj-wgsl-ledger Phase 1.2.

  Pure — no IO, no ECS, no GPU. A system threads `(world dt)` and calls
  `advance` per entity; this namespace only owns the table walker.")

(defn fsm
  "Build a finite-state machine from a sequence of transition maps.
  Each row: {:from <state> :event <event> :to <state> :guard? fn :on-enter? fn}.
  `:initial` is the starting state (default :idle). Returns an opaque table."
  ([rows] (fsm rows {:initial :idle}))
  ([rows opts]
   (let [initial (:initial opts :idle)]
     {:initial initial
      :rows   (mapv #(merge {:guard (constantly true)} %) rows)})))

(defn- matching-row
  "Find the first row matching `state` + `event` whose :guard (if any) holds."
  [{:keys [rows]} state event ctx]
  (reduce
   (fn [_ row]
     (when (and (= (:from row) state)
                (= (:event row) event)
                ((:guard row (constantly true)) ctx))
       (reduced row)))
   nil rows))

(defn advance
  "Advance `machine` from `state` by one `event` (with optional `ctx` map passed
  to guards / on-enter hooks). Returns:
    {:state <new-state> :actions [<hook results>] :transitioned? bool}
  If no row matches (or guard fails), state is unchanged and :transitioned? is
  false. Pure — the on-enter hook is called only for its return value (an
  'action'), never for side effects."
  ([machine state event] (advance machine state event {}))
  ([machine state event ctx]
   (if-let [row (matching-row machine state event ctx)]
     (let [to       (:to row)
           on-enter (:on-enter row)
           actions  (when on-enter [(on-enter ctx)])]
       {:state to :actions (vec actions) :transitioned? true})
     {:state state :actions [] :transitioned? false})))

(defn advance-seq
  "Reduce `machine` over a sequence of `[event ctx]` pairs (or bare `event`s)
  starting from `state`. Returns the final {:state :actions} after threading
  each event through `advance` (appending actions). The canonical per-frame
  walker shape: one entity's pending events for this tick."
  ([machine state events] (advance-seq machine state events {}))
  ([machine state events default-ctx]
   (reduce
    (fn [{:keys [state] :as acc} ev-spec]
      (let [[event ctx] (cond
                          (map? ev-spec)   [(:event ev-spec) (merge default-ctx ev-spec)]
                          (vector? ev-spec) ev-spec
                          :else            [ev-spec default-ctx])
            step (advance machine state event ctx)]
        (-> acc
            (assoc :state (:state step))
            (update :actions into (:actions step)))))
    {:state state :actions []}
    events)))

(defn reachable-states
  "All states reachable from `machine`'s initial state (BFS over transition
  rows). Useful for linting a scene's FSM table — mirrors the 'unknown state'
  lint `kami-scene` does for enums."
  [{:keys [initial rows]}]
  (loop [frontier [initial] seen #{initial}]
    (if (empty? frontier)
      seen
      (let [neighbors (->> rows
                           (filter #(some #{(first frontier)} [(:from %)]))
                           (map :to)
                           (remove (partial contains? seen)))]
        (recur (concat (rest frontier) neighbors) (into seen neighbors))))))
