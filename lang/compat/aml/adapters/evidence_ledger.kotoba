(ns aml.adapters.evidence-ledger
  (:require [identity.adapters.ledger :as ledger]
            [identity.model :as identity]))

(defn result->evidence [request result]
  (identity/evidence-ref (str (:aml/id result) ":" (name (:aml/route result)) ":evidence")
                         :screening
                         {:ref (:aml/evidence-ref result)
                          :source (:aml/asserter result)
                          :observed-at (:aml/observed-at result)
                          :non-adjudicating true}))

(defn result->attestation [request result evidence]
  (identity/attestation (str (:aml/id result) ":" (name (:aml/route result)) ":attestation")
                        (:aml/subject request)
                        (keyword "aml" (name (:aml/level result)))
                        {:issuer (:aml/asserter result)
                         :evidence [(:identity.evidence/id evidence)]
                         :issued-at (:aml/observed-at result)
                         :non-adjudicating true}))

(defn persist-result!
  ([ledger request result] (persist-result! ledger request result {}))
  ([ledger request result opts]
   (let [evidence (result->evidence request result)
         attestation (result->attestation request result evidence)]
     {:evidence-tx (ledger/persist-evidence! ledger evidence opts)
      :attestation-tx (ledger/persist-attestation! ledger attestation opts)
      :identity/evidence evidence
      :identity/attestation attestation})))

(defn persist-screening!
  ([ledger request screening] (persist-screening! ledger request screening {}))
  ([ledger request screening opts]
   (mapv #(persist-result! ledger request % opts) (:aml/results screening))))
