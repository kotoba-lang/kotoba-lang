(ns kotoba.spec.explain-1
  "explain-1, explain-coll, explain-map -- addressed on its own.

  Split out of kotoba.lang.spec on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.

  It holds 3 definitions, not one, because they call each
  other: explain-1 and explain-coll and explain-map are mutually recursive, and the
  source says so itself with a (declare ...). Two definitions that call each
  other cannot be two repos with an acyclic dependency, so the unit is the
  cycle. Unison groups mutually recursive definitions the same way."
  (:require [kotoba.spec.explain-prim :refer [explain-prim]]
            [kotoba.spec.problem :refer [problem]]))

(declare explain-1 explain-coll explain-map)

(defn explain-map [spec x path in]
  (cond
    (not (map? x))
    (list (problem path in x :type/map))

    :else
    (let [key-specs (:keys spec)
          closed?   (:closed? spec)
          required  (reduce-kv (fn [acc k s]
                                 (if (:optional? s) acc (conj acc k)))
                               #{} key-specs)
          present   (set (keys x))
          ;; missing required keys
          missing   (reduce (fn [ps k]
                              (conj ps (problem (conj path k) (conj in k) nil :key/missing)))
                            '() (sort-by str (remove present required)))
          ;; extra keys when closed
          extra     (if closed?
                      (reduce (fn [ps k]
                                (conj ps (problem (conj path k) (conj in k) (get x k) :key/extra)))
                              '() (sort-by str (remove (set (keys key-specs)) present)))
                      '())
          ;; per-key value problems
          per-key   (reduce-kv
                     (fn [ps k v]
                       (if-let [kspec (get key-specs k)]
                         (concat ps (explain-1 kspec v (conj path k) (conj in k)))
                         ps))
                     '() x)]
      (concat missing extra per-key))))

(defn explain-coll [spec x path in kind]
  (let [coll-pred (case kind :vector vector? :set set?)]
    (cond
      (not (coll-pred x))
      (list (problem path in x (keyword "type" (name kind))))

      :else
      (let [of (:of spec)]
        (cond-> '()
          of (concat
              (mapcat (fn [i item]
                        (explain-1 of item (conj path i) (conj in i)))
                      (range (count x))
                      (seq x))))))))

(defn explain-1 [spec x path in]
  (let [t (:type spec)]
    (cond
      (#{:any :string :keyword :boolean :int :double :fn} t) (explain-prim spec x path in)
      (= t :map)  (explain-map spec x path in)
      (= t :vector) (explain-coll spec x path in :vector)
      (= t :set)    (explain-coll spec x path in :set)
      :else (list (problem path in x :spec/unknown-type)))))
