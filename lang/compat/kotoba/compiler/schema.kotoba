(ns kotoba.compiler.schema
  "Closed, bounded nominal schema graphs for structured application values."
  (:require [kotoba.artifact.core :as artifact]))

(def max-schemas 32)
(def max-schema-nodes 64)
(def max-schema-depth 8)

(def ^:private primitives
  ;; :document admitted 2026-08-01 (document-in-record): design-system
  ;; Delivery-6 document-plane multi-arg folds need guest records that carry
  ;; logical :document fields (css rule-doc, html constructors, tokens groups).
  ;; Frontend value-types and KIR already treat :document as a scalar-like
  ;; heap value; schema closed profile was the remaining gate.
  #{:i64 :f32 :f64 :string :keyword :bool :vector-i64 :vector-f64 :document})
;; unary :set / :list / :option are productive constructors — guest records
;; may already carry e.g. [:set :string] fields (set-in-record / T8.3 true-set
;; uniqueness on pure/KIR; see set_in_record_test). Not a bare primitive.
(def ^:private unary-tags #{:option :set :list})
(def ^:private binary-tags #{:result :map})
(def ^:private productive-tags
  #{:option :set :list :result :map :vector :record :variant})

(defn ref-type? [value]
  (and (vector? value) (= 2 (count value)) (= :ref (first value))))

(defn- fail! [message data]
  (throw (ex-info message (assoc data :phase :schema))))

(defn validate-table!
  "Validates and returns a closed schema table. Recursive references are
  admitted only through a productive value constructor."
  [table]
  (when-not (and (map? table) (seq table) (<= (count table) max-schemas)
                 (every? #(and (keyword? %) (namespace %)) (keys table)))
    (fail! "schema table must contain bounded qualified names" {:table table}))
  (letfn [(walk [origin nominal-root node path productive? depth budget]
            (vswap! budget inc)
            (when (> @budget max-schema-nodes)
              (fail! "schema graph exceeds node limit" {:root origin}))
            (when (> depth max-schema-depth)
              (fail! "schema graph exceeds depth limit" {:root origin}))
            (cond
              (primitives node) nil
              (ref-type? node)
              (let [target (second node)]
                (when-not (contains? table target)
                  (fail! "schema reference is not declared"
                         {:root origin :ref target}))
                (if (contains? path target)
                  (when-not productive?
                    (fail! "schema alias cycle is not productive"
                           {:root origin :ref target}))
                  ;; A reference changes the nominal owner whose descriptor is
                  ;; being checked. Keeping the original root separately
                  ;; preserves whole-graph depth/node accounting and useful
                  ;; diagnostics without requiring the referenced record or
                  ;; variant to forge the caller's nominal identity.
                  (walk origin target (get table target)
                        (conj path target) false (inc depth) budget)))
              (and (vector? node) (unary-tags (first node)) (= 2 (count node)))
              (walk origin nominal-root (second node)
                    path true (inc depth) budget)
              (and (vector? node) (binary-tags (first node)) (= 3 (count node)))
              (doseq [child (rest node)]
                (walk origin nominal-root child
                      path true (inc depth) budget))
              (and (vector? node) (= :vector (first node)) (= 2 (count node))
                   (vector? (second node)))
              (doseq [child (second node)]
                (walk origin nominal-root child
                      path true (inc depth) budget))
              (and (vector? node) (#{:record :variant} (first node)) (= 3 (count node))
                   (= nominal-root (second node)) (vector? (nth node 2)))
              (doseq [[member child] (nth node 2)]
                (when-not (keyword? member)
                  (fail! "schema member name must be a keyword"
                         {:root origin :member member}))
                (walk origin nominal-root child
                      path true (inc depth) budget))
              :else (fail! "schema descriptor is outside the closed profile"
                           {:root origin :descriptor node})))]
    (doseq [[root descriptor] table]
      (walk root root descriptor #{root} false 0 (volatile! 0))))
  table)

(defn identities
  "Returns stable content identities. Each identity binds its nominal root to
  the complete closed table, so changing any reachable definition changes it."
  [table]
  (validate-table! table)
  (into (sorted-map)
        (map (fn [root]
               [root (artifact/sha256 {:root root :schemas table})]))
        (sort-by str (keys table))))
