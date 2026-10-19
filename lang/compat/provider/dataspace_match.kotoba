(ns provider.dataspace-match
  "Pure EDN pattern matcher for the dataspace-v1 kit.

  Temporary .cljc (not a .kotoba decision core): matching walks maps and
  heterogeneous vectors. Native word-typed admission and missing HOF/get-in
  in the Kotoba stdlib make a portable .kotoba core a backend gap, not a
  language prohibition (ADR-2608650000). Move the core when recursive
  values and map walks are native-qualified.

  Convention (Clojure thesis `'?t` as data, not as a guest quote form):
  - binding: symbol `?t` or keyword `:?t`
  - wildcard: `_` or `:_`
  - `'?t` / `'_` as `(quote …)` also accepted so EDN copies of the thesis
    examples match
  - maps match by keys present; extra keys on the assertion are OK
  - vectors match positionally at equal length
  - other values: `=`

  Bindings never carry authority. A copied assertion vector is inert data."
  (:require [clojure.string :as str]))

(defn- unwrap-quote [form]
  (if (and (seq? form) (= 'quote (first form)) (= 2 (count form)))
    (second form)
    form))

(defn wildcard?
  [pattern]
  (let [p (unwrap-quote pattern)]
    (or (= p '_) (= p :_))))

(defn binding-key
  "Return the symbol used as a binding key, or nil."
  [pattern]
  (let [p (unwrap-quote pattern)]
    (cond
      (and (symbol? p) (str/starts-with? (name p) "?")) p
      (and (keyword? p) (str/starts-with? (name p) "?"))
      (symbol (name p))
      :else nil)))

(defn match
  "Return a binding map when PATTERN matches VALUE, otherwise nil.
  Repeated bindings of the same key must unify."
  ([pattern value] (match pattern value {}))
  ([pattern value env]
   (let [pattern (unwrap-quote pattern)]
     (cond
       (nil? env) nil

       (wildcard? pattern) env

       (binding-key pattern)
       (let [k (binding-key pattern)]
         (if (contains? env k)
           (when (= (get env k) value) env)
           (assoc env k value)))

       (and (map? pattern) (map? value))
       (reduce (fn [acc [pk pv]]
                 (or (and acc (contains? value pk)
                          (match pv (get value pk) acc))
                     (reduced nil)))
               env
               pattern)

       (and (vector? pattern) (vector? value)
            (= (count pattern) (count value)))
       (reduce (fn [acc [p v]]
                 (or (and acc (match p v acc))
                     (reduced nil)))
               env
               (map vector pattern value))

       :else (when (= pattern value) env)))))
