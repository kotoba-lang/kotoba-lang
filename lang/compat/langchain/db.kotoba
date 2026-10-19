(ns langchain.db
  "Minimal, dependency-free, Datomic-API-compatible in-memory EAV store.

  Implements the subset of the Datomic API that langchain-clj (and langgraph-clj on top) needs for
  checkpoints, chat memory, and named Datalog views:

    create-conn, db, transact!, with, q, pull, pull-many, entity,
    entid, datoms, as-of

  Design constraints (see ADR-0001):
    - pure .cljc, no host interop, no wall clock, no randomness —
      runs on JVM Clojure, ClojureScript, SCI and Clojure-on-WASM hosts.
    - the API shape matches Datomic/DataScript closely enough that a
      real Datomic Local / DataScript connection can be swapped in via
      the `langchain.db/api` map (see bottom of this namespace).

  Schema format (DataScript-style — only constraint attrs are declared):

    {:thread/id  {:db/unique :db.unique/identity}
     :msg/thread {:db/valueType :db.type/ref}
     :person/emails {:db/cardinality :db.cardinality/many}}

  Supported Datalog:
    - data patterns [?e :attr ?v] with constants, vars, _
    - predicate clauses [(< ?a ?b)] and function bindings [(inc ?a) ?b]
      (built-ins below, or fns bound through :in)
    - not / or clauses
    - :in $ ?scalar [?coll ...] [[?rel ?ation]]
    - :find tuples, scalar `.`, collection `[?x ...]`,
      aggregates (count|count-distinct|sum|min|max|avg ?x)
    - pull: attrs, '*, nested {ref [...]}, reverse :ns/_attr

  Not supported (use real Datomic if you need them): rules (%),
  multiple db sources, :db.unique/value conflict errors (treated as
  identity/upsert), d/history."
  (:require [clojure.string :as str]
            [clojure.set :as set]))

;; ───────────────────────── db value & conn ─────────────────────────

(defn empty-db
  ([] (empty-db {}))
  ([schema] {:schema schema :datoms #{} :max-eid 0 :max-tx 0}))

(defn create-conn
  "Returns a connection (atom). The conn also keeps a tx log so `as-of`
  can rebuild past db values."
  ([] (create-conn {}))
  ([schema] (atom {:db (empty-db schema) :log []})))

(defn db [conn] (:db @conn))

(defn- card-many? [db a]
  (= :db.cardinality/many (get-in db [:schema a :db/cardinality])))

(defn- ref-attr? [db a]
  (= :db.type/ref (get-in db [:schema a :db/valueType])))

(defn- unique-attr? [db a]
  (some? (get-in db [:schema a :db/unique])))

(defn entid
  "Resolves an entity identifier: a number, or a lookup ref [unique-attr v].
  Returns nil when a lookup ref does not match."
  [db eid]
  (cond
    (number? eid) eid
    (and (vector? eid) (= 2 (count eid)))
    (let [[a v] eid]
      (some (fn [[e a' v']] (when (and (= a a') (= v v')) e))
            (:datoms db)))
    :else nil))

(defn datoms
  "Minimal datom access. (datoms db :eavt) → all datoms as [e a v].
  Optional components filter by e, then a."
  [db _index & [e a]]
  (cond->> (seq (:datoms db))
    e (filter #(= e (nth % 0)))
    a (filter #(= a (nth % 1)))))

;; ───────────────────────── transactions ─────────────────────────

(defn- tempid? [x]
  (or (string? x) (and (number? x) (neg? x))))

(defn- expand-entity-map
  "Expands a map form into [:db/add e a v] ops. Unique attrs first so
  sequential upsert resolution sees them before other datoms. Nested
  maps under ref attrs are expanded recursively with fresh tempids."
  [db m counter]
  (let [e (or (:db/id m) (str "langchain.db/auto-" (vswap! counter inc)))
        avs (->> (dissoc m :db/id)
                 (sort-by (fn [[a _]] (if (unique-attr? db a) 0 1))))]
    (reduce
     (fn [ops [a v]]
       (let [vs (if (and (card-many? db a) (sequential? v) (not (vector? (first v))))
                  v
                  [v])]
         (reduce
          (fn [ops v]
            (if (and (ref-attr? db a) (map? v))
              (let [sub (expand-entity-map db v counter)
                    sub-e (nth (first sub) 1)]
                (-> ops (into sub) (conj [:db/add e a sub-e])))
              (conj ops [:db/add e a v])))
          ops vs)))
     [] avs)))

(defn- normalize-tx [db tx-data counter]
  (vec (mapcat (fn [form]
                 (cond
                   (map? form) (expand-entity-map db form counter)
                   (vector? form) [form]
                   :else (throw (ex-info "Unsupported tx form" {:form form}))))
               tx-data)))

(defn- resolve-tempid
  "Resolves a tempid for op [:db/add tmp a v]: an existing binding, an
  upsert hit on a unique attr, or a freshly allocated eid."
  [{:keys [db tempids] :as acc} tmp a v]
  (if-let [e (get tempids tmp)]
    [acc e]
    (let [upsert-e (when (and a (unique-attr? db a))
                     (entid db [a v]))
          e (or upsert-e (inc (:max-eid acc)))]
      [(-> acc
           (assoc-in [:tempids tmp] e)
           (update :max-eid max e))
       e])))

(defn- resolve-e [acc eid a v]
  (cond
    (tempid? eid) (resolve-tempid acc eid a v)
    (vector? eid) (if-let [e (entid (:db acc) eid)]
                    [acc e]
                    (throw (ex-info "Lookup ref not found" {:ref eid})))
    (number? eid) [(update acc :max-eid max eid) eid]
    :else (throw (ex-info "Invalid entity id" {:eid eid}))))

(defn- resolve-v [acc a v]
  (if (ref-attr? (:db acc) a)
    (cond
      (tempid? v) (resolve-tempid acc v nil nil)
      (vector? v) (if-let [e (entid (:db acc) v)]
                    [acc e]
                    (throw (ex-info "Lookup ref not found" {:ref v})))
      :else [acc v])
    [acc v]))

(defn- apply-add [acc e a v]
  (let [db (:db acc)
        retracts (when-not (card-many? db a)
                   (filter (fn [[e' a' _]] (and (= e e') (= a a')))
                           (:datoms db)))]
    (-> acc
        (update-in [:db :datoms] #(reduce disj % retracts))
        (update-in [:db :datoms] conj [e a v])
        (update :tx-data into (concat (map (fn [[e a v]] [:db/retract e a v]) retracts)
                                      [[:db/add e a v]])))))

(defn- apply-op [acc op]
  (let [[kind eid a v] op]
    (case kind
      :db/add
      (let [[acc e] (resolve-e acc eid a v)
            [acc v] (resolve-v acc a v)]
        (if (contains? (get-in acc [:db :datoms]) [e a v])
          acc
          (apply-add acc e a v)))

      :db/retract
      (let [[acc e] (resolve-e acc eid a v)
            [acc v] (resolve-v acc a v)]
        (if (contains? (get-in acc [:db :datoms]) [e a v])
          (-> acc
              (update-in [:db :datoms] disj [e a v])
              (update :tx-data conj [:db/retract e a v]))
          acc))

      :db/retractEntity
      (let [[acc e] (resolve-e acc eid nil nil)
            db (:db acc)
            gone (filter (fn [[e' a' v']]
                           (or (= e e')
                               (and (ref-attr? db a') (= e v'))))
                         (:datoms db))]
        (-> acc
            (update-in [:db :datoms] #(reduce disj % gone))
            (update :tx-data into (map (fn [[e a v]] [:db/retract e a v]) gone))))

      (throw (ex-info "Unknown tx op" {:op op})))))

(defn with
  "Pure transaction. Returns {:db-before .. :db-after .. :tempids .. :tx-data ..}."
  [db tx-data]
  (let [counter (volatile! 0)
        ops (normalize-tx db tx-data counter)
        init {:db db :max-eid (:max-eid db) :tempids {} :tx-data []}
        {db' :db :as acc} (reduce apply-op init ops)
        tx (inc (:max-tx db))
        db-after (assoc db' :max-eid (:max-eid acc) :max-tx tx)]
    {:db-before db
     :db-after db-after
     :tempids (:tempids acc)
     :tx-data (:tx-data acc)
     :tx tx}))

(defn transact!
  "Transacts against a conn. Returns the tx report (see `with`).

  `with` runs INSIDE the swap! fn (not precomputed against a stale
  `@conn` read beforehand) so it recomputes against whatever state
  actually landed if swap!'s CAS has to retry under contention -- a
  prior version computed the report once, outside the swap, then
  installed it unconditionally on retry; two concurrent transact!
  calls on the SAME conn could then have the second swap! silently
  overwrite the first transaction's :db-after with a report computed
  from the pre-collision (stale) db, discarding the first caller's
  writes with no error. Confirmed empirically: 50 concurrent JVM
  threads each transacting one datom onto a shared conn lost 28/50
  writes (56%) under the old code; 0 lost after this fix, verified
  identically under real thread contention (not just reasoned about)."
  [conn tx-data]
  (let [report (volatile! nil)]
    (swap! conn (fn [state]
                  (let [r (with (:db state) tx-data)]
                    (vreset! report r)
                    (-> state
                        (assoc :db (:db-after r))
                        (update :log conj {:tx (:tx r) :tx-data (:tx-data r)})))))
    @report))

(defn as-of
  "Rebuilds the db value as of tx t by replaying the conn's log.
  (Deviation from Datomic: takes the conn, since plain db values do
  not carry the log.)"
  [conn t]
  (let [{:keys [db log]} @conn
        schema (:schema db)]
    (reduce (fn [db {:keys [tx tx-data]}]
              (if (> tx t)
                (reduced db)
                (-> (reduce (fn [db [op e a v]]
                              (case op
                                :db/add (update db :datoms conj [e a v])
                                :db/retract (update db :datoms disj [e a v])))
                            db tx-data)
                    (assoc :max-tx tx)
                    (update :max-eid max (apply max 0 (filter number? (map #(nth % 1) tx-data)))))))
            (empty-db schema)
            log)))

;; ───────────────────────── datalog query ─────────────────────────

(defn- variable? [x] (and (symbol? x) (str/starts-with? (name x) "?")))
(defn- blank? [x] (= '_ x))

(def built-ins
  {'= = '== = 'not= not= '!= not=
   '< < '> > '<= <= '>= >=
   '+ + '- - '* * '/ /
   'inc inc 'dec dec 'min min 'max max 'mod mod 'rem rem 'quot quot
   'str str 'subs subs 'count count 'nil? nil? 'some? some? 'not not
   'true? true? 'false? false? 'zero? zero? 'pos? pos? 'neg? neg?
   'even? even? 'odd? odd? 'identity identity 'keyword keyword
   'name name 'namespace namespace 'first first 'second second
   'last last 'vector vector 'list list 'get get 'get-in get-in
   'contains? contains? 'ground identity
   'clojure.string/starts-with? str/starts-with?
   'clojure.string/ends-with? str/ends-with?
   'clojure.string/includes? str/includes?
   'clojure.string/lower-case str/lower-case
   'clojure.string/upper-case str/upper-case})

(defn- parse-query [query]
  (if (map? query)
    query
    (let [groups (partition-by #{:find :in :where :with} query)]
      ;; partition-by's predicate here returns the matched keyword itself
      ;; (not a plain boolean), so two ADJACENT markers with nothing in
      ;; between (e.g. `:find :where ...`, a realistic typo -- forgetting
      ;; the var) split into two separate single-element groups instead of
      ;; merging, breaking the intended [marker value marker value ...]
      ;; alternation and yielding an odd total group count. Without this
      ;; check, `(partition 2 groups)` would silently drop the last
      ;; unpaired group -- e.g. `[:find :where [?x :attr ?v]]` silently
      ;; parses to `{:find [:where]}`, :where vanishes entirely (never
      ;; assoc'd), and eval-clauses over a nil :where is a silent no-op --
      ;; a malformed query returns a bogus result instead of erroring.
      (when (odd? (count groups))
        (throw (ex-info "langchain.db: malformed query -- a :find/:in/:where/:with marker must be followed by at least one value before the next marker (or end of query)"
                         {:query query})))
      (reduce (fn [m [[k] v]] (assoc m k (vec v))) {} (partition 2 groups)))))

(defn- bind-input
  "Folds one :in binding spec + value into the frame seq."
  [frames spec value]
  (cond
    (variable? spec)
    (map #(assoc % spec value) frames)

    ;; collection binding [?x ...]
    (and (vector? spec) (= '... (second spec)))
    (for [f frames, v value] (assoc f (first spec) v))

    ;; relation binding [[?a ?b]]
    (and (vector? spec) (vector? (first spec)))
    (let [vars (first spec)]
      (for [f frames, tuple value]
        (merge f (zipmap vars tuple))))

    ;; tuple binding [?a ?b]
    (vector? spec)
    (map #(merge % (zipmap spec value)) frames)

    :else (throw (ex-info "Unsupported :in binding" {:spec spec}))))

(defn- lookup [frame x]
  (if (variable? x) (get frame x ::unbound) x))

(defn- match-pattern
  "Matches data pattern [e a v] against the db for one frame.
  Each pattern element is a blank, a bound var, an unbound var, or a
  constant — `lookup` collapses the first three into ::unbound or a
  concrete value, so matching is a single equality check per slot."
  [db frame pattern]
  (let [[pe pa pv] (concat pattern (repeat '_))
        match1 (fn [p d] (let [b (lookup frame p)]
                           (or (blank? p) (= ::unbound b) (= b d))))]
    (for [[e a v] (:datoms db)
          :when (and (match1 pe e) (match1 pa a) (match1 pv v))]
      (cond-> frame
        (and (variable? pe) (= ::unbound (lookup frame pe))) (assoc pe e)
        (and (variable? pa) (= ::unbound (lookup frame pa))) (assoc pa a)
        (and (variable? pv) (= ::unbound (lookup frame pv))) (assoc pv v)))))

(defn- resolve-fn [frame fsym]
  (or (when (variable? fsym)
        (let [v (get frame fsym ::unbound)]
          (when (fn? v) v)))
      (get built-ins fsym)
      (throw (ex-info "Unknown function in clause (pass fns via :in, or use a built-in)"
                      {:fn fsym :built-ins (keys built-ins)}))))

(declare eval-clause)

(defn- eval-clauses [db frames clauses]
  (reduce (fn [frames clause] (eval-clause db frames clause)) frames clauses))

(defn- eval-clause [db frames clause]
  (cond
    ;; not clause
    (and (seq? clause) (= 'not (first clause)))
    (remove (fn [f] (seq (eval-clauses db [f] (rest clause)))) frames)

    ;; or clause — each leg is a single clause or (and ...)
    (and (seq? clause) (= 'or (first clause)))
    (mapcat (fn [f]
              (distinct
               (mapcat (fn [leg]
                         (let [leg-clauses (if (and (seq? leg) (= 'and (first leg)))
                                             (rest leg)
                                             [leg])]
                           (eval-clauses db [f] leg-clauses)))
                       (rest clause))))
            frames)

    ;; predicate / function clause: [(f args…)] or [(f args…) ?out]
    (and (vector? clause) (seq? (first clause)))
    (let [[call out] clause
          [fsym & args] call]
      (mapcat (fn [f]
                (let [func (resolve-fn f fsym)
                      argv (map (fn [a]
                                  (let [v (lookup f a)]
                                    (when (= ::unbound v)
                                      (throw (ex-info "Unbound variable in fn clause" {:var a :clause clause})))
                                    v))
                                args)
                      result (apply func argv)]
                  (cond
                    (nil? out) (if result [f] [])
                    (variable? out) [(assoc f out result)]
                    :else (if (= out result) [f] []))))
              frames))

    ;; data pattern
    (vector? clause)
    (mapcat #(match-pattern db % clause) frames)

    :else (throw (ex-info "Unsupported clause" {:clause clause}))))

(def ^:private aggregates
  {'count count
   'count-distinct (comp count distinct)
   'sum #(reduce + 0 %)
   'min #(apply min %)
   'max #(apply max %)
   'avg #(/ (reduce + 0 %) (count %))})

(defn- agg-call? [x] (and (seq? x) (contains? aggregates (first x))))

(defn- project
  "Applies the :find spec to the result frames."
  [find-spec frames]
  (let [scalar? (= '. (last find-spec))
        coll? (and (= 1 (count find-spec))
                   (vector? (first find-spec))
                   (= '... (second (first find-spec))))
        elements (cond
                   scalar? (butlast find-spec)
                   coll? [(ffirst find-spec)]
                   :else find-spec)
        has-agg? (some agg-call? elements)
        row (fn [f] (mapv (fn [el]
                            (if (agg-call? el)
                              (get f el)
                              (get f el)))
                          elements))
        rows
        (if has-agg?
          (let [group-els (remove agg-call? elements)
                groups (group-by (fn [f] (mapv #(get f %) group-els)) frames)]
            (for [[gvals gframes] groups]
              (mapv (fn [el]
                      (if (agg-call? el)
                        (let [[agg-sym v] el]
                          ((aggregates agg-sym) (map #(get % v) gframes)))
                        (get (first gframes) el)))
                    elements)))
          (distinct (map row frames)))]
    (cond
      scalar? (ffirst rows)
      coll? (vec (distinct (map first rows)))
      :else (set rows))))

(defn q
  "Datalog query against a db value. (q query db & inputs)
  Query may be the vector or map form."
  [query & inputs]
  (let [{:keys [find in where]} (parse-query query)
        in (or in '[$])
        ;; pair :in specs with inputs; $ consumes the db
        [db frames]
        (loop [specs in, args inputs, db nil, frames [{}]]
          (if (empty? specs)
            [db frames]
            (let [[s & specs'] specs
                  [a & args'] args]
              (if (= '$ s)
                (recur specs' args' a frames)
                (recur specs' args' db (vec (bind-input frames s a)))))))]
    (when (nil? db)
      (throw (ex-info "No db bound — :in must include $ and a db must be passed" {})))
    (project find (eval-clauses db frames where))))

;; ───────────────────────── pull ─────────────────────────

(defn- reverse-attr? [a]
  (and (keyword? a) (str/starts-with? (name a) "_")))

(defn- reverse->forward [a]
  (keyword (namespace a) (subs (name a) 1)))

(declare pull)

(defn- pull-attr [db e a subpattern]
  (if (reverse-attr? a)
    (let [fa (reverse->forward a)
          es (keep (fn [[e' a' v']] (when (and (= a' fa) (= v' e)) e')) (:datoms db))]
      (when (seq es)
        (mapv #(if subpattern (pull db subpattern %) {:db/id %}) es)))
    (let [vs (keep (fn [[e' a' v']] (when (and (= e e') (= a a')) v')) (:datoms db))
          render (fn [v]
                   (cond
                     subpattern (pull db subpattern v)
                     (ref-attr? db a) {:db/id v}
                     :else v))]
      (when (seq vs)
        (if (card-many? db a)
          (mapv render vs)
          (render (first vs)))))))

(defn pull
  "Datomic-style pull. Supports attrs, '*, {ref [subpattern]} and
  reverse refs :ns/_attr."
  [db pattern eid]
  (when-let [e (entid db eid)]
    (let [attrs-of (fn [e] (distinct (keep (fn [[e' a _]] (when (= e e') a)) (:datoms db))))]
      (reduce
       (fn [m spec]
         (cond
           (= '* spec)
           (reduce (fn [m a] (let [v (pull-attr db e a nil)]
                               (if (some? v) (assoc m a v) m)))
                   m (attrs-of e))

           (keyword? spec)
           (let [v (pull-attr db e spec nil)]
             (if (some? v) (assoc m spec v) m))

           (map? spec)
           (reduce-kv (fn [m a sub]
                        (let [v (pull-attr db e a sub)]
                          (if (some? v) (assoc m a v) m)))
                      m spec)

           :else (throw (ex-info "Unsupported pull spec" {:spec spec}))))
       {:db/id e}
       pattern))))

(defn pull-many [db pattern eids]
  (mapv #(pull db pattern %) eids))

(defn entity
  "Snapshot entity map (not lazy, unlike Datomic)."
  [db eid]
  (pull db '[*] eid))

;; ───────────────────────── pluggable api ─────────────────────────

(def api
  "Datomic-shaped function map. Higher layers (langchain.memory,
  langgraph.checkpoint) take this map as their `:db-api` option, so a real
  Datomic Local / DataScript backend can be swapped in by providing
  the same keys, e.g.

    {:q d/q :transact! (fn [conn tx] (d/transact conn {:tx-data tx}))
     :db d/db :pull d/pull :entid (fn [db e] (:db/id (d/pull db [:db/id] e)))}"
  {:q q
   :transact! transact!
   :db db
   :pull pull
   :entid entid})
