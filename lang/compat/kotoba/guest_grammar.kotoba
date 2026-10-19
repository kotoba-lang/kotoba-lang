(ns kotoba.guest-grammar
  "Embedded loader for the shared guest-grammar catalog (ADR-2607180900).

  Prefers classpath `kotoba/lang/guest-grammar.edn` (vendored from
  kotoba-lang/lang/guest-grammar.edn) so emit/check work without waiting for
  a kotoba-lang git pin that includes `kotoba.lang.guest-grammar`.

  P0 strict-grammar: `strict-problems` rejects forbidden heads always and,
  when strict mode is on, unknown call heads not in the admitted set."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [kotoba.core.contracts :as core-contracts]))

(def ^:private catalog*
  (delay
    (let [c (or (io/resource "kotoba/lang/guest-grammar.edn")
                (io/resource "lang/guest-grammar.edn")
                (let [f (io/file "lang/guest-grammar.edn")]
                  (when (.isFile f) f)))]
      (if c
        (with-open [r (io/reader c)]
          (edn/read (java.io.PushbackReader. r)))
        {:kotoba.lang.guest-grammar/version 0
         :kotoba.lang.guest-grammar/status :missing
         :forbidden-heads #{}
         :diagnostic-hints {}
         :string-head-host-ops #{}
         :core-special-forms #{}
         :arithmetic #{}
         :comparisons #{}
         :predicates #{}
         :admitted-builtins #{}
         :strict-grammar {:default true}}))))

(defn catalog [] @catalog*)

(defn- as-sym-set [xs]
  (into #{} (map (fn [x]
                   (cond (symbol? x) x
                         (string? x) (symbol x)
                         (keyword? x) (symbol (name x))
                         :else (symbol (str x))))
                 xs)))

(defn forbidden-heads
  []
  (as-sym-set (:forbidden-heads (catalog) #{})))

(defn string-head-host-ops
  []
  (as-sym-set (:string-head-host-ops (catalog) #{})))

(defn diagnostic-hint
  [head]
  (let [k (cond (string? head) head
                (symbol? head) (name head)
                :else (str head))]
    (get (:diagnostic-hints (catalog) {}) k)))

(defn with-hint
  "Assoc :kotoba.lang/hint onto PROBLEM when HEAD has a catalog entry."
  [problem head]
  (if-let [hint (diagnostic-hint head)]
    (assoc problem :kotoba.lang/hint hint)
    problem))

(defn host-import-ops
  "Ops registered on the runtime capability contract (live host surface)."
  []
  (try
    (let [contract (core-contracts/capability-contract)]
      (into #{} (keys (or (core-contracts/host-imports contract) {}))))
    (catch Exception _
      #{})))

(defn admitted-heads
  "Union of catalog-admitted symbols + live host-import ops."
  []
  (let [c (catalog)
        sugar-keys (into #{} (map (fn [k] (symbol (name k))))
                         (keys (:sugar c {})))]
    (into #{}
          (concat (as-sym-set (:core-special-forms c #{}))
                  sugar-keys
                  (as-sym-set (:arithmetic c #{}))
                  (as-sym-set (:comparisons c #{}))
                  (as-sym-set (:predicates c #{}))
                  (as-sym-set (:admitted-builtins c #{}))
                  (as-sym-set (:string-head-host-ops c #{}))
                  (host-import-ops)
                  ;; with-variants of host ops
                  (map (fn [op] (symbol (str (name op) "-with")))
                       (host-import-ops))))))

(defn strict-grammar?
  "True when policy enables strict grammar (default ON)."
  [policy]
  (let [default? (get-in (catalog) [:strict-grammar :default] true)
        key (get-in (catalog) [:strict-grammar :policy-key]
                    :kotoba.policy/strict-grammar)]
    (if (and (map? policy) (contains? policy key))
      (boolean (get policy key))
      default?)))

(defn- list-head [form]
  (when (seq? form)
    (first form)))

(defn- walk-heads
  "Call f on every list-head symbol in form tree."
  [f form]
  (cond
    (seq? form)
    (do (when-let [h (list-head form)]
          (when (symbol? h) (f h)))
        (doseq [x form] (walk-heads f x)))
    (map? form)
    (doseq [[k v] form] (walk-heads f k) (walk-heads f v))
    (coll? form)
    (doseq [x form] (walk-heads f x))))

(defn strict-problems
  "Return grammar problems for FORMS under POLICY.
  - Always: forbidden heads from the catalog.
  - When strict-grammar?: unknown call heads not in admitted-heads."
  [forms policy]
  (let [forbidden (forbidden-heads)
        admitted (admitted-heads)
        strict? (strict-grammar? policy)
        problems (atom [])]
    (doseq [form forms]
      (walk-heads
       (fn [head]
         (let [nm (name head)]
           (cond
             (contains? forbidden head)
             (swap! problems conj
                    (with-hint
                      {:kotoba.runtime/problem :denied-form
                       :kotoba.runtime/form nm
                       :kotoba.lang/grammar :forbidden}
                      head))

             (and strict?
                  (not (contains? admitted head))
                  ;; namespaced symbols / interop-looking heads already forbidden
                  (not (str/includes? nm "/")))
             (swap! problems conj
                    (with-hint
                      {:kotoba.runtime/problem :unknown-form
                       :kotoba.runtime/form nm
                       :kotoba.lang/grammar :strict}
                      head)))))
       form))
    @problems))
