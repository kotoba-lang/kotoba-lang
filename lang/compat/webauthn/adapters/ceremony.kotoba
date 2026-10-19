(ns webauthn.adapters.ceremony
  (:require [webauthn.model :as m]
            [webauthn.ports :as p]))

(defprotocol ICeremonyVerifier
  (verify-registration! [verifier payload opts])
  (verify-authentication! [verifier payload opts])
  (derive-prf-envelope! [verifier payload opts]))

(defn- challenge-payload [challenge data]
  {:challenge-id (:webauthn.challenge/id challenge)
   :ceremony (:webauthn.challenge/ceremony challenge)
   :rp-id (:webauthn.challenge/rp-id challenge)
   :user-handle (:webauthn.challenge/user-handle challenge)
   :challenge (:webauthn.challenge/challenge challenge)
   :data data})

(defn- registration-credential [challenge response]
  (m/credential (:credential-id response)
                {:rp-id (:webauthn.challenge/rp-id challenge)
                 :user-handle (:webauthn.challenge/user-handle challenge)
                 :public-key-ref (:public-key-ref response)
                 :aaguid (:aaguid response)
                 :transports (:transports response)
                 :discoverable? (:discoverable? response)
                 :created-at (:verified-at response)}))

(defn- authentication-assertion [challenge response]
  (m/assertion challenge (:credential-id response) (not (:error response))
               {:sign-count (:sign-count response)
                :user-present? (:user-present? response)
                :user-verified? (:user-verified? response)
                :evidence-ref (:evidence-ref response)
                :attested-at (:verified-at response)}))

(defn ceremony-port [verifier config]
  (reify p/IWebAuthn
    (register! [_ challenge attestation]
      (registration-credential
       challenge
       (verify-registration! verifier (challenge-payload challenge attestation)
                             {:origin (:origin config)})))
    (authenticate! [_ challenge assertion]
      (authentication-assertion
       challenge
       (verify-authentication! verifier (challenge-payload challenge assertion)
                               {:origin (:origin config)})))
    (derive-prf! [_ envelope]
      (derive-prf-envelope! verifier envelope {:origin (:origin config)}))))
