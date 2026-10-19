(ns kotoba.swift.ui
  "Operator-facing console for an interbank-messaging actor.

  Renders an HTML read-only panel of BIC validation, real-wire-format SWIFT
  MT messages (the actual `{1:...}...{5:...}` wire string, not the old EDN
  shape), and real ISO 20022 XML documents, using kotoba-lang/html + css.
  Pure data → markup: no network. The governor gates dispatch; this view
  only observes."
  (:require [clojure.string :as str]
            [html.core :as html]
            [css.core :as css]
            [kotoba.swift :as swift]
            [kotoba.swift.iso20022 :as iso]))

;; Domain-specific rules layered on top of the shared operator-theme (css.core).
(def ^:private extra-rules
  {".wire" {:font-family "ui-monospace, monospace"
            :font-size   "0.85em"
            :white-space "pre-wrap"
            :word-break  "break-all"}})

(def ^:private sheet (css/merge-theme extra-rules))

(defn- stylesheet [] (html/->html (css/style-node sheet)))

(defn- bic-rows [bics]
  (for [b bics]
    (let [r (swift/validate-bic b)]
      [:tr [:td (if (:swift/valid? r) [:span.ok "✓"] [:span.err "✕"])]
           [:td (str b)]
           [:td (or (:swift/country (:swift/parsed r)) "—")]
           [:td (or (:swift/bank (:swift/parsed r)) "—")]
           [:td (or (:swift/branch (:swift/parsed r)) "—")]])))

(defn- mt-status [r]
  ;; validate-mt returns a singular :swift/error for structural failures
  ;; (unsupported-mt-type, missing-block-1/4, not-a-map) and a plural
  ;; :swift/errors vector for field-format failures -- render both.
  (cond
    (:swift/valid? r) [:span.ok "valid"]
    (:swift/error r)  [:span.err (name (:swift/error r))]
    :else             [:span.err (str/join "," (map name (:swift/errors r)))]))

(defn- mt-rows [messages]
  (for [m messages]
    (let [r (swift/validate-mt m)]
      [:tr [:td (or (get-in m [:swift/block-2 :swift/message-type]) "—")]
           [:td (mt-status r)]
           [:td (or (get-in m [:swift/block-1 :swift/lt-address]) "—")]
           [:td (or (get-in m [:swift/block-2 :swift/destination-address]) "—")]
           [:td (or (swift/mt-field (:swift/block-4 m) "20") "—")]
           [:td [:pre.wire (swift/mt->wire m)]]])))

(defn- iso20022-rows [docs]
  (for [d docs]
    (let [r (iso/validate-iso20022 d)
          grp-hdr (or (some-> d (iso/xml-find :CstmrCdtTrfInitn) (iso/xml-find :GrpHdr))
                      (some-> d (iso/xml-find :FIToFICstmrCdtTrf) (iso/xml-find :GrpHdr)))
          msg-id (some-> grp-hdr (iso/xml-find :MsgId) iso/xml-text)]
      [:tr [:td (or (:swift/message-type r) "—")]
           [:td (if (:swift/valid? r) [:span.ok "valid"] [:span.err (name (:swift/error r :invalid))])]
           [:td (or msg-id "—")]
           [:td [:pre.wire (iso/xml->str d)]]])))

(defn dashboard
  "Render a full HTML console for a SWIFT / ISO 20022 operator.
  :messages is a seq of MT message records (mt103 / mt202 / parse-mt-wire);
  :iso20022-docs is a seq of ISO 20022 Hiccup documents (pain001-doc /
  pacs008-doc / parse-xml)."
  [{:keys [bics messages iso20022-docs]}]
  (html/->html
    [:html
     [:head [:meta {:charset "utf-8"}] [:title "cloud-itonami · swift"]
      [:hiccup/raw (stylesheet)]]
     [:body
      [:header.bar [:h1 "Interbank Messaging — Operator Console"] [:span.badge "read-only · governor-gated"]]
      [:main
       (when (seq bics)
         [:section.card [:h2 "BIC validation"]
          [:table [:thead [:tr [:th ""] [:th "BIC"] [:th "Country"] [:th "Bank"] [:th "Branch"]]]
           [:tbody (bic-rows bics)]]])
       (when (seq messages)
         [:section.card [:h2 "SWIFT MT messages (real wire format)"]
          [:table [:thead [:tr [:th "MT"] [:th "Status"] [:th "Sender"] [:th "Receiver"] [:th "Ref (:20:)"] [:th "Wire"]]]
           [:tbody (mt-rows messages)]]])
       (when (seq iso20022-docs)
         [:section.card [:h2 "ISO 20022 documents (real XML)"]
          [:table [:thead [:tr [:th "Message type"] [:th "Status"] [:th "MsgId"] [:th "XML"]]]
           [:tbody (iso20022-rows iso20022-docs)]]])]]]))
