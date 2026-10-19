(ns mise.order
  "Order record + status statechart (pure). An Order is {:id :items :shipping
  :contact :payment-ref :status :totals :created-at}. Status transitions:
  :pending → :paid → :fulfilled, with :cancelled reachable from :pending/:paid.
  IOrderStore is an injected port (mock in-memory); D1/live adapters are
  follow-ups.

  Portable .cljc, zero host effects.")

(def statuses #{:pending :paid :fulfilled :cancelled})

(def transitions
  {:pending   #{:paid :cancelled}
   :paid      #{:fulfilled :cancelled}
   :fulfilled #{}
   :cancelled #{}})

(defrecord Order [id items shipping contact payment-ref status totals created-at])

(defn order
  "Construct an Order at :pending status."
  [{:keys [id items shipping contact payment-ref totals created-at]}]
  (map->Order {:id id
               :items (vec items)
               :shipping shipping
               :contact contact
               :payment-ref payment-ref
               :status :pending
               :totals totals
               :created-at created-at}))

(defn can-transition?
  "True if from-status can reach to-status per the statechart."
  [from to]
  (contains? (get transitions from #{}) to))

(defn transition
  "Move an order from its current status to `to`. Returns {:ok order} or
  {:error :invalid-transition :order order}. Pure."
  [ord to]
  (if (can-transition? (:status ord) to)
    {:ok (assoc ord :status to)}
    {:error :invalid-transition :from (:status ord) :to to :order ord}))

(defn mark-paid [ord] (transition ord :paid))
(defn mark-fulfilled [ord] (transition ord :fulfilled))
(defn cancel [ord] (transition ord :cancelled))

;; ---------------------------------------------------------------------------
;; order store port (injected)
;; ---------------------------------------------------------------------------

(defprotocol IOrderStore
  (put-order! [this order]
    "Persist/replace an order. Returns the order (possibly with host-assigned
    fields like :id/:created-at). Side-effectful in real adapters; the mock is
    in-memory and pure-ish.")
  (get-order [this id]
    "Fetch an order by id, or nil.")
  (list-orders [this]
    "Return a seq of all orders (newest-first where applicable)."))

(defrecord MockOrderStore [state]
  IOrderStore
  (put-order! [_ ord]
    (let [stored (if (:id ord) ord (assoc ord :id (str "ord_" (hash ord))))]
      (swap! state assoc (:id stored) stored)
      stored))
  (get-order [_ id]
    (get @state id))
  (list-orders [_]
    (vals @state)))

(defn mock-order-store
  "An in-memory IOrderStore backed by an atom. Tests/SSR/dev only."
  []
  (->MockOrderStore (atom {})))
