(ns mise.pricing
  "Price + totals math (pure). A Price is {:amount <number> :currency \"JPY\"}.

  All amounts are plain numbers in the smallest currency unit convention is the
  host's choice; mise keeps them as numbers and rounds tax to the nearest whole
  unit via the injected rounding fn (default clojure.core/round). Tax is a
  simple rate (0.08 = 8%). Discounts are an injected IDiscount port (stub).

  Portable .cljc, zero host effects."
  (:require [clojure.string :as str]))

(defrecord Price [amount currency])

(defn price
  "Construct a Price. amount is a number; currency defaults to \"JPY\"."
  ([amount]
   (price amount "JPY"))
  ([amount currency]
   (->Price amount currency)))

(defn price?
  [x] (and (map? x) (number? (:amount x)) (:currency x)))

(defn zero-price
  ([]
   (zero-price "JPY"))
  ([currency]
   (->Price 0 currency)))

(defn add
  "Add two prices (must share currency). Returns a Price."
  [a b]
  (cond
    (nil? a) b
    (nil? b) a
    :else
    (let [cur (:currency a (:currency b "JPY"))]
      (when (not= (:currency a) (:currency b))
        (throw (ex-info "currency mismatch"
                        {:a a :b b})))
      (->Price (+ (:amount a 0) (:amount b 0)) cur))))

(defn multiply
  "Multiply a price by a scalar (e.g. qty). Returns a Price."
  [p n]
  (->Price (* (:amount p 0) (or n 0)) (:currency p "JPY")))

(defn line-total
  "unit-price × qty."
  [unit-price qty]
  (multiply unit-price qty))

;; ---------------------------------------------------------------------------
;; protocol ports (injected adapters)
;; ---------------------------------------------------------------------------

(defprotocol IDiscount
  (apply-discount [this ctx subtotal]
    "Return a Price representing the discount amount subtracted from subtotal.
    ctx is an opaque map the adapter may inspect (cart/checkout). Implementations
    must be pure functions of (ctx, subtotal)."))

(defrecord NoDiscount []
  IDiscount (apply-discount [_ _ _] (zero-price)))

(defn no-discount [] (->NoDiscount))

(defprotocol ITax
  (compute-tax [this ctx subtotal]
    "Return a Price representing the tax on subtotal. ctx may carry :rate or
    shipping address. Pure."))

(defrecord FlatTax [rate]
  ITax (compute-tax [_ _ subtotal]
        (->Price (long (* (:amount subtotal 0) (or rate 0))) (:currency subtotal "JPY"))))

(defn flat-tax
  "Simple flat-rate tax adapter (rate is a fraction, 0.08 = 8%)."
  ([rate] (->FlatTax rate)))

(defprotocol IShipping
  (compute-shipping [this ctx subtotal]
    "Return a Price for shipping given ctx + subtotal. Pure."))

(defrecord FreeShipping []
  IShipping (compute-shipping [_ _ _] (zero-price)))

(defn free-shipping [] (->FreeShipping))

;; ---------------------------------------------------------------------------
;; totals
;; ---------------------------------------------------------------------------

(defn subtotal
  "Sum of line totals. lines is a seq of {:unit-price :qty} (or :price/:qty)."
  [lines]
  (reduce add (zero-price)
          (map (fn [l]
                 (line-total (or (:unit-price l) (:price l)) (or (:qty l) 0)))
               lines)))

(defn totals
  "Compute {:subtotal :discount :tax :shipping :total} for a cart's lines.
  Adapters: :discount IDiscount, :tax ITax, :shipping IShipping, :ctx opaque."
  [lines {:keys [discount tax shipping ctx] :or {discount (no-discount)
                                                 tax (flat-tax 0.0)
                                                 shipping (free-shipping)}}]
  (let [sub (subtotal lines)
        disc (apply-discount discount (or ctx {}) sub)
        after-disc (add sub (multiply disc -1))
        tax-amt (compute-tax tax (or ctx {}) after-disc)
        ship-amt (compute-shipping shipping (or ctx {}) after-disc)
        total (add (add after-disc tax-amt) ship-amt)]
    {:subtotal sub
     :discount disc
     :tax tax-amt
     :shipping ship-amt
     :total total}))

(defn- group-digits
  "Insert thousands separators into the decimal string of a non-negative number.
  Portable (no java.text). 12800 -> \"12,800\"."
  [n]
  (let [s (str (long (max 0 (or n 0))))]
    (->> (reverse s)
         (partition 3 3 nil)
         (map (comp #(apply str %) reverse)) ; un-reverse each group's chars
         reverse                              ; restore group order
         (str/join ","))))

(defn- currency-prefix [currency]
  (get {:JPY "¥" :USD "$" :EUR "€"} (keyword currency)
       (str currency " ")))

(defn format-price
  "Render a Price as a human string, e.g. \"¥12,800\". Currency prefix map is
  extensible; default JPY uses ¥ and no decimals. Portable (no java.text)."
  ([p]
   (format-price p nil))
  ([{:keys [amount currency] :or {currency "JPY" amount 0}} opts]
   (str (currency-prefix currency) (group-digits (or amount 0)))))
