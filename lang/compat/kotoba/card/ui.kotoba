(ns kotoba.card.ui
  "Operator-facing console for a card-payment-processing actor.

  Renders an HTML read-only panel of PAN validation, ISO 8583 messages and
  authorization decisions, using kotoba-lang/html + css. Pure data → markup:
  no network. The governor gates settlement; this view only observes. PANs
  are masked — only the last 4 digits are shown."
  (:require [html.core :as html]
            [css.core :as css]
            [kotoba.card :as card]))

;; Domain-specific rules layered on top of the shared operator-theme (css.core).
(def ^:private extra-rules
  {})

(def ^:private sheet (css/merge-theme extra-rules))

(defn- stylesheet [] (html/->html (css/style-node sheet)))

(defn- mask-pan [pan]
  (let [d (or (some-> pan str) "")
        n (count d)]
    (if (>= n 4) (str "•••• " (subs d (- n 4))) d)))

(defn- pan-rows [pans]
  (for [p pans]
    (let [r (card/validate-pan p)
          parsed (:card/parsed r)]
      [:tr [:td (if (:card/valid? r) [:span.ok "✓"] [:span.err "✕"])]
           [:td (mask-pan p)]
           [:td (or (:card/network parsed) "—")]
           [:td (name (or (:card/error r) :ok))]])))

(defn- mt-rows [messages]
  (for [m messages]
    (let [r (card/validate-message m)]
      [:tr [:td (or (:card/mti m) "—")]
           [:td (if (:card/valid? r) [:span.ok "valid"] [:span.err (name (:card/error r "—"))])]
           [:td (or (:card/class m) "—")]
           [:td (count (keys (:card/de m)))]
           [:td (count (:card/bitmap m))]])))

(defn- auth-badge [a]
  (cond
    (card/approved? a) [:span.ok "approved"]
    (= :refer (:card/action a)) [:span.warn "refer"]
    :else [:span.err "declined"]))

(defn- auth-rows [auths]
  (for [a auths]
    [:tr [:td (mask-pan (:card/pan a))]
     [:td.amt (:card/amount a)]
     [:td (auth-badge a)]
     [:td.amt (card/authorized-amount a)]
     [:td (or (:card/rrn a) [:span.muted "—"])]
     [:td (or (:card/reason a) [:span.muted "—"])]]))

(defn dashboard
  "Render a full HTML console for a card-processing operator."
  [{:keys [pans messages authorizations] :as ctx}]
  (html/->html
    [:html
     [:head [:meta {:charset "utf-8"}] [:title "cloud-itonami · card"]
      [:hiccup/raw (stylesheet)]]
     [:body
      [:header.bar [:h1 "Card Processing — Operator Console"] [:span.badge "read-only · PANs masked · governor-gated"]]
      [:main
       (when (seq pans)
         [:section.card [:h2 "PAN validation"]
          [:table [:thead [:tr [:th ""] [:th "PAN"] [:th "Network"] [:th "Status"]]]
           [:tbody (pan-rows pans)]]])
       (when (seq messages)
         [:section.card [:h2 "ISO 8583 messages"]
          [:table [:thead [:tr [:th "MTI"] [:th "Status"] [:th "Class"] [:th "Data elements"] [:th "Bitmap"]]]
           [:tbody (mt-rows messages)]]])
       (when (seq authorizations)
         [:section.card [:h2 "Authorization decisions"]
          [:table [:thead [:tr [:th "PAN"] [:th.amt "Requested"] [:th "Decision"] [:th.amt "Approved"] [:th "RRN"] [:th "Reason"]]]
           [:tbody (auth-rows authorizations)]]])]]]))
