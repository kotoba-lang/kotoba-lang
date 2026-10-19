(ns webauthn.adapters.production
  (:require [webauthn.model :as m]
            [webauthn.ports :as p]))

(defprotocol IWebAuthnVerifier
  (verify-client-data! [verifier challenge payload opts])
  (verify-authenticator-data! [verifier challenge payload opts])
  (verify-attestation-chain! [verifier attestation policy opts]))

(defprotocol IBrowserOsWebAuthn
  (create-credential! [client challenge opts])
  (get-assertion! [client challenge opts]))

(defn- verified! [stage result]
  (when-not (:ok? result)
    (throw (ex-info "WebAuthn production verification failed"
                    {:webauthn.verification/stage stage
                     :webauthn.verification/result result})))
  result)

(defn production-port
  ([verifier] (production-port verifier {}))
  ([verifier opts]
   (reify p/IWebAuthn
     (register! [_ challenge attestation]
       (let [client (verified! :client-data
                               (verify-client-data! verifier challenge attestation opts))
             authn (verified! :authenticator-data
                              (verify-authenticator-data! verifier challenge attestation opts))
             chain (verified! :attestation-chain
                              (verify-attestation-chain! verifier attestation (:attestation-policy opts) opts))]
         (m/credential (:credential-id attestation)
                       {:rp-id (:webauthn.challenge/rp-id challenge)
                        :user-handle (:webauthn.challenge/user-handle challenge)
                        :public-key-ref (:public-key-ref chain)
                        :aaguid (:aaguid authn)
                        :transports (:transports attestation)
                        :discoverable? (:discoverable? attestation)
                        :created-at (:created-at client)})))
     (authenticate! [_ challenge assertion]
       (let [client (verify-client-data! verifier challenge assertion opts)
             authn (verify-authenticator-data! verifier challenge assertion opts)]
         (m/assertion challenge
                      (:credential-id assertion)
                      (and (:ok? client) (:ok? authn))
                      {:sign-count (:sign-count authn)
                       :user-present? (:user-present? authn)
                       :user-verified? (:user-verified? authn)
                       :evidence-ref (:evidence-ref authn)
                       :attested-at (:attested-at authn)})))
     (derive-prf! [_ envelope] envelope))))

(defn browser-os-port [client verifier opts]
  {:client client
   :port (production-port verifier opts)})

(defn static-verifier [claims]
  (reify IWebAuthnVerifier
    (verify-client-data! [_ _challenge _payload _opts] (merge {:ok? true} claims))
    (verify-authenticator-data! [_ _challenge _payload _opts] (merge {:ok? true :user-present? true :user-verified? true} claims))
    (verify-attestation-chain! [_ _attestation _policy _opts] (merge {:ok? true} claims))))
