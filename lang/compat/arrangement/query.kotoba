(ns arrangement.query
  "COMPATIBILITY SHIM. The pattern-routing query layer is not here any more --
  its home is `kotoba-lang/datalog`'s `datalog.query`, and this namespace does
  nothing but re-export it so existing callers resolve unchanged.

  Why it moved: this repo and `datalog` both carried the query layer, and two
  copies of the same code drift. They already did -- arrangement kept
  developing while the extracted library sat 20 commits stale, and it took a
  full re-extraction to notice. A shim over one copy cannot go stale; a
  second copy is what can.

  **Removing this namespace is a follow-up**, once the known consumers
  (`kotobase-peer`, `kotoba-git`/`bonsai`) require `datalog.query` directly.
  Until then it is load-bearing and its delegations are deliberately
  mechanical: every public var of `datalog.query`, in its declaration order,
  with the arglists and docstrings a caller would have seen before.

  They are `defn` wrappers rather than `(def x dq/x)` aliases because
  ClojureScript's analyzer computes `:arglists` for a `def` from its init
  expression and overwrites a declared one -- an alias reports `()` as its
  signature there. See `arrangement.core`'s note for the measurement.

  For what the routing actually does -- which index answers which bound
  positions, and why `visible?` is a required argument rather than a
  defaulted one -- see `datalog.query` itself."
  (:require [datalog.query :as dq]))

(defn query
  "`pattern` is `[s p o]`, any position `nil` for wildcard. `visible?` is
  applied as a post-filter over every candidate quad before it's returned.
  Returns a set of matching `{:s :p :o}` quads.

  Delegates to `datalog.query/query`."
  [db pattern visible?]
  (dq/query db pattern visible?))

(defn query-range
  "Quads of `attr` whose object is in `[lo, hi)` under `visible?`.

  Delegates to `datalog.query/query-range`."
  ([db attr lo hi visible?] (dq/query-range db attr lo hi visible?))
  ([db attr lo hi visible? opts] (dq/query-range db attr lo hi visible? opts)))

(defn cardinality
  "How many quads `pattern` matches under `visible?` -- the same number as
  `(count (query db pattern visible?))`, without building the set.

  Delegates to `datalog.query/cardinality`."
  [db pattern visible?]
  (dq/cardinality db pattern visible?))

(defn estimate-cardinality
  "Cheap index-derived upper bound for planning. Unlike `cardinality`, this
  does not invoke visibility filtering and may overestimate visible rows.

  Delegates to `datalog.query/estimate-cardinality`."
  [db pattern]
  (dq/estimate-cardinality db pattern))
