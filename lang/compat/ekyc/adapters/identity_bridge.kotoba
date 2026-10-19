(ns ekyc.adapters.identity-bridge
  (:require [ekyc.core :as core]
            [identity.adapters.ledger :as ledger]
            [identity.model :as identity]))

(def check->evidence-kind
  {:document-authenticity :document
   :document-ocr :document
   :liveness :liveness
   :face-match :biometric-attestation
   :address :external-ref
   :pep :screening
   :sanctions :screening
   :adverse-media :screening
   :manual-review :credential})

(defn identity-evidence-id [evidence]
  (str "ekyc:" (:ekyc/id evidence) ":" (name (:ekyc/check evidence))))

(defn identity-evidence [evidence]
  (identity/evidence-ref
   (identity-evidence-id evidence)
   (get check->evidence-kind (:ekyc/check evidence) :external-ref)
   {:ref (:ekyc/evidence-ref evidence)
    :source (:ekyc/source evidence)
    :observed-at (:ekyc/observed-at evidence)
    :non-adjudicating (:ekyc/non-adjudicating evidence)}))

(defn identity-attestation [session completion evidence]
  (identity/attestation
   (str "ekyc:" (:ekyc/id session) ":attestation")
   (:ekyc/subject session)
   (if (:ekyc/complete? completion) :verified :review)
   {:issuer (:ekyc/provider session)
    :evidence (mapv identity-evidence-id evidence)
    :issued-at (:ekyc/observed-at (last evidence))
    :non-adjudicating true}))

(defn persist-evidence! [ledger evidence opts]
  (when (= :verified (:ekyc/status evidence))
    (ledger/persist-evidence! ledger (identity-evidence evidence) opts)))

(defn persist-completion! [ledger session evidence opts]
  (let [completion (core/completion session evidence)
        verified (filterv #(= :verified (:ekyc/status %)) evidence)]
    (doseq [e verified]
      (persist-evidence! ledger e opts))
    (ledger/persist-attestation! ledger (identity-attestation session completion verified) opts)
    completion))
