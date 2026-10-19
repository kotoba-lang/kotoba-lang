(ns murakumo.ci.quorum
  "Resolve persisted runner attestations into one threshold-canonical verdict."
  (:require [murakumo.ci.attest :as attest]))

(defn evaluate [policy logical-id runs]
  (let [attestations (vec (keep :ci.run/attestation runs))
        sigrefs (mapv :sigref attestations)
        verdict-cid (attest/canonical-verdict
                     (assoc policy :run-id logical-id) sigrefs)]
    (if-not verdict-cid
      {:state :pending :run-id logical-id
       :votes (count attestations) :threshold (:threshold policy)}
      (let [documents (->> attestations
                           (filter #(= verdict-cid (:verdict-cid %)))
                           (map :verdict)
                           distinct vec)]
        (when-not (= 1 (count documents))
          (throw (ex-info "murakumo-ci: canonical verdict document disagreement"
                          {:reason :verdict-document-disagreement})))
        {:state :canonical :run-id logical-id :verdict-cid verdict-cid
         :verdict (first documents)
         :result (:verdict/status (first documents))}))))
