(ns identity.datom)

(defn subject-datoms [s]
  [{:db/id (:identity.subject/id s)
    :identity.subject/type (:identity.subject/type s)
    :identity.subject/did (:identity.subject/did s)
    :identity.subject/labels (:identity.subject/labels s)
    :identity.subject/source (:identity.subject/source s)
    :identity.subject/aliases (:identity.subject/aliases s)
    :identity.subject/conflict? (:identity.subject/conflict? s)}])

(defn evidence-datoms [e]
  [{:db/id (:identity.evidence/id e)
    :identity.evidence/kind (:identity.evidence/kind e)
    :identity.evidence/ref (:identity.evidence/ref e)
    :identity.evidence/hash (:identity.evidence/hash e)
    :identity.evidence/source (:identity.evidence/source e)
    :identity.evidence/observed-at (:identity.evidence/observed-at e)
    :identity.evidence/claims (:identity.evidence/claims e)
    :identity/non-adjudicating (:identity/non-adjudicating e)}])

(defn attestation-datoms [a]
  [{:db/id (:identity.attestation/id a)
    :identity.attestation/subject (:identity.attestation/subject a)
    :identity.attestation/predicate (:identity.attestation/predicate a)
    :identity.attestation/issuer (:identity.attestation/issuer a)
    :identity.attestation/evidence (:identity.attestation/evidence a)
    :identity.attestation/issued-at (:identity.attestation/issued-at a)
    :identity/non-adjudicating (:identity/non-adjudicating a)}])

(defn causal-datoms
  "Project one validated causal identity record without erasing attribution."
  [record]
  [(assoc record :db/id
          (or (:identity.epoch/id record)
              (:identity.link/id record)
              (:trust.claim/id record)
              (:identity.obligation/id record)
              (:identity.transition/id record)))])
