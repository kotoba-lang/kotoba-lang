(ns valueflows.commitment
  "Promises and their settlement: `vf:fulfills` (event -> Commitment) and
   `vf:satisfies` (event or commitment -> Intent).

   This is the layer the workspace did not have. It already had observed
   events (ENGI transfers, PLM postings) and it had planned quantities (MPS),
   but nothing recorded a PROMISE and then answered whether it was kept. With
   it, an order, an MPS line, and a lender's offer are the same shape and land
   on one queryable plane.

   ## `finished` does not mean fulfilled

   Upstream is explicit: `vf:finished` is \"complete or not … irrespective of
   if the original goal has been met, and indicates simply that no more will
   be done.\" So a commitment closed at 60% is not a contradiction to be
   flagged — it is `:closed-short`, an ordinary business outcome, and
   collapsing it into either `:fulfilled` or an error would destroy the one
   fact someone wants to query.

   ## Time

   `:due` is compared with `compare`, so both a caller-defined period number
   and an ISO-8601 timestamp string work — ISO-8601 sorts lexicographically in
   chronological order, which is why no date library is needed. MIXING the two
   in one set is refused rather than silently ordered.

   Overdue is only computable against an `:as-of`. Without one the answer is
   `:unknown`, never `false`."
  (:require [valueflows.vocabulary :as vocab]
            [valueflows.unit :as vfu]))

;; ── measures ──────────────────────────────────────────────────────────────

(defn- qty [m] (:has-numerical-value m))
(defn- unit [m] (:has-unit m))

(defn- promised-quantity
  "The quantity a commitment or intent is stated in, per the action's own
   `eventQuantity`: effort for `work`, resource for `produce`."
  [c]
  (case (vocab/event-quantity (:action c))
    :effort (:effort-quantity c)
    :resource (:resource-quantity c)
    :both (or (:resource-quantity c) (:effort-quantity c))
    nil))

(defn- sum-quantities
  "=> [:ok m] | [:error {:code :unit-mismatch …}]"
  [ms]
  (reduce (fn [[_ acc] m]
            (cond
              (nil? m) [:ok acc]
              (nil? acc) [:ok m]
              (and (not= (unit acc) (unit m))
                   (not (vfu/same? (unit acc) (unit m))))
              (reduced [:error {:code :unit-mismatch
                                :units [(unit acc) (unit m)]}])
              :else [:ok (update acc :has-numerical-value + (qty m))]))
          [:ok nil] ms))

;; ── time ──────────────────────────────────────────────────────────────────

(defn- comparable? [a b]
  (or (and (number? a) (number? b)) (and (string? a) (string? b))))

(defn- overdue
  "=> true | false | :unknown. :unknown covers both 'no as-of given' and 'no
   due date recorded' — a promise with no deadline cannot be late, and saying
   `false` would claim it is on time."
  [due as-of]
  (cond
    (nil? as-of) :unknown
    (nil? due) :unknown
    (not (comparable? due as-of)) :unknown
    :else (pos? (compare as-of due))))

;; ── one commitment ────────────────────────────────────────────────────────

(defn- state
  [ratio finished?]
  (cond
    (and finished? (nil? ratio)) :closed-unmeasurable
    finished? (cond (> ratio 1) :closed-over
                    (= ratio 1) :closed-exact
                    :else :closed-short)
    (nil? ratio) :open
    (> ratio 1) :over-fulfilled
    (= ratio 1) :fulfilled
    (zero? ratio) :open
    :else :partially-fulfilled))

(defn fulfilment
  "How much of `commitment` the given events fulfilled.

   Only events whose `:fulfills` names this commitment count; passing the
   whole log is fine. An event that fulfils a commitment with a DIFFERENT
   action is an error naming both, because a `consume` does not settle a
   promise to `produce`.

   => {:ok? true :commitment id :state :partially-fulfilled
       :promised m :fulfilled m :outstanding m :ratio 3/5
       :events n :overdue true|false|:unknown}"
  [commitment events {:keys [as-of] :as _opts}]
  (let [id (:id commitment)
        a (:action commitment)
        promised (promised-quantity commitment)
        mine (filterv #(let [f (:fulfills %)]
                         (or (= f id) (and (coll? f) (some #{id} f))))
                      events)
        wrong-action (filterv #(not= (:action %) a) mine)]
    (cond
      (not (vocab/action? a))
      {:ok? false :insufficient :unknown-action :detail {:commitment id :action a}}

      (nil? (qty promised))
      {:ok? false :insufficient :promise-not-measured
       :detail {:commitment id :action a
                :why "a promise with no quantity cannot be settled against"}}

      (seq wrong-action)
      {:ok? false :insufficient :action-mismatch
       :detail {:commitment id :commitment-action a
                :event-actions (into (sorted-set) (map :action) wrong-action)
                :why "an event settles a promise only for the same action"}}

      :else
      (let [[tag done] (sum-quantities (map promised-quantity mine))]
        (if (= :error tag)
          {:ok? false :insufficient :unit-mismatch
           :detail (assoc done :commitment id)}
          (if (and done
                   (not= (unit done) (unit promised))
                   (not (vfu/same? (unit done) (unit promised))))
            {:ok? false :insufficient :unit-mismatch
             :detail {:commitment id :units [(unit promised) (unit done)]}}
            (let [d (or (qty done) 0)
                  p (qty promised)
                  ratio (/ d p)]
              {:ok? true
               :commitment id
               :action a
               :promised promised
               :fulfilled (or done (assoc promised :has-numerical-value 0))
               :outstanding (assoc promised :has-numerical-value (max 0 (- p d)))
               :ratio ratio
               :events (count mine)
               :finished? (boolean (:finished commitment))
               :state (state ratio (boolean (:finished commitment)))
               :due (:due commitment)
               :overdue (overdue (:due commitment) as-of)})))))))

;; ── many, plus the events that point at nothing ───────────────────────────

(defn fulfilments
  "Settle a whole set. Reports SCANNED next to failed, and lists events whose
   `:fulfills` names a commitment that is not in the set — an orphan
   fulfilment is evidence of a missing record, and dropping it would make the
   remaining commitments look correctly settled."
  [commitments events {:keys [as-of] :as opts}]
  (let [ids (into #{} (map :id) commitments)
        named (fn [e] (let [f (:fulfills e)] (if (coll? f) f (when f [f]))))
        orphans (filterv (fn [e] (some #(not (contains? ids %)) (or (named e) [])))
                         events)
        rows (mapv #(fulfilment % events opts) commitments)
        bad (filterv #(not (:ok? %)) rows)
        ok (filterv :ok? rows)]
    {:scanned (count commitments)
     :settled (count ok)
     :unsettleable (count bad)
     :ok? (and (pos? (count commitments)) (empty? bad))
     :empty-input? (zero? (count commitments))
     :rows rows
     :failures bad
     :orphan-fulfilments (mapv #(select-keys % [:action :fulfills]) orphans)
     :complete? (and (pos? (count commitments)) (empty? bad) (empty? orphans))
     :by-state (frequencies (map :state ok))
     :overdue-open (filterv #(and (true? (:overdue %))
                                  (contains? #{:open :partially-fulfilled} (:state %)))
                            ok)
     :as-of as-of}))

(defn- due-sort-key
  "Order by deadline without ever comparing a number to a string.

   NOT `(str due)`: stringifying to make dates sortable puts \"10\" before
   \"4\", which reorders a queue of promises while looking like it worked. The
   type rank comes FIRST so element-wise vector comparison stops before it can
   reach two values of different types."
  [r]
  (let [d (:due r)
        id (str (:commitment r))]
    (cond
      (nil? d) [2 0 "" id]                 ; undated last, and marked separately
      (number? d) [0 d "" id]
      :else [1 0 (str d) id])))

(defn outstanding
  "What is still owed, soonest first. Commitments with no due date sort last
   and are marked, rather than being given a date so they can be ordered."
  [commitments events opts]
  (let [f (fulfilments commitments events opts)]
    {:ok? (:ok? f)
     :scanned (:scanned f)
     :rows (vec (sort-by due-sort-key
                         (filterv #(and (:ok? %)
                                        (pos? (qty (:outstanding %)))
                                        (not (:finished? %)))
                                  (:rows f))))
     :undated (filterv #(and (:ok? %) (nil? (:due %))) (:rows f))
     :unsettleable (:unsettleable f)
     :orphan-fulfilments (:orphan-fulfilments f)}))

;; ── intents ───────────────────────────────────────────────────────────────

(defn satisfaction
  "How much of an Intent the satisfying commitments and events cover.

   `vf:satisfies` may come from either a Commitment or an EconomicEvent, so
   both are accepted in one collection. `vf:minimumQuantity` is checked
   separately: a satisfaction under the stated floor is not a partial success,
   it is below the minimum the offer was made at."
  [intent satisfiers _opts]
  (let [id (:id intent)
        wanted (promised-quantity intent)
        mine (filterv #(let [s (:satisfies %)]
                         (or (= s id) (and (coll? s) (some #{id} s))))
                      satisfiers)]
    (if (nil? (qty wanted))
      {:ok? false :insufficient :intent-not-measured :detail {:intent id}}
      (let [[tag got] (sum-quantities (map promised-quantity mine))]
        (if (= :error tag)
          {:ok? false :insufficient :unit-mismatch :detail (assoc got :intent id)}
          (let [g (or (qty got) 0)
                w (qty wanted)
                minimum (qty (:minimum-quantity intent))]
            {:ok? true
             :intent id
             :wanted wanted
             :satisfied (or got (assoc wanted :has-numerical-value 0))
             :unsatisfied (assoc wanted :has-numerical-value (max 0 (- w g)))
             :ratio (/ g w)
             :satisfiers (count mine)
             :below-minimum? (when minimum (< g minimum))
             :minimum (:minimum-quantity intent)
             :available (:available-quantity intent)
             :finished? (boolean (:finished intent))
             :state (state (/ g w) (boolean (:finished intent)))}))))))
