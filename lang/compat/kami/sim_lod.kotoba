(ns kami.sim-lod
  "Pure, deterministic simulation-LOD planning.

  The logical population is never culled. Visibility only selects the render
  population and update cadence controls how often offscreen actors enter a
  coarse simulation workload. Hosts remain responsible for spatial indexes,
  parallel execution, rendering and clocks; this namespace is a portable data
  contract and intentionally claims no measured throughput.")

(def contract-version 1)

(defn- fail [message data]
  (throw (ex-info (str "sim-lod: " message) data)))

(defn- ordered [entities]
  (sort-by (comp pr-str :entity/id) entities))

(defn- distance-squared [a b]
  (reduce + (map (fn [x y] (let [d (- x y)] (* d d))) a b)))

(defn plan-frame
  "Partition `entities` into logical, visible and due-update populations.

  Every entity requires a unique :entity/id, non-negative integer
  :entity/index and numeric :position vector. Visible entities update every
  tick. Offscreen entities update once per :offscreen-cadence ticks, staggered
  by :entity/index so a whole population is not released in one burst.

  The returned vectors are ordered by printed entity id, making the result
  independent of input iteration order and portable as EDN."
  [{:keys [tick observer visible-distance offscreen-cadence]
    :or {observer [0 0]}} entities]
  (when-not (and (int? tick) (<= 0 tick))
    (fail ":tick must be a non-negative integer" {:tick tick}))
  (when-not (and (number? visible-distance) (<= 0 visible-distance))
    (fail ":visible-distance must be non-negative" {:visible-distance visible-distance}))
  (when-not (and (int? offscreen-cadence) (pos? offscreen-cadence))
    (fail ":offscreen-cadence must be a positive integer" {:offscreen-cadence offscreen-cadence}))
  (let [entities (vec (ordered entities))
        ids (mapv :entity/id entities)]
    (when (or (some nil? ids) (not= (count ids) (count (set ids))))
      (fail ":entity/id values must be present and unique" {:ids ids}))
    (doseq [{:entity/keys [id index] :keys [position]} entities]
      (when-not (and (int? index) (<= 0 index))
        (fail ":entity/index must be a non-negative integer" {:entity/id id :value index}))
      (when-not (and (vector? position)
                     (= (count observer) (count position))
                     (every? number? position))
        (fail ":position must match the numeric observer vector" {:entity/id id :value position})))
    (let [visible? (fn [entity]
                     (<= (distance-squared observer (:position entity))
                         (* visible-distance visible-distance)))
          visible (filterv visible? entities)
          visible-ids (set (map :entity/id visible))
          due? (fn [entity]
                 (or (contains? visible-ids (:entity/id entity))
                     (zero? (mod (+ tick (:entity/index entity)) offscreen-cadence))))]
      {:sim-lod/contract contract-version
       :sim-lod/tick tick
       :sim-lod/logical entities
       :sim-lod/visible visible
       :sim-lod/due-updates (filterv due? entities)
       :sim-lod/deferred (filterv (complement due?) entities)})))

(defn formation-workload
  "Build one deterministic coarse command per formation in `due-entities`.

  `targets` maps formation ids to target vectors. Commands contain sorted
  member ids and the arithmetic centroid; they describe work and do not mutate
  a world."
  [due-entities targets]
  (->> due-entities
       (filter :formation/id)
       (group-by :formation/id)
       (sort-by (comp pr-str key))
       (mapv (fn [[formation members]]
               (let [members (vec (ordered members))
                     dimensions (count (:position (first members)))
                     centroid (mapv (fn [axis]
                                      (/ (reduce + (map #(nth (:position %) axis) members))
                                         (count members)))
                                    (range dimensions))]
                 {:work/type :formation
                  :formation/id formation
                  :formation/members (mapv :entity/id members)
                  :formation/centroid centroid
                  :formation/target (get targets formation centroid)})))))

(defn schedule-workload
  "Resolve due actors' cyclic schedules at `tick`.

  A schedule is a non-empty vector of opaque activity keywords. `period` is
  the positive number of ticks per slot. Entity index staggers actors without
  randomness, so replay and headless hosts produce the same commands."
  [due-entities tick period schedules]
  (when-not (and (int? period) (pos? period))
    (fail "schedule period must be a positive integer" {:period period}))
  (mapv (fn [{:entity/keys [id index] :as entity}]
          (let [schedule (get schedules (:schedule/id entity))]
            (when-not (seq schedule)
              (fail "entity has no non-empty schedule" {:entity/id id
                                                         :schedule/id (:schedule/id entity)}))
            {:work/type :schedule
             :entity/id id
             :schedule/activity (nth schedule
                                     (mod (quot (+ tick index) period)
                                          (count schedule)))}))
        (ordered due-entities)))
