(ns mise.ssr
  "SSR parity: render mise.views with sample data via shitsuke.hiccup/->html.

  Proves the dual-render contract — the SAME views reagent mounts in the browser
  also render to HTML on the JVM. The live site (ai-gftd-fashion) is client-only
  (loads catalog from the worker); this namespace is exercised by the parity
  test and as a build-time pre-render option."
  (:require [shitsuke.hiccup :as hic]
            [shitsuke.style :as style]
            [shitsuke.components :as sc]
            [mise.catalog :as catalog]
            [mise.cart :as cart]
            [mise.checkout :as checkout]
            [mise.pricing :as pricing]
            [mise.views :as views]
            [mise.order :as order]))

(defn sample-catalog
  "A minimal gftd. brand catalog for parity rendering (real seed lives in
  ai-gftd-fashion)."
  []
  (catalog/catalog
   [{:id "parker-hatra"
     :brand "gftd."
     :name "GIFTED × HATRA パーカ"
     :category "outerwear"
     :description "感覚過敏の人に向けた第1弾コラボパーカ。"
     :images []
     :variants [{:id "parker-hatra-m"
                 :product-id "parker-hatra"
                 :name "M"
                 :options {:size "M"}
                 :price (pricing/price 38000 "JPY")}]}]
   {:outerwear "Outerwear"}))

(defn sample-db
  "A representative app-db for parity rendering."
  ([]
   (sample-db :contact))
  ([view]
   (let [cat (sample-catalog)
         prod (first (:products cat))
         sku (catalog/default-sku prod)
         c (-> (cart/cart)
               (cart/add-line {:sku (:id sku)
                               :product-id (:id prod)
                               :name (:name prod)
                               :qty 1
                               :unit-price (:price sku)}))
         co (checkout/checkout c)]
     {:catalog cat
      :cart c
      :checkout (assoc co :stage view)
      :order nil})))

(defn root
  "Render a full store page to hiccup data. db is the app-db map."
  [db]
  (let [products (:products (:catalog db) [])
        c (:cart db)
        co (:checkout db)
        totals (cart/totals c)]
    [:div {:class (views/class-name :store)}
     [:header (sc/toolbar nil)]
     [:main
      [:section {:class (views/class-name :product-list)}
       (for [p products] (views/product-card p {:act-add :cart/add}))]
      (when (and co (not= (:stage co) :confirmed))
        (views/checkout-form co {:act-submit :checkout/next :act-back :checkout/prev}))
      (when (seq (:lines c))
        [:section {:class (views/class-name :cart-section)}
         (for [line (:lines c)]
           (views/cart-line line {:act-inc :cart/inc :act-dec :cart/dec :act-remove :cart/remove}))
         (views/cart-summary totals)])]
     (when-let [ord (:order db)]
       (views/order-confirmation ord))]))

(defn root-html
  "Render the full store page to an HTML string (SSR). Includes the shitsuke
  :root token vars as an inline <style> so the static HTML is self-contained."
  ([]
   (root-html (sample-db)))
  ([db]
   (str "<!doctype html>\n"
        (hic/->html [:html {:lang "ja"}
                     [:head
                      [:meta {:charset "utf-8"}]
                      [:title "gftd. — mise SSR"]]
                     [:style [:hiccup/raw (style/root-css)]]
                     [:body (root db)]]))))
