(ns kotoba.reservation.ui
  "Operator-facing console for a reservation-bearing actor.

  Renders an HTML read-only panel of inventory buckets, open holds and
  quotes, using kotoba-lang/html + css. Pure data → markup: no network.
  The governor gates booking and pricing; this view only observes."
  (:require [html.core :as html]
            [css.core :as css]
            [kotoba.reservation :as res]))

;; Domain-specific rules layered on top of the shared operator-theme (css.core).
(def ^:private extra-rules
  {})

(def ^:private sheet (css/merge-theme extra-rules))

(defn- stylesheet [] (html/->html (css/style-node sheet)))

(defn- availability-badge
  "An authorized-overbooking sale is not the same thing as a sold-out
  bucket and neither is the same thing as physically oversold — the
  console shows all three distinctly rather than collapsing them into
  one 'full' state an operator would have to go read the numbers to
  disambiguate."
  [b]
  (let [n (res/available b)]
    (cond
      (res/oversold? b) [:span.err (str "oversold · " n " left")]
      (zero? n)         [:span.warn "sold out"]
      :else             [:span.ok (str n " left")])))

(defn- bucket-rows [buckets]
  (for [b buckets]
    [:tr [:td (:inv/resource b)]
     [:td (str (:inv/date b))]
     [:td (name (:inv/class b))]
     [:td (:inv/capacity b)]
     [:td (:inv/sold b)]
     [:td (:inv/held b)]
     [:td (if (pos? (:inv/overbook b 0)) (str "+" (:inv/overbook b)) "—")]
     [:td (availability-badge b)]]))

(defn- hold-rows [holds now]
  (for [h holds]
    [:tr [:td (:hold/id h)]
     [:td (:hold/resource h)]
     [:td (str (:hold/date h))]
     [:td (name (:hold/class h))]
     [:td (:hold/qty h)]
     [:td (str (:hold/expires-at h))]
     [:td (cond
            (= :confirmed (:hold/status h)) [:span.ok "confirmed"]
            (res/expired? h now)            [:span.err "expired"]
            :else                           [:span.muted "held"])]]))

(defn- quote-rows [q]
  (for [l (:quote/lines q)]
    [:tr [:td (name (:line/kind l))]
     [:td (str (or (:line/date l) (:line/label l) "—"))]
     [:td (or (:line/qty l) "—")]
     [:td (if (:line/bp l) (str (:line/bp l) " bp") "—")]
     [:td (:line/amount l)]]))

(defn dashboard
  "Render a full HTML console for a reservation operator. `now` is passed
  in, never read from a clock — the same discipline the core namespace
  keeps, so a console rendered from an audit record shows what was true
  at that record's time rather than at render time."
  [{:keys [buckets holds quote now]}]
  (html/->html
   [:html
    [:head [:meta {:charset "utf-8"}] [:title "cloud-itonami · reservation"]
     [:hiccup/raw (stylesheet)]]
    [:body
     [:header.bar [:h1 "Reservation — Operator Console"] [:span.badge "read-only · governor-gated"]]
     [:main
      (when (seq buckets)
        [:section.card [:h2 "Inventory"]
         [:table [:thead [:tr [:th "Resource"] [:th "Date"] [:th "Class"] [:th "Capacity"]
                          [:th "Sold"] [:th "Held"] [:th "Overbook"] [:th "Available"]]]
          [:tbody (bucket-rows buckets)]]])
      (when (seq holds)
        [:section.card [:h2 "Holds"]
         [:table [:thead [:tr [:th "ID"] [:th "Resource"] [:th "Date"] [:th "Class"]
                          [:th "Qty"] [:th "Expires"] [:th "Status"]]]
          [:tbody (hold-rows holds now)]]])
      (when (seq (:quote/lines quote))
        [:section.card [:h2 (str "Quote — " (:quote/total quote) " " (:quote/currency quote))]
         [:table [:thead [:tr [:th "Kind"] [:th "Date / label"] [:th "Qty"] [:th "Modifier"] [:th "Amount"]]]
          [:tbody (quote-rows quote)]]])]]]))
