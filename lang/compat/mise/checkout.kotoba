(ns mise.checkout
  "Checkout flow state machine + payment port (pure).

  A Checkout is {:stage :contact :shipping :payment :cart :order-id}. Stages
  advance :contact → :shipping → :payment → :review → :confirmed. Fields are
  free-form maps the host fills; validate-stage checks required keys per stage.

  IPaymentPort is an injected adapter (authorize/capture). v1 ships a mock
  adapter that always authorizes. Stripe/live adapters are follow-ups.

  Portable .cljc, zero host effects."
  (:require [mise.pricing :as pricing]
            [mise.cart :as cart]
            [mise.order :as order]))

(def stages [:contact :shipping :payment :review :confirmed])

(def required-fields
  {:contact    #{:email :name}
   :shipping   #{:address :city :postal :country}
   :payment    #{:payment-method-ref}
   :review     #{}})

(defrecord Checkout [stage contact shipping payment cart order-id])

(defn checkout
  "Start a checkout for a cart at the :contact stage."
  ([cart]
   (checkout cart {}))
  ([cart fields]
   (->Checkout :contact
               (:contact fields {})
               (:shipping fields {})
               (:payment fields {})
               cart
               nil)))

(defn stage-index
  "Index of a stage in `stages`, or nil if unknown."
  [stage]
  (loop [i 0 [s & rest] stages]
    (cond
      (nil? s) nil
      (= s stage) i
      :else (recur (inc i) rest))))

(defn- next-after [stage]
  (let [i (stage-index stage)]
    (when (and i (< i (dec (count stages))))
      (nth stages (inc i)))))

(defn stage-fields
  "The merged fields for a stage from a checkout."
  [checkout stage]
  (case stage
    :contact  (:contact checkout)
    :shipping (:shipping checkout)
    :payment  (:payment checkout)
    {}))

(defn validate-stage
  "Return nil if the stage's required fields are present, else a seq of missing
  keys. A nil/empty result means the stage is valid."
  [checkout stage]
  (let [required (get required-fields stage #{})
        fields (stage-fields checkout stage)]
    (seq (filter #(not (contains? fields %)) required))))

(defn set-field
  "Set a single field in the current stage's field map. Returns a new Checkout."
  [checkout stage key value]
  (case stage
    :contact  (assoc checkout :contact (assoc (:contact checkout) key value))
    :shipping (assoc checkout :shipping (assoc (:shipping checkout) key value))
    :payment  (assoc checkout :payment (assoc (:payment checkout) key value))
    checkout))

(defn set-fields
  "Merge a map into a stage's fields."
  [checkout stage m]
  (reduce-kv (fn [c k v] (set-field c stage k v)) checkout m))

(defn next-stage
  "Advance to the next stage if the current one validates; returns
  {:ok checkout} or {:errors [..] checkout}. Never skips :confirmed."
  [checkout]
  (let [stage (:stage checkout)]
    (if (= stage :confirmed)
      {:ok checkout}
      (if-let [missing (validate-stage checkout stage)]
        {:errors missing :checkout checkout}
        (if-let [nxt (next-after stage)]
          {:ok (assoc checkout :stage nxt)}
          {:ok checkout})))))

(defn prev-stage
  "Go back one stage (clamped at :contact)."
  [checkout]
  (let [i (stage-index (:stage checkout))]
    (if (or (nil? i) (<= i 0))
      checkout
      (assoc checkout :stage (nth stages (dec i))))))

;; ---------------------------------------------------------------------------
;; payment port
;; ---------------------------------------------------------------------------

(defprotocol IPaymentPort
  (authorize [this ctx amount]
    "Reserve `amount` for the order. Returns a PaymentRef map {:ref :status
    :amount} where :status is :authorized | :declined | :failed. Pure (mock) or
    side-effectful (real adapter — wrap in host effect).")
  (capture [this ctx ref]
    "Settle an authorized payment. Returns an updated PaymentRef."))

(defrecord MockPaymentPort []
  IPaymentPort
  (authorize [_ ctx amount]
    {:ref (str "mock_" (hash ctx) "_" (hash amount))
     :status :authorized
     :amount amount})
  (capture [_ _ ref]
    (assoc ref :status :captured)))

(defn mock-payment-port [] (->MockPaymentPort))

;; ---------------------------------------------------------------------------
;; place order
;; ---------------------------------------------------------------------------

(defn place-order
  "Finalize a :review-stage checkout into an Order via the payment port.
  Returns {:ok order} on success or {:error msg :checkout checkout}. The
  checkout must be at :review; caller advances through stages first."
  [checkout payment-port opts]
  (let [{:keys [discount tax shipping ctx order-id-fn]
         :or {discount (pricing/no-discount)
              tax (pricing/flat-tax 0.0)
              shipping (pricing/free-shipping)
              order-id-fn #(str "ord_" (hash %))}} opts
        cart (:cart checkout)
        totals (cart/totals cart {:discount discount :tax tax
                                  :shipping shipping :ctx ctx})
        amount (:total totals)]
    (if (not= (:stage checkout) :review)
      {:error "checkout not at review stage" :checkout checkout}
      (let [pref (authorize payment-port (or ctx {}) amount)]
        (if (= (:status pref) :authorized)
          (let [ord (order/map->Order
                     {:id (order-id-fn checkout)
                      :items (:lines cart)
                      :shipping (:shipping checkout)
                      :contact (:contact checkout)
                      :payment-ref pref
                      :status :pending
                      :totals totals})]
            {:ok (assoc checkout :stage :confirmed :order-id (:id ord)) :order ord})
          {:error (:status pref) :checkout checkout})))))
