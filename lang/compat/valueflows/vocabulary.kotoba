(ns valueflows.vocabulary
  "Read access to the pinned Valueflows vocabulary.

   The vocabulary is data (`valueflows.data/vocabulary`, generated from the
   upstream TTL by bin/ingest.cljs). This namespace is the only place that
   knows its shape, so a change upstream shows up here rather than in every
   caller.

   The operative part is the ACTION BEHAVIOUR TABLE: for each of the 19
   actions, what it does to an inventoried resource's accounting quantity,
   onhand quantity, location, container, accountable agent, stage and state.
   `valueflows.event` executes that table; nothing here interprets it."
  (:require [valueflows.data :as data]))

(def vocabulary data/vocabulary)

(defn upstream
  "Which Valueflows this is: url, sha256, fetch date. A vocabulary without
   this is not answerable about its own provenance."
  []
  (:upstream vocabulary))

;; ── actions ───────────────────────────────────────────────────────────────

(defn actions [] (:actions vocabulary))

(defn action
  "The behaviour row for `k` (:produce, :transfer, ...), or nil."
  [k]
  (get (:actions vocabulary) k))

(defn action? [k] (contains? (:actions vocabulary) k))

(defn action-names [] (into (sorted-set) (keys (:actions vocabulary))))

(def behaviour-keys
  "The eleven properties that make an action mean something. Held as data so
   `valueflows.event` cannot quietly implement ten of them."
  [:input-output :pairs-with :create-resource :event-quantity
   :accounting-effect :onhand-effect :location-effect :contained-effect
   :accountable-effect :stage-effect :state-effect])

(defn effect
  "One cell of the behaviour table, e.g. (effect :transfer :onhand-effect)
   => :decrementIncrement. nil when the action is unknown."
  [action-k behaviour-k]
  (get (action action-k) behaviour-k))

(defn input-output [action-k] (effect action-k :input-output))
(defn pairs-with [action-k] (effect action-k :pairs-with))
(defn event-quantity [action-k] (effect action-k :event-quantity))

(defn process-input?
  "Can an event with this action be an input of a process? `outputInput`
   counts as both, which is the whole reason it exists upstream."
  [action-k]
  (contains? #{:input :outputInput} (input-output action-k)))

(defn process-output? [action-k]
  (contains? #{:output :outputInput} (input-output action-k)))

(defn effort-based?
  "Actions measured in effort rather than resource quantity — work, use,
   sometimes cite. These are the ones a value equation distributes over."
  [action-k]
  (contains? #{:effort :both} (event-quantity action-k)))

(defn stable?
  "Upstream marks each term stable / testing / unstable. A caller that
   depends on a testing term should know it."
  [action-k]
  (= "stable" (:term-status (action action-k))))

;; ── classes, properties, enums ────────────────────────────────────────────

(defn classes [] (:classes vocabulary))
(defn class-def [k] (get (:classes vocabulary) k))
(defn known-class? [k] (contains? (:classes vocabulary) k))

(defn properties [] (:properties vocabulary))
(defn property [k] (get (:properties vocabulary) k))
(defn property? [k] (contains? (:properties vocabulary) k))

(defn enum
  "Members of an enumerated class, e.g. (enum :OnhandEffect)
   => #{:decrement :decrementIncrement :increment :incrementTo}."
  [class-k]
  (get (:enums vocabulary) class-k))

(defn counts [] (:counts vocabulary))
