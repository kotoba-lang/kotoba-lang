(ns kotoba.ekyc.ui
  "Operator-facing console for an identity-verification actor.

  Renders an HTML read-only panel of verification records, their catalog
  method, and their structural-validation disposition, using kotoba-lang/html
  + css. Pure data -> markup: no network, no DOM, no biometric image
  rendering (evidence refs are opaque strings, never images). The governor
  gates acceptance; this view only observes, so it can never leak a write
  path."
  (:require [clojure.string :as str]
            [html.core :as html]
            [css.core :as css]
            [kotoba.ekyc :as ekyc]))

;; Domain-specific rules layered on top of the shared operator-theme (css.core).
(def ^:private extra-rules
  {})

(def ^:private sheet (css/merge-theme extra-rules))

(defn- stylesheet [] (html/->html (css/style-node sheet)))

(defn- disposition-badge [result]
  (case (:ekyc.result/disposition result)
    :pass       [:span.ok "pass"]
    :incomplete [:span.warn "incomplete"]
    [:span.err "fail"]))

(defn- verification-rows [verifications]
  (for [v verifications]
    (let [r (ekyc/validate v)
          m (ekyc/method (:ekyc.verification/method v))]
      [:tr [:td (:ekyc.verification/id v)]
           [:td (or (:ekyc.method/label m) (str (:ekyc.verification/method v)))]
           [:td (if m (:ekyc.method/citation m) "—")]
           [:td (disposition-badge r)]
           [:td (name (or (:ekyc.result/assurance-level r) :na))]
           [:td (str (count (:ekyc.result/reasons r))
                      (when (seq (:ekyc.result/reasons r))
                        (str " (" (str/join ", " (map name (:ekyc.result/reasons r))) ")")))]])))

(defn- method-rows [subject]
  (for [m (ekyc/methods-for subject)]
    [:tr [:td (name (:ekyc.method/id m))]
         [:td (:ekyc.method/label m)]
         [:td (:ekyc.method/citation m)]
         [:td (name (or (:ekyc.method/ial-approx m) :na))]]))

(defn dashboard
  "Render a full HTML console for an eKYC operator."
  [{:keys [verifications]}]
  (html/->html
    [:html
     [:head [:meta {:charset "utf-8"}] [:title "cloud-itonami · ekyc"]
      [:hiccup/raw (stylesheet)]]
     [:body
      [:header.bar [:h1 "Identity Verification — Operator Console"]
       [:span.badge "read-only · governor-gated"]]
      [:main
       (when (seq verifications)
         [:section.card [:h2 "Verification records"]
          [:table [:thead [:tr [:th "ID"] [:th "Method"] [:th "Citation"] [:th "Disposition"] [:th "IAL"] [:th "Reasons"]]]
           [:tbody (verification-rows verifications)]]])
       [:section.card [:h2 "Recognized methods — individual (自然人)"]
        [:table [:thead [:tr [:th "Id"] [:th "Label"] [:th "Citation"] [:th "IAL (approx.)"]]]
         [:tbody (method-rows :individual)]]]
       [:section.card [:h2 "Recognized methods — corporate (法人)"]
        [:table [:thead [:tr [:th "Id"] [:th "Label"] [:th "Citation"] [:th "IAL (approx.)"]]]
         [:tbody (method-rows :corporate)]]]]]]))
