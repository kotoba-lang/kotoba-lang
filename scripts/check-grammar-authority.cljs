#!/usr/bin/env nbb
;; W0: guest-grammar authority + surface classification + vendor sync.
;; Run from the kotoba-lang/kotoba-lang repository root:
;;   nbb scripts/check-grammar-authority.cljs
(ns check-grammar-authority
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]
            ["fs" :as fs]
            ["path" :as path]))

(def grammar-path "lang/guest-grammar.edn")
(def surface-path "lang/surface-status.edn")
(def pipeline-path "lang/elaboration-pipeline.edn")
(def local-vendor-path "resources/kotoba/lang/guest-grammar.edn")

;; DERIVED from the registry, not restated here. This list used to be three
;; hard-coded paths while `src/kotoba/lang/grammar_authority.clj` named six and
;; `lang/elaboration-pipeline.edn` named seven -- three lists of the same thing,
;; all wrong in different ways. `grammar-authority-test/the-registry-is-the-only-
;; list-of-copies` refuses a hard-coded sibling path in this file.
(def registry-path "lang/vendored-copies.edn")

(def registry
  (edn/read-string (fs/readFileSync registry-path "utf8")))

(def sibling-vendor-paths
  (vec (for [c (:copies registry)
             :when (and (= "lang/guest-grammar.edn" (:authority c))
                        (not= local-vendor-path (:checkout-path c)))]
         (:checkout-path c))))
(def portable-backends #{:compiler :kotoba-wasm :kotoba-cljs})

(defn read-edn [p]
  (edn/read-string (fs/readFileSync p "utf8")))

(defn exists? [p]
  (try (fs/existsSync p) (catch :default _ false)))

(defn as-sym [x]
  (cond (symbol? x) x
        (keyword? x) (symbol (name x))
        (string? x) (symbol x)
        :else x))

(defn as-kw [x]
  (cond (keyword? x) x
        (symbol? x) (keyword (name x))
        (string? x) (keyword x)
        :else x))

(defn forbidden-heads [grammar]
  (into #{} (map as-sym) (:forbidden-heads grammar #{})))

(defn invariant-surfaces [surface]
  (into #{}
        (mapcat (fn [[_ v]] (map as-sym (:surface v #{}))))
        (:invariants surface {})))

(defn sugar-source-forms [sugar-key entry]
  (let [explicit (seq (filter #(or (symbol? %) (keyword? %)) (:forms entry)))
        k (as-kw sugar-key)]
    (into #{}
          (map as-sym)
          (or explicit
              (when (and (keyword? k) (not (str/includes? (name k) "-literal")))
                (case k
                  (:map-literal :vector-literal :set-literal
                   :nested-destructuring :record-constructor
                   :protocol-dispatch :interface-contract
                   :protocol-extension :keyword-literal
                   :portable-string-symbol-values :string-host-arg
                   :typed-capability-call :inline-fn-callback
                   :loop-recur :closed-multimethod
                   :variadic-comparison)
                  nil
                  [(symbol (name k))]))))))

(defn feature-form-hints [feature-key entry]
  (let [named (case (as-kw feature-key)
                :contextual-document-literal '[document]
                :map-literal '[get assoc]
                :vector-literal '[]
                :set-literal '[contains? conj disj]
                :map-function '[map]
                :filter-function '[filter]
                :reduce-function '[reduce]
                :first-class-closure-values '[fn invoke fn-ref apply]
                :lazy-sequences '[lazy-cons lazy-first lazy-rest lazy-empty?
                                  lazy-map lazy-filter take drop]
                :dynamic-arity-apply '[apply]
                :typed-eval '[eval]
                :bounded-control-and-sugar '[loop recur match defdesugar]
                :protocol-and-record-dispatch '[defrecord defprotocol definterface
                                                extend-type extend-protocol]
                :persistent-collection-semantics '[count nth peek pop keys vals
                                                   get assoc dissoc conj disj contains?]
                :portable-value-model '[string? symbol? keyword? string-length string=]
                :inline-fn-callbacks '[fn]
                :multi-collection-map '[map lazy-map]
                nil)]
    (into #{} cat
          [(map as-sym (or (:operations entry) #{}))
           (map as-sym (or (:primary-implementation entry) #{}))
           (map as-sym (or (:compiler-implementation entry) #{}))
           (map as-sym (or (:extensions entry) #{}))
           (when-let [f (get-in entry [:record-data :form])] [(as-sym f)])
           (when-let [f (get-in entry [:protocols :form])] [(as-sym f)])
           (when-let [f (get-in entry [:interface :form])] [(as-sym f)])
           named])))

(defn classified-forms [surface]
  (let [invariants (invariant-surfaces surface)
        features (merge (:collections surface {}) (:other-gaps surface {}))
        feature-forms (into #{} (mapcat (fn [[k v]] (feature-form-hints k v)) features))
        core-defaults '#{ns def defn defprotocol definterface defrecord
                         extend-type extend-protocol let if do main}
        arith-defaults '#{+ - * quot bit-xor bit-and bit-or}
        cmp-defaults '#{= < > <= >=}
        pred-defaults '#{not zero? pos? neg? string? symbol? keyword?
                         string-length string= string-concat string-substring symbol}
        sugar-defaults '#{-> ->> as-> and or when if-not when-not
                          cond condp cond->> case if-let when-let if-some when-some
                          some-> some->> not= do get assoc contains? conj disj
                          map filter reduce fn invoke fn-ref apply
                          lazy-cons lazy-first lazy-rest lazy-empty? lazy-map
                          lazy-filter take drop count nth peek pop keys vals dissoc
                          match defdesugar loop recur assert doseq dotimes
                          defmulti defmethod}]
    (set/union invariants feature-forms core-defaults arith-defaults
               cmp-defaults pred-defaults sugar-defaults)))

(defn admitted-forms [grammar]
  (set/union
   (into #{} (map as-sym) (:core-special-forms grammar #{}))
   (into #{} (map as-sym) (:arithmetic grammar #{}))
   (into #{} (map as-sym) (:comparisons grammar #{}))
   (into #{} (map as-sym) (:predicates grammar #{}))
   (into #{} (mapcat (fn [[k v]] (sugar-source-forms k v)) (:sugar grammar {})))))

(defn vendor-drift [authority]
  (into []
        (keep (fn [p]
                (cond
                  (not (exists? p)) {:path p :error :missing}
                  (not= authority (fs/readFileSync p "utf8")) {:path p :error :byte-mismatch}
                  :else nil)))
        (cons local-vendor-path sibling-vendor-paths)))

(defn main []
  (let [grammar (read-edn grammar-path)
        surface (read-edn surface-path)
        pipeline (read-edn pipeline-path)
        authority (fs/readFileSync grammar-path "utf8")
        forbidden (forbidden-heads grammar)
        inv (invariant-surfaces surface)
        admitted (admitted-forms grammar)
        classified (classified-forms surface)
        missing-forbidden (set/difference forbidden inv)
        unclassified (set/difference admitted classified)
        vendor (vendor-drift authority)
        errors (cond-> []
                 (not= "kotoba-lang/kotoba-lang"
                       (:kotoba.lang.guest-grammar/authority grammar))
                 (conj {:code :grammar/authority})
                 (not= 1 (:kotoba.lang.elaboration-pipeline/version pipeline))
                 (conj {:code :pipeline/version})
                 (not= grammar-path
                       (:kotoba.lang.elaboration-pipeline/source-surface-authority pipeline))
                 (conj {:code :pipeline/authority-path})
                 (not (map? (:contract-versions pipeline)))
                 (conj {:code :pipeline/contract-versions})
                 (seq missing-forbidden)
                 (conj {:code :forbidden/unclassified :forms missing-forbidden})
                 (seq unclassified)
                 (conj {:code :admitted/unclassified :forms unclassified})
                 (let [local-missing (filter #(and (= local-vendor-path (:path %))
                                                     (= :missing (:error %)))
                                                vendor)
                         mismatches (filter #(= :byte-mismatch (:error %)) vendor)
                         bad (vec (concat local-missing mismatches))]
                   (seq bad))
                 (conj {:code :vendor/drift
                        :paths (vec (concat (filter #(and (= local-vendor-path (:path %))
                                                          (= :missing (:error %)))
                                                     vendor)
                                            (filter #(= :byte-mismatch (:error %)) vendor)))}))]
    (println "stats"
             (pr-str {:forbidden (count forbidden)
                      :admitted (count admitted)
                      :classified (count classified)
                      :unclassified (count unclassified)
                      :vendor-issues (count vendor)}))
    (if (seq errors)
      (do (println "W0 FAIL" (pr-str errors))
          (js/process.exit 1))
      (do (println "W0 PASS: guest-grammar authority, surface classification, vendor sync")
          (js/process.exit 0)))))

(main)
