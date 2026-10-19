(ns chobo.invoice
  "Invoice + settlement (pure). An Invoice is {:id :tenant :lines :status
  :totals :issued-at :due-at}. Lines are {:description :amount :currency}.
  Status: :draft → :issued → :paid | :overdue | :cancelled; dunning (滞留) =
  issued past due. IInvoiceStore is an injected port (mock). Portable .cljc.

  This is the billing counterpart to mise.pricing: mise computes cart totals
  at checkout; chobo.invoice persists the billed document and tracks payment."
  (:require [chobo.ledger :as ledger]))

(defrecord Invoice [id tenant lines status totals issued-at due-at])
(defrecord Line [description amount currency])

(defn line
  ([description amount]
   (line description amount "JPY"))
  ([description amount currency]
   (->Line description amount currency)))

(def statuses #{:draft :issued :paid :overdue :cancelled})
(def transitions
  {:draft   #{:issued :cancelled}
   :issued  #{:paid :overdue :cancelled}
   :paid    #{}
   :overdue #{:paid :cancelled}
   :cancelled #{}})

(defn invoice
  ([tenant]
   (invoice tenant {}))
  ([tenant {:keys [id lines issued-at due-at]}]
   (->Invoice (or id (str "inv_" (hash [tenant lines])))
              tenant (vec lines) :draft nil issued-at due-at)))

(defn add-line [inv l] (update inv :lines conj l))

(defn totals
  "Sum line amounts (per currency; v1 assumes single currency)."
  [inv]
  (let [cur (get-in inv [:lines 0 :currency] "JPY")
        amt (reduce + 0 (map :amount (:lines inv)))]
    {:amount amt :currency cur}))

(defn can-transition? [from to] (contains? (get transitions from #{}) to))

(defn transition [inv to]
  (when (can-transition? (:status inv :draft) to)
    (assoc inv :status to)))

(defn mark-issued [inv] (transition inv :issued))
(defn mark-paid [inv] (transition inv :paid))
(defn cancel [inv] (transition inv :cancelled))
(defn mark-overdue [inv] (transition inv :overdue))

(defn dunning?
  "True if issued and past due (due-at < now) — caller passes `now` for purity."
  [inv now]
  (and (= (:status inv) :issued)
       (:due-at inv)
       (pos? (compare (str now) (str (:due-at inv))))))

;; ---------------------------------------------------------------------------
;; invoice → ledger projection (billing activity)
;; ---------------------------------------------------------------------------

(defn billing-activity
  "Build a ledger activity for an invoice (lane :billing, kind :invoice). Caller
  appends to the audit ledger."
  [inv opts]
  (ledger/activity
   (merge {:lane :billing :kind :invoice
           :title (str "Invoice " (:id inv))
           :tenant (:tenant inv)
           :source :billing
           :props {:invoice-id (:id inv) :status (:status inv)
                   :total (totals inv)}} opts)))

;; ---------------------------------------------------------------------------
;; IInvoiceStore port (injected)
;; ---------------------------------------------------------------------------

(defprotocol IInvoiceStore
  (put-invoice! [this inv])
  (get-invoice [this id])
  (list-invoices [this])
  (overdue-invoices [this now]))

(defrecord MockInvoiceStore [state]
  IInvoiceStore
  (put-invoice! [_ inv]
    (let [inv (if (:id inv) inv (assoc inv :id (str "inv_" (hash inv))))]
      (swap! state assoc (:id inv) inv)
      inv))
  (get-invoice [_ id] (get @state id))
  (list-invoices [_] (vals @state))
  (overdue-invoices [s now] (filterv #(dunning? % now) (vals @state))))

(defn mock-invoice-store [] (->MockInvoiceStore (atom {})))
