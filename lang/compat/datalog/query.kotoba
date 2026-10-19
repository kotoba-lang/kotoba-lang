(ns datalog.query
  "`[s p o]` triple-pattern query with wildcards (nil) over a `datalog.index`
  db, routing to whichever index matches the bound positions -- bound subject
  -> `:eavt`, bound predicate only -> `:aevt`, bound predicate + bound object
  -> `:avet`, bound object only -> honest full scan, fully unbound -> full
  `:eavt` scan.

  Every entry point here calls `index/check-shape!` first, so a db built by a
  pre-rename `datalog` raises instead of scanning a `nil` index and reporting
  zero rows as a successful answer.

  This is a pure routing function over `datalog.index`'s four indices. It has
  no storage of its own and reads only the in-memory db it is handed.

  `query`'s `visible?` is REQUIRED: a query is not a bare read. This
  namespace stays auth-agnostic by design -- no purpose/scope/capability
  opinion lives here -- but it refuses to run a query without the caller
  stating a visibility decision, because there is no permissive default that
  is safe to fall back on silently. Pass `(constantly true)` to see
  everything; that is a caller's explicit choice, not this namespace's.
  `cardinality` carries the same requirement, for the same reason and by the
  same mechanism -- see its docstring."
  (:require [datalog.index :as index]))

(defn- query-seq
  "Matching quads as a SEQ, in index order, routed exactly as the ns docstring
  describes. `query` turns this into a set; `cardinality` counts it without
  ever building one.

  This used to build the set inline, so a caller that only wanted to know HOW
  MANY rows a pattern matches still paid to hash every quad into a set and then
  discarded it. That caller exists and is not rare: the query planner estimates
  every clause this way on every query
  (kotobase-peer.core/datalog-query-plan), and one such estimate -- 361,246
  rows for a both-ends-unbound `knows` clause -- measured as 38% of an LDBC
  IC09 query's total time (kotobase-peer
  bench/results/2026-08-02-scan-instrumentation.edn)."
  [db [s p o]]
  (cond
    (some? s)
    (for [[p2 os] (index/entity-attrs db s)
          :when (or (nil? p) (= p p2))
          o2 os
          :when (or (nil? o) (= o o2))]
      {:s s :p p2 :o o2})

    (and (some? p) (some? o))
    (for [s2 (index/by-predicate-value db p o)] {:s s2 :p p :o o})

    (some? p)
    (for [[s2 os] (index/by-predicate db p) o2 os] {:s s2 :p p :o o2})

    ;; `[_ _ o]` — bound VALUE only. Before this branch existed the pattern
    ;; fell through to `:else` and returned the ENTIRE database, ignoring the
    ;; object it was given: a single-clause query answered wrongly, and a
    ;; Datalog clause of this shape produced an intermediate set of every
    ;; datom. There is no index for it (`:vaet` covers only ref-valued objects,
    ;; matching `assert-quad`'s `ref?`), so it is an honest O(database) scan --
    ;; but a correct one. Found by datom-source's conformance suite.
    (some? o)
    (for [[s2 pm] (:eavt db) [p2 os] pm o2 os :when (= o o2)]
      {:s s2 :p p2 :o o2})

    :else
    (for [[s2 pm] (:eavt db) [p2 os] pm o2 os] {:s s2 :p p2 :o o2})))

(defn query
  "`pattern` is `[s p o]`, any position `nil` for wildcard. `visible?` is
  applied as a post-filter over every candidate quad before it's returned
  -- see the ns docstring. Returns a set of matching `{:s :p :o}` quads."
  [db pattern visible?]
  (index/check-shape! db)
  (into #{} (filter visible?) (query-seq db pattern)))

(defn query-vec
  "`query` as a VECTOR, built eagerly off the index with no lazy seq and no
  set: the quads an index scan yields are distinct by construction, so the
  set in `query` paid a hasheq of every `{:s :p :o}` map to remove nothing.
  The join engine's keyed path (`datalog.core/scan*`) calls this once per
  distinct substituted pattern; measured 2026-09-12 (ayatori iteration 41,
  JFR on IS3 at saturation on the full SF1): `Util.hasheq` + hash-map
  assoc + the `for` lazy-seq machinery were ~35% of the join's samples.
  `query` keeps its set contract for every other caller."
  [db [s p o :as pattern] visible?]
  (index/check-shape! db)
  (let [keep! (fn [acc q] (if (visible? q) (conj! acc q) acc))]
    (persistent!
     (cond
       (some? s)
       (reduce-kv (fn [acc p2 os]
                    (if (or (nil? p) (= p p2))
                      (reduce (fn [acc o2] (if (or (nil? o) (= o o2)) (keep! acc {:s s :p p2 :o o2}) acc)) acc os)
                      acc))
                  (transient []) (index/entity-attrs db s))

       (and (some? p) (some? o))
       (reduce (fn [acc s2] (keep! acc {:s s2 :p p :o o})) (transient []) (index/by-predicate-value db p o))

       (some? p)
       (reduce-kv (fn [acc s2 os] (reduce (fn [acc o2] (keep! acc {:s s2 :p p :o o2})) acc os))
                  (transient []) (index/by-predicate db p))

       :else
       (reduce keep! (transient []) (query-seq db pattern))))))

(defn query-range
  "Quads of `attr` whose object is in `[lo, hi)` under `visible?`.

  Same `visible?` contract as `query`. Default interval is `[lo, hi)`
  (`:lo-open? false`, `:hi-open? true`). This is the in-memory form of
  Datomic `index-range`; it filters `:avet` rather than cutting a tree."
  ([db attr lo hi visible?] (query-range db attr lo hi visible? {}))
  ([db attr lo hi visible? opts]
   (index/check-shape! db)
   (into #{}
         (comp (mapcat (fn [[s os]]
                         (map (fn [o] {:s s :p attr :o o}) os)))
               (filter visible?))
         (index/by-predicate-range db attr lo hi opts))))

(defn cardinality
  "How many quads `pattern` matches under `visible?` -- the same number as
  `(count (query db pattern visible?))`, without building the set.

  Same `visible?` contract as `query`: REQUIRED, and applied to every
  candidate, so a count can never report rows a read would not return. What is
  skipped is only the set -- no hashing, no membership checks, no retained
  collection.

  `query` returns a set, so in principle it could report fewer rows than this
  counts if a pattern could yield the same quad twice. None can: every branch
  of `query-seq` walks its index once and each quad appears at one position in
  it. The equivalence test asserts that rather than assuming it."
  [db pattern visible?]
  (index/check-shape! db)
  (count (filter visible? (query-seq db pattern))))

(defn estimate-cardinality
  "Cheap upper bound for how many quads `pattern` can match, derived directly
  from the covering indexes without constructing quads or invoking a
  visibility predicate.

  This is deliberately NOT `cardinality`: authorization may hide some of the
  indexed rows, so the result can be larger than a visible query's result.
  That makes it suitable for a query planner's cost hint (an overestimate can
  choose a less aggressive strategy but cannot expose a row or change an
  answer), while callers that need an exact visible count must continue to use
  `cardinality`.

  The common graph-planning cases are proportional to index groups rather than
  matching rows: predicate-only patterns sum the sizes of the predicate's
  subject buckets, and a fully unbound pattern sums the subject/attribute
  buckets. In particular, no `{:s :p :o}` maps are allocated and no set is
  built merely to learn a planning number."
  [db [s p o]]
  (index/check-shape! db)
  (cond
    (some? s)
    (let [attrs (index/entity-attrs db s)]
      (cond
        (some? p) (let [os (get attrs p #{})]
                    (if (some? o) (if (contains? os o) 1 0) (count os)))
        (some? o) (reduce-kv (fn [n _ os] (if (contains? os o) (inc n) n)) 0 attrs)
        :else (reduce-kv (fn [n _ os] (+ n (count os))) 0 attrs)))

    (and (some? p) (some? o))
    (count (index/by-predicate-value db p o))

    (some? p)
    (reduce-kv (fn [n _ os] (+ n (count os))) 0 (index/by-predicate db p))

    ;; There is no general object index: :vaet intentionally contains only
    ;; ref-valued objects. Walk the subject/attribute buckets, but still avoid
    ;; allocating one quad per row as query-seq/cardinality must.
    (some? o)
    (reduce-kv (fn [n _ attrs]
                 (+ n (reduce-kv (fn [m _ os] (if (contains? os o) (inc m) m)) 0 attrs)))
               0
               (:eavt db))

    :else
    (reduce-kv (fn [n _ attrs]
                 (+ n (reduce-kv (fn [m _ os] (+ m (count os))) 0 attrs)))
               0
               (:eavt db))))
