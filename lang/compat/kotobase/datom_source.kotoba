(ns kotobase.datom-source
  "`datom.source/IPatternSource` over the Datomic API's index reads.

  ## Why this exists

  `kotoba-lang/datom-source`'s own docstring states the problem it was
  extracted to solve: kotobase's query namespaces take a MATERIALIZED db, and
  that one choice fixes the cost of every query at **O(database) rather than
  O(result)** — producing the db means reading the whole index tree first.
  The `IPatternSource` seam exists so a source can answer `[s p o]` without
  that, and its rule for where implementations live is explicit: *in whichever
  library owns the storage they read*. This library owns the Datomic API
  client, so the Datomic-API-backed source belongs here.

  `datomic.datoms` is already the right shape to be that source. It is a
  FILTERED index read — `{:graph :index :components_edn :limit}` over
  `:eavt`/`:aevt`/`:avet`, served by `hot-datoms` (snapshot + novelty merge,
  range-pruned on the snapshot side, never a whole-graph rehydrate). One
  triple pattern is one `datoms` call with its bound positions pushed down.

  **This is the Datomic API as an index, not as Datalog.** A caller with its
  own algebra (SPARQL's BGP/join/filter/union/optional, a property-graph
  traversal) binds to the index and keeps its own semantics; it does not get
  translated into `:find`/`:where` first (superproject ADR-2608039970).

  ## Quads carry stored VALUES, not `v_edn`

  A row reports the object as its EDN encoding; this decodes one level, so
  `:o` is the value the write path stored. Pattern components are in the
  same representation — a caller holding a logical value passes `(str v)`,
  not `(pr-str v)`. Measured against the datom plane
  (`kotobase-server#29`): `:avet` components filter on the STORED value and
  match nothing against the encoded one.

  ## Async construction, synchronous scanning

  `IPatternSource/-scan` is synchronous by contract, and `kotoba-lang/sparql`'s
  algebra walks it synchronously. `datoms` returns a Promise. Rather than make
  either of those async, this namespace **prefetches the patterns a query
  actually mentions** and hands back a plain source over the result:

      (-> (from-client client \"my-db\" patterns)
          (.then (fn [source] (sparql/select algebra (src/scan source ...)))))

  Same shape the kotobase Worker already uses for documents (hydrate before
  the synchronous router runs) — and the important property survives: what is
  fetched is **the patterns the query names**, not the database. A BGP over
  two predicates reads two index ranges, whatever the graph's size.

  ## Pattern → index

  | pattern      | index   | components | note |
  |--------------|---------|------------|------|
  | `[s _ _]`    | `:eavt` | `[s]`      | entity prefix |
  | `[s p _]`    | `:eavt` | `[s p]`    | |
  | `[_ p o]`    | `:avet` | `[p o]`    | the selective one |
  | `[_ p _]`    | `:aevt` | `[p]`      | |
  | `[_ _ o]`    | `:eavt` | `[]`       | **honest full scan** — see below |
  | `[_ _ _]`    | `:eavt` | `[]`       | full scan, as asked |

  **`[_ _ o]` has no index and this namespace does not pretend otherwise.**
  `:vaet` covers only ref-valued attributes in Datomic's model, so a literal
  object is not reachable through it; the read degrades to a full `:eavt` scan
  with an in-memory filter. `datalog.query` makes exactly the same call and
  says so in the same words — a correct answer at O(database) is better than a
  fast wrong one, and a caller that cares can bind a predicate.

  ## What a quad is here

  Rows come back as `{e a v_edn added}`. The quad is `{:s e :p a :o v_edn}` —
  **the object stays the EDN wire string as stored**, not a decoded value.
  That matches how the datom plane's own SPARQL surface already treats values
  (`kotobase.server.sparql`: *values are stored as wire strings*), and a
  decode here would have to be undone by every caller that compares against a
  literal from a query string.

  Retractions are dropped: only `added` rows are returned, so a scan answers
  what is currently asserted."
  (:require [cljs.reader :as reader]
            [datom.source :as src]
            [kotobase.client :as kc]
            [kotobase.datom-plan :as plan]))


(def plan
  "See `kotobase.datom-plan/plan`. Re-exported so a caller reasoning
  about this source's reads does not have to know the plan was extracted to a
  portable namespace for `kotobase-server`'s in-process use."
  plan/plan)

(defn- row->quad-fields [^js row]
  {:e (.-e row) :a (.-a row) :v_edn (.-v_edn row) :added (.-added row)})

(defn rows->quads
  "`.-datoms` rows -> asserted quads. Retractions are dropped.

  The object is DECODED one level: a row reports `v_edn` — the EDN encoding
  of the stored value — and a quad carries the value itself. `30` stored as
  the string \"30\" arrives as the four characters `\"30\"` and comes back out
  as `30`'s stored form, `\"30\"`.

  That is the representation a consumer can work in: a filter comparing
  `?v > 25` can parse `\"30\"` and can do nothing with its encoding. It also
  matches `kotobase.server.pattern-source`, the in-process implementation of
  this same seam — the two would otherwise hand the same query different
  values depending on which transport answered it."
  [rows]
  (into #{}
        (map (fn [q] (update q :o #(when (string? %) (reader/read-string %)))))
        (plan/rows->quads row->quad-fields (array-seq (or rows #js [])))))

;; -------------------------------------------------------------------- prefetch

(defn prefetch
  "`datoms-fn` : (fn [index components] -> Promise of `.-datoms` rows).
  `patterns` : the `[s p o]` patterns the caller will scan.

  -> Promise of an `IPatternSource` over the union of what those patterns
  read. Duplicate plans are issued once: two patterns that differ only in a
  position the index cannot bind are the same read.

  The source is `datom.source/of-quads`, so it conforms by construction —
  what this namespace has to get right is the PLAN, and that is what the
  conformance test exercises."
  [datoms-fn patterns]
  (let [plans (plan/reads patterns)]
    (-> (js/Promise.all
         (clj->js (map (fn [{:keys [index components]}]
                         (-> (js/Promise.resolve (datoms-fn index components))
                             (.then rows->quads)))
                       plans)))
        (.then (fn [results]
                 (src/of-quads (plan/union-quads (array-seq results))))))))

(defn from-client
  "`prefetch` wired to `kotobase.client/datoms` for `db-name`.

  `opts` are passed through to `datoms` (`:limit`, `:public?`). A `:limit`
  applies PER READ, not per query — it is the index read's own limit, so a
  query whose patterns each match more than `:limit` datoms gets a truncated
  answer with no error. Leave it unset unless the caller can accept that."
  ([client db-name patterns] (from-client client db-name patterns nil))
  ([client db-name patterns opts]
   (prefetch (fn [index components]
               (-> (kc/datoms client db-name index
                              (cond-> (or opts {})
                                (seq components) (assoc :components components)))
                   (.then (fn [^js resp] (.-datoms resp)))))
             patterns)))
