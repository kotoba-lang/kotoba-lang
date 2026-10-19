(ns mise.billing
  "Cross-domain projection: mise.order → chobo.invoice.

  Bridges retail EC (mise) and billing (chobo): a placed mise order is
  converted into a chobo.invoice (draft → issued) with the order's totals as a
  single invoice line, plus optional line-item invoicing. v1 is a pure stub —
  the host app persists the invoice via its IInvoiceStore."
  (:require [chobo.invoice :as invoice]
            [mise.pricing :as pricing]))

(defn order->invoice
  "Build a draft chobo.invoice from a mise order. Adds one line for the order
  total (description from order title/id). Returns a draft Invoice."
  ([ord]
   (order->invoice ord {}))
  ([ord opts]
   (let [totals (:totals ord)
         total (:total totals (pricing/zero-price))
         tenant (or (:tenant opts) (:account-id ord) (:tenant ord))]
     (-> (invoice/invoice tenant
                          {:lines [(invoice/line
                                    (str "Order " (:id ord))
                                    (:amount total 0)
                                    (:currency total "JPY"))]})
         (assoc :totals total)))))

(defn issue-invoice
  "Mark a draft invoice as issued. Returns the issued invoice or nil if not
  draft."
  [inv]
  (invoice/mark-issued inv))

(defn billing-activity
  "Project the order→invoice event onto chobo.ledger as a :billing activity
  (kind :invoice). Delegates to chobo.invoice/billing-activity."
  [inv opts]
  (invoice/billing-activity inv opts))
