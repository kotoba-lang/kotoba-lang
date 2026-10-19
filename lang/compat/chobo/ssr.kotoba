(ns chobo.ssr
  "SSR parity for chobo: render chobo.views with sample data via
  shitsuke.hiccup/->html. Exercised by the parity test."
  (:require [shitsuke.hiccup :as hic]
            [shitsuke.style :as style]
            [chobo.ledger :as ledger]
            [chobo.subscription :as sub]
            [chobo.invoice :as invoice]
            [chobo.tenant :as tenant]
            [chobo.views :as views]))

(defn sample-db
  ([]
   (sample-db nil))
  ([_]
   (let [plan sub/pro-tier
         usage (-> (sub/map->Usage {:tenant "gftd" :consumed {}})
                   (sub/record-usage :image-gen 210))   ; over quota 200
         inv (-> (invoice/invoice "gftd")
                 (invoice/add-line (invoice/line "Pro subscription" 2900))
                 (invoice/add-line (invoice/line "Image overage (10)" 100))
                 invoice/mark-issued)
         t (tenant/tenant "gftd" {:name "gftd." :plan-id :pro :operator-level :certified
                                  :capabilities #{:billing:read :billing:write}})
         l (-> (ledger/ledger)
               (ledger/append-activity (ledger/activity
                                        {:id "rec_sample"
                                         :lane :billing :kind :metering
                                         :title "meter image-gen" :tenant "gftd"})))]
     {:plan plan :usage usage :invoices [inv] :tenant t :ledger l})))

(defn root-html
  ([]
   (root-html (sample-db)))
  ([db]
   (str "<!doctype html>\n"
        (hic/->html [:html {:lang "ja"}
                     [:head [:meta {:charset "utf-8"}]
                      [:title "chobo — services EC SSR"]
                      [:style [:hiccup/raw (style/root-css)]]]
                     [:body (views/root db)]]))))
