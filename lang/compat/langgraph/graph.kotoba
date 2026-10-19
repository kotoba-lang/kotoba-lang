(ns langgraph.graph
  "StateGraph — LangGraph-style graph orchestration in portable Clojure.

  Model:
    - shared state = a map
    - channels declare per-key reducers, e.g.
        {:messages {:reducer into :default []}}
      (default reducer = last-write-wins)
    - nodes are Runnables: state → partial state update map
    - edges are static, or conditional via a router fn
    - execution is a Pregel-style superstep loop with a recursion
      limit, optional checkpointing per superstep, and
      interrupt-before/after for human-in-the-loop

  Single-threaded by design (WASM premise): nodes in one superstep run
  sequentially in insertion order; their updates are folded through
  the channel reducers in that order."
  (:require [langchain.runnable :as r]
            [langgraph.checkpoint :as cp]))

(def START :langgraph/start)
(def END :langgraph/end)

;; ───────────────────────── building ─────────────────────────

(defn state-graph
  "Creates a graph builder. opts: {:channels {key {:reducer f :default v}}}"
  [& [{:keys [channels]}]]
  {:nodes (array-map)
   :edges {}
   :conditional {}
   :channels (or channels {})})

(defn add-node [g name runnable]
  (update g :nodes assoc name runnable))

(defn add-edge [g from to]
  (update-in g [:edges from] (fnil conj []) to))

(defn add-conditional-edges
  "Router fn: state → node-name | END | seq of node-names. An optional
  path-map translates router outputs to node names."
  ([g from router] (add-conditional-edges g from router nil))
  ([g from router path-map]
   (assoc-in g [:conditional from] {:router router :path-map path-map})))

(defn set-entry-point [g node] (add-edge g START node))
(defn set-finish-point [g node] (add-edge g node END))

;; ───────────────────────── state & channels ─────────────────────────

(defn- initial-state [channels]
  (reduce-kv (fn [m k spec]
               (if (contains? spec :default) (assoc m k (:default spec)) m))
             {} channels))

(defn apply-updates
  "Folds a partial update map into state through the channel reducers."
  [channels state updates]
  (reduce-kv (fn [state k v]
               (let [reducer (get-in channels [k :reducer] (fn [_ new] new))]
                 (update state k #(reducer % v))))
             state updates))

;; ───────────────────────── execution ─────────────────────────

(defn- successors [g node state]
  (if-let [{:keys [router path-map]} (get-in g [:conditional node])]
    (let [out (router state)
          out (if (sequential? out) out [out])
          out (if path-map (map #(get path-map % %) out) out)]
      (vec (remove #{END} out)))
    (vec (remove #{END} (get-in g [:edges node] [])))))

(defn- save! [checkpointer thread-id ckpt]
  (when (and checkpointer thread-id)
    (cp/put! checkpointer thread-id ckpt)))

(defn- run-loop
  "Core superstep loop. Returns
  {:state .. :events [..] :status :done|:interrupted :frontier ..}."
  [{:keys [nodes channels] :as g}
   {:keys [checkpointer thread-id interrupt-before interrupt-after recursion-limit on-event]
    :or {recursion-limit 25}}
   state frontier step0 resume?]
  (let [interrupt-before (set interrupt-before)
        interrupt-after (set interrupt-after)]
    ;; skip-before?: on resume the saved frontier is exactly the node we
    ;; interrupted before — don't re-trigger the interrupt on it.
    (loop [state state, frontier frontier, step step0, events [], skip-before? resume?]
      (cond
        (empty? frontier)
        (do (save! checkpointer thread-id
                   {:step step :state state :frontier [] :status :done})
            {:state state :events events :status :done :frontier []})

        (> step recursion-limit)
        (throw (ex-info "Recursion limit reached"
                        {:limit recursion-limit :state state :frontier frontier}))

        (and (not skip-before?) (some interrupt-before frontier))
        (do (save! checkpointer thread-id
                   {:step step :state state :frontier frontier :status :interrupted})
            {:state state :events events :status :interrupted :frontier frontier})

        :else
        (let [;; run the superstep
              [state' next-frontier events' hit-after]
              (reduce
               (fn [[state nf events hit] node]
                 (let [f (get nodes node)
                       _ (when-not f (throw (ex-info "Unknown node" {:node node})))
                       updates (r/invoke f state)
                       state' (if (map? updates)
                                (apply-updates channels state updates)
                                state)
                       event {:node node :updates updates :state state' :step step}
                       _ (when on-event (on-event event))]
                   [state'
                    (into nf (successors g node state'))
                    (conj events event)
                    (or hit (when (interrupt-after node) node))]))
               [state [] events nil]
               frontier)
              next-frontier (vec (distinct next-frontier))]
          (if hit-after
            (do (save! checkpointer thread-id
                       {:step (inc step) :state state' :frontier next-frontier
                        :status :interrupted})
                {:state state' :events events' :status :interrupted
                 :frontier next-frontier})
            (do (save! checkpointer thread-id
                       {:step (inc step) :state state' :frontier next-frontier
                        :status :running})
                (recur state' next-frontier (inc step) events' false))))))))

(declare run*)

(defrecord CompiledGraph [graph opts]
  r/IRunnable
  (-invoke [this input run-opts]
    (:state (run* this input run-opts)))
  (-stream [this input run-opts]
    (:events (run* this input run-opts))))

(defn run*
  "Full-result invoke: {:state :events :status :frontier}.
  run-opts: {:thread-id .. :resume? bool ..} merged over compile opts.

  Resume semantics: when input is nil (or :resume? true) and the
  thread's latest checkpoint is :interrupted, execution continues
  from the saved frontier.

  Concurrent-resume safety: if `checkpointer` implements
  ClaimableCheckpointer (e.g. `mem-checkpointer`), resuming first
  atomically claims the right to do so -- without this, two concurrent
  resume calls on the SAME thread-id could both read the same
  :interrupted checkpoint and both run the interrupt-before-gated
  node, silently defeating interrupt-before's entire purpose as a
  human-in-the-loop safety gate (confirmed empirically: 10 concurrent
  resumes on one thread-id ran the gated node 5 times). A checkpointer
  that does NOT implement ClaimableCheckpointer (any Checkpointer
  written before this existed, including langgraph.checkpoint/
  datomic-checkpointer and langgraph.kg-checkpoint's own reify) keeps
  the exact prior (racy-under-concurrency) behavior unchanged -- a
  known, tracked gap for those backends, not silently papered over."
  [cg input run-opts]
  (let [{:keys [graph opts]} cg
        opts (merge opts run-opts)
        {:keys [checkpointer thread-id]} opts
        latest (when (and checkpointer thread-id)
                 (cp/get-latest checkpointer thread-id))
        resume? (and latest
                     (= :interrupted (:status latest))
                     (or (nil? input) (:resume? opts)))]
    (if resume?
      (do
        (when (satisfies? cp/ClaimableCheckpointer checkpointer)
          (when-not (cp/claim-resume! checkpointer thread-id latest)
            (throw (ex-info "Concurrent resume: another caller already claimed this thread"
                            {:thread-id thread-id :step (:step latest)}))))
        (let [state (if (map? input)
                      (apply-updates (:channels graph) (:state latest) input)
                      (:state latest))]
          (run-loop graph opts state (:frontier latest) (:step latest) true)))
      (let [base (initial-state (:channels graph))
            ;; continue an existing (done) thread's state if present
            base (if latest (merge base (:state latest)) base)
            state (if (map? input)
                    (apply-updates (:channels graph) base input)
                    base)
            entry (vec (remove #{END} (get-in graph [:edges START] [])))]
        (when (empty? entry)
          (throw (ex-info "No entry point — call set-entry-point" {})))
        (run-loop graph opts state entry (or (:step latest) 0) false)))))

(defn compile-graph
  "opts: {:checkpointer cp :interrupt-before #{..} :interrupt-after #{..}
          :recursion-limit n}"
  ([g] (compile-graph g {}))
  ([g opts] (->CompiledGraph g opts)))

(defn invoke
  "Runs the graph to completion (or interrupt); returns final state."
  ([cg input] (invoke cg input {}))
  ([cg input run-opts] (:state (run* cg input run-opts))))

(defn stream
  "Runs the graph; returns the superstep events
  [{:node .. :updates .. :state .. :step ..} …]."
  ([cg input] (stream cg input {}))
  ([cg input run-opts] (:events (run* cg input run-opts))))

(defn get-state
  "Latest checkpointed state for a thread."
  [cg thread-id]
  (let [cpr (get-in cg [:opts :checkpointer])]
    (cp/get-latest cpr thread-id)))

(defn update-state!
  "Human-in-the-loop edit: applies updates to the latest checkpoint of
  the thread (through channel reducers) and saves a new checkpoint."
  [cg thread-id updates]
  (let [cpr (get-in cg [:opts :checkpointer])
        latest (or (cp/get-latest cpr thread-id)
                   (throw (ex-info "No checkpoint for thread" {:thread-id thread-id})))
        state' (apply-updates (get-in cg [:graph :channels]) (:state latest) updates)]
    (cp/put! cpr thread-id (assoc latest
                                  :step (inc (:step latest))
                                  :state state'))))
