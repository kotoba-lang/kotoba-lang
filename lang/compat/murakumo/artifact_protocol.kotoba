(ns murakumo.artifact-protocol
  "Chunked, capability-bound CAS replication messages over overlay RPC.")

(def version 1)
(def operations #{:begin :chunk :commit :abort})
(def release-actions #{:deploy :rollback})

(defn request [operation fields]
  (merge {:murakumo.artifact/version version
          :murakumo.artifact/type :request
          :murakumo.artifact/operation operation}
         fields))

(defn valid-request? [m]
  (and (map? m)
       (= version (:murakumo.artifact/version m))
       (= :request (:murakumo.artifact/type m))
       (contains? operations (:murakumo.artifact/operation m))
       (contains? release-actions (:murakumo.artifact/release-action m))
       (string? (:murakumo.artifact/node m))
       (map? (:murakumo.artifact/issued-capability m))
       (string? (:murakumo.artifact/bundle-cid m))
       (string? (:murakumo.artifact/revision m))
       (string? (:murakumo.artifact/environment m))
       (string? (:murakumo.artifact/verdict-cid m))
       (string? (:murakumo.artifact/transfer-id m))
       (string? (:murakumo.artifact/object-cid m))))

(defn response [operation node result]
  {:murakumo.artifact/version version
   :murakumo.artifact/type :response
   :murakumo.artifact/operation operation
   :murakumo.artifact/node node
   :murakumo.artifact/result result})

(defn valid-response? [m operation node]
  (and (map? m)
       (= version (:murakumo.artifact/version m))
       (= :response (:murakumo.artifact/type m))
       (= operation (:murakumo.artifact/operation m))
       (= node (:murakumo.artifact/node m))
       (boolean? (get-in m [:murakumo.artifact/result :ok?]))))
