(ns kotoba.lang.spec
  "Data-spec validation for the kotoba foundational stdlib. Layer 1 (data):
  a spec is plain EDN, validation and explanation are pure functions, no host
  capability, no third-party deps. Runs on JVM / SCI / ClojureScript / GraalVM /
  kotoba-WASM.

  A spec is a map with a `:type`. Primitives: `:any :string :keyword :boolean
  :int :double`. Composites: `:map` (with `:keys`, per-key `:optional?`, and
  `:closed?`), `:vector` and `:set` (with `:of`). `:fn` applies a predicate.

  `explain` returns a seq of problem maps:
    {:path [..keys to the failing point..]
     :in   [..indices/keys into the value..]
     :val  the offending value
     :reason keyword}
  An empty problem seq means valid. `validate` returns the value when valid and
  ::invalid otherwise; `valid?` is the boolean form."
  (:refer-clojure :exclude [valid?]))

(def invalid ::invalid)

(defn- problem
  ([path in val reason]
   {:path path :in in :val val :reason reason})
  ([path in val reason spec]
   (assoc (problem path in val reason) :spec spec)))

(declare explain-1)

(defn- int-like?
  "True for an integer-valued number (covers boxed Long/Integer and
  integer-valued doubles, but not booleans — booleans are not numbers in
  Clojure). Portable across JVM/CLJS."
  [x]
  (and (number? x)
       (not (true? x))
       (not (false? x))
       (zero? (mod (double x) 1.0))))

(defn- explain-prim [spec x path in]
  (let [t (:type spec)]
    (case t
      :any      '()
      :string   (if (string? x) '() (list (problem path in x :type/string)))
      :keyword  (if (keyword? x) '() (list (problem path in x :type/keyword)))
      :boolean  (if (boolean? x) '() (list (problem path in x :type/boolean)))
      :int      (if (int-like? x) '() (list (problem path in x :type/int)))
      :double   (if (and (number? x) (not (boolean? x)))
                  '() (list (problem path in x :type/double)))
      :fn       (if ((:pred spec) x) '() (list (problem path in x :fn/predicate)))
      ;; composite or unknown handled below
      nil)))

(defn- explain-map [spec x path in]
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

(defn- explain-coll [spec x path in kind]
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

(defn explain
  "Return a seq of problem maps for `x` against `spec`. Empty seq means valid."
  [spec x]
  (explain-1 spec x [] []))

(defn valid?
  "True iff `x` conforms to `spec` (no problems)."
  [spec x]
  (empty? (explain spec x)))

(defn validate
  "Return `x` if it conforms to `spec`, else `::invalid`."
  [spec x]
  (if (valid? spec x) x invalid))
