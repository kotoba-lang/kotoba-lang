(ns datom.core
  "Kotoba Datom-log representation — EAVT, Datomic-isomorphic (ADR-2605262130
  + ADR-2605312345), the same shape the nvidia_cosmos-compat /
  nvidia_isaac-compat actors speak. Shared by every actor that datafies
  results so verification, BOM, CAM, assembly, aero, crash … all land as
  queryable facts on one log.

  Two views from one entity map:
    • `entity` — a Datomic transaction map  {:domain.Kind/id .. :domain.Kind/a ..}
    • `eavt`   — flat [e a v] tuples, the Datom-log view ('datom 化')

  Purpose: DATA REPRESENTATION only. No physics, no solver logic — those live
  in vphysics-clj and cae-solver-clj respectively."
  (:require [clojure.string :as str]))

(defn entity
  "Datomic-style entity (tx-map). `domain` is the attribute prefix (e.g.
  \"aero\", \"vdesign\"), `kind` the entity type → namespace :domain.Kind/.
  `id` is the unique identity; nil attrs are dropped."
  [domain kind id attrs]
  (let [ns (str (name domain) "." (name kind))]
    (into {(keyword ns "id") id}
          (for [[a v] attrs :when (some? v)]
            [(keyword ns (name a)) v]))))

(defn eavt
  "Flatten an entity tx-map into [e a v] tuples, keyed by its /id attribute."
  [ent]
  (let [id-attr (first (filter #(= "id" (name %)) (keys ent)))
        e       (get ent id-attr)]
    (mapv (fn [[a v]] [e a v]) (dissoc ent id-attr))))

(defn log
  "Collect entity tx-maps into {:tx [..] :datoms [..] :count n}."
  [entities]
  (let [ents (vec (remove nil? entities))]
    {:tx     ents
     :datoms (vec (mapcat eavt ents))
     :count  (reduce + 0 (map (comp count eavt) ents))}))
