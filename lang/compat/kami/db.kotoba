(ns kami.db
  "L4 — Datalog connection, transaction, time-travel, query helpers + snapshot
  projection. JVM-only (the browser never touches the DB; it loads snapshots,
  §4/§6).

  Default impl targets **datalevin** (OSS, embeddable, blockchain-friendly — see
  ADR-CLJ-02). connect/db/transact!/q/pull use the portable Datalog vocabulary;
  as-of/history require a time-travel-capable store (Datomic Cloud/Peer) and are
  guarded. All scene attributes come from `kami.scene/schema`."
  (:require [datalevin.core :as d]
            [kami.scene :as scene]))

;; ---------------------------------------------------------------------------
;; Connection / read basis
;; ---------------------------------------------------------------------------

(defn- ->schema-map
  "datalevin wants schema as {ident {:db/valueType .. :db/cardinality ..}}.
  Reshape `kami.scene/schema` (a vector of attribute maps) accordingly."
  []
  (into {} (map (fn [{:keys [:db/ident] :as a}] [ident (dissoc a :db/ident)]))
        scene/schema))

(defn connect
  "Open/create a datalevin store at `dir` with the KAMI scene schema installed.
  Returns a conn. Idempotent on schema."
  [dir]
  (d/get-conn dir (->schema-map)))

(defn db
  "Current immutable db value of `conn`."
  [conn]
  (d/db conn))

(defn transact!
  "Transact `tx-data` (from `kami.scene/add-entity` or `kami.ecs/->tx`).
  Returns the tx-report; `(:tx-data report)` etc. The save path of §6."
  [conn tx-data]
  (d/transact! conn tx-data))

(defn q
  "Run a Datalog query against a db value (or conn). Thin pass-through kept here
  so callers depend on `kami.db`, not a specific Datalog impl."
  [query db & inputs]
  (apply d/q query db inputs))

;; ---------------------------------------------------------------------------
;; Time travel (undo / provenance) — store-capability dependent
;; ---------------------------------------------------------------------------

(defn as-of
  "A db value as of basis-t `t` — the engine of undo / timeline scrubbing
  (re-`snapshot` the result and ship it to the browser to rewind). Requires a
  time-travel store; throws on stores without it (see ADR-CLJ-02)."
  [db t]
  (if-let [f (resolve 'datalevin.core/as-of)]
    (f db t)
    (throw (ex-info "as-of: configured store has no time-travel; use Datomic Cloud/Peer"
                    {:t t}))))

(defn history
  "A history db value (every assertion/retraction). Powers edit provenance.
  Store-capability dependent, like `as-of`."
  [db]
  (if-let [f (resolve 'datalevin.core/history)]
    (f db)
    (throw (ex-info "history: configured store has no history view" {}))))

;; ---------------------------------------------------------------------------
;; Snapshot — pull datoms, delegate to the pure builder
;; ---------------------------------------------------------------------------

(def ^:private ref-attrs
  (into #{} (comp (filter #(= :db.type/ref (:db/valueType %))) (map :db/ident))
        scene/schema))

(def ^:private entity-pull
  "Explicit pull pattern: scalars as-is; refs pulled to their lookup keys so the
  portable snapshot carries {:asset/id ..}/{:kami/eid ..} maps, not :db/id ints."
  (into [] (for [{:keys [:db/ident]} scene/schema]
             (if (ref-attrs ident)
               {ident [:kami/eid :asset/id]}
               ident))))

(defn- clean
  "Drop :db/id and nil attrs from a pulled entity map."
  [m]
  (into {} (remove (fn [[k v]] (or (= k :db/id) (nil? v)))) m))

(defn snapshot
  "Project a db value into a portable scene-snapshot (plain data, transit-
  serializable) the browser loads with no DB dependency. Pulls every entity with
  :kami/eid and every asset with :asset/id at `db`'s basis, then delegates to
  `kami.scene/build-snapshot`. `opts` may carry :scene and :env metadata."
  ([db] (snapshot db {}))
  ([db {:keys [scene env] :as _opts}]
   (let [ent-eids (d/q '[:find [?e ...] :where [?e :kami/eid]] db)
         ast-eids (d/q '[:find [?e ...] :where [?e :asset/id]] db)
         entities (->> (d/pull-many db entity-pull ent-eids) (map clean))
         assets   (->> (d/pull-many db [:asset/id :asset/kind :asset/uri
                                        :asset/sha256 :asset/inline] ast-eids)
                       (map clean))
         ;; portable basis-t: highest tx id present across the eav index.
         ;; (datalevin doesn't support the [_ _ _ ?tx] datom-pattern aggregate.)
         t        (reduce max 0 (map :tx (d/datoms db :eav)))]
     (scene/build-snapshot entities assets {:t t :scene scene :env env}))))
