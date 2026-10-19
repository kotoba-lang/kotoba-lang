(ns identity.adapters.vc
  (:require [identity.model :as m]))

(defprotocol ICredentialVerifier
  (verify-credential! [verifier credential-ref opts]))

(defn- subject-id [credential opts]
  (or (:subject-id opts)
      (get-in credential [:credentialSubject :id])
      (:subject credential)))

(defn credential->evidence [credential-ref verified opts]
  (m/evidence-ref (or (:evidence-id opts)
                      (str "vc:" (or (:id verified) credential-ref)))
                  :credential
                  {:ref credential-ref
                   :hash (:hash verified)
                   :source (:issuer verified)
                   :observed-at (:verified-at verified)
                   :claims (:claims verified)
                   :non-adjudicating true}))

(defn credential->attestation [credential-ref verified evidence opts]
  (m/attestation (or (:attestation-id opts)
                     (str "vc-attestation:" (or (:id verified) credential-ref)))
                 (subject-id verified opts)
                 (or (:predicate opts) :vc/verified)
                 {:issuer (:issuer verified)
                  :evidence [(:identity.evidence/id evidence)]
                  :issued-at (:verified-at verified)
                  :non-adjudicating true}))

(defn bind-credential!
  ([verifier credential-ref] (bind-credential! verifier credential-ref {}))
  ([verifier credential-ref opts]
   (let [verified (verify-credential! verifier credential-ref opts)]
     (when (:error verified)
       (throw (ex-info "Verifiable Credential verification failed"
                       {:credential-ref credential-ref
                        :credential/result verified})))
     (let [evidence (credential->evidence credential-ref verified opts)
           attestation (credential->attestation credential-ref verified evidence opts)]
       {:identity/evidence evidence
        :identity/attestation attestation
        :vc/result verified}))))

(defn static-verifier [credentials]
  (reify ICredentialVerifier
    (verify-credential! [_ credential-ref _opts]
      (or (get credentials credential-ref)
          {:error :vc/not-found
           :credential-ref credential-ref}))))
