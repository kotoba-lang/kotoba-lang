(ns datalog.index
  "The in-memory, 4-covering-index arrangement a Datalog query runs over.
  The canonical names of the four indexes are `:eavt` `:aevt` `:avet` `:vaet`
  -- the sort orders themselves, spelled the way every reader of a triple
  store already spells them.

  They used to be `:spo` `:pso` `:pos` `:ocp`, with the EAVT vocabulary
  carried alongside in prose. Two vocabularies for one structure is one
  vocabulary too many: every docstring here had to translate, and the
  translation was where `ocp` -> VAET stopped being obvious (it is
  value-attribute-entity; `ocp` reads as object-something-predicate and
  says nothing about the order). The sort order is the name now.

  **The `t` is a position this structure does not have.** A triple is
  `{:s subject :p predicate :o object}` -- there is no transaction
  component, so nothing is sorted by one. The four names are Datomic's four
  index names, adopted whole because they are how this shape is read; the
  trailing `t` marks the slot we do not fill rather than one we do. The
  triple's own field names stay `:s`/`:p`/`:o` and are not part of this
  rename.

  s/p/o are opaque values for indexing purposes -- this namespace never
  inspects, decodes, or hashes them, it only uses them as map keys, so
  anything with value equality and a hash works (strings, keywords, numbers,
  records, content addresses).

  | index | key order | accessor |
  | --- | --- | --- |
  | `:eavt` | s -> p -> o | `entity-attrs` |
  | `:aevt` | p -> s -> o | `by-predicate` |
  | `:avet` | p -> o -> s | `by-predicate-value`, `by-predicate-range` |
  | `:vaet` | o -> p -> s | `refs-to` (ref-valued objects only) |

  **No storage, no content addressing, no IPLD.** This namespace is a pure data
  structure plus `datom.source/in-range?` so a value interval on `:avet` uses
  the same total order as the query seam. `empty-db` returns a plain map;
  every mutation is a pure function of a map to a map. Persisting one of these -- snapshotting the indices into a
  prolly-tree, addressing that by CID, committing/restoring -- is somebody
  else's job (see `kotoba-lang/arrangement`, which this was extracted from
  and which keeps the persistence half). The split is deliberate: querying is
  useful without any of that, and the coupling ran exactly one way.

  ## Why `ref?` is a required argument here

  In `arrangement` these functions had an arity-2 form that defaulted `ref?`
  to `ipld.core/link?` (\"a reference is whatever is an IPLD Link\"). That one
  default was the ONLY thing tying the quad index to IPLD -- the index itself
  never did anything with a Link except call a predicate on it. Rather than
  invent a new implicit default here (every candidate is a guess about the
  caller's data model, and a wrong guess silently produces an empty or
  over-full reverse index), `ref?` is a required parameter. State it, or wrap
  these functions in your own arity-2 that supplies your domain's answer:

      (defn assert-quad [db q] (index/assert-quad db q ipld/link?))

  Pass `(constantly false)` if you do not use reverse-reference lookup;
  `refs-to` then always returns `{}` and `:vaet` stays empty."
  (:require [datom.source :as ds]
            [datalog.singleton :as singleton]))

(defn empty-db
  "A db with all four indices empty. Just a map -- nothing to close, nothing
  to open, safe to hold in an atom or thread through pure functions."
  []
  {:eavt {} :aevt {} :avet {} :vaet {}})

(defn legacy-db?
  "True if `db` still carries the pre-rename index keys `:spo`/`:pso`/`:pos`/
  `:ocp`."
  [db]
  (boolean (and (map? db)
                (some #(contains? db %) [:spo :pso :pos :ocp]))))

(defn check-shape!
  "Throw if `db` is a pre-rename db, rather than letting a lookup of the
  renamed key return `nil`.

  This is the whole reason the rename is safe to land before every consumer's
  pin has moved. A db built by the old library has no `:eavt`, so `(:eavt db)`
  is `nil`, so a scan over it is empty, so a query answers **zero rows and
  reports success** -- a wrong answer wearing the face of a correct one, on a
  substrate whose entire job is answering questions. One `contains?` per query
  turns that into an error that names itself.

  Callers that mutate per-datom do not pay this; it is the query entry points
  that check, once per query."
  [db]
  (when (legacy-db? db)
    (throw (ex-info "Pre-rename arrangement db: indexes are :eavt/:aevt/:avet/:vaet"
                    {:problem :datalog.index/legacy-index-keys
                     :found (filterv #(contains? db %) [:spo :pso :pos :ocp])
                     :expected [:eavt :aevt :avet :vaet]})))
  db)

;; A counterpart set that holds one value is `singleton/of` (one object), not
;; `#{v}` (about 160 bytes on the JVM): 99.5% of them hold one value, and this
;; is where a full LDBC SF1 stopped fitting a 16 GiB host (ADR-2609121100).
;; `conj` on it returns an ordinary set, so nothing below this line changes.
(defn- conj-counterpart [s v]
  (if (nil? s) (singleton/of v) (conj s v)))

(defn- upd [m k1 k2 v]
  (update m k1 (fnil (fn [m2] (update m2 k2 conj-counterpart v)) {})))

(defn- rm [m k1 k2 v]
  (if-let [m2 (get m k1)]
    (let [s (disj (get m2 k2 #{}) v)]
      (if (empty? s)
        (let [m2' (dissoc m2 k2)]
          (if (empty? m2') (dissoc m k1) (assoc m k1 m2')))
        (assoc m k1 (assoc m2 k2 s))))
    m))

;; ── value order: the sorted values of an attribute, maintained on write ──────
;;
;; The ordered drive (datalog.core) walks an attribute's distinct values in
;; order. That order is a projection of `:avet`'s keys, held here per
;; attribute as a persistent sorted-set, keyed by IDENTITY of the `:avet`
;; inner map it describes: a hit is exact, and a write that goes through
;; `assert-quad` / `retract-quad` carries the tracked entry forward with one
;; conj/disj (O(log V)) instead of leaving a stale entry for the next read to
;; rebuild in O(V log V). Measured 2026-09-11 (ayatori iteration 31, LDBC
;; SNB SF1 subset, 6 of 17 operations inserts): with rebuild-on-miss every
;; read after a write re-sorted 87k creationDate values -- IC02 3.8 -> 33.8 ms
;; served, IC09 7.4 -> 34.0, IS2 3.6 -> 30.3, and the mix fell from ~2,000
;; to 511 ops/s. The bulk transient path (`assert-quads!`) does not track;
;; its entry goes stale and the next read rebuilds once.

(def ^:private value-order
  "attr -> vector of {:index <an :avet inner map> :sorted <sorted-set of its
  keys>}, newest version LAST, at most 2 x `value-order-versions` deep.

  Several versions, not one: a reader may hold a db value a few writes older
  than the newest (a snapshot read taken while a writer advances the db -- the
  serving shape). With one tracked version, such a read missed, rebuilt the
  order for ITS version, and thereby untracked the newest, so the next write
  could not carry either; under a mixed load every read rebuilt. Measured
  2026-09-11 (iteration 31, second attempt): IC02 33.8 -> 98.8 ms, worse than
  no carry at all. Sixteen versions cover any read whose snapshot is within
  sixteen writes of the head."
  (atom {}))

(def ^:private value-order-versions
  "Sixteen was iteration 31's choice for the value order. Iteration 35
  (the full SF1, 1,000 inserts/s, a group order that costs ~20 s to rebuild
  from 3M entries) measured misses: a reader whose independent half took
  longer than sixteen writes found its snapshot untracked and rebuilt. A
  version is a path copy (O(log n) nodes), so a thousand of them is a few
  MB, and the lookup hits index 0 for the head."
  1024)

;; Versions are held newest-LAST in a vector: a push is one `conj`, the head
;; is `peek`, and the vector is trimmed to the window only every window's
;; worth of pushes (`subvec` copied into a fresh vector so the old base is not
;; retained). The first form -- newest-first, `(vec (take n (cons v versions)))`
;; -- rebuilt a 1,024-element vector through a lazy seq on EVERY push, three
;; or four pushes per insert: measured 2026-09-11 (iteration 36, full SF1,
;; 1,100 inserts/s) as ~40 KB of garbage per push, visible as read p95 7-16 ms
;; under the stream against ~1 ms without it.
(defn- find-version [versions inner]
  (some (fn [v] (when (identical? (:index v) inner) v)) (rseq (or versions []))))

(defn- push-version [versions v]
  (let [versions (conj (or versions []) v)]
    (if (> (count versions) (* 2 value-order-versions))
      (into [] (subvec versions (- (count versions) value-order-versions)))
      versions)))

(def ^:private value-order-rebuilds
  "How many times `ordered-values` sorted from scratch -- so a test can tell
  a maintained entry from a rebuilt one, which return the same set."
  (atom 0))

(defn ordered-values
  "The distinct values of `attr` on `db`, as a sorted-set (ascending by
  `compare`; use `subseq`/`rsubseq` for cuts). Built once per (attr, index
  version) and carried across `assert-quad`/`retract-quad`."
  [db attr]
  (let [inner (get (:avet db) attr)]
    (if-let [hit (find-version (get @value-order attr) inner)]
      (:sorted hit)
      (let [sorted (into (sorted-set) (keys inner))]
        (swap! value-order-rebuilds inc)
        (swap! value-order update attr push-version {:index inner :sorted sorted})
        sorted))))

(defn value-order-rebuild-count [] @value-order-rebuilds)

(defn- carry-value-order!
  "After a write moved `:avet`'s inner map for `p` from `before` to `after`:
  if the tracked entry described `before`, make it describe `after` with the
  value conj'd (assert) or disj'd (retract, when no subject holds it any more)."
  [before after p o op]
  (when-let [hit (find-version (get @value-order p) before)]
    (let [sorted (case op
                   :assert (conj (:sorted hit) o)
                   :retract (if (contains? after o) (:sorted hit) (disj (:sorted hit) o)))]
      (swap! value-order update p push-version {:index after :sorted sorted}))))

;; ── group order: an attribute's values in order, per value of another ───────
;;
;; The ordered drive walks an attribute's values in order. When the driver's
;; entity is also tied to a SMALL bound set through another attribute -- IC02:
;; messages by the person's friends, ordered by creationDate -- the global
;; value walk is nearly all misses (0.3% of 3M dates on the full SF1, so the
;; 512-value budget is spent before 20 hits) and the plain join then touches
;; every candidate (21k messages, 80 ms). Held here per pair [g a]: for every
;; value of g, the sorted-set of `[v e]` for every `[e a v]` with `[e g gv]`
;; -- so the drive merges the groups it needs and reads at most `limit`
;; entries from each. Built on first use per (pair, index version), carried
;; across `assert-quad`/`retract-quad` on either attribute; the bulk path does
;; not track and the next read rebuilds.

(def ^:private group-order
  "[g a] -> vector of {:g-index <aevt inner of g> :a-index <aevt inner of a>
  :groups {gv (sorted-set [v e] ...)}}, newest first, at most
  `value-order-versions` deep -- the same snapshot-reader reason as
  `value-order`."
  (atom {}))

(defn ve-compare
  "Order for a group's `[v e]` entries: by value, then by entity. `compare`
  on vectors orders by LENGTH first, so `[v]` would sort before every
  `[v e]` and no cut could name a value; instead a cut names `[v ::lo]` /
  `[v ::hi]`, and those sentinels sort below / above every entity."
  [[va ea] [vb eb]]
  (let [c (compare va vb)]
    (if (zero? c)
      (cond (identical? ea eb) 0
            (= ea ::lo) -1 (= eb ::lo) 1
            (= ea ::hi) 1 (= eb ::hi) -1
            :else (compare ea eb))
      c)))

(defn empty-group [] (sorted-set-by ve-compare))

(def ^:private group-order-rebuilds (atom 0))

(defn group-order-rebuild-count [] @group-order-rebuilds)

(defn- find-group-version [versions g-inner a-inner]
  (some (fn [v] (when (and (identical? (:g-index v) g-inner) (identical? (:a-index v) a-inner)) v)) (rseq (or versions []))))

(defn grouped-values
  "`{gv (sorted-set [v e] ...)}` for the pair: every `[e a v]` filed under
  each `gv` with `[e g gv]`. Entities with no `g` value are absent (nothing
  groups them). Built once per (pair, index version) and carried across
  writes."
  [db g a]
  (let [g-inner (get (:aevt db) g)
        a-inner (get (:aevt db) a)]
    (if-let [hit (find-group-version (get @group-order [g a]) g-inner a-inner)]
      (:groups hit)
      (let [groups (persistent!
                    (reduce-kv (fn [m e vs]
                                 (let [gvs (get (get (:eavt db) e) g)]
                                   (if (seq gvs)
                                     (reduce (fn [m gv]
                                               (assoc! m gv (reduce (fn [ss v] (conj ss [v e]))
                                                                    (get m gv (empty-group))
                                                                    vs)))
                                             m gvs)
                                     m)))
                               (transient {})
                               (or a-inner {})))]
        (swap! group-order-rebuilds inc)
        (swap! group-order update [g a] push-version {:g-index g-inner :a-index a-inner :groups groups})
        groups))))

(defn- carry-group-order!
  "After a write of `{s p o}` moved `db` to `db'`: for every tracked pair
  [g a] where p is g or a, make the tracked entry describe db' with the
  affected `[v e]` entries added (assert) or removed (retract, when the
  quad is gone from db')."
  [db db' s p o op]
  (doseq [[[g a] versions] @group-order
          :when (or (= p g) (= p a))]
    (when-let [hit (find-group-version versions (get (:aevt db) g) (get (:aevt db) a))]
      (let [attrs (get (:eavt db') s)
            still? (or (= op :assert) (contains? (get attrs p #{}) o))
            edits (if (= p a)
                    (for [gv (get attrs g)] [gv [o s]])      ; o is a value of a
                    (for [v (get attrs a)] [o [v s]]))        ; o is a value of g
            groups (reduce (fn [m [gv entry]]
                             (if still?
                               (update m gv (fnil conj (empty-group)) entry)
                               (let [ss (disj (get m gv (empty-group)) entry)]
                                 (if (empty? ss) (dissoc m gv) (assoc m gv ss)))))
                           (:groups hit) edits)]
        (swap! group-order update [g a] push-version
               {:g-index (get (:aevt db') g) :a-index (get (:aevt db') a) :groups groups})))))

(defn assert-quad
  "Add `{:s :p :o}` to `db`'s four indices. `ref?` is REQUIRED (see the ns
  docstring): it decides whether `:o` is additionally indexed in `:vaet` for
  reverse-reference lookup via `refs-to`. Pure -- returns a new db."
  [db {:keys [s p o]} ref?]
  (let [before (get (:avet db) p)
        db' (cond-> db
              true     (update :eavt upd s p o)
              true     (update :aevt upd p s o)
              true     (update :avet upd p o s)
              (ref? o) (update :vaet upd o p s))]
    (carry-value-order! before (get (:avet db') p) p o :assert)
    (carry-group-order! db db' s p o :assert)
    db'))

(defn- ->mutable
  "One index, `{k1 {k2 #{v}}}`, with every inner map made transient. The outer
  map stays persistent on purpose: after a key exists, a bulk insert mutates
  its inner map in place and never touches the outer map again."
  [m]
  (reduce-kv (fn [acc k v] (assoc acc k (transient v))) {} m))

(defn- freeze [m]
  (reduce-kv (fn [acc k v] (assoc acc k (persistent! v))) {} m))

(defn- upd-mut [m k1 k2 v]
  (if-let [inner (get m k1)]
    ;; `assoc!` USUALLY mutates in place and returns the same object, so the
    ;; outer map usually needs no path copy at all. It does NOT always: a
    ;; transient array-map returns a different object when it grows past eight
    ;; entries and becomes a hash-map. Discarding the return value therefore
    ;; silently truncates every inner map at exactly eight entries -- which is
    ;; what the first version of this did, and what the equivalence test caught.
    (let [inner' (assoc! inner k2 (conj-counterpart (get inner k2) v))]
      (if (identical? inner' inner) m (assoc m k1 inner')))
    (assoc m k1 (assoc! (transient {}) k2 (singleton/of v)))))

(defn mutable-db
  "A mutable bulk-loading accumulator over `db`. Pair with `assert-quads!` and
  finish with `persist-db`. NOT a db: nothing else in this namespace accepts
  one, and it is not safe to share or to hold across threads.

  This exists because the cost `assert-quads` pays once -- walking the existing
  outer keys to make their inner maps transient -- is proportional to the db,
  not to the batch. Calling `assert-quads` in a loop therefore pays that walk
  once per batch, which is quadratic in the number of batches.

  Measured 2026-08-13, LDBC SNB SF-1 (31,837,452 datoms) in 500,000-datom
  batches: the looped `assert-quads` form was still running after 25 minutes,
  with a JVM thread dump showing the main thread inside the transient-conversion
  walk. That was mistaken for the engine being super-linear in ingest. It is
  not; the loop was."
  [db]
  {:eavt (->mutable (:eavt db)) :aevt (->mutable (:aevt db))
   :avet (->mutable (:avet db)) :vaet (->mutable (:vaet db))})

(defn assert-quads!
  "Add `quads` to a `mutable-db`, returning the accumulator. Cheap to call
  repeatedly: no per-call walk of the existing db."
  [mdb quads ref?]
  (loop [qs (seq quads)
         eavt (:eavt mdb) aevt (:aevt mdb) avet (:avet mdb) vaet (:vaet mdb)]
    (if-let [{:keys [s p o]} (first qs)]
      (recur (next qs)
             (upd-mut eavt s p o)
             (upd-mut aevt p s o)
             (upd-mut avet p o s)
             (if (ref? o) (upd-mut vaet o p s) vaet))
      {:eavt eavt :aevt aevt :avet avet :vaet vaet})))

(defn persist-db
  "Finish a `mutable-db`, returning a db. The accumulator must not be used
  afterwards."
  [mdb]
  {:eavt (freeze (:eavt mdb)) :aevt (freeze (:aevt mdb))
   :avet (freeze (:avet mdb)) :vaet (freeze (:vaet mdb))})

(defn assert-quads
  "Bulk `assert-quad`. Same result as `(reduce #(assert-quad %1 %2 ref?) db qs)`
  -- there is a test asserting exactly that -- built for loading a dataset
  rather than for one write.

  Measured 2026-08-13, loading 2,000,000 real LDBC SNB datoms into an empty db
  on an Apple M4: **888k datoms/s here against 600k for reducing `assert-quad`**
  -- about 1.5x. Worth having for a dataset load, and no more than that.

  It is deliberately recorded here that this was written believing the per-quad
  path was a far worse bottleneck than it is. It is not: 2,000,000 datoms take
  3.3 seconds through `assert-quad`. Do not cite this function as the reason a
  large load became possible.

  ONE CALL PER LOAD. It pays an up-front walk over the existing db's outer keys,
  so calling it once per batch in a loop is quadratic in the number of batches.
  To load in batches, use `mutable-db` / `assert-quads!` / `persist-db`."
  [db quads ref?]
  (persist-db (assert-quads! (mutable-db db) quads ref?)))

(defn retract-quad
  "Remove `{:s :p :o}` from `db`'s four indices. `ref?` is REQUIRED and must
  agree with the one used to assert the same quad -- otherwise the `:vaet`
  entry is left behind. Pure -- returns a new db."
  [db {:keys [s p o]} ref?]
  (let [before (get (:avet db) p)
        db' (cond-> db
              true     (update :eavt rm s p o)
              true     (update :aevt rm p s o)
              true     (update :avet rm p o s)
              (ref? o) (update :vaet rm o p s))]
    (carry-value-order! before (get (:avet db') p) p o :retract)
    (carry-group-order! db db' s p o :retract)
    db'))

(defn entity-attrs
  "All `{p #{o...}}` for subject `s`, off `:eavt`."
  [db s] (get (:eavt db) s {}))

(defn by-predicate
  "All `{s #{o...}}` for predicate `p`, off `:aevt`."
  [db p] (get (:aevt db) p {}))

(defn by-predicate-value
  "All subjects `s` where `[s p o]` holds -- an `:avet` point lookup."
  [db p o] (get-in db [:avet p o] #{}))

(defn by-predicate-range
  "`:avet` value interval: `{s #{o...}}` for predicate `p` whose object
  is in `[lo, hi)` (see `datom.source/in-range?`).

  Walks the values of `p` in `:avet` and filters. That is O(|values of p|),
  not a tree cut — in-memory `:avet` is a hash map, not a sorted map
  (sorted-map cannot be transient, and `assert-quads!` depends on that).
  The tree cut lives in `prolly-tree/scan-range` and in lake columnar
  stats; this function is the same *question* on the hot db."
  ([db p lo hi] (by-predicate-range db p lo hi {}))
  ([db p lo hi opts]
   (reduce-kv (fn [acc o ss]
                (if (ds/in-range? o lo hi opts)
                  (reduce (fn [m s] (update m s (fnil conj #{}) o)) acc ss)
                  acc))
              {}
              (get-in db [:avet p] {}))))

(defn refs-to
  "All `{p #{s...}}` referencing object `o` -- the `:vaet` reverse lookup;
  only populated for quads asserted with a truthy `ref?`."
  [db o] (get (:vaet db) o {}))
