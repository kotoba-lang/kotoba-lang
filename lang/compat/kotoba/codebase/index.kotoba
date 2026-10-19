(ns kotoba.codebase.index
  "Which definitions point AT this one — derived, never stored.

  `names/dependents` asked the store the same question once per binding: for
  every name a namespace selects, walk that definition's whole closure and see
  whether the target is in it. Shared dependencies were re-walked once per
  dependent, so a store with B bindings over D reachable definitions did O(B·D)
  block reads to answer one question, and the definitions closest to the root
  of the graph — the ones most worth asking about — were the ones re-read most.

  A single pass over the same closure builds the edges backwards, and then
  every dependents query is a graph walk with no I/O at all.

  This is a PROJECTION in the sense ADR-2608580000 D1 fixes: it is computed
  from blocks and is not written anywhere. Deleting it is not a state you can
  get into. That is deliberate rather than lazy — a durable index (the kotobase
  datom plane, D3) can replace `scan` without changing a caller, and until then
  nothing has to reason about invalidating a cache whose inputs are immutable
  but whose SELECTION moves with every namespace commit."
  (:require [kotoba.codebase.ir :as ir]
            [kotoba.codebase.store :as store]))

(defn scan
  "Walk the closure of ROOTS once, returning both directions of the graph.

  `:definitions` is every CID reachable from ROOTS, `:dependents` maps a CID to
  the definitions that reference it DIRECTLY. A block that cannot be read is
  skipped rather than raising: an index is an answer about what is here, and a
  missing dependency is the evaluator's problem to fail on, not this one's."
  [root roots]
  (loop [pending (vec (distinct roots)) seen #{} reverse {}]
    (if-let [current (first pending)]
      (if (contains? seen current)
        (recur (subvec pending 1) seen reverse)
        (let [block (try (store/get-block root current)
                         (catch clojure.lang.ExceptionInfo _ nil))
              outbound (if block (ir/outbound-cids block) [])]
          (recur (into (subvec pending 1) outbound)
                 (conj seen current)
                 (reduce (fn [acc dependency]
                           (update acc dependency (fnil conj #{}) current))
                         reverse outbound))))
      {:definitions seen :dependents reverse})))

(defn depending-on
  "Every CID in INDEX that transitively depends on CID, plus CID itself.

  CID is included because the caller usually has to exclude it explicitly
  anyway, and a set that silently dropped its own seed would make `contains?`
  mean something different at the boundary than in the middle."
  [{:keys [dependents]} cid]
  (loop [pending [cid] seen #{}]
    (if-let [current (first pending)]
      (if (contains? seen current)
        (recur (subvec pending 1) seen)
        (recur (into (subvec pending 1) (get dependents current))
               (conj seen current)))
      seen)))
