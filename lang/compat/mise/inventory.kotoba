(ns mise.inventory
  "Stock tracking (pure). A Stock is a map {sku → on-hand qty}. All ops are pure
  fns returning a new Stock map. Reservation is decremental; the host decides
  whether to commit (e.g. at order placement) vs hold (a separate holds map)."
  (:refer-clojure :exclude [available?]))

(defrecord Stock [levels])

(defn stock
  "Build a Stock from a {sku → qty} map (or a seq of [sku qty] pairs)."
  [m]
  (->Stock (into {} m)))

(defn levels [stock] (:levels stock {}))

(defn on-hand
  "On-hand qty for a sku (0 if absent)."
  [stock sku]
  (get (levels stock) sku 0))

(defn available?
  "True if on-hand(qty) >= needed."
  ([stock sku]
   (available? stock sku 1))
  ([stock sku needed]
   (>= (on-hand stock sku) (max 0 needed))))

(defn reserve
  "Decrement on-hand by qty (clamped at 0). Returns a new Stock."
  [stock sku qty]
  (let [cur (on-hand stock sku)
        new-level (max 0 (- cur (max 0 qty)))]
    (update stock :levels assoc sku new-level)))

(defn restock
  "Increment on-hand by qty. Returns a new Stock."
  [stock sku qty]
  (let [cur (on-hand stock sku)]
    (update stock :levels assoc sku (+ cur (max 0 qty)))))

(defn set-level
  "Set on-hand to an absolute qty. Returns a new Stock."
  [stock sku qty]
  (update stock :levels assoc sku (max 0 (long qty))))

(defn reserve-many
  "Reserve a seq of [sku qty] in one pass. Returns a new Stock."
  [stock reservations]
  (reduce (fn [s [sku qty]] (reserve s sku qty)) stock reservations))
