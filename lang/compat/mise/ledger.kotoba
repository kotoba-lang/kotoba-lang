(ns mise.ledger
  "Projection adapter: mise.order Order → chobo.ledger activity (v1 stub).

  Bridges retail EC (mise) and services EC (chobo): a completed mise order is
  projected onto the shared audit ledger as an :itonami-style activity so the
  order lives on the same EAVT substrate as itonami's billing/subscription
  flows. v1 is a pure stub (no store side-effect) — the host app appends the
  returned activity to its ILedgerStore. Full auto-projection (order→invoice→
  ledger pipeline) is a follow-up."
  (:require [chobo.ledger :as ledger]
            [mise.order :as order]))

(defn order->activity
  "Project a mise Order into a chobo.ledger Activity map (lane :sales, kind
  :order). Does NOT append — caller appends to its ILedgerStore. opts:
  :tenant :repo :source :source-id."
  [ord opts]
  (let [totals (:totals ord)
        total-amount (get-in totals [:total :amount] 0)
        currency (get-in totals [:total :currency] "JPY")]
    (ledger/activity
     (merge {:lane :sales
             :kind :order
             :title (str "Order " (:id ord))
             :state :open
             :props {:order-id (:id ord)
                     :status (:status ord :pending)
                     :item-count (count (:items ord))
                     :total-amount total-amount
                     :currency currency}}
            (select-keys opts [:id :tenant :repo :source :source-id :created-at])))))

(defn append-order-activity
  "Project `ord` and append it onto a chobo Ledger. Returns the new ledger."
  [lg ord opts]
  (ledger/append-activity lg (order->activity ord opts)))
