(ns kotobase.engine
  "Public JVM database engine over the provider-neutral storage contract.

  The historical kotobase-peer implementation is deliberately hidden behind
  this database-shaped API."
  (:require [kotobase-peer.core :as peer]
            [kotobase.storage.core :as storage]))

(defrecord Database
  [storage ref-name encrypt-fn decrypt-fn blind-fn visible? max-retries
   tx-functions listeners])

(defrecord Db
  [connection basis-cid mode since-t value])

(defn open
  [{:keys [storage ref-name encrypt-fn decrypt-fn blind-fn visible? max-retries
           tx-functions]
    :or {ref-name "main" max-retries 16 tx-functions {}}}]
  (storage/validate-backend! storage)
  (doseq [[control value]
          [[:encrypt-fn encrypt-fn] [:decrypt-fn decrypt-fn]
           [:blind-fn blind-fn] [:visible? visible?]]]
    (when-not (ifn? value)
      (throw (ex-info "Kotobase engine requires an explicit security control"
                      {:type :kotobase.engine/missing-control
                       :control control}))))
  (when-not (every? ifn? (vals tx-functions))
    (throw (ex-info "Every transaction function must be callable"
                    {:type :kotobase.datomic/invalid-tx-functions})))
  (->Database storage ref-name encrypt-fn decrypt-fn blind-fn visible?
              max-retries tx-functions (atom {})))

(defn head [^Database database]
  (if (instance? Db database)
    (:basis-cid database)
    (some-> (storage/-read-ref (:storage database) (:ref-name database)) :cid)))

(defn db
  "Return an immutable database value pinned to the connection's current CID."
  [connection]
  (if (instance? Db connection)
    connection
    (->Db connection (head connection) :current nil nil)))

(defn- ensure-db [database]
  (if (instance? Db database) database (db database)))

(defn- connection [database]
  (if (instance? Db database) (:connection database) database))

(defn tx-function [database ident]
  (let [functions (:tx-functions (connection database))]
    (or (get functions ident) (get functions (str ident)))))

(defn listen!
  "Register an in-process transaction-report listener on CONNECTION."
  [database listener]
  (when-not (ifn? listener)
    (throw (ex-info "Listener must be callable"
                    {:type :kotobase.datomic/invalid-listener})))
  (let [database (connection database)
        id (str (java.util.UUID/randomUUID))]
    (swap! (:listeners database) assoc id listener)
    id))

(defn unlisten! [database listener-id]
  (let [database (connection database)
        present? (contains? @(:listeners database) listener-id)]
    (swap! (:listeners database) dissoc listener-id)
    present?))

(defn notify-listeners! [database tx-report]
  (doseq [[_ listener] @(:listeners (connection database))]
    (try
      (listener tx-report)
      (catch Throwable _ nil)))
  tx-report)

(defn as-of
  "Return an immutable database value at-or-before commit sequence T."
  [database t]
  (let [{:keys [basis-cid] :as database} (ensure-db database)
        {:keys [get-fn]} (storage/ports (:storage (connection database)))]
    (assoc database
           :basis-cid (peer/as-of get-fn basis-cid t)
           :mode :current
           :since-t nil)))

(defn since
  "Return a database value containing changes strictly after commit sequence T."
  [database t]
  (assoc (ensure-db database) :mode :since :since-t t))

(defn history
  "Return a database value whose datoms expose the complete assertion/retraction log."
  [database]
  (assoc (ensure-db database) :mode :history :since-t nil))

(defn basis-cid [database]
  (:basis-cid (ensure-db database)))

(defn basis-t [database]
  (let [{:keys [basis-cid] :as database} (ensure-db database)]
    (when basis-cid
      (let [{:keys [get-fn]} (storage/ports (:storage (connection database)))]
        (:seq (peer/head get-fn basis-cid))))))

(defn tx-range
  ([database] (tx-range database nil nil))
  ([database start end]
   (let [{:keys [basis-cid] :as snapshot} (ensure-db database)
         database (connection snapshot)
         {:keys [get-fn]} (storage/ports (:storage database))]
     (peer/tx-range get-fn basis-cid start end (:decrypt-fn database)))))

(defn transact!
  [^Database database tx-data]
  (when (instance? Db database)
    (throw (ex-info "Cannot transact against an immutable database value"
                    {:type :kotobase.datomic/immutable-db})))
  (let [{:keys [put! get-fn cas!]} (storage/ports (:storage database))]
    (peer/commit-serialized!
     put! get-fn cas! (:ref-name database) (head database) tx-data
     (:encrypt-fn database) (:max-retries database))))

(defn novelty-size
  "How many not-yet-folded tx blocks sit on `chain-cid` (kotobase-peer.core/
  novelty-size) -- O(1), reads a maintained counter field. `chain-cid` is a
  caller-supplied chain (e.g. `transact!`'s own return value) rather than
  re-deriving via `head`, avoiding a redundant ref read for the common case
  of checking novelty right after writing."
  [database chain-cid]
  (let [{:keys [get-fn]} (storage/ports (:storage database))]
    (peer/novelty-size get-fn chain-cid)))

(defn- db-value [database]
  (let [{:keys [basis-cid mode since-t] :as snapshot} (ensure-db database)
        database (connection snapshot)
        {:keys [get-fn]} (storage/ports (:storage database))]
    (or (:value snapshot)
        (case mode
          :since (peer/since get-fn basis-cid since-t (:decrypt-fn database))
          :history (peer/history get-fn basis-cid
                                 (:blind-fn database) (:decrypt-fn database))
          (peer/hydrate-chain get-fn basis-cid
                              (:blind-fn database) (:decrypt-fn database))))))

(defn with
  "Speculatively apply normalized TX-DATA to an immutable database value."
  [database tx-data]
  (let [before (ensure-db database)
        after-value (peer/transact (db-value before) tx-data)]
    {:db-before before
     :db-after (assoc before :value after-value)
     :tx-data tx-data}))

(defn- history-row-matches? [options {:keys [e a v_edn]}]
  (let [value (read-string v_edn)
        key (case (or (:index options) :eavt)
              :eavt [e a value]
              :aevt [a e value]
              :avet [a value e]
              :vaet [value a e])
        components (:components options)]
    (= (vec components) (subvec key 0 (min (count key)
                                            (count components))))))

(defn datoms
  ([database] (datoms database nil))
  ([database options]
   (let [{:keys [basis-cid mode] :as snapshot} (ensure-db database)
         database (connection snapshot)
         {:keys [get-fn]} (storage/ports (:storage database))]
     (if (= :history mode)
       (let [entity (when (and (= :eavt (:index options))
                               (seq (:components options)))
                      (first (:components options)))
             rows (peer/history-datoms get-fn basis-cid entity
                                       (:visible? database)
                                       (:decrypt-fn database))]
         (cond->> rows
           (seq (:components options))
           (filter #(history-row-matches? options %))
           (:limit options) (take (:limit options))
           true vec))
       (peer/datoms (db-value snapshot) options (:visible? database))))))

(defn q [database pattern]
  (peer/q (db-value database) pattern (:visible? (connection database))))

(defn query
  ([database query] (query database query []))
  ([database query inputs]
   (peer/query (db-value database) query
               (:visible? (connection database)) inputs)))

(defn pull [database entity pattern]
  (peer/pull (db-value database) entity pattern))

(defn pull-many [database pattern entities]
  (mapv #(pull database % pattern) entities))

(defn entity [database entity-id]
  (peer/entity (db-value database) entity-id))

(defn entid [database id]
  (peer/entid (db-value database) id))

(defn ident [database id]
  (peer/ident (db-value database) id))

(defn fold!
  "Compact the database's accumulated novelty into a fresh indexed
  snapshot. Self-retrying on head-CAS contention
  (`kotobase-peer.core/fold-serialized-if-needed!`), the JVM sibling of
  the cljs `fold!` in `engine.cljs` (see its docstring for `opts`, in
  particular `:views`)."
  ([database] (fold! database {}))
  ([^Database database opts]
   (when (instance? Db database)
     (throw (ex-info "Cannot fold an immutable database value"
                     {:type :kotobase.datomic/immutable-db})))
   (let [{:keys [put! get-fn cas!]} (storage/ports (:storage database))]
     (peer/fold-serialized-if-needed!
      put! get-fn cas! (:ref-name database) (head database)
      (:blind-fn database) (:encrypt-fn database) (:decrypt-fn database)
      opts))))

(defn view
  "Rows of a fold-materialized view, always fresh. nil when the graph has
  no head yet or the view isn't declared (a prior `fold!` with `:views`
  declares it). JVM sibling of `engine.cljs`'s `view` (see its docstring)."
  [database view-name]
  (let [current (head database)]
    (when current
      (let [conn (connection database)
            {:keys [get-fn]} (storage/ports (:storage conn))]
        (peer/view-rows get-fn current view-name (:visible? conn) (:decrypt-fn conn))))))
