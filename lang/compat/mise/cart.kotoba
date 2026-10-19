(ns mise.cart
  "Shopping cart (pure). A Cart is {:lines [{:sku :product-id :name :qty
  :unit-price Price}]}. All operations are pure functions returning a new Cart;
  the host (re-frame app-db) owns state. Invariants maintained: qty is a pos-int,
  lines are deduped by :sku (adding an existing sku merges qty)."
  (:refer-clojure :exclude [empty?])
  (:require [mise.pricing :as pricing]))

(defrecord Cart [lines])

(declare remove-line)

(defn cart [] (->Cart []))

(defn- normalize-qty [q]
  (let [n (long (or q 1))]
    (if (pos? n) n 1)))

(defn- merge-line [existing line]
  (-> existing
      (update :qty + (:qty line))
      (assoc :unit-price (:unit-price line (:unit-price existing)))))

(defn- index-of-sku [lines sku]
  (loop [i 0 [l & rest] lines]
    (cond
      (nil? l) nil
      (= (:sku l) sku) i
      :else (recur (inc i) rest))))

(defn add-line
  "Add a line to the cart. If the :sku already exists, qty is merged. Returns a
  new Cart. line must carry :sku and :qty (default 1); :unit-price optional."
  ([cart line]
   (let [qty (normalize-qty (:qty line))
         line (assoc line :qty qty)
         sku (:sku line)
         lines (:lines cart)]
     (if-let [idx (index-of-sku lines sku)]
       (->Cart (assoc lines idx (merge-line (get lines idx) line)))
       (->Cart (conj (vec lines) line))))))

(defn update-qty
  "Set the qty for a sku. qty <= 0 removes the line. Returns a new Cart."
  [cart sku qty]
  (if (<= qty 0)
    (remove-line cart sku)
    (let [lines (:lines cart)]
      (if-let [idx (index-of-sku lines sku)]
        (->Cart (assoc lines idx (assoc (get lines idx) :qty (long qty))))
        cart))))

(defn remove-line
  "Remove the line for a sku. Returns a new Cart."
  [cart sku]
  (->Cart (filterv #(not= (:sku %) sku) (:lines cart))))

(defn clear [cart] (cart))

(defn line-count [cart] (count (:lines cart)))

(defn item-count
  "Total quantity across all lines."
  [cart] (reduce + 0 (map :qty (:lines cart))))

(defn totals
  "Delegate to mise.pricing/totals for the cart's lines."
  ([cart]
   (totals cart nil))
  ([cart opts]
   (pricing/totals (:lines cart) opts)))

(defn empty? [cart] (zero? (line-count cart)))
