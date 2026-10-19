(ns murakumo.ci.protocol
  "Versioned CI messages carried as Murakumo overlay stream payloads.")

(def version 1)
(def message-types
  #{:ci/lease-request :ci/lease-offer :ci/run-started
    :ci/heartbeat :ci/heartbeat-ack :ci/run-event :ci/run-completed
    :ci/artifact-begin :ci/artifact-chunk :ci/artifact-commit :ci/artifact-abort
    :ci/artifact-response
    :ci/ack :ci/error :ci/no-work :ci/run-status})

(defn message [type body]
  (when-not (contains? message-types type)
    (throw (ex-info "murakumo-ci: unknown protocol message"
                    {:reason :unknown-message-type :type type})))
  {:murakumo.ci/version version :murakumo.ci/type type
   :murakumo.ci/body body})

(defn valid? [m]
  (and (= version (:murakumo.ci/version m))
       (contains? message-types (:murakumo.ci/type m))
       (map? (:murakumo.ci/body m))))

(defn lease-request [runner]
  (message :ci/lease-request
           (select-keys runner [:runner/id :runner/capabilities
                                :runner/environment-digest])))

(defn lease-offer [lease run]
  (message :ci/lease-offer
           {:lease lease
            :run (select-keys run [:ci.run/id :ci.run/source
                                   :ci.run/logical-id :ci.run/pipeline-digest
                                   :ci.run/requires])}))

(defn completion [lease result receipt-cid]
  (message :ci/run-completed
           {:lease lease :result result :receipt/cid receipt-cid}))

(defn attested-completion [lease result receipt-cid attestation]
  (message :ci/run-completed
           {:lease lease :result result :receipt/cid receipt-cid
            :attestation attestation}))
