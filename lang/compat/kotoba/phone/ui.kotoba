(ns kotoba.phone.ui
  "Operator-facing console for a telecommunications-access actor.

  Renders an HTML read-only panel of E.164 validation, recent CDRs and SMS
  records, using kotoba-lang/html + css. Pure data → markup: no network.
  The governor gates provisioning/billing; this view only observes."
  (:require [html.core :as html]
            [css.core :as css]
            [kotoba.phone :as phone]))

;; Domain-specific rules layered on top of the shared operator-theme (css.core).
(def ^:private extra-rules
  {})

(def ^:private sheet (css/merge-theme extra-rules))

(defn- stylesheet [] (html/->html (css/style-node sheet)))

(defn- e164-rows [nums]
  (for [n nums]
    (let [r (phone/validate-e164 n)]
      [:tr [:td (if (:phone/valid? r) [:span.ok "✓"] [:span.err "✕"])]
           [:td (str n)]
           [:td (or (:phone/normalized r) "—")]])))

(defn- cdr-rows [cdrs]
  (for [c cdrs]
    [:tr [:td (:phone/cdr-id c)]
     [:td (or (:phone/calling c) "—")]
     [:td (or (:phone/called c) "—")]
     [:td (name (:phone/direction c))]
     [:td.amt (:phone/duration c)]]))

(defn- sms-rows [messages]
  (for [m messages]
    [:tr [:td (:phone/sms-id m)]
     [:td (or (:phone/from m) "—")]
     [:td (or (:phone/to m) "—")]
     [:td (str (:phone/body m))]]))

(defn dashboard
  "Render a full HTML console for a telecom-access operator."
  [{:keys [numbers cdrs sms] :as ctx}]
  (html/->html
    [:html
     [:head [:meta {:charset "utf-8"}] [:title "cloud-itonami · telecom"]
      [:hiccup/raw (stylesheet)]]
     [:body
      [:header.bar [:h1 "Telecommunications Access — Operator Console"] [:span.badge "read-only · governor-gated"]]
      [:main
       (when (seq numbers)
         [:section.card [:h2 "E.164 validation"]
          [:table [:thead [:tr [:th ""] [:th "Number"] [:th "Normalized"]]]
           [:tbody (e164-rows numbers)]]])
       (when (seq cdrs)
         [:section.card [:h2 "Recent calls (CDR)"]
          [:table [:thead [:tr [:th "ID"] [:th "Calling"] [:th "Called"] [:th "Direction"] [:th.amt "Duration (s)"]]]
           [:tbody (cdr-rows cdrs)]]])
       (when (seq sms)
         [:section.card [:h2 "Recent SMS"]
          [:table [:thead [:tr [:th "ID"] [:th "From"] [:th "To"] [:th "Body"]]]
           [:tbody (sms-rows sms)]]])]]]))
