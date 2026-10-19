(ns kotobase-peer.transactor
  "Pure batching boundary for reducing hot-head CAS amplification.")

(defn- valid-request? [{:keys [request-id head-key tx-data]}]
  (and (some? request-id)
       (string? head-key) (seq head-key)
       (sequential? tx-data) (seq tx-data)))

(defn- append-request [batches request max-requests max-datoms]
  (let [datom-count (count (:tx-data request))
        current (peek batches)
        fits? (and current
                   (< (:request-count current) max-requests)
                   (<= (+ (:datom-count current) datom-count) max-datoms))]
    (if fits?
      (conj (pop batches)
            (-> current
                (update :request-ids conj (:request-id request))
                (update :request-count inc)
                (update :datom-count + datom-count)
                (update :tx-data into (:tx-data request))))
      (conj batches
            {:head-key (:head-key request)
             :request-ids [(:request-id request)]
             :request-count 1
             :datom-count datom-count
             :tx-data (vec (:tx-data request))}))))

(defn plan-head-batches
  "Coalesce ordered logical requests per mutable head. Each returned batch is
  one atomic commit/HeadCAS domain. Heads are sorted for deterministic host
  scheduling; requests within a head retain caller order. A request larger than
  MAX-DATOMS remains one batch rather than being split across transactions."
  ([requests] (plan-head-batches requests {}))
  ([requests {:keys [max-requests max-datoms]
              :or {max-requests 32 max-datoms 4096}}]
   (when-not (and (pos-int? max-requests) (pos-int? max-datoms))
     (throw (ex-info "Transactor batch bounds must be positive"
                     {:max-requests max-requests :max-datoms max-datoms})))
   (let [requests (vec requests)]
     (when-let [invalid (first (remove valid-request? requests))]
       (throw (ex-info "Malformed transactor request" {:request invalid})))
     (when-not (= (count requests) (count (set (map :request-id requests))))
       (throw (ex-info "Transactor request ids must be unique"
                       {:request-ids (mapv :request-id requests)})))
     (->> requests
          (group-by :head-key)
          (sort-by key)
          (mapcat (fn [[_ head-requests]]
                    (reduce #(append-request %1 %2 max-requests max-datoms)
                            [] head-requests)))
          vec))))

(defn validate-batch
  "Validate a planned batch before any immutable block or head is written."
  [batch]
  (when-not (and (:head-key batch) (seq (:request-ids batch))
                 (= (:request-count batch) (count (:request-ids batch)))
                 (= (:datom-count batch) (count (:tx-data batch))))
    (throw (ex-info "Malformed planned transactor batch" {:batch batch})))
  batch)

(defn batch-receipt
  "Attach logical-request acknowledgement metadata to one successful commit
  report without changing the canonical transaction or chain format."
  [batch commit-report]
  (validate-batch batch)
  (assoc commit-report
         :head-key (:head-key batch)
         :request-ids (:request-ids batch)
         :request-count (:request-count batch)
         :datom-count (:datom-count batch)
         :cas-publications 1))

(defn execution-waves
  "Schedule at most one batch per head in each parallel wave. This preserves
  per-head order without globally serializing independent actor/tenant heads."
  [batches]
  (let [by-head (->> batches (group-by :head-key) (sort-by key) (mapv val))
        wave-count (reduce max 0 (map count by-head))]
    (mapv (fn [offset]
            (->> by-head (keep #(nth % offset nil)) vec))
          (range wave-count))))
