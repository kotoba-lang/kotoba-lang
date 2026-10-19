(ns identity.model)

(def subject-types #{:person :organization :device :account :wallet :ip-address :agent})
(def evidence-kinds
  #{:document :liveness :biometric-attestation :screening :credential :external-ref
    :onchain-attestation :humanity-proof
    :agent-registration :agent-feedback :agent-validation})

(defn subject [id type opts]
  {:identity.subject/id id
   :identity.subject/type type
   :identity.subject/did (:did opts)
   :identity.subject/labels (set (:labels opts))
   :identity.subject/source (:source opts)
   :identity.subject/aliases (vec (:aliases opts))
   :identity.subject/conflict? (boolean (:conflict? opts))})

(defn evidence-ref [id kind opts]
  {:identity.evidence/id id
   :identity.evidence/kind kind
   :identity.evidence/ref (:ref opts)
   :identity.evidence/hash (:hash opts)
   :identity.evidence/source (:source opts)
   :identity.evidence/observed-at (:observed-at opts)
   :identity.evidence/claims (:claims opts)
   :identity/non-adjudicating (boolean (:non-adjudicating opts true))})

(defn attestation [id subject predicate opts]
  {:identity.attestation/id id
   :identity.attestation/subject subject
   :identity.attestation/predicate predicate
   :identity.attestation/issuer (:issuer opts)
   :identity.attestation/evidence (:evidence opts)
   :identity.attestation/issued-at (:issued-at opts)
   :identity/non-adjudicating (boolean (:non-adjudicating opts true))})
