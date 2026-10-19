(ns murakumo.cd.release
  "Policy bridge from threshold CI verdict to external status and CD authority."
  (:require [murakumo.cd.capability :as capability]
            [murakumo.ci.attest :as attest]))

(defn finalize-ci
  "Resolve the canonical verdict CID and its document. Without quorum the
   result stays pending; an unknown CID is a hard integrity error."
  [policy sigrefs verdicts-by-cid]
  (if-let [cid (attest/canonical-verdict policy sigrefs)]
    (if-let [verdict (get verdicts-by-cid cid)]
      {:state :canonical :verdict-cid cid :verdict verdict
       :result (:verdict/status verdict)}
      (throw (ex-info "murakumo-cd: canonical verdict document unavailable"
                      {:reason :missing-verdict :verdict-cid cid})))
    {:state :pending :result :running}))

(defn github-status
  [finalized repo sha run-id target-url]
  {:repo repo :sha sha :run-id run-id :target-url target-url
   :result (:result finalized)
   :description (if (= :canonical (:state finalized))
                  "Murakumo quorum reached"
                  "Murakumo quorum pending")})

(defn issue-deployment
  "Issue no capability unless the threshold-canonical verdict passed and the
   requested artifact is one of that verdict's immutable artifacts."
  [issuer-seed rid finalized {:keys [artifact-cid] :as request}]
  (when-not (and (= :canonical (:state finalized))
                 (= :passed (:result finalized)))
    (throw (ex-info "murakumo-cd: CI verdict does not authorize deployment"
                    {:reason :ci-not-passed})))
  (let [allowed (set (keep #(when (= :murakumo/release-bundle (:type %))
                             (or (:cid %) (:digest %)))
                           (:verdict/artifacts (:verdict finalized))))]
    (when-not (contains? allowed artifact-cid)
      (throw (ex-info "murakumo-cd: artifact is not in canonical verdict"
                      {:reason :artifact-not-canonical :artifact-cid artifact-cid})))
    (capability/issue issuer-seed rid
                      (assoc request :verdict-cid (:verdict-cid finalized)))))
