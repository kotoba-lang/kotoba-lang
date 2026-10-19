;; murakumo.task.plan — PURE fleet task scheduler (task-level distribution).
;;
;; W6 product-shell + T6.4: slots / failed? / eligible? / task-id / retry bounds /
;; wave-slot / percentile / summary / unschedulable detail require the shipped
;; `:task-plan` KIR on **every** platform. Host pure mirrors are gone —
;; cljs/nbb must preload shipped KIR before requiring this ns
;; (ADR-260731-w6-t64-task-reconcile-mirror-delete).
;; Host remains: admit/prepare folds, set membership projection, sort-by
;; node-score, map assembly.

(ns murakumo.task.plan
  "Task pure helpers use kotoba/task_plan_core.kotoba authority (oracle required)."
  (:require [kotoba.lang.text :as str]
            [murakumo.kotoba.oracle :as oracle]))

(def ^:private oid :task-plan)

(defn- o
  "Call a pure export. Requires the shipped oracle on every platform (T6.4)."
  [export args]
  (oracle/require-ready! oid)
  (oracle/call oid export args))

(defn- o-record
  "T5.2: structural host map → call-record (requires shipped oracle)."
  [export host-map field-specs]
  (oracle/require-ready! oid)
  (oracle/call-record oid export host-map field-specs))

(defn- oi64
  "oracle の i64 結果 → host の整数。

  **`long` に直接渡してはならない。** KIR は i64 を JVM では Long、cljs では
  **BigInt** で返すので、cljs 側で `(long <BigInt>)` は
  `Cannot convert a BigInt value to a number` で落ちる。実測 2026-08-11:
  この ns は 11 箇所で素の `long` を使っており、**nbb では require した瞬間に
  `default-opts` の評価で死んでいた** —— つまり `murakumo task plan/run/report`
  （この ns の唯一の CLI）が nbb 上で 1 つも動かなかった。JVM では動くので
  test が緑のまま通っていた。

  `oracle/i64->host` はまさにこの変換のために在り、`murakumo.tunnel` /
  `murakumo.persist` / `murakumo.component-authority` は既にこれを通している ——
  この ns だけが例外だった。"
  [v]
  (long (oracle/i64->host v)))

(def exclude-join-sep
  "CSV join for :exclude-nodes in unschedulable detail. Kotoba SSoT."
  (o 'exclude-join-sep []))

(def unsched-placement-prefix
  "Prefix before placement pr-str in unschedulable detail. Kotoba SSoT."
  (o 'unsched-placement-prefix []))

(def unsched-excluding-prefix
  "Prefix before exclude CSV in unschedulable detail. Kotoba SSoT."
  (o 'unsched-excluding-prefix []))

(def unsched-min-mem-prefix
  "Prefix before min-mem-bytes in unschedulable detail. Kotoba SSoT."
  (o 'unsched-min-mem-prefix []))

(defn- opt-i64 [v]
  (if (some? v) (long v) -1))

(def default-opts
  "Default planner opts. max-slots / max-attempts / timeout-ms from oracle."
  {:max-slots (oi64 (o 'default-max-slots []))
   :slots-per-node nil
   :max-attempts (oi64 (o 'default-max-attempts []))
   :timeout-ms (oi64 (o 'default-timeout-ms []))
   :connect-timeout-s 8})

(def ^:private eligibility-schema
  "Guest descriptor for task_plan_core's eligibility record (T5.3 + profile 5)."
  [:record :task/eligibility
   [[:online :bool] [:labels-ok :bool] [:roles-ok :bool]
    [:not-excluded :bool] [:allowlist-ok :bool]
    [:mem-bytes :i64] [:min-mem :i64]]])

(def ^:private retry-schema
  "T5.2 native guest record for can-retry?."
  [:record :task/retry [[:attempt :i64] [:max-attempts :i64]]])

(def ^:private slots-schema
  [:record :task/slots
   [[:budget :i64] [:node-slots :i64] [:slots-per :i64]
    [:max-slots :i64] [:cores :i64]]])
(def ^:private wave-schema
  [:record :task/wave [[:used :i64] [:slots :i64]]])
(def ^:private better-mem-schema
  [:record :task/better-mem
   [[:memneg0 :i64] [:memneg1 :i64] [:name-ord0 :i64] [:name-ord1 :i64]]])
(def ^:private unsched-schema
  [:record :task/unsched
   [[:placement :string] [:excluding :string] [:min-mem-str :string]]])
(def ^:private pick-fold-schema
  [:record :task/pick-fold
   [[:champ [:option :i64]] [:ok-i :bool] [:better-c-i :bool]]])
(def ^:private pair-schema
  [:record :task/pair [[:a :i64] [:b :i64]]])

(defn slots
  "Concurrent task capacity of `node`. Kotoba `slots` with projected i64s.
   T5.2: native guest record wire."
  [node opts]
  (let [merged (merge default-opts opts)
        budget (if (contains? (or (:slots-by-node opts) {}) (:name node))
                 (long (get (:slots-by-node opts) (:name node)))
                 -1)
        node-slots (opt-i64 (:slots node))
        slots-per (opt-i64 (:slots-per-node merged))
        max-slots (long (or (:max-slots merged) 8))
        cores (opt-i64 (:cores node))]
    (oi64 (o-record 'slots
                    {:s (oracle/record slots-schema
                                       {:budget budget
                                        :node-slots node-slots
                                        :slots-per slots-per
                                        :max-slots max-slots
                                        :cores cores})}
                    [[:s :raw]]))))

(defn admit
  "Operational admission gate, applied BEFORE placement."
  [nodes {:keys [max-load1 max-load-per-core]}]
  (reduce (fn [acc n]
            (let [l (:load1 n)
                  per-core (when (and l (pos? (or (:cores n) 0))) (/ l (:cores n)))
                  over (cond
                         (false? (:online? n true))
                         [:unreachable (or (:probe-error n) "probe failed")]

                         (and max-load1 l (> l max-load1))
                         [:saturated (str "load1 " l " > " max-load1)]

                         (and max-load-per-core per-core (> per-core max-load-per-core))
                         [:saturated (str "load/core " (/ (Math/round (* 100.0 per-core)) 100.0)
                                          " > " max-load-per-core)])]
              (if over
                (update acc :skipped conj
                        {:node (:name n) :reason (first over) :detail (second over)})
                (update acc :nodes conj n))))
          {:nodes [] :skipped []}
          nodes))

(defn- trim-to-budget
  "Scale a {node-name slots} map down so the total stays within `budget`."
  [slots-map budget]
  (let [total (reduce + 0 (vals slots-map))]
    (if (or (nil? budget) (<= total budget))
      slots-map
      (let [ratio (/ (double budget) (double total))
            scaled (into {} (map (fn [[k v]] [k (max 1 (int (Math/floor (* v ratio))))]) slots-map))]
        (loop [m scaled]
          (let [t (reduce + 0 (vals m))]
            (if (<= t budget)
              m
              (let [[k v] (first (sort-by (juxt (comp - val) key) m))]
                (if (<= v 1)
                  (recur (dissoc m (first (sort (keys m)))))
                  (recur (assoc m k (dec v))))))))))))

(defn prepare
  "Fold health gates and the fleet-wide concurrency budget into a ready-to-use
   {:nodes :skipped :opts}."
  [nodes opts]
  (let [{:keys [nodes skipped]} (admit nodes opts)
        base (into {} (map (fn [n] [(:name n) (slots n (dissoc opts :slots-by-node))]) nodes))
        budgeted (trim-to-budget base (:max-inflight opts))
        kept (filterv #(contains? budgeted (:name %)) nodes)
        dropped (for [n nodes :when (not (contains? budgeted (:name n)))]
                  {:node (:name n) :reason :over-inflight-budget
                   :detail (str "fleet --max-inflight " (:max-inflight opts)
                                " is smaller than the node count")})]
    {:nodes kept
     :skipped (into (vec skipped) dropped)
     :opts (assoc opts :slots-by-node budgeted)}))

(defn- eligibility-fields
  "Host projects set/map membership + mem bounds into eligibility fields."
  [node {:keys [placement min-mem-bytes exclude-nodes nodes] :as task}]
  (let [{:keys [labels roles]} placement]
    {:online (not (false? (:online? node true)))
     :labels-ok (boolean (every? (fn [[k v]] (= v (get (:labels node) k)))
                                 (or labels {})))
     :roles-ok (boolean (every? (set (or (:roles node) #{}))
                                (or roles [])))
     :not-excluded (not (contains? (set (or exclude-nodes [])) (:name node)))
     :allowlist-ok (boolean (or (empty? (or nodes []))
                                (contains? (set nodes) (:name node))))
     :mem-bytes (or (:mem-bytes node) 0)
     :min-mem (or min-mem-bytes 0)}))

(defn- eligible-fields?
  "Kotoba `task-eligible?` を eligibility fields から直に引く。"
  [fields]
  (oracle/bool->host
   (o-record 'task-eligible?
             {:eligibility (oracle/record eligibility-schema fields)}
             [[:eligibility :raw]])))

(defn eligible?
  "Can `node` run `task`? Kotoba `task-eligible?` with a single eligibility
  record (T5.2 native guest record wire: mem bounds on the record)."
  [node task]
  (eligible-fields? (eligibility-fields node task)))

(defn- eligible-memo?
  "`eligible?` を eligibility fields で memo する。

  **fields が同じなら答えは同じ —— これは定義そのもの**（`eligible?` は fields
  だけを見て、他のどこも参照しない）。したがってこの memo は意味を変えない。

  効くのは、fleet の batch が **placement を数種類しか持たない**ため。実測
  2026-08-11（superproject fleet-ci の gate batch）: 101 task の placement は
  `[\"node\"]` と `[\"jvm\"]` の 2 種だけで、`eligible?` の呼び出しは
  ノード数 × task 数 = 808 回だったが、実際に異なる fields は 16 通りしかなかった。
  1 回 3.2ms（KIR、warm）なので 2.6 秒が重複計算だった。"
  [cache node task]
  (let [k (eligibility-fields node task)]
    (if-let [hit (find @cache k)]
      (val hit)
      (let [v (eligible-fields? k)]
        (swap! cache assoc k v)
        v))))

(defn node-score
  "Lower is better. Fill ratio dominates; load1 / memory / name break ties.

  `slots-fn` は (fn [node] → slots)。**fold の内側では 4-arity を使うこと。**
  3-arity は毎回 `slots` を引き、それは KIR 呼び出しで実測 7.4ms かかる ——
  `sort-by` の keyfn として task ごとに全候補ノードへ当たるので、
  ノード数 × task 数 回の KIR 呼び出しになる（実測 2026-08-11: 101 task × 8 node で
  約 6 秒がこれだけに消えていた）。**`slots` は task に依存しない**ので、
  fold の外で 1 ノード 1 回でよい。"
  ([node opts load] (node-score node opts load #(slots % opts)))
  ([node opts load slots-fn]
   [(/ (double (get load (:name node) 0)) (double (slots-fn node)))
    (double (or (:load1 node) 0))
    (- (double (or (:mem-bytes node) 0)))
    (str (:name node))]))

(defn- why-unschedulable [task]
  "Reject detail string via kotoba `unschedulable-detail`.
   T5.2: native guest record wire."
  (let [placement (pr-str (:placement task))
        excluding (if (seq (:exclude-nodes task))
                    (str/join exclude-join-sep (:exclude-nodes task))
                    "")
        min-mem (if-let [m (:min-mem-bytes task)] (str m) "")]
    (o-record 'unschedulable-detail
              {:u (oracle/record unsched-schema
                                 {:placement placement
                                  :excluding excluding
                                  :min-mem-str min-mem})}
              [[:u :raw]])))

(defn- assign-1
  "Place one task onto the currently least-filled eligible node.

  `ctx` は `assign` が fold の外で 1 回だけ作る不変量:
    :slots-of  node 名 → slots（task に依存しないので 1 ノード 1 回）
    :elig      eligibility fields → bool の memo
  どちらも純関数の memo であって、配置の意味は変わらない。"
  [acc task nodes opts {:keys [slots-of elig] :as ctx}]
  (let [load (:load acc)
        slots-fn (fn [n] (get slots-of (:name n)))
        candidates (filterv #(eligible-memo? elig % task) nodes)]
    (if (empty? candidates)
      (update acc :unschedulable conj
              {:task task :reason :no-eligible-node :detail (why-unschedulable task)})
      (let [n (first (sort-by #(node-score % opts load slots-fn) candidates))
            k (:name n)
            used (get load k 0)
            s (slots-fn n)
            wave (oi64 (o-record 'wave-of
                                  {:w (oracle/record wave-schema
                                                     {:used used :slots s})}
                                  [[:w :raw]]))
            slot (oi64 (o-record 'slot-of
                                  {:w (oracle/record wave-schema
                                                     {:used used :slots s})}
                                  [[:w :raw]]))
            a {:task task :node k :host (:host n)
               :wave wave :slot slot}
            next-load (oi64 (o-record 'load-after-assign
                                       {:used used}
                                       [[:used :i64]]))]
        (-> acc
            (update :assignments conj a)
            (assoc-in [:load k] next-load))))))

(defn assign
  "Assign `tasks` to `nodes`, greedily least-loaded-first.

  **task 不変量を fold の外で 1 回だけ引く。** `slots` は (node, opts) だけの関数で
  task を見ないので、ノードごとに 1 回。`eligible?` は eligibility fields だけの
  関数なので fields で memo する。どちらも純関数の memo で、配置の結果は変わらない
  （`task_plan_kotoba_parity_test` が同一性を検査している）。

  実測 2026-08-11（superproject fleet-ci の 101 gate × 8 node、warm）:
  この 2 つの hoist で 12.6s → 下記 commit の測定値まで落ちた。落ちた分はすべて
  **同じ引数で同じ答えを何百回も KIR に計算させていた**ぶんである。"
  ([{:keys [nodes tasks opts]}] (assign nodes tasks opts))
  ([nodes tasks opts]
   (let [ctx {:slots-of (into {} (map (fn [n] [(:name n) (slots n opts)])) nodes)
              :elig (atom {})}]
     (reduce (fn [acc task] (assign-1 acc task nodes opts ctx))
             {:assignments [] :unschedulable [] :load {}}
             tasks))))

(defn expand
  "Build `n` identical tasks from a template. Task ids via kotoba `task-id`."
  [n template]
  (mapv (fn [i]
          (merge {:id (o-record 'task-id
                                 {:i i}
                                 [[:i :i64]])
                  :attempt 1}
                 template))
        (range n)))

(defn failed?
  "Process could not start, timed out, or exited non-zero. Profile 5: :bool.
   T5.2 native guest record wire: single :task/failed argument."
  [{:keys [exit timeout? error] :as r}]
  (oracle/bool->host
   (o-record 'failed?
             {:x (oracle/record
                  [:record :task/failed
                   [[:exit [:option :i64]]
                    [:timeout :bool]
                    [:error [:option :string]]]]
                  {:exit exit
                   :timeout (boolean timeout?)
                   :error error})}
             [[:x :raw]])))

(defn retry-tasks
  "Tasks to re-submit from `results`, one attempt later."
  [results opts]
  (let [{:keys [max-attempts]} (merge default-opts opts)]
    (->> results
         (filter failed?)
         (keep (fn [{:keys [task node]}]
                 (let [attempt (or (:attempt task) 1)
                       can? (oracle/bool->host
                             (o-record 'can-retry?
                                       {:retry (oracle/record retry-schema
                                                              {:attempt attempt
                                                               :max-attempts max-attempts})}
                                       [[:retry :raw]]))]
                   (when can?
                     (-> task
                         (assoc :attempt (oi64 (o-record 'attempt-next
                                                           {:attempt attempt}
                                                           [[:attempt :i64]])))
                         (update :exclude-nodes (fnil conj []) node))))))
         vec)))

(defn percentile
  "Nearest-rank percentile of `xs` (0.0–1.0), nil for an empty sample."
  [xs p]
  (let [v (vec (sort xs))]
    (when (seq v)
      (let [idx (oi64 (o-record 'nearest-rank-idx
                                  {:p (oracle/record pair-schema
                                                     {:a (count v)
                                                      :b (long (Math/floor (* p 1000)))})}
                                  [[:p :raw]]))]
        (nth v idx)))))

(defn final-results
  "One result per task id — the last attempt made."
  [results]
  (->> results
       (group-by #(get-in % [:task :id]))
       (map (fn [[_ rs]] (last (sort-by #(or (get-in % [:task :attempt]) 1) rs))))
       (sort-by #(get-in % [:task :id]))
       vec))

(defn summary
  "Fold results (+ wall-clock) into a report map."
  [results wall-ms]
  (let [finals (final-results results)
        ok (remove failed? finals)
        ko (filter failed? finals)
        durations (keep :duration-ms results)
        task-ms (reduce + 0 durations)
        retried (oi64 (o-record 'summary-retried
                                  {:p (oracle/record pair-schema
                                                     {:a (count results)
                                                      :b (count finals)})}
                                  [[:p :raw]]))
        speedup (let [milli (oi64 (o-record 'speedup-milli
                                             {:p (oracle/record pair-schema
                                                                {:a task-ms
                                                                 :b (or wall-ms 0)})}
                                             [[:p :raw]]))]
                  (when (pos? milli) (/ (double milli) 1000.0)))]
    {:tasks (count finals)
     :ok (count ok)
     :failed (count ko)
     :attempts (count results)
     :retried retried
     :nodes-used (count (distinct (keep :node results)))
     :by-node (into (sorted-map) (frequencies (keep :node results)))
     :wall-ms wall-ms
     :task-ms task-ms
     :speedup speedup
     :p50-ms (percentile durations 0.5)
     :p95-ms (percentile durations 0.95)
     :slowest (when (seq durations) (apply max durations))
     :failures (mapv (fn [r] {:id (get-in r [:task :id])
                              :node (:node r)
                              :attempt (get-in r [:task :attempt])
                              :exit (:exit r)
                              :timeout? (:timeout? r)
                              :error (:error r)})
                     ko)}))

(defn plan-table
  "Rows for a human-readable plan preview: node, assigned count, slots, waves."
  [{:keys [assignments]} nodes opts]
  (let [by-node (frequencies (map :node assignments))]
    (->> nodes
         (map (fn [n]
                (let [c (get by-node (:name n) 0)
                      s (slots n opts)]
                  {:node (:name n) :assigned c :slots s
                   :waves (if (zero? c) 0 (int (Math/ceil (/ (double c) (double s)))))})))
         (sort-by (juxt (comp - :assigned) :node))
         vec)))
