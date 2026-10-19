(ns murakumo.infer.rebalance
  "Demand-aware fleet rebalancing (pure cljc).

  murakumo.infer.plan cuts ONE model across ranks; murakumo.infer.schedule picks
  WHICH node runs a job. This namespace sits above both: given the live fleet
  capacity and the recent REQUEST MIX, it decides how many nodes each capability
  pool (text / media / postproc) should hold, and re-places nodes when demand
  shifts — with hysteresis so it doesn't thrash. Output is a placement plan the
  operator applies and the scheduler routes against.

  Pure data → data: same inputs → same plan (no clock, no RNG), so it runs in
  bb, the Worker, tests, and a WASM component alike.

  Inputs
    capacity : from a fleet snapshot — [{:id :ram-gb :usable-gb :roles-capable
                                         :disk-free-gb :status}]
    demand   : rolling request counts by class {:text n :image n :video n :postproc n}
    current  : the pool→[ids] map in force now (for diffing / hysteresis)

  One invariant: exactly one node is reserved as head/relay (I/O + dispatch), it
  never holds a shard. The rest are split across pools proportional to demand,
  each with a floor so a live capability never drops to zero while any demand exists.

  W6 product-shell + T6.4: usable-gb / seats-of-* require the shipped
  `:infer-rebalance` KIR on **every** platform. Host pure mirrors are gone —
  cljs/nbb must preload shipped KIR before requiring this ns
  (ADR-260731-w6-t64-infer-sched-rebal-engine-mirror-delete).
  Placement folds stay host."
  (:require [murakumo.kotoba.oracle :as oracle]))

(def ^:private oid :infer-rebalance)

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

(def ^:private seats-in-schema
  "Guest :rebalance/seats-in — T5.2 native record for seats-record input."
  [:record :rebalance/seats-in
   [[:total :i64] [:text-w :i64] [:media-w :i64]
    [:postproc-w :i64] [:floor :i64]]])

(def ^:private run-flags-schema
  "Guest :rebalance/run-flags — T5.2 option-string-in-record classify inputs."
  [:record :rebalance/run-flags
   [[:images [:option :string]]
    [:video [:option :string]]
    [:audio [:option :string]]
    [:swarm [:option :string]]
    [:tokens [:option :string]]]])

(def ^:private class-code->key
  {0 nil 1 :text 2 :image 3 :video 4 :audio 5 :postproc})

(defn- classify-run
  "Project one run's unit/kind presence → guest class code (0–5)."
  [run]
  (let [u (or (:units run) (get run "units") {})
        kind (or (:run/kind run) (get run "run/kind")
                 (:model run) (get run "model"))]
    (oracle/i64->host
     (o-record 'classify-run-flags
               {:x (oracle/record
                    run-flags-schema
                    {:images (when (or (:images u) (get u "images")) "images")
                     :video (when (or (:video-seconds u) (get u "video-seconds")) "video")
                     :audio (when (or (:audio-seconds u) (get u "audio-seconds")) "audio")
                     :swarm (when (= "browser-swarm" (str kind)) "swarm")
                     :tokens (when (or (:tokens u) (get u "tokens")) "tokens")})}
               [[:x :raw]]))))

(def shard-ceiling-gb
  "16GB stability limit → ~10GB usable shard. Kotoba `shard-ceiling-gb`."
  (oracle/i64->host (o 'shard-ceiling-gb [])))

;; ── capacity ────────────────────────────────────────────────────────────────

(defn node-capacity
  "One anonymized snapshot node → a capacity record. usable-gb is min(shard
   ceiling, ram - OS/KV headroom). Kotoba `usable-gb`.
   T5.2: structural map → call-record."
  [{:keys [id ram-gb disk-free roles status] :as n}]
  (let [ram (or ram-gb 0)
        usable (oracle/i64->host
                (o-record 'usable-gb
                          {:ram-gb ram}
                          [[:ram-gb :i64]]))]
    {:id id :status status :ram-gb ram :usable-gb usable
     :roles-capable (set (map keyword (or roles [])))
     :disk-free disk-free}))

(defn capacity
  "Fleet snapshot → the online capacity list (down nodes dropped)."
  [snapshot]
  (->> (:nodes snapshot)
       (filter #(= "up" (or (:status %) (get % "status"))))
       (map node-capacity)
       vec))

;; ── demand ──────────────────────────────────────────────────────────────────

(defn demand-from-runs
  "Recent run ledger → request counts by capability class. `runs` is the
   append-only feed; we bucket each run's units/kind.
   Class tag via kotoba `classify-run-flags` (T5.2 option-string record)."
  [runs]
  (reduce (fn [d run]
            (if-let [k (class-code->key (classify-run run))]
              (update d k inc)
              d))
          {:text 0 :image 0 :video 0 :audio 0 :postproc 0}
          runs))

(def ^:private class->pool
  {:text :text-pool :image :media-pool :video :media-pool
   :audio :media-pool :postproc :postproc-pool})

(defn pool-demand
  "Collapse per-class demand into per-POOL weights (text / media / postproc)."
  [demand]
  (reduce-kv (fn [m cls n] (update m (class->pool cls :text-pool) (fnil + 0) (or n 0)))
             {:text-pool 0 :media-pool 0 :postproc-pool 0}
             demand))

;; ── allocation ────────────────────────────────────────────────────────────────

(defn- largest-remainder
  "Apportion `total` seats across `weights` (map k→w) by largest-remainder, with
   a floor of `floor` seats for any pool whose weight > 0. Deterministic.
   3-pool text/media/postproc: kotoba `seats-record` once → lane projections.
   T5.2: seats-in → seats-record → lanes; seats-of-* project fields."
  [total weights floor]
  ;; T5.3: lanes record, no base-65536 seat pack. One guest largest-remainder
  ;; (seats-record); three thin field projections (seats-of-*).
  (let [in (oracle/record
            seats-in-schema
            {:total total
             :text-w (or (get weights :text-pool) 0)
             :media-w (or (get weights :media-pool) 0)
             :postproc-w (or (get weights :postproc-pool) 0)
             :floor floor})
        lanes (o-record 'seats-record {:in in} [[:in :raw]])]
    {:text-pool (oracle/i64->host
                 (o-record 'seats-of-text {:lanes lanes} [[:lanes :raw]]))
     :media-pool (oracle/i64->host
                  (o-record 'seats-of-media {:lanes lanes} [[:lanes :raw]]))
     :postproc-pool (oracle/i64->host
                     (o-record 'seats-of-postproc {:lanes lanes} [[:lanes :raw]]))}))

(defn target-allocation
  "capacity + demand → a placement plan:
     {:head <id> :pools {:text-pool [ids] :media-pool [ids] :postproc-pool [ids]}
      :pool-seats {…} :pipeline {:width n :note …} :online n}
   Reserves one head/relay node (prefers one already :relay-capable), then
   apportions the rest across pools by demand (largest-remainder, floor 1 for any
   pool with demand). `opts` = {:floor 1}."
  ([cap demand] (target-allocation cap demand {}))
  ([cap demand {:keys [floor] :or {floor 1}}]
   (let [online (vec cap)
         n (count online)]
     (if (zero? n)
       {:head nil :pools {} :pool-seats {} :online 0 :note "no nodes online"}
       (let [relay (or (first (filter #(contains? (:roles-capable %) :relay) online))
                       (first online))
             head-id (:id relay)
             workers (vec (remove #(= (:id %) head-id) online))
             seats (largest-remainder (count workers) (pool-demand demand) floor)
             ;; assign concrete nodes to pools in seat order (biggest pool first)
             ordered (sort-by (comp - val) seats)
             [pools _] (reduce (fn [[acc rem] [pool k]]
                                 [(assoc acc pool (vec (take k rem))) (drop k rem)])
                               [{} (map :id workers)] ordered)
             ;; text models over the shard ceiling need a pipeline across the text pool
             text-n (count (:text-pool pools []))
             usable (some-> workers first :usable-gb)]
         {:head head-id
          :pools pools
          :pool-seats seats
          :online n
          :pipeline {:width text-n
                     :usable-gb-per-node usable
                     :effective-gb (* (or usable 0) text-n)
                     :note (str "text pool sharded as a " text-n "-way pipeline "
                                "(~" (* (or usable 0) text-n) "GB effective, ceiling "
                                shard-ceiling-gb "GB/node)")}})))))

;; ── rebalance (with hysteresis) ───────────────────────────────────────────────

(defn- moves-between
  "Diff current pool→ids vs target pool→ids → [{:id :from :to} …]."
  [current target]
  (let [loc (fn [pools] (into {} (for [[p ids] pools, id ids] [id p])))
        c (loc current) t (loc target)]
    (vec (for [[id to] t :let [from (get c id)] :when (not= from to)]
           {:id id :from from :to to}))))

(defn rebalance
  "current placement + fresh capacity + demand → {:target :moves :changed? :reason}.
   Applies hysteresis: if the demand-driven seat allocation is unchanged from
   current, returns :changed? false with no moves (avoid thrashing on noise).
   `opts` passes through to target-allocation plus {:min-moves 1}."
  ([current cap demand] (rebalance current cap demand {}))
  ([current cap demand opts]
   (let [target (target-allocation cap demand opts)
         cur-pools (or (:pools current) {})
         moves (moves-between cur-pools (:pools target))
         seat-shift (not= (:pool-seats target)
                          (:pool-seats (target-allocation cap
                                                          (or (:demand current) demand) opts)))]
     {:target target
      :moves moves
      :changed? (boolean (seq moves))
      :reason (cond
                (empty? (:nodes-of cur-pools)) "initial placement"
                (seq moves) (str (count moves) " node(s) re-placed by demand shift")
                :else "stable — demand within current allocation")})))
