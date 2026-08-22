(ns kotoba.lang.capability-catalog
  "Pure target projections from the ADR-2607279200 semantic capability catalog.
  Build tooling may serialize these values, but target repositories must not
  invent names, IDs, effects, schemas, WIT names, or provider membership."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn read-authority
  ([] (read-authority "lang/capability-semantics.edn"))
  ([path] (edn/read-string (slurp (io/file path)))))

(defn projections
  [semantics]
  (let [operations (:compiler-operations semantics)
        component-operations (filterv :kit operations)]
    {:registry (into (sorted-map) (map (juxt :name :id)) operations)
     :effects (into (sorted-map) (map (juxt :name :effect)) operations)
     :component-capabilities
     (mapv #(select-keys % [:name :id :interface :function :kit :provider-wasi])
           component-operations)
     :provider-capabilities
     (->> component-operations
          (map #(select-keys % [:name :id]))
          (sort-by :id)
          vec)
     :contract-resources
     (into (sorted-map)
           (map (juxt :name :contract-resource))
           component-operations)}))

(defn validate
  [semantics]
  (let [operations (:compiler-operations semantics)
        ids (map :id operations)
        names (map :name operations)
        component (filter :kit operations)
        problems
        (cond-> []
          (not= (count ids) (count (set ids))) (conj :duplicate-id)
          (not= (count names) (count (set names))) (conj :duplicate-name)
          (not-every? #(<= 1 % 255) ids) (conj :id-out-of-range)
          (not-every? #(= (:name %) (:effect %)) operations)
          (conj :effect-name-drift)
          (not-every? (fn [operation]
                        (every? #(contains? operation %)
                                [:contract-resource :interface :function
                                 :provider-wasi]))
                      component)
          (conj :component-contract-incomplete))]
    {:valid? (empty? problems) :problems problems
     :projections (projections semantics)}))
