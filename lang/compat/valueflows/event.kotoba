(ns valueflows.event
  "Apply economic events to an inventory, driven by the action behaviour
   table — REA accounting as a pure function.

   This is what makes the mirror executable rather than decorative. The
   arithmetic is not written per action: `produce` increments and `transfer`
   decrement/increments because the pinned vocabulary says so, so a change
   upstream changes behaviour without an edit here.

   Shapes (Valueflows property names, kebab-cased):

     event      {:action :transfer
                 :provider a :receiver b
                 :resource-inventoried-as \"r1\"
                 :to-resource-inventoried-as \"r2\"
                 :resource-quantity {:has-numerical-value 3 :has-unit :kg}
                 :effort-quantity   {:has-numerical-value 2 :has-unit :hour}
                 :to-location :warehouse-b}

     inventory  {\"r1\" {:accounting-quantity {:has-numerical-value 10 :has-unit :kg}
                        :onhand-quantity     {:has-numerical-value 10 :has-unit :kg}
                        :primary-accountable a
                        :current-location :warehouse-a}}

   Errors are values, never exceptions, and an event that errors leaves the
   inventory untouched — fail closed, no partial application."
  (:require [valueflows.vocabulary :as vocab]
            [valueflows.unit :as unit]))

;; ── measures ──────────────────────────────────────────────────────────────

(defn- measure? [m]
  (and (map? m) (number? (:has-numerical-value m))))

(defn- add-measure
  "Sum two measures, refusing across units. Returns [:ok m] / [:error code].
   Unit mismatch is a real defect (3 kg + 3 hours), not a rounding concern."
  [a b]
  (cond
    (nil? a) [:ok b]
    (nil? b) [:ok a]
    ;; Additive: exact equality still passes (including two unitless
    ;; measures), and alias equality now passes too, so `:kg` and `:kilogram`
    ;; stop being a spurious mismatch. Genuinely different units still fail.
    (and (not= (:has-unit a) (:has-unit b))
         (not (unit/same? (:has-unit a) (:has-unit b))))
    [:error :unit-mismatch]
    :else [:ok (assoc a :has-numerical-value (+ (:has-numerical-value a)
                                                (:has-numerical-value b)))]))

(defn- negate [m]
  (when m (update m :has-numerical-value -)))

;; ── quantity register updates ─────────────────────────────────────────────

(def ^:private quantity-moving-effects
  #{:increment :decrement :incrementTo :decrementIncrement})

(defn- moves-quantity? [effect] (contains? quantity-moving-effects effect))

(def ^:private registers
  ;; behaviour key -> the inventory field it moves
  {:accounting-effect :accounting-quantity
   :onhand-effect     :onhand-quantity})

(defn- bump
  "Add `delta` to one register of one resource. Absent resource starts at nil,
   which `add-measure` treats as zero — a first `produce` creates the row."
  [inventory res-id register delta]
  (if (nil? res-id)
    [:error {:code :missing-resource :register register}]
    (let [[tag v] (add-measure (get-in inventory [res-id register]) delta)]
      (if (= tag :error)
        [:error {:code v :register register :resource res-id}]
        [:ok (assoc-in inventory [res-id register] v)]))))

(defn- apply-quantity-effect
  [inventory event effect-kind register]
  (let [{:keys [resource-inventoried-as to-resource-inventoried-as
                resource-quantity]} event
        q resource-quantity]
    (case effect-kind
      :notApplicable [:ok inventory]
      nil [:ok inventory]
      :increment (bump inventory resource-inventoried-as register q)
      :decrement (bump inventory resource-inventoried-as register (negate q))
      :incrementTo (bump inventory to-resource-inventoried-as register q)
      :decrementIncrement
      (let [[tag inv] (bump inventory resource-inventoried-as register (negate q))]
        (if (= tag :error)
          [:error inv]
          (bump inv to-resource-inventoried-as register q)))
      [:error {:code :unknown-effect :effect effect-kind :register register}])))

;; ── non-quantity effects ──────────────────────────────────────────────────

(defn- apply-scalar-effect
  "location / contained / accountable / stage / state. `new` and `update`
   write the event's value onto the subject resource; `updateTo` writes onto
   the receiving resource; `remove` clears; `notApplicable` does nothing."
  [inventory event effect-kind field value-from-event]
  (let [{:keys [resource-inventoried-as to-resource-inventoried-as]} event
        v (value-from-event event)]
    (case effect-kind
      (:notApplicable nil) inventory
      (:new :update) (if (and resource-inventoried-as (some? v))
                       (assoc-in inventory [resource-inventoried-as field] v)
                       inventory)
      :updateTo (if (and to-resource-inventoried-as (some? v))
                  (assoc-in inventory [to-resource-inventoried-as field] v)
                  inventory)
      :remove (if resource-inventoried-as
                (update inventory resource-inventoried-as dissoc field)
                inventory)
      inventory)))

(def ^:private scalar-effects
  ;; behaviour key -> [inventory field, how to read the new value off the event]
  [[:location-effect    :current-location   #(or (:to-location %) (:at-location %))]
   [:contained-effect   :contained-in       :contained-in]
   [:accountable-effect :primary-accountable #(or (:receiver %) (:provider %))]
   [:stage-effect       :stage              :output-of]
   [:state-effect       :state              :state]])

;; ── the entry point ───────────────────────────────────────────────────────

(defn apply-event
  "Fold one event into the inventory.

   => {:ok? true  :inventory inv' :action k}
      {:ok? false :inventory inv (unchanged) :errors [{:code ...}]}"
  [inventory event]
  (let [a (:action event)]
    (cond
      (not (vocab/action? a))
      {:ok? false :inventory inventory
       :errors [{:code :unknown-action :action a
                 :known (vocab/action-names)}]}

      (and (= :resource (vocab/event-quantity a))
           (not (measure? (:resource-quantity event)))
           ;; NB: not `(some #{effect-a effect-b} [...])` — when an action has
           ;; the same effect on both registers (produce: increment/increment)
           ;; that set literal has a duplicate key and throws at runtime.
           (boolean (some moves-quantity?
                          [(vocab/effect a :accounting-effect)
                           (vocab/effect a :onhand-effect)])))
      {:ok? false :inventory inventory
       :errors [{:code :missing-resource-quantity :action a}]}

      :else
      (let [step (fn [[inv errs] [behaviour-k register]]
                   (if (seq errs)
                     [inv errs]
                     (let [[tag v] (apply-quantity-effect
                                    inv event (vocab/effect a behaviour-k) register)]
                       (if (= tag :error) [inv [v]] [v errs]))))
            [inv errs] (reduce step [inventory []] registers)]
        (if (seq errs)
          {:ok? false :inventory inventory :errors errs}
          {:ok? true :action a
           :inventory (reduce (fn [i [behaviour-k field read-v]]
                                (apply-scalar-effect i event (vocab/effect a behaviour-k)
                                                     field read-v))
                              inv scalar-effects)})))))

(defn apply-events
  "Fold a sequence. Stops at the first error and reports its index, so a bad
   event in position 40 does not read as 'the whole ledger is fine'."
  [inventory events]
  (reduce (fn [acc [i e]]
            (let [r (apply-event (:inventory acc) e)]
              (if (:ok? r)
                (assoc acc :inventory (:inventory r) :applied (inc (:applied acc)))
                (reduced (assoc acc :ok? false :failed-at i :errors (:errors r))))))
          {:ok? true :inventory inventory :applied 0}
          (map-indexed vector events)))

(defn quantity
  "Read one register, e.g. (quantity inv \"r1\" :onhand-quantity)."
  [inventory res-id register]
  (get-in inventory [res-id register]))

(defn numerical
  "The number only, or nil. nil means NOT MEASURED — callers must not read it
   as zero (that is how an unmeasured balance becomes a clean-looking 0)."
  [inventory res-id register]
  (:has-numerical-value (quantity inventory res-id register)))
