(ns murakumo.cd.controller
  "Controller composition for stage → canary deploy → health → promotion."
  (:require [murakumo.artifact-replication :as replication]
            [murakumo.cd.executor :as executor]
            [murakumo.cd.fleet-adapter :as fleet-adapter]))

(defn execute-release!
  [{:keys [node-lookup session issued-capability source-get request-fn
           timeout-ms chunk-bytes verification-policy clock-fn rollout-plan]}]
  (let [stage! (replication/create-overlay-replicator
                (cond-> {:node-lookup node-lookup :session session
                         :issued-capability issued-capability :source-get source-get
                         :request-fn request-fn}
                  timeout-ms (assoc :timeout-ms timeout-ms)
                  chunk-bytes (assoc :chunk-bytes chunk-bytes)))
        fleet-operations (fleet-adapter/create-overlay-adapter
                          (cond-> {:node-lookup node-lookup :session session
                                   :issued-capability issued-capability
                                   :request-fn request-fn :stage-fn stage!}
                            timeout-ms (assoc :timeout-ms timeout-ms)))]
    (executor/execute!
     (merge fleet-operations
            {:issued-capability issued-capability
             :verification-policy verification-policy
             :clock-fn clock-fn :rollout-plan rollout-plan}))))
