(ns kami.world-partition
  "Portable M3 world-partition contract.

  Hosts provide camera positions, cell metadata, IO completion events, and a
  byte budget. These pure functions choose LODs and lifecycle transitions; no
  filesystem, network, clock, renderer, or measured throughput is implied.")

(def lifecycle-states
  #{:unloaded :requested :loading :resident :evicting :failed})

(defn valid-cell?
  "A cell has a stable id, world-space center, positive byte estimate, and
  strictly increasing LOD distance thresholds."
  [{:cell/keys [id center estimated-bytes lod-distances]}]
  (and (some? id)
       (vector? center) (= 3 (count center)) (every? number? center)
       (pos-int? estimated-bytes)
       (vector? lod-distances) (seq lod-distances)
       (every? #(and (number? %) (not (neg? %))) lod-distances)
       (apply < lod-distances)))

(defn distance-squared [a b]
  (when-not (and (vector? a) (vector? b) (= 3 (count a)) (= 3 (count b))
                 (every? number? a) (every? number? b))
    (throw (ex-info "world-partition: positions must be numeric vec3 values"
                    {:a a :b b})))
  (reduce + (map (fn [x y] (let [d (- x y)] (* d d))) a b)))

(defn desired-lod
  "Return zero-based LOD (0 = nearest/highest detail), or :culled beyond the
  final threshold. Squared comparisons avoid host-specific square roots."
  [cell observer]
  (when-not (valid-cell? cell)
    (throw (ex-info "world-partition: invalid cell" {:cell cell})))
  (let [d2 (distance-squared (:cell/center cell) observer)]
    (or (first (keep-indexed (fn [lod distance]
                               (when (<= d2 (* distance distance)) lod))
                             (:cell/lod-distances cell)))
        :culled)))

(defn initial-state [cells]
  (when-let [invalid (seq (remove valid-cell? cells))]
    (throw (ex-info "world-partition: invalid cells" {:cells (vec invalid)})))
  {:partition/cells (into {} (map (juxt :cell/id identity) cells))
   :partition/lifecycle (zipmap (map :cell/id cells) (repeat :unloaded))
   :partition/resident-lod {}
   :partition/failures {}})

(def allowed-transitions
  {:request #{[:unloaded :requested] [:failed :requested]}
   :begin-load #{[:requested :loading]}
   :load-ok #{[:loading :resident]}
   :load-failed #{[:loading :failed]}
   :begin-evict #{[:resident :evicting]}
   :evict-ok #{[:evicting :unloaded]}})

(defn transition
  "Apply an explicit host event to one cell. Illegal transitions throw. A
  successful load requires :lod; a failure may include :reason."
  [state cell-id event]
  (let [from (get-in state [:partition/lifecycle cell-id])
        to ({:request :requested :begin-load :loading :load-ok :resident
             :load-failed :failed :begin-evict :evicting :evict-ok :unloaded}
            (:event/type event))]
    (when-not (contains? (:partition/cells state) cell-id)
      (throw (ex-info "world-partition: unknown cell" {:cell/id cell-id})))
    (when-not (contains? (get allowed-transitions (:event/type event) #{}) [from to])
      (throw (ex-info "world-partition: illegal lifecycle transition"
                      {:cell/id cell-id :from from :event event})))
    (when (and (= :load-ok (:event/type event)) (not (nat-int? (:lod event))))
      (throw (ex-info "world-partition: load-ok requires a non-negative :lod" event)))
    (cond-> (assoc-in state [:partition/lifecycle cell-id] to)
      (= :load-ok (:event/type event))
      (-> (assoc-in [:partition/resident-lod cell-id] (:lod event))
          (update :partition/failures dissoc cell-id))
      (= :load-failed (:event/type event))
      (assoc-in [:partition/failures cell-id] (:reason event))
      (= :evict-ok (:event/type event))
      (update :partition/resident-lod dissoc cell-id))))

(defn budget-plan
  "Choose desired visible cells under `maximum-bytes`. Nearest cells win, then
  stable printed id order. Returns selected requests and deferred ids."
  [cells observer maximum-bytes]
  (when-not (and (integer? maximum-bytes) (not (neg? maximum-bytes)))
    (throw (ex-info "world-partition: budget must be a non-negative integer"
                    {:maximum-bytes maximum-bytes})))
  (let [candidates (->> cells
                        (map (fn [cell] (assoc cell :plan/lod (desired-lod cell observer)
                                                   :plan/distance-squared
                                                   (distance-squared (:cell/center cell) observer))))
                        (remove #(= :culled (:plan/lod %)))
                        (sort-by (juxt :plan/distance-squared (comp pr-str :cell/id))))
        result (reduce (fn [{:keys [used] :as plan} cell]
                         (let [bytes (:cell/estimated-bytes cell)]
                           (if (<= (+ used bytes) maximum-bytes)
                             (-> plan (update :used + bytes)
                                 (update :selected conj {:cell/id (:cell/id cell)
                                                        :lod (:plan/lod cell)
                                                        :estimated-bytes bytes}))
                             (update plan :deferred conj (:cell/id cell)))))
                       {:used 0 :selected [] :deferred []} candidates)]
    {:plan/maximum-bytes maximum-bytes
     :plan/estimated-bytes (:used result)
     :plan/selected (:selected result)
     :plan/deferred (:deferred result)}))
