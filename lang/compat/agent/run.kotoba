(ns agent.run
  "One bounded execution. An agent is invoked with a request, does it, and
  ends.

  An agent neither self-evolves nor self-judges: it does not decide WHAT to
  do — a yakuwari (or an operator) already did — so its bound arrives with
  the invocation rather than having to be imposed on it. That is the whole
  reason this layer needs no lease and no policy of its own: the goal, the
  budget and the capabilities are all fixed before it starts.

  Residency is orthogonal. An agent kept warm on murakumo is still an agent;
  it just does not pay cold start. Being resident changes latency and cost,
  never authority.

  Extracted from `kotoba.tamaki.model` via `ao.run` (kotoba-lang/tamaki
  ADR-0001). Pure and portable: no clock, no storage, no runner — every
  function takes `now-ms` from its caller."
  (:require [clojure.string :as str]))

(def contract-version 1)

(def statuses
  #{:queued :leased :running :checkpointed :held
    :succeeded :failed :rejected :cancelled})

(def active-statuses
  "Statuses that still occupy a slot. Anything else has stopped consuming."
  #{:queued :leased :running :checkpointed :held})

(def terminal-statuses
  #{:succeeded :failed :rejected :cancelled})

(def transitions
  "Legal status moves. `:failed -> :queued` is the only way back out of a
  terminal state, and it exists because a failed run is often a retryable
  one; `:rejected` and `:cancelled` are deliberately dead ends — a human
  said no, and re-deriving a run from that would launder the refusal."
  {:queued #{:leased :cancelled}
   :leased #{:running :queued :failed :cancelled}
   :running #{:checkpointed :held :succeeded :failed :cancelled}
   :checkpointed #{:leased :running :held :succeeded :failed :cancelled}
   :held #{:leased :running :rejected :cancelled}
   :succeeded #{}
   :failed #{:queued}
   :rejected #{}
   :cancelled #{}})

(def default-budget
  "A run without a ceiling is an unbounded spend against someone's money and
  someone's patience. These are defaults, not suggestions — `agent-run`
  merges over them rather than replacing them."
  {:max-turns 12
   :max-tool-calls 30
   :max-tokens 4000
   :deadline-ms 1200000
   :test-timeout-ms 180000})

(defn run-id
  ([now-ms] (run-id now-ms (str (random-uuid))))
  ([now-ms entropy]
   (let [normalized (str/replace (str entropy) #"-" "")]
     (when (< (count normalized) 8)
       (throw (ex-info "Run ID entropy must contain at least 8 characters"
                       {:entropy entropy :minimum-length 8})))
     (str "run-" now-ms "-" (subs normalized 0 8)))))

(defn agent-run
  "Build a queued AgentRun. `goal` is the only hard requirement: a run with
  no stated goal cannot be reviewed, cannot be judged done, and cannot be
  explained to the person it acted for."
  [{:keys [id goal project source-project repo pin mode node model runner
           capabilities budget parent actor organism replica
           require-done-no-edit?]
    :or {mode :local node :auto capabilities #{} budget {}
         require-done-no-edit? false}}
   now-ms]
  (when (str/blank? goal)
    (throw (ex-info "AgentRun requires a non-blank goal" {:field :goal})))
  {:agent.run/version contract-version
   :agent.run/id (or id (run-id now-ms))
   :agent.run/goal goal
   :agent.run/project project
   :agent.run/source-project source-project
   :agent.run/repo repo
   :agent.run/pin pin
   :agent.run/mode mode
   :agent.run/node node
   :agent.run/model model
   :agent.run/runner runner
   :agent.run/required-capabilities (set capabilities)
   :agent.run/budget (merge default-budget budget)
   :agent.run/parent parent
   :agent.run/actor actor
   :agent.run/organism organism
   :agent.run/replica replica
   ;; Observe-only roles (independent review, audit) must finish with DONE
   ;; and leave the tree untouched. Implementation roles must be free to
   ;; edit; only set this where the role is genuinely no-edit.
   :agent.run/require-done-no-edit? (boolean require-done-no-edit?)
   :agent.run/status :queued
   :agent.run/created-at now-ms
   :agent.run/updated-at now-ms
   :agent.run/attempt 0
   :agent.run/artifacts []})

(defn transition
  "Move a run to `status`, refusing anything the machine does not allow.
  Throws rather than returning a bad run: an illegal transition that is
  merely logged becomes a run whose history no longer explains its state."
  [run status now-ms attrs]
  (let [from (:agent.run/status run)]
    (when-not (contains? (get transitions from #{}) status)
      (throw (ex-info "Invalid AgentRun transition"
                      {:run-id (:agent.run/id run) :from from :to status})))
    (cond-> (merge run attrs
                   {:agent.run/status status
                    :agent.run/updated-at now-ms})
      (= status :leased) (update :agent.run/attempt inc))))

(defn event-keys
  "The six attribute names an event record uses, derived from a namespace.

  Parameterized because an existing host's event stream is PERSISTED DATA.
  tamaki has 231 `:tamaki.event/*` occurrences reaching `store` and
  `storage`; forcing this library's prefix on it would be a migration, not a
  refactor. A shared library that can only be adopted by rewriting the
  adopter's database is not shared."
  [ns-str]
  {:version (keyword ns-str "version")
   :id (keyword ns-str "id")
   :run (keyword ns-str "run")
   :parent (keyword ns-str "parent")
   :kind (keyword ns-str "kind")
   :at (keyword ns-str "at")
   :data (keyword ns-str "data")})

(def default-event-keys (event-keys "agent.event"))

(defn event
  ([run kind now-ms data] (event default-event-keys run kind now-ms data))
  ([ks run kind now-ms data]
   {(:version ks) contract-version
    (:id ks) (str (random-uuid))
    (:run ks) (:agent.run/id run)
    (:parent ks) (:agent.run/parent run)
    (:kind ks) kind
    (:at ks) now-ms
    (:data ks) data}))

(defn- apply-event*
  [run kind at data]
  (case kind
    :run/submitted (or run (:run data))
    :run/configured (merge run data)
    :run/leased (transition run :leased at data)
    :run/started (transition run :running at data)
    :run/checkpointed (transition run :checkpointed at data)
    :run/held (transition run :held at data)
    ;; Histories written before lease/start were recorded can still be
    ;; folded — their audit value is real — but they are marked, and they
    ;; never bypass `transition` for any state that has a legal path.
    :run/succeeded (cond
                     (= :succeeded (:agent.run/status run)) run
                     (= :queued (:agent.run/status run))
                     (merge run data
                            {:agent.run/status :succeeded
                             :agent.run/updated-at at
                             :agent.run/recovered-lifecycle true})
                     :else (transition run :succeeded at data))
    :run/failed (cond
                  (= :failed (:agent.run/status run)) run
                  (= :queued (:agent.run/status run))
                  (merge run data
                         {:agent.run/status :failed
                          :agent.run/updated-at at
                          :agent.run/recovered-lifecycle true})
                  :else (transition run :failed at data))
    :run/requeued (transition run :queued at data)
    :run/rejected (if (= :rejected (:agent.run/status run))
                    run
                    (transition run :rejected at data))
    :run/cancelled (if (= :cancelled (:agent.run/status run))
                     run
                     (transition run :cancelled at data))
    run))

(defn apply-event
  ([run ev] (apply-event default-event-keys run ev))
  ([ks run ev]
   (let [kind (get ev (:kind ks))
         at (get ev (:at ks))
         data (get ev (:data ks))]
     (apply-event* run kind at data))))

(defn fold-events
  "Rebuild every run from the append-only stream. Loop, role and audit events
  share that stream but are not AgentRuns — they must never materialize as
  nil entries, which is why the nil check is here rather than left to the
  caller."
  ([events] (fold-events default-event-keys events))
  ([ks events]
   (reduce
    (fn [runs ev]
      (let [id (get ev (:run ks))
            current (get runs id)
            next-run (apply-event ks current ev)]
        (if next-run (assoc runs id next-run) runs)))
    {}
    events)))

(defn resumable? [run]
  (contains? #{:failed :checkpointed :held} (:agent.run/status run)))

(defn active? [run]
  (contains? active-statuses (:agent.run/status run)))

(defn terminal? [run]
  (contains? terminal-statuses (:agent.run/status run)))
