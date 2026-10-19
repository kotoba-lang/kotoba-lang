(ns kotobase.classification
  "What each attribute is, so a projection can be reasoned about.

  `kotobase.authorized-query` already narrows a read to a set-valued
  `:projection`, which is the right shape: the boundary is admission, not
  redaction, because a Datalog query with an unconstrained join leaks through
  existence and aggregation whatever you do to the rows afterwards.

  What the gate cannot do on its own is say *which* attributes matter. A
  policy that names `:mail/body` and one that names `:mail/thread` are the
  same shape to it. So the schema declares a class per attribute, and two
  things follow that a schema alone cannot enforce:

  1. An attribute with no class cannot be queried. Not `:internal` by
     default — a default is how personal data acquires the wrong class
     without anybody deciding.

  2. An attribute above `:internal` cannot hold its value inline. It holds a
     content address, and the bytes live behind a separate grant. This is
     what makes erasure possible at all: the chain preserves retracted facts
     on purpose (that is what an audit view is for), so a body written inline
     can never be erased, while a key can be destroyed."
  (:require [clojure.set :as set]))

(def classes
  "Ordered, least sensitive first. An order is needed because the rules below
  are thresholds rather than equalities."
  [:public :internal :personal :restricted])

(def ^:private rank (into {} (map-indexed (fn [i c] [c i]) classes)))

(def inline-threshold
  "Above this, a value may not be stored inline. `:internal` is the last class
  whose values are ordinary datom values."
  :internal)

(defn inline-allowed?
  "May an attribute of this class hold its value directly in the chain?"
  [class*]
  (and (contains? rank class*)
       (<= (rank class*) (rank inline-threshold))))

(defn schema-errors
  "What is wrong with a classified schema.

  `schema` maps an attribute keyword to `{:class … :erasure-scope …}`.
  `:erasure-scope` names the unit whose key destruction erases it — the thing
  a subject-access or deletion request is actually about. It is required above
  `:internal` because `erase this person's data` is unanswerable without it."
  [schema]
  (vec
   (remove
    nil?
    (mapcat
     (fn [[attribute {:keys [class erasure-scope inline?]}]]
       [(when-not (keyword? attribute)
          {:error :attribute-must-be-a-keyword :attribute attribute})
        (when-not (contains? rank class)
          {:error :unclassified-attribute :attribute attribute :value class})
        (when (and (contains? rank class)
                   (not (inline-allowed? class))
                   (true? inline?))
          {:error :sensitive-value-stored-inline :attribute attribute
           :class class})
        (when (and (contains? rank class)
                   (not (inline-allowed? class))
                   (not (keyword? erasure-scope)))
          {:error :no-erasure-scope :attribute attribute :class class})])
     schema))))

(defn projection-errors
  "Whether a projection may be served, given the schema and what the caller
  holds.

  `granted` is the set of classes the caller's policy allows and `scopes` the
  erasure scopes it may reach. An attribute the schema does not classify is
  refused rather than treated as `:internal`: a read of something nobody
  classified is a read nobody authorised."
  [schema projection {:keys [granted scopes]}]
  (let [granted (set granted)
        scopes (set scopes)]
    (vec
     (remove
      nil?
      (mapcat
       (fn [attribute]
         (let [{:keys [class erasure-scope]} (get schema attribute)]
           [(when (nil? (get schema attribute))
              {:error :attribute-not-in-schema :attribute attribute})
            (when (and class (not (contains? granted class)))
              {:error :class-not-granted :attribute attribute :class class})
            (when (and class
                       (not (inline-allowed? class))
                       (not (contains? scopes erasure-scope)))
              {:error :erasure-scope-not-granted :attribute attribute
               :scope erasure-scope})]))
       projection)))))

(defn inline-attributes
  "The attributes whose values may appear in the chain itself. Everything else
  is a content address, and the bytes are somebody's separate decision."
  [schema]
  (into (sorted-set)
        (keep (fn [[attribute {:keys [class]}]]
                (when (inline-allowed? class) attribute)))
        schema))

(defn classified?
  "Whether every attribute a schema mentions carries a class. The check that
  stops personal data from acquiring a class by default."
  [schema]
  (empty? (filter #(= :unclassified-attribute (:error %)) (schema-errors schema))))
