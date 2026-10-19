(ns kotoba.compiler.interface
  (:require [kotoba.artifact.core :as artifact]
            [kotoba.sema :as sema]))

(def schema :kotoba.interface/v1)

(defn inspect-source
  "Return the closed public contract of SOURCE. Bodies, constants, local names,
  paths and host details are deliberately excluded."
  [source]
  (let [hir (sema/analyze source)
        exports (set (:exports hir))
        functions (->> (:functions hir)
                       (filter #(contains? exports (:name %)))
                       (map (fn [{:keys [name source-name params param-types result effects]}]
                              (cond-> {:name (or source-name name)
                                       :arity (count params)
                                       :param-types (vec (or param-types (repeat (count params) :i64)))
                                       :result result
                                       :effects (set effects)}
                                (and source-name (not= source-name name))
                                (assoc :abi-name name))))
                       (sort-by (comp str :name))
                       vec)
        value {:format schema
               :namespace (:namespace hir)
               :entry (:entry hir)
               :hir-format (:format hir)
               :floating-point-policy :kotoba.floating-point/ieee-754-f32-f64-v7
               :exports functions
               :effects (set (:effects hir))}]
    (assoc value :sha256 (artifact/sha256 value))))

(defn valid? [value]
  (and (= schema (:format value))
       (vector? (:exports value))
       (string? (:sha256 value))
       (= (:sha256 value) (artifact/sha256 (dissoc value :sha256)))))
