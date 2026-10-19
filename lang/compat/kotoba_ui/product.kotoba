(ns kotoba-ui.product
  "Semantic product-surface primitives for dashboards and consoles."
  (:require [clojure.string :as str]))

(defn- classes [base extra]
  (str base (when (seq extra) (str " " extra))))

(defn metric
  "Compact labelled value. opts: :label, :value, :detail, :status, :id, :class."
  [{:keys [label value detail status id class]}]
  [:div (cond-> {:class (classes "kotoba-product__metric" class)} id (assoc :id id))
   [:div {:class "kotoba-product__metric-label hig-footnote"} label]
   [:div {:class "kotoba-product__metric-value hig-title2"} value]
   (when (or detail status)
     [:div {:class "kotoba-product__metric-detail hig-footnote"}
      (when status [:span {:class "kotoba-product__status"} status])
      detail])])

(defn empty-state
  "Purposeful empty state. opts: :title, :body, :actions, :id, :class."
  [{:keys [title body actions id class]}]
  [:div (cond-> {:class (classes "kotoba-product__empty" class)} id (assoc :id id))
   [:h3 {:class "hig-headline"} title]
   (when body [:p {:class "hig-footnote kotoba-product__muted"} body])
   (when (seq actions)
     (into [:div {:class "kotoba-product__empty-actions"}] actions))])

(defn data-table
  "Accessible responsive table. Columns are {:key k :label s}; rows are maps.
  Cell values may be hiccup. opts: :caption, :columns, :rows, :empty, :id, :class."
  [{:keys [caption columns rows empty id class]}]
  (if (seq rows)
    [:div {:class "kotoba-product__table-scroll"}
     [:table (cond-> {:class (classes "kotoba-product__table" class)} id (assoc :id id))
      (when caption [:caption caption])
      [:thead [:tr (for [{:keys [key label]} columns]
                    [:th {:scope "col" :key (str key)} label])]]
      [:tbody (for [[idx row] (map-indexed vector rows)]
                [:tr {:key idx}
                 (for [{:keys [key]} columns]
                   [:td {:key (str key)} (get row key)])])]]]
    (or empty (empty-state {:title "Nothing here yet"}))))

(def product-css
  (str
   "@layer kotoba.hig{"
   ".kotoba-product__metric{min-width:0;padding:var(--hig-spacing-3);border:var(--hig-hairline) solid var(--hig-color-separator);border-radius:var(--hig-radius-large);background:var(--hig-color-secondary-system-background)}"
   ".kotoba-product__metric-label,.kotoba-product__metric-detail,.kotoba-product__muted{color:var(--hig-color-secondary-label)}"
   ".kotoba-product__metric-value{margin:var(--hig-spacing-1) 0;overflow-wrap:anywhere}"
   ".kotoba-product__status{display:inline-block;margin-right:var(--hig-spacing-2);color:var(--hig-color-tint);font-weight:600}"
   ".kotoba-product__empty{text-align:center;padding:var(--hig-spacing-6);border:var(--hig-hairline) dashed var(--hig-color-separator);border-radius:var(--hig-radius-large)}"
   ".kotoba-product__empty-actions{display:flex;justify-content:center;flex-wrap:wrap;gap:var(--hig-spacing-2);margin-top:var(--hig-spacing-3)}"
   ".kotoba-product__table-scroll{overflow-x:auto}"
   ".kotoba-product__table{width:100%;border-collapse:collapse}"
   ".kotoba-product__table caption{text-align:left;margin-bottom:var(--hig-spacing-2);font-weight:600}"
   ".kotoba-product__table th,.kotoba-product__table td{text-align:left;vertical-align:top;padding:var(--hig-spacing-2) var(--hig-spacing-3);border-bottom:var(--hig-hairline) solid var(--hig-color-separator)}"
   ".kotoba-product__table th{color:var(--hig-color-secondary-label);font-weight:600}"
   "}"))
