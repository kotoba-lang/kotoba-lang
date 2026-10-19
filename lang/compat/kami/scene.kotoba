(ns kami.scene
  "L4 — Datomic schema (ECS-as-datoms) + scene graph + snapshot/restore.

  The KAMI scene model: a *component* is a Datomic attribute, an *entity* is a
  Datomic entity, an *archetype* is the set of attributes present. There is no
  separate ECS persistence format — the scene IS the database (ARCHITECTURE.md §5).

  This namespace is `.cljc`: the schema and snapshot *shape* are platform-neutral.
  Actual transacting/querying lives in `kami.db` (JVM); loading a snapshot into
  the runtime store lives in `kami.ecs` (cljc, runs in the browser).")

;; ---------------------------------------------------------------------------
;; Schema — declared as data (Datalog-portable across Datomic/datalevin/datahike)
;; ---------------------------------------------------------------------------

(def schema
  "Datomic/datalevin schema for the KAMI ECS. Transact once into a fresh conn.
  Tuples carry fixed-arity f32 vectors; refs build the scene DAG and asset table.
  See ARCHITECTURE.md §5 for the component → attribute mapping."
  ;; NOTE: written in the Datomic attribute-map style; datalevin accepts the
  ;; same :db/ident + :db/valueType + :db/cardinality vocabulary.
  [;; --- identity ---
   {:db/ident :kami/eid   :db/valueType :db.type/uuid   :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :kami/name  :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   ;; --- hierarchy / transform ---
   {:db/ident :transform/parent      :db/valueType :db.type/ref   :db/cardinality :db.cardinality/one}
   {:db/ident :transform/translation :db/valueType :db.type/tuple :db/tupleType :db.type/double :db/cardinality :db.cardinality/one}
   {:db/ident :transform/rotation    :db/valueType :db.type/tuple :db/tupleType :db.type/double :db/cardinality :db.cardinality/one}
   {:db/ident :transform/scale       :db/valueType :db.type/tuple :db/tupleType :db.type/double :db/cardinality :db.cardinality/one}
   ;; --- render components ---
   {:db/ident :mesh/asset       :db/valueType :db.type/ref    :db/cardinality :db.cardinality/one}
   {:db/ident :material/asset   :db/valueType :db.type/ref    :db/cardinality :db.cardinality/one}
   {:db/ident :material/params  :db/valueType :db.type/string :db/cardinality :db.cardinality/one} ; edn-encoded override map
   {:db/ident :shader/asset     :db/valueType :db.type/ref    :db/cardinality :db.cardinality/one}
   ;; --- camera / light ---
   {:db/ident :camera/fov     :db/valueType :db.type/double  :db/cardinality :db.cardinality/one}
   {:db/ident :camera/near    :db/valueType :db.type/double  :db/cardinality :db.cardinality/one}
   {:db/ident :camera/far     :db/valueType :db.type/double  :db/cardinality :db.cardinality/one}
   {:db/ident :camera/active? :db/valueType :db.type/boolean :db/cardinality :db.cardinality/one}
   {:db/ident :camera/projection :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one} ; :perspective (default) / :ortho
   {:db/ident :camera/ortho-w :db/valueType :db.type/double  :db/cardinality :db.cardinality/one}
   {:db/ident :camera/ortho-h :db/valueType :db.type/double  :db/cardinality :db.cardinality/one}
   {:db/ident :light/kind      :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one} ; :dir/:point/:spot
   {:db/ident :light/color     :db/valueType :db.type/tuple :db/tupleType :db.type/double :db/cardinality :db.cardinality/one}
   {:db/ident :light/intensity :db/valueType :db.type/double  :db/cardinality :db.cardinality/one}
   ;; --- systems / scene ---
   {:db/ident :script/systems :db/valueType :db.type/keyword :db/cardinality :db.cardinality/many}
   {:db/ident :scene/name :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :scene/root :db/valueType :db.type/ref    :db/cardinality :db.cardinality/one}
   {:db/ident :scene/env  :db/valueType :db.type/string :db/cardinality :db.cardinality/one} ; edn: {:clear [..] :sky .. :fog ..}
   ;; --- assets (content-addressed) ---
   {:db/ident :asset/id     :db/valueType :db.type/string  :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :asset/kind   :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one} ; :mesh/:material/:texture/:shader
   {:db/ident :asset/uri    :db/valueType :db.type/string  :db/cardinality :db.cardinality/one} ; B2/IPFS
   {:db/ident :asset/sha256 :db/valueType :db.type/string  :db/cardinality :db.cardinality/one}
   {:db/ident :asset/inline :db/valueType :db.type/string  :db/cardinality :db.cardinality/one}]) ; edn for procedural assets

(def render-components
  "The component attributes a renderable entity may carry. `kami.render` queries
  these to build the per-frame draw-list."
  #{:transform/translation :transform/rotation :transform/scale
    :mesh/asset :material/asset :shader/asset})

;; ---------------------------------------------------------------------------
;; Scene-graph helpers (pure, platform-neutral)
;; ---------------------------------------------------------------------------

(def ^:private known-attrs
  (into #{} (map :db/ident) schema))

(defn add-entity
  "Return tx-data (a 1-vector) for one entity. `components` is a plain component
  map, e.g. {:kami/name \"tree\" :transform/translation [0 0 0]
             :mesh/asset [:asset/id \"mesh/conifer\"]}.
  Ensures a stable :kami/eid (generates one if absent) and rejects unknown attrs."
  [components]
  (let [unknown (remove known-attrs (keys (dissoc components :db/id)))]
    (when (seq unknown)
      (throw (ex-info "add-entity: unknown component attrs" {:unknown (vec unknown)}))))
  [(cond-> components
     (not (:kami/eid components)) (assoc :kami/eid (random-uuid)))])

(defn- ref-id
  "Resolve a ref value to its lookup id: a map → :kami/eid|:asset/id,
  a lookup ref [:asset/id \"…\"] → the value, otherwise the value itself."
  [r]
  (cond (map? r)    (or (:asset/id r) (:kami/eid r))
        (vector? r) (second r)
        :else       r))

(defn tree
  "Build the parent→children scene DAG from a seq of entity maps.
  Returns {eid {:entity {…} :children [node …]}} keyed by :kami/eid. With no
  `root-eid`, returns {::roots [node …]} over every parentless entity.
  Pure given the entity maps."
  ([entity-maps] (tree entity-maps nil))
  ([entity-maps root-eid]
   (let [by-eid   (into {} (map (juxt :kami/eid identity)) entity-maps)
         children (reduce (fn [m e]
                            (if-let [p (some-> (:transform/parent e) ref-id)]
                              (update m p (fnil conj []) (:kami/eid e))
                              m))
                          {} entity-maps)
         node     (fn node [eid]
                    {:entity   (by-eid eid)
                     :children (mapv node (get children eid []))})]
     (if root-eid
       {root-eid (node root-eid)}
       {::roots (mapv node (->> entity-maps
                                (remove (comp some? :transform/parent))
                                (map :kami/eid)))}))))

(defn valid?
  "Check a scene snapshot for structural integrity. Returns true, or throws
  ex-info describing the first violation:
    - every :transform/parent resolves to a present entity
    - every asset ref resolves to a present :asset/id
    - at most one :camera/active? true
    - no :transform/parent cycles."
  [{:keys [:snapshot/entities :snapshot/assets]}]
  (let [eids      (into #{} (map :kami/eid) entities)
        asset-ids (into #{} (map :asset/id) assets)]
    (doseq [e entities :when (:transform/parent e)]
      (let [p (ref-id (:transform/parent e))]
        (when-not (eids p)
          (throw (ex-info "valid?: dangling :transform/parent" {:entity (:kami/eid e) :parent p})))))
    (doseq [e entities, k [:mesh/asset :material/asset :shader/asset] :when (k e)]
      (let [a (ref-id (k e))]
        (when-not (asset-ids a)
          (throw (ex-info "valid?: dangling asset ref" {:entity (:kami/eid e) :attr k :asset a})))))
    (let [actives (filter :camera/active? entities)]
      (when (> (count actives) 1)
        (throw (ex-info "valid?: multiple active cameras" {:cameras (mapv :kami/eid actives)}))))
    (let [parent-of (into {} (for [e entities :when (:transform/parent e)]
                               [(:kami/eid e) (ref-id (:transform/parent e))]))]
      (doseq [start (keys parent-of)]
        (loop [cur start, seen #{}]
          (when cur
            (when (seen cur)
              (throw (ex-info "valid?: transform cycle" {:at cur})))
            (recur (parent-of cur) (conj seen cur))))))
    true))

;; ---------------------------------------------------------------------------
;; Snapshot — portable scene-snapshot (the JVM→browser handoff)
;; ---------------------------------------------------------------------------

(defn build-snapshot
  "Pure snapshot constructor. `entities` and `assets` are plain component maps;
  `meta` carries {:t basis-t :scene name :env {…}}. Returns the portable map:

    {:snapshot/t .. :snapshot/scene .. :snapshot/env ..
     :snapshot/entities [..] :snapshot/assets [..]}

  `kami.db/snapshot` pulls datoms from a Datalog db then delegates here, keeping
  the projection logic pure and testable with no Datomic dependency."
  [entities assets {:keys [t scene env]}]
  {:snapshot/t        t
   :snapshot/scene    scene
   :snapshot/env      env
   :snapshot/entities (vec entities)
   :snapshot/assets   (vec assets)})
