(ns kotoba.banking.ui
  "Operator-facing dashboard for a community banking actor.

  Renders an HTML read-only panel from banking records (accounts, postings,
  clearing batches) plus Open Banking (Berlin Group XS2A) payment-initiation
  responses built by kotoba.banking.api, using kotoba-lang/html + css. Pure
  data → markup: no network, no DOM. The governor and audit ledger decide
  writes; this view only observes, so it can never leak a write path."
  (:require [html.core :as html]
            [css.core :as css]
            [kotoba.banking :as bank]
            [kotoba.banking.api :as api]))

;; Domain-specific rules layered on top of the shared operator-theme (css.core).
(def ^:private extra-rules
  {})

(def ^:private sheet (css/merge-theme extra-rules))

(defn- stylesheet []
  (html/->html (css/style-node sheet)))

(defn- money [n currency]
  (str (or n 0) " " (or currency "USD")))

(defn- status-badge [posting]
  (if (:ledger/unbalanced posting)
    [:span.err "unbalanced"]
    [:span.ok "balanced"]))

(defn accounts-table [accounts]
  [:table
   [:thead [:tr [:th "Account"] [:th "Holder"] [:th "Type"] [:th.amt "Balance"]]]
   [:tbody
    (for [a accounts]
      [:tr [:td (:account/id a)]
           [:td (or (:account/holder a) "—")]
           [:td (name (or (:account/type a) :checking))]
           [:td.amt (money (:account/balance a) (:account/currency a))]])]])

(defn postings-table [postings]
  [:table
   [:thead [:tr [:th "Posting"] [:th "Entries"] [:th "Status"] [:th "Memo"]]]
   [:tbody
    (for [p postings]
      [:tr [:td (:ledger/posting p)]
           [:td (count (:ledger/entries p))]
           [:td (status-badge p)]
           [:td (or (:ledger/memo p) [:span.muted "—"])]])]])

(defn clearing-table [batches]
  [:table
   [:thead [:tr [:th "Batch"] [:th "Postings"] [:th "Currency"] [:th "Status"]]]
   [:tbody (for [b batches]
             [:tr [:td (:clearing/id b)]
                  [:td (count (:clearing/postings b))]
                  [:td (:clearing/currency b)]
                  [:td (name (:clearing/status b))]])]])

(defn- payment-status-badge [status]
  (cond
    (contains? #{"RJCT" "CANC"} status) [:span.err (or status "—")]
    (contains? #{"ACSC" "ACCC" "ACCP" "ACFC"} status) [:span.ok (or status "—")]
    :else [:span.muted (or status "—")]))

(defn payment-initiations-table
  "Render Open Banking (Berlin Group XS2A) payment-initiation 201 response
  bodies, as built by kotoba.banking.api/payment-initiation-response — the
  raw JSON-shaped map with string keys \"paymentId\"/\"transactionStatus\"."
  [payment-initiations]
  [:table
   [:thead [:tr [:th "Payment ID"] [:th "Status"] [:th "Meaning"]]]
   [:tbody
    (for [p payment-initiations]
      (let [status (get p "transactionStatus")]
        [:tr [:td (or (get p "paymentId") "—")]
             [:td (payment-status-badge status)]
             [:td (or (get api/transaction-statuses status) [:span.muted "—"])]]))]])

(defn account-details-table
  "Render Open Banking (Berlin Group XS2A) accountDetails maps, as built by
  kotoba.banking.api/account->account-details — the raw JSON-shaped map
  returned inside GET /v1/accounts's \"accounts\" array."
  [accounts-details]
  [:table
   [:thead [:tr [:th "Resource ID"] [:th "IBAN"] [:th "Product"] [:th "Cash account type"] [:th.amt "Currency"]]]
   [:tbody
    (for [a accounts-details]
      [:tr [:td (or (get a "resourceId") "—")]
           [:td (or (get a "iban") "—")]
           [:td (or (get a "product") "—")]
           [:td (or (get a "cashAccountType") "—")]
           [:td.amt (or (get a "currency") "—")]])]])

(defn dashboard
  "Render a full HTML dashboard page for a banking operator."
  [{:keys [accounts postings clearing-batches payment-initiations account-details] :as ctx}]
  (html/->html
    [:html
     [:head [:meta {:charset "utf-8"}] [:title "cloud-itonami · banking"]
      [:hiccup/raw (stylesheet)]]
     [:body
      [:header.bar
       [:h1 "Community Banking — Operator Console"]
       [:span.badge "read-only · governor-gated"]]
      [:main
       [:section.card [:h2 "Accounts"] (accounts-table accounts)]
       (when (seq postings)
         [:section.card [:h2 "Recent postings"] (postings-table postings)])
       (when (seq clearing-batches)
         [:section.card [:h2 "Clearing batches"] (clearing-table clearing-batches)])
       (when (seq payment-initiations)
         [:section.card [:h2 "Payment initiations (Open Banking / XS2A)"]
          (payment-initiations-table payment-initiations)])
       (when (seq account-details)
         [:section.card [:h2 "Account information (Open Banking / XS2A)"]
          (account-details-table account-details)])]]]))
