(ns chobo.views
  "Pure-hiccup services-EC components (.cljc, no reagent import) on
  shitsuke.components. Renders ledger entries, subscription/plan status, meter
  gauges, invoice lines, tenant badges. Same dual-render contract as mise.views."
  (:require [shitsuke.components :as c]
            [shitsuke.style :as sstyle]
            [chobo.subscription :as sub]
            [chobo.invoice :as invoice]
            [chobo.tenant :as tenant]
            [chobo.ledger :as ledger]))

(defn class-name [x] (sstyle/class-name x))

(defn- amount [n currency]
  (str (get {:JPY "¥" :USD "$" :EUR "€"} (keyword currency) (str currency " "))
       n))

(defn tenant-badge [t]
  [:span {:class (class-name :tenant-badge)} (or (:name t) (:id t))
   [:small " " (name (:operator-level t :contributor))]])

(defn ledger-entry [a]
  [:div {:class (class-name :ledger-entry) :data-activity (:id a)}
   [:span {:class (class-name :ledger-lane)} (name (:lane a))]
   [:span {:class (class-name :ledger-kind)} (name (:kind a))]
   [:span {:class (class-name :ledger-title)} (:title a)]
   [:span {:class (class-name :ledger-state)} (name (:state a :open))]])

(defn meter-gauge
  "Render a quota/usage gauge: 'used / limit'. opts: :key."
  [plan usage key]
  (let [used (sub/consumed-for usage key)
        limit (sub/quota-for plan key)]
    [:div {:class (class-name :meter-gauge)}
     [:span (name key)]
     [:span (str used (when limit (str " / " limit)))]
     (when (and limit (>= used limit))
       [:span {:class (class-name :meter-over)} "OVER"])]))

(defn plan-status [plan usage]
  [:section {:class (class-name :plan-status)}
   [:h3 (:name plan) " — " (name (:tier plan))]
   [:p "price: " (amount (:price plan 0) (:currency plan "JPY"))]
   (into [:div] (map #(meter-gauge plan usage %) (keys (:quotas plan))))
   (when (pos? (sub/total-overage plan usage))
     [:p {:class (class-name :overage)} "overage: " (amount (sub/total-overage plan usage) (:currency plan "JPY"))])])

(defn invoice-line [l]
  [:div {:class (class-name :invoice-line)}
   [:span (:description l)]
   [:span (amount (:amount l 0) (:currency l "JPY"))]])

(defn invoice-card [inv]
  [:section {:class (class-name :invoice-card) :data-invoice (:id inv)}
   [:h3 "Invoice " (:id inv)]
   (into [:div] (map invoice-line (:lines inv)))
   [:p "status: " (name (:status inv))]
   [:p "total: " (let [t (invoice/totals inv)] (amount (:amount t 0) (:currency t "JPY")))]])

(defn root [db]
  (let [plan (:plan db)
        usage (:usage db)
        invoices (:invoices db)
        t (:tenant db)
        l (or (:ledger db) (ledger/ledger))]
    [:div {:class (class-name :chobo)}
     (when t (tenant-badge t))
     (when plan (plan-status plan usage))
     (when (seq invoices)
       [:section {:class (class-name :invoice-list)}
        (for [i invoices] (invoice-card i))])
     (when (seq (:activities l))
       [:section {:class (class-name :ledger-list)}
        [:h2 "Audit ledger"]
        (for [a (:activities l)] (ledger-entry a))])]))
