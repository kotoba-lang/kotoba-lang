(ns num.expert-cache
  "Portable byte-budgeted cache policy for expert-streamed MoE execution.

  The cache stores opaque `(layer, expert, projection)` keys.  Storage and I/O
  remain host concerns; this namespace decides hits, misses and LRU evictions
  without ever confusing a reserved address range with resident bytes.")

(defn new-cache
  "Create an empty cache state with an exact resident-byte ceiling."
  [budget-bytes]
  (when (neg? budget-bytes)
    (throw (ex-info "expert cache budget must be non-negative"
                    {:budget-bytes budget-bytes})))
  {:budget-bytes (long budget-bytes)
   :resident-bytes 0
   :clock 0
   :entries {}
   :metrics {:requests 0 :hits 0 :misses 0 :evictions 0
             :bytes-requested 0 :bytes-loaded 0 :bytes-evicted 0}})

(defn- oldest-key [entries]
  (->> entries
       (sort-by (fn [[key {:keys [last-used]}]] [last-used (pr-str key)]))
       ffirst))

(defn- evict-until [state required-bytes]
  (loop [s state, evicted []]
    (if (or (empty? (:entries s))
            (<= (+ (:resident-bytes s) required-bytes) (:budget-bytes s)))
      [s evicted]
      (let [key (oldest-key (:entries s))
            bytes (get-in s [:entries key :bytes])]
        (recur (-> s
                   (update :resident-bytes - bytes)
                   (update :entries dissoc key)
                   (update-in [:metrics :evictions] inc)
                   (update-in [:metrics :bytes-evicted] + bytes))
               (conj evicted key))))))

(defn access
  "Access one opaque expert slice.

  Returns `[new-state decision]`. A slice larger than the entire cache is a
  `:bypass`: the host must read and use it, but it is not inserted."
  [state key byte-count]
  (when-not (pos? byte-count)
    (throw (ex-info "expert slice byte count must be positive"
                    {:key key :byte-count byte-count})))
  (let [bytes (long byte-count)
        tick (inc (:clock state))
        base (-> state
                 (assoc :clock tick)
                 (update-in [:metrics :requests] inc)
                 (update-in [:metrics :bytes-requested] + bytes))]
    (if-let [entry (get-in base [:entries key])]
      [(-> base
           (assoc-in [:entries key :last-used] tick)
           (update-in [:metrics :hits] inc))
       {:status :hit :key key :bytes (:bytes entry) :evicted []}]
      (let [base (update-in base [:metrics :misses] inc)]
        (if (> bytes (:budget-bytes base))
          [(update-in base [:metrics :bytes-loaded] + bytes)
           {:status :bypass :key key :bytes bytes :evicted []}]
          (let [[room evicted] (evict-until base bytes)]
            [(-> room
                 (assoc-in [:entries key] {:bytes bytes :last-used tick})
                 (update :resident-bytes + bytes)
                 (update-in [:metrics :bytes-loaded] + bytes))
             {:status :miss :key key :bytes bytes :evicted evicted}]))))))

(defn access-many
  "Access a routing set in order, de-duplicating identical expert slices."
  [state requests]
  (reduce (fn [[s decisions seen] {:keys [key bytes]}]
            (if (contains? seen key)
              [s decisions seen]
              (let [[next-state decision] (access s key bytes)]
                [next-state (conj decisions decision) (conj seen key)])))
          [state [] #{}]
          requests))

(defn selected-expert-ids
  "Read one router row while respecting its physical row stride.

  `values` may contain padding or a wider argsort row. `row-stride` is in
  elements, not bytes; flattening `row * top-k` is incorrect for such views."
  [values row row-stride top-k]
  (when-not (and (<= 0 row) (pos? row-stride) (<= 0 top-k row-stride)
                 (<= (+ (* row row-stride) top-k) (count values)))
    (throw (ex-info "selected expert view is out of bounds"
                    {:row row :row-stride row-stride :top-k top-k
                     :value-count (count values)})))
  (subvec (vec values) (* row row-stride) (+ (* row row-stride) top-k)))

(defn hit-rate
  "Cache hit ratio, or nil before the first request."
  [state]
  (let [{:keys [requests hits]} (:metrics state)]
    (when (pos? requests) (/ (double hits) requests))))
