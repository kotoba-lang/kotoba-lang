(ns murakumo.cd.protocol
  "Versioned wire messages for capability-gated fleet deployment RPCs.")

(def version 1)
(def actions #{:deploy :health :rollback})

(defn request
  [action node issued-capability operation-artifact-cid environment verdict-cid revision]
  {:murakumo.cd/version version
   :murakumo.cd/type :request
   :murakumo.cd/action action
   :murakumo.cd/node node
   :murakumo.cd/issued-capability issued-capability
   :murakumo.cd/artifact-cid operation-artifact-cid
   :murakumo.cd/environment environment
   :murakumo.cd/verdict-cid verdict-cid
   :murakumo.cd/revision revision})

(defn valid-request? [m]
  (and (map? m)
       (= version (:murakumo.cd/version m))
       (= :request (:murakumo.cd/type m))
       (contains? actions (:murakumo.cd/action m))
       (string? (:murakumo.cd/node m))
       (map? (:murakumo.cd/issued-capability m))
       (string? (:murakumo.cd/artifact-cid m))
       (string? (:murakumo.cd/environment m))
       (string? (:murakumo.cd/verdict-cid m))
       (string? (:murakumo.cd/revision m))))

(defn response [action node result]
  {:murakumo.cd/version version
   :murakumo.cd/type :response
   :murakumo.cd/action action
   :murakumo.cd/node node
   :murakumo.cd/result result})

(defn valid-response? [m action node]
  (and (map? m)
       (= version (:murakumo.cd/version m))
       (= :response (:murakumo.cd/type m))
       (= action (:murakumo.cd/action m))
       (= node (:murakumo.cd/node m))
       (map? (:murakumo.cd/result m))
       (boolean? (get-in m [:murakumo.cd/result :ok?]))))
