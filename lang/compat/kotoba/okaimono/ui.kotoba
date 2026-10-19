(ns kotoba.okaimono.ui
  "Operator-facing console for a community last-mile courier actor.

  Renders an HTML read-only panel of orders (status badges, totals,
  dispatchability) using kotoba-lang/html + css. Pure data → markup: no
  network. The governor gates dispatch/settlement; this view only
  observes."
  (:require [html.core :as html]
            [css.core :as css]
            [kotoba.okaimono :as ok]))

;; Domain-specific rules layered on top of the shared operator-theme (css.core).
(def ^:private extra-rules
  {})

(def ^:private sheet (css/merge-theme extra-rules))

(defn- stylesheet [] (html/->html (css/style-node sheet)))

(defn- status-badge [o]
  (case (:okaimono/status o)
    :delivered   [:span.ok "delivered"]
    :packed      [:span.warn "packed"]
    :handed-over [:span.warn "handed-over"]
    :cancelled   [:span.err "cancelled"]
    [:span.muted (name (:okaimono/status o))]))

(defn- order-rows [orders]
  (for [o orders]
    [:tr [:td (:okaimono/id o)]
     [:td (:okaimono/store o)]
     [:td (str (ok/item-count o))]
     [:td (str (ok/total o) " " (:okaimono/currency o))]
     [:td (if (:okaimono/cod? o) [:span.warn "COD"] [:span.muted "—"])]
     [:td (if (ok/dispatchable? o) [:span.ok "✓"] [:span.muted "—"])]
     [:td (status-badge o)]]))

(defn- line-rows [lines]
  (for [l lines]
    [:tr [:td (:line/sku l)]
     [:td (:line/name l)]
     [:td (str (:line/qty l))]
     [:td (str (:line/unit-price l))]
     [:td (str (ok/line-total l))]]))

(defn dashboard
  "Render a full HTML console for a courier operator's order book.
  ctx: {:orders [..] :lines [..]}."
  [{:keys [orders lines]}]
  (html/->html
    [:html
     [:head [:meta {:charset "utf-8"}] [:title "cloud-itonami · okaimono"]
      [:hiccup/raw (stylesheet)]]
     [:body
      [:header.bar [:h1 "Orders — Operator Console"] [:span.badge "read-only · governor-gated"]]
      [:main
       (when (seq orders)
         [:section.card [:h2 "Orders"]
          [:table [:thead [:tr [:th "ID"] [:th "Store"] [:th "Items"] [:th "Total"] [:th "COD"] [:th "Dispatchable"] [:th "Status"]]]
           [:tbody (order-rows orders)]]])
       (when (seq lines)
         [:section.card [:h2 "Lines"]
          [:table [:thead [:tr [:th "SKU"] [:th "Name"] [:th "Qty"] [:th "Unit price"] [:th "Total"]]]
           [:tbody (line-rows lines)]]])]]]))
