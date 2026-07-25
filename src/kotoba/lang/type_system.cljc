(ns kotoba.lang.type-system
  "Portable compatibility surface for Kotoba's typed HIR contract.

  Type/effect validation stays data-only: it authorizes neither a host import
  nor a runtime provider."
  (:require [clojure.set :as set]
            [kotoba.lang.capability-values :as capabilities]))

(def primitive-types #{:nil :bool :i32 :i64 :f32 :string :bytes :keyword :value})

(defn effect-row? [x] (and (set? x) (every? keyword? x)))

(declare type-problems)

(defn type-problems [t]
  (cond
    (contains? primitive-types t) []
    (not (vector? t)) [{:problem :type/invalid :type t}]
    :else (let [[tag & args] t]
            (case tag
              (:option :vector) (if (= 1 (count args))
                                   (vec (type-problems (first args)))
                                   [{:problem :type/arity :type t}])
              (:result :map) (if (= 2 (count args))
                               (vec (mapcat type-problems args))
                               [{:problem :type/arity :type t}])
              :cap (let [[kind resource] args]
                     (cond-> []
                       (not (and (= 2 (count args)) (keyword? kind)))
                       (conj {:problem :type/cap-kind :type t})
                       (not (capabilities/resource-constraint? resource))
                       (conj {:problem :type/cap-resource :type t})))
              [{:problem :type/tag-unknown :type t}]))))

(defn type? [t] (empty? (type-problems t)))

(defn- capability-kinds [t]
  (cond (not (vector? t)) #{}
        (= :cap (first t)) #{(second t)}
        :else (into #{} (mapcat capability-kinds (rest t)))))

(defn validate-signature [{:keys [params returns effects] :as signature}]
  (let [types (concat (or params []) [returns])
        required (into #{} (map #(get capabilities/effect-for-kind % %))
                       (mapcat capability-kinds (or params [])))
        problems (cond-> []
                   (not (map? signature)) (conj {:problem :signature/not-a-map})
                   (not (vector? params)) (conj {:problem :signature/params})
                   (not (effect-row? effects)) (conj {:problem :signature/effects})
                   (some #(seq (type-problems %)) types) (into (mapcat type-problems types))
                   (= :nil returns) (conj {:problem :type/no-nil-return :type returns}))
        missing (if (effect-row? effects) (set/difference required effects) required)]
    {:ok? (and (empty? problems) (empty? missing))
     :problems (vec (concat problems
                            (when (seq missing)
                              [{:problem :signature/missing-effect :effects missing}])))
     :missing-effects missing}))

(defn signature-from-defn [form]
  (when (and (seq? form) (= 'defn (first form)))
    (let [[_ name params] form]
      (when-let [signature (:signature (meta name))]
        {:name name :params params :signature signature}))))

(defn typed-hir-module [forms]
  (let [entries (keep (fn [form]
                        (when-let [{:keys [name signature]} (signature-from-defn form)]
                          (let [validated (validate-signature signature)]
                            (when (:ok? validated)
                              {:op :typed-defn :name (str name)
                               :params (:params signature) :returns (:returns signature)
                               :effects (:effects signature) :schema :kotoba.typed-hir/v1}))))
                      forms)]
    {:ok? true :schema :kotoba.typed-hir-module/v1 :entries (vec entries) :problems []}))
