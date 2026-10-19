(ns valueflows.conform
  "Structural conformance of Valueflows records against the pinned vocabulary.

   Deliberately narrow: this checks the SHAPE the vocabulary constrains — that
   the action exists, that the quantity the action is measured in is present,
   that a two-sided flow names both sides, that an event claimed as a process
   input is allowed to be one. It does not check that the numbers are true.

   Every result reports what it examined. `{:ok? true}` on an empty input
   would say 'measured and fine' about something never measured, so an empty
   or non-map record is an error, not a pass (ADR-2608136000)."
  (:require [valueflows.vocabulary :as vocab]))

(defn- err [code m] (merge {:code code} m))

(def ^:private two-sided-effects
  #{:decrementIncrement :incrementTo :updateTo})

(defn- needs-to-resource? [action-k]
  (boolean (some two-sided-effects
                 (map #(vocab/effect action-k %)
                      [:accounting-effect :onhand-effect :accountable-effect
                       :location-effect :state-effect]))))

(defn conform-event
  "=> {:ok? bool :errors [...] :examined #{keys}}"
  [event]
  (cond
    (not (map? event))
    {:ok? false :examined #{} :errors [(err :not-a-map {:got (type event)})]}

    (empty? event)
    {:ok? false :examined #{} :errors [(err :empty-record {})]}

    :else
    (let [a (:action event)
          eq (vocab/event-quantity a)
          errors
          (cond-> []
            (not (vocab/action? a))
            (conj (err :unknown-action {:action a}))

            (and (vocab/action? a)
                 (#{:resource :both} eq)
                 (not (:resource-quantity event)))
            (conj (err :missing-resource-quantity {:action a :event-quantity eq}))

            (and (vocab/action? a)
                 (#{:effort :both} eq)
                 (not (:effort-quantity event)))
            (conj (err :missing-effort-quantity {:action a :event-quantity eq}))

            (and (vocab/action? a)
                 (needs-to-resource? a)
                 (not (:to-resource-inventoried-as event))
                 (not (:receiver event)))
            (conj (err :missing-receiving-side
                       {:action a
                        :why "a two-sided effect needs to-resource-inventoried-as or receiver"}))

            (and (vocab/action? a)
                 (:input-of event)
                 (not (vocab/process-input? a)))
            (conj (err :not-a-process-input {:action a :input-output (vocab/input-output a)}))

            (and (vocab/action? a)
                 (:output-of event)
                 (not (vocab/process-output? a)))
            (conj (err :not-a-process-output {:action a :input-output (vocab/input-output a)})))]
      {:ok? (empty? errors) :errors errors :examined (set (keys event))})))

(defn conform-commitment
  "A Commitment is a planned flow: same action vocabulary, plus a time it is
   due (`vf:due`, or a begin/end) — a commitment with no time cannot be
   scheduled, which is the only thing downstream wants it for."
  [c]
  (let [base (conform-event c)]
    (if-not (map? c)
      base
      (let [errors (cond-> (:errors base)
                     (not (or (:due c) (:has-end c) (:has-point-in-time c)))
                     (conj (err :missing-due
                                {:why "a commitment without a time cannot be scheduled"})))]
        (assoc base :errors errors :ok? (empty? errors))))))

(defn conform
  "Dispatch on the class being asserted: :EconomicEvent / :Commitment /
   :Intent. An unknown class is an error rather than a default pass."
  [class-k record]
  (case class-k
    :EconomicEvent (conform-event record)
    (:Commitment :Intent) (conform-commitment record)
    {:ok? false :examined #{}
     :errors [(err :unsupported-class
                   {:class class-k
                    :supported #{:EconomicEvent :Commitment :Intent}
                    :in-vocabulary? (vocab/known-class? class-k)})]}))

(defn conform-all
  "Check many records. Reports how many were SCANNED next to how many failed,
   so 'nothing was checked' cannot look like 'nothing was wrong'."
  [class-k records]
  (let [rs (mapv #(conform class-k %) records)
        bad (keep-indexed (fn [i r] (when-not (:ok? r) (assoc r :index i))) rs)]
    {:scanned (count rs)
     :failed (count bad)
     :ok? (and (pos? (count rs)) (zero? (count bad)))
     :empty-input? (zero? (count rs))
     :failures (vec bad)}))
