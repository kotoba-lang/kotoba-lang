(ns kotoba.okaimono
  "Shopping orders (お買い物), line items and the order lifecycle — pure
  data contracts.

  A kotoba-lang capability library for the cloud-itonami-5320 (community
  last-mile courier) open business. No network, no I/O. Models the records
  a merchant-facing courier operator keeps: order records with line items
  and totals, an explicit status-transition table, and the single
  `dispatchable?` question the courier governor asks before a delivery
  may be dispatched.

  Portable (.cljc) across JVM / ClojureScript / SCI / GraalVM."
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Line item
;; ---------------------------------------------------------------------------

(defn line
  "Construct an order line. Returns nil when the sku is blank, qty is not
  a positive integer, or unit-price is negative/not a number."
  [sku name qty unit-price]
  (when (and (string? sku) (not (str/blank? sku))
             (integer? qty) (pos? qty)
             (number? unit-price) (not (neg? unit-price)))
    {:line/sku        sku
     :line/name       name
     :line/qty        qty
     :line/unit-price unit-price}))

(defn line-total [l] (* (:line/qty l) (:line/unit-price l)))

;; ---------------------------------------------------------------------------
;; Order lifecycle
;; ---------------------------------------------------------------------------

(def statuses #{:placed :confirmed :packed :handed-over :delivered :cancelled})

(def transitions
  "Allowed status transitions. Terminal states (:delivered/:cancelled)
  have no exits; a handed-over order can no longer be cancelled — the
  parcel is with the courier."
  {:placed      #{:confirmed :cancelled}
   :confirmed   #{:packed :cancelled}
   :packed      #{:handed-over :cancelled}
   :handed-over #{:delivered}
   :delivered   #{}
   :cancelled   #{}})

(defn order
  "Construct an order record placed at a store. Returns nil when the
  status is unknown or any line is nil/empty."
  [id store-id customer lines & {:keys [currency status cod?]}]
  (let [st (or status :placed)]
    (when (and (contains? statuses st)
               (seq lines)
               (every? map? lines))
      {:okaimono/id       id
       :okaimono/store    store-id
       :okaimono/customer customer
       :okaimono/lines    (vec lines)
       :okaimono/currency (or currency "JPY")
       :okaimono/status   st
       :okaimono/cod?     (boolean cod?)})))

(defn total
  "Order total: sum of qty × unit-price over all lines."
  [o]
  (reduce + 0 (map line-total (:okaimono/lines o))))

(defn item-count [o] (reduce + 0 (map :line/qty (:okaimono/lines o))))

(defn advance
  "Advance the order to status `to` when the transition table allows it;
  nil otherwise (an illegal transition is a refusal, not an exception)."
  [o to]
  (when (contains? (get transitions (:okaimono/status o) #{}) to)
    (assoc o :okaimono/status to)))

(defn dispatchable?
  "Can a courier delivery be dispatched for this order? — the single
  question the courier governor asks: only a :packed order (picked and
  packed by the store, not yet handed over, not cancelled) may be
  dispatched."
  [o]
  (= :packed (:okaimono/status o)))

(defn delivered? [o] (= :delivered (:okaimono/status o)))
(defn cancelled? [o] (= :cancelled (:okaimono/status o)))

;; ---------------------------------------------------------------------------
;; Validation
;; ---------------------------------------------------------------------------

(defn validate-order
  "Return a validation result for a candidate order record."
  [o]
  (cond
    (not (map? o))                              {:okaimono/valid? false :okaimono/error :not-a-map}
    (str/blank? (str (:okaimono/id o)))         {:okaimono/valid? false :okaimono/error :missing-id}
    (str/blank? (str (:okaimono/store o)))      {:okaimono/valid? false :okaimono/error :missing-store}
    (empty? (:okaimono/lines o))                {:okaimono/valid? false :okaimono/error :no-lines}
    (not (contains? statuses (:okaimono/status o)))
    {:okaimono/valid? false :okaimono/error :unknown-status}
    :else {:okaimono/valid? true}))
