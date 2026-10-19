(ns kotoba.labor.ui
  "Operator-facing console for a community domestic-employment actor.

  Renders an HTML read-only panel of contracts, timesheets and payroll,
  using kotoba-lang/html + css. Pure data → markup: no network. The governor
  gates dispatch/payment/disclosure; this view only observes."
  (:require [html.core :as html]
            [css.core :as css]
            [kotoba.labor :as labor]))

;; Domain-specific rules layered on top of the shared operator-theme (css.core).
(def ^:private extra-rules
  {})

(def ^:private sheet (css/merge-theme extra-rules))

(defn- stylesheet [] (html/->html (css/style-node sheet)))

(defn- money [n currency] (str (or n 0) " " (or currency "USD")))

(defn- contract-rows [contracts]
  (for [c contracts]
    [:tr [:td (:contract/id c)]
     [:td (:contract/worker c)]
     [:td (:contract/role c)]
     [:td (name (:contract/wage-type c))]
     [:td.amt (money (:contract/rate c) (:contract/currency c))]]))

(defn- timesheet-rows [entries]
  (for [e entries]
    [:tr [:td (:ts/worker e)]
     [:td (:ts/date e)]
     [:td.amt (:ts/hours e)]
     [:td.amt (or (:ts/break e) "—")]]))

(defn- payroll-rows [payrolls]
  (for [p payrolls]
    [:tr [:td (:payroll/id p)]
     [:td (:payroll/worker p)]
     [:td (:payroll/period p)]
     [:td.amt (money (:payroll/gross p) (:payroll/currency p))]
     [:td.amt (money (:payroll/deductions p) (:payroll/currency p))]
     [:td.amt (money (:payroll/net p) (:payroll/currency p))]]))

(defn dashboard
  "Render a full HTML console for a domestic-employment operator."
  [{:keys [contracts timesheets payrolls] :as ctx}]
  (html/->html
    [:html
     [:head [:meta {:charset "utf-8"}] [:title "cloud-itonami · domestic-employment"]
      [:hiccup/raw (stylesheet)]]
     [:body
      [:header.bar [:h1 "Domestic Employment — Operator Console"] [:span.badge "read-only · governor-gated"]]
      [:main
       (when (seq contracts)
         [:section.card [:h2 "Contracts"]
          [:table [:thead [:tr [:th "ID"] [:th "Worker"] [:th "Role"] [:th "Wage"] [:th.amt "Rate"]]]
           [:tbody (contract-rows contracts)]]])
       (when (seq timesheets)
         [:section.card [:h2 "Timesheets"]
          [:table [:thead [:tr [:th "Worker"] [:th "Date"] [:th.amt "Hours"] [:th.amt "Break"]]]
           [:tbody (timesheet-rows timesheets)]]])
       (when (seq payrolls)
         [:section.card [:h2 "Payroll"]
          [:table [:thead [:tr [:th "ID"] [:th "Worker"] [:th "Period"] [:th.amt "Gross"] [:th.amt "Deductions"] [:th.amt "Net"]]]
           [:tbody (payroll-rows payrolls)]]])]]]))
