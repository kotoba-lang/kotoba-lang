(ns kotoba.kgraph
  "Pure in-memory EAVT graph-store logic backing the kgraph-* host-import
  surface (kgraph-assert!/kgraph-retract!/kgraph-get-objects/kgraph-query).

  Formerly named `kqe` (Kotoba Query Engine) — renamed because the surface
  covers writes (assert/retract) as well as reads, and the old name read as
  query-only. `kgraph` (Kotoba Graph[-store]) names the actual scope: a small
  EAVT datom store, not just a query facility.

  Datom shape: `[e a v]` — e/a/v are arbitrary EDN values (a is typically a
  keyword). This namespace is pure data manipulation over a datom vector; the
  mutable store (an atom) and the host boundaries (CLJ interpreter handlers in
  kotoba.host-providers, WASM host functions in kotoba.wasm-exec) live
  elsewhere, so the same logic is exercised identically from both.

  The `[e a v]` shape IS the canonical datom model, `datom.core` (datom-clj) —
  the SAME representation the kotobase datom database uses (kotobase-engine's
  entities->datoms / transact-tx). So the language's in-mem datom view and the
  database's persistent datom view datafy entities identically. kotoba :
  kotobase = Clojure : Datomic (ADR-2607032500)."
  (:require [clojure.string :as str]
            [datom.core :as dc]))

(defn assert-datom
  "Append `datom` (`[e a v]`) to `datoms`. Idempotent-in-effect duplicates are
  kept (matching a Datom-log's append-only semantics); callers wanting
  set-like uniqueness can `retract-datom` first."
  [datoms datom]
  (conj (vec datoms) datom))

(defn assert-entity
  "Datafy a Datomic-style entity tx-map `{:.../id e :ns/a v …}` into `[e a v]`
   datoms via the canonical datom model (`datom.core/eavt`) and append them all.
   The language-side mirror of kotobase-engine's `entities->datoms`/`transact-tx`
   — both datafy entities through the one shared model."
  [datoms ent]
  (into (vec datoms) (dc/eavt ent)))

(defn assert-entities
  "assert-entity over a seq of entity tx-maps (one flattened datom log)."
  [datoms entities]
  (into (vec datoms) (mapcat dc/eavt entities)))

(defn retract-datom
  "Remove every occurrence of `datom` from `datoms`."
  [datoms datom]
  (vec (remove #(= % datom) datoms)))

(defn get-objects
  "All datoms whose entity is `e`."
  [datoms e]
  (vec (filter #(= e (first %)) datoms)))

(defn- logic-var? [x]
  (and (symbol? x) (str/starts-with? (name x) "?")))

(defn- unify [bindings pat val]
  (cond
    (nil? bindings) nil
    (logic-var? pat) (if (contains? bindings pat)
                 (when (= (get bindings pat) val) bindings)
                 (assoc bindings pat val))
    :else (when (= pat val) bindings)))

(defn- match-clause
  "Every extension of `bindings` that satisfies one `[e-pat a-pat v-pat]`
  clause against `datoms`."
  [datoms bindings [ep ap vp]]
  (keep (fn [[e a v]]
          (some-> bindings (unify ep e) (unify ap a) (unify vp v)))
        datoms))

(defn query
  "Minimal join-based datalog: `{:find [?vars...] :where [[e a v] ...]}`.
  Where-clauses are joined left to right over shared logic-variables
  (symbols starting with `?`); `:find` projects the final bindings. Returns
  a vector of result rows (vectors), deduplicated."
  [datoms {:keys [find where]}]
  (let [results (reduce (fn [bindings-seq clause]
                          (mapcat #(match-clause datoms % clause) bindings-seq))
                        [{}]
                        where)]
    (vec (distinct (map (fn [b] (mapv b find)) results)))))
