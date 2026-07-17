(ns kotoba.lang.type-system
  "The portable, machine-checkable type/effect contract for safe Kotoba.

  This namespace deliberately validates *language contract data*, not JVM
  Clojure classes.  Host compilers consume the same data before lowering it to
  their own typed IR.  See docs/lang/type-system.md."
  (:require [clojure.set :as set]
            [kotoba.lang.capability-values :as capabilities]))

(def primitive-types
  #{:nil :bool :i32 :i64 :f32 :string :bytes :keyword :value})

(defn effect-row?
  [x]
  (and (set? x) (every? keyword? x)))

(declare type-problems)

(defn type?
  "True when T is a well-formed safe-Kotoba type-contract expression."
  [t]
  (empty? (type-problems t)))

(defn type-problems
  "Returns structural problems for a type expression.

  `[:cap kind resource]` denotes an opaque capability value; resource is a
  scope descriptor, never authority by itself.  `[:region r]` and
  `[:region-ref r value]` are lexical-only types.  They may appear inside a
  region body but may not escape a public function signature."
  [t]
  (cond
    (contains? primitive-types t) []

    (not (vector? t)) [{:problem :type/invalid :type t}]

    :else
    (let [[tag & args] t]
      (case tag
        :result (if (= 2 (count args))
                  (vec (mapcat type-problems args))
                  [{:problem :type/result-arity :type t}])
        :vector (if (= 1 (count args))
                  (vec (type-problems (first args)))
                  [{:problem :type/vector-arity :type t}])
        :map (if (= 2 (count args))
               (vec (mapcat type-problems args))
               [{:problem :type/map-arity :type t}])
        :cap (let [[kind resource] args]
               (cond-> []
                 (not (keyword? kind))
                 (conj {:problem :type/cap-kind :type t})
                 (not (capabilities/resource-constraint? resource))
                 (conj {:problem :type/cap-resource :type t})))
        :region (if (and (= 1 (count args)) (symbol? (first args)))
                  []
                  [{:problem :type/region-name :type t}])
        :region-ref (let [[region value] args]
                      (cond-> []
                        (not= 2 (count args))
                        (conj {:problem :type/region-ref-arity :type t})
                        (not (symbol? region))
                        (conj {:problem :type/region-ref-name :type t})
                        (some? value)
                        (into (type-problems value))))
        :task (let [[value effects] args]
                (cond-> []
                  (not= 2 (count args))
                  (conj {:problem :type/task-arity :type t})
                  (some? value)
                  (into (type-problems value))
                  (not (effect-row? effects))
                  (conj {:problem :type/task-effects :type t})))
        [{:problem :type/tag-unknown :type t}]))))

(defn contains-region?
  "Whether T contains a lexical region token or reference at any depth."
  [t]
  (cond
    (not (vector? t)) false
    (#{:region :region-ref} (first t)) true
    :else (boolean (some contains-region? (rest t)))))

(defn capability-kinds
  "All capability kinds mentioned in T, recursively."
  [t]
  (cond
    (not (vector? t)) #{}
    (= :cap (first t)) #{(second t)}
    :else (into #{} (mapcat capability-kinds (rest t)))))

(defn implied-effects
  "Effects made visible by capability parameters. Unknown kinds remain their
  own effect keyword so a typo cannot be treated as pure." 
  [types]
  (into #{}
        (map #(get capabilities/effect-for-kind % %))
        (mapcat capability-kinds types)))

(defn validate-signature
  "Validate one public function signature.

  Signature shape:
  {:params [Type ...] :returns Type :effects #{Effect ...}}

  Capability parameter effects must be declared.  Region values cannot appear
  in the return type: this is the contract-level non-escape rule."
  [{:keys [params returns effects] :as signature}]
  (let [types (concat (or params []) [returns])
        problems (cond-> []
                   (not (map? signature))
                   (conj {:problem :signature/not-a-map})
                   (not (vector? params))
                   (conj {:problem :signature/params})
                   (not (effect-row? effects))
                   (conj {:problem :signature/effects})
                   (some #(seq (type-problems %)) types)
                   (into (mapcat type-problems types))
                   (contains-region? returns)
                   (conj {:problem :region/escape :type returns}))
        required (implied-effects (or params []))
        missing (if (effect-row? effects)
                  (set/difference required effects)
                  required)
        missing-problems (when (seq missing)
                           [{:problem :signature/missing-effect
                             :effects missing}])]
    {:ok? (and (empty? problems) (empty? missing))
     :problems (vec (concat problems missing-problems))
     :missing-effects missing}))

(defn signature-from-defn
  "Extract the optional public signature from a canonical `(defn name [..])`
  form.  The signature lives in name metadata so it remains ordinary Clojure
  reader data:

    (defn ^{:signature {:params [:string] :returns :string :effects #{}}}
      normalize [s] ...)

  Returns nil for non-defn forms and for unannotated definitions."
  [form]
  (when (and (seq? form) (= 'defn (first form)))
    (let [[_ name params] form
          signature (:signature (meta name))]
      (when signature
        {:name name :params params :signature signature}))))

(defn validate-defn
  "Validate the optional signature annotation on one canonical defn form.
  An omitted annotation is valid during M1 migration.  A present annotation
  must describe every parameter, agree with `^{:cap kind}` parameter metadata,
  and satisfy `validate-signature`."
  [form]
  (if-let [{:keys [name]} (signature-from-defn form)]
    (let [{:keys [params signature]} (signature-from-defn form)
          signature-result (validate-signature signature)
          signature-params (:params signature)
          arity-problem (when (and (vector? params) (vector? signature-params)
                                   (not= (count params) (count signature-params)))
                          {:problem :signature/parameter-count
                           :function (str name)
                           :expected (count params)
                           :actual (count signature-params)})
          cap-problems
          (when (and (vector? params) (vector? signature-params))
            (keep (fn [[param param-type]]
                    (let [metadata-kind (:cap (meta param))
                          signature-kind (when (and (vector? param-type)
                                                    (= :cap (first param-type)))
                                           (second param-type))]
                      (when (not= metadata-kind signature-kind)
                        {:problem :signature/capability-parameter-mismatch
                         :function (str name)
                         :parameter (str param)
                         :metadata-kind metadata-kind
                         :signature-kind signature-kind})))
                  (map vector params signature-params)))]
      {:ok? (and (:ok? signature-result)
                 (nil? arity-problem)
                 (empty? cap-problems))
       :problems (vec (concat (:problems signature-result)
                              (when arity-problem [arity-problem])
                              cap-problems))
       :missing-effects (:missing-effects signature-result)})
    {:ok? true :problems [] :missing-effects #{}}))

(defn validate-forms
  "Validate all optional M1 public-signature annotations in FORMS.  This is
  intentionally independent of a host compiler and therefore suitable for
  CLJ, CLJS, or native adapters before their typed-HIR lowering."
  [forms]
  (let [results (map validate-defn forms)
        problems (vec (mapcat :problems results))]
    {:ok? (empty? problems)
     :problems problems
     :missing-effects (into #{} (mapcat :missing-effects results))}))

(defn typed-hir-entry
  "Normalize one validated public defn signature into host-neutral typed HIR.
  This intentionally contains no executable body yet: lowering remains a host
  concern, while type/effect/region facts have one portable representation."
  [form]
  (when-let [{:keys [name signature]} (signature-from-defn form)]
    (let [validated (validate-defn form)]
      (when (:ok? validated)
        {:op :typed-defn
         :name (str name)
         :params (:params signature)
         :returns (:returns signature)
         :effects (:effects signature)}))))

(defn validate-scope
  "Validate the static obligations of a proposed `scope` form.

  DATA describes the parent effect row and each spawned child:
  {:effects #{...}
   :children [{:effects #{...} :captures [Type ...]} ...]}

  A child may only have effects already declared by its parent.  This is the
  structured-concurrency effect containment rule. Capability captures are
  rejected in this first slice: moving an affine capability needs syntax and
  lowering support, while silently sharing it would reopen authority leaks."
  [{:keys [effects children] :as scope}]
  (let [base-problems (cond-> []
                        (not (map? scope)) (conj {:problem :scope/not-a-map})
                        (not (effect-row? effects)) (conj {:problem :scope/effects})
                        (not (vector? children)) (conj {:problem :scope/children}))
        child-problems
        (mapcat (fn [{child-effects :effects captures :captures :as child}]
                  (let [capture-types (or captures [])]
                    (concat
                     (when-not (map? child) [{:problem :spawn/not-a-map}])
                     (when-not (effect-row? child-effects)
                       [{:problem :spawn/effects}])
                     (when-not (vector? captures)
                       [{:problem :spawn/captures}])
                     (when (and (effect-row? effects) (effect-row? child-effects))
                       (for [effect (set/difference child-effects effects)]
                         {:problem :spawn/effect-escapes :effect effect}))
                     (mapcat type-problems capture-types)
                     (for [kind (mapcat capability-kinds capture-types)]
                       {:problem :spawn/capability-move-unimplemented :kind kind}))))
                (or children []))]
    {:ok? (empty? (concat base-problems child-problems))
     :problems (vec (concat base-problems child-problems))}))

(defn validate-case
  "Run one portable type-system conformance case.  TYPE is `:signature` or
  `:scope`; DATA holds the corresponding contract map."
  [{:keys [type]} data]
  (case type
    :signature (validate-signature (:signature data))
    :scope (validate-scope (:scope data))
    {:ok? false :problems [{:problem :conformance/unknown-type :type type}]}))
