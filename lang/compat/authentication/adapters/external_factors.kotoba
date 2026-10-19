(ns authentication.adapters.external-factors
  (:require [authentication.model :as m]
            [authentication.ports :as p]
            [faceid.core :as faceid]
            [faceid.model :as faceid-model]
            [oauth.core :as oauth]
            [oauth.model :as oauth-model]
            [oidc.core :as oidc]
            [onetime.core :as onetime]
            [onetime.model :as onetime-model]
            [saml.core :as saml]
            [touchid.core :as touchid]
            [touchid.model :as touchid-model]
            [webauthn.core :as webauthn]
            [webauthn.model :as webauthn-model]))

(defn- response-value [response k fallback]
  (or (get response k) fallback))

(defn- factor [factor-request ok? opts]
  (m/factor (:authn.factor-request/id factor-request)
            (:authn.factor-request/type factor-request)
            ok?
            {:subject (or (:subject opts)
                          (:authn.factor-request/subject factor-request))
             :evidence-ref (:evidence-ref opts)
             :assurance (:assurance opts)
             :at (:at opts)}))

(defn faceid-verifier
  ([port] (faceid-verifier port {}))
  ([port opts]
   (reify p/IFactorVerifier
     (verify-factor! [_ factor-request response]
       (let [request (or (:faceid/request response)
                         (:faceid/request opts)
                         (faceid-model/request (:authn.factor-request/id factor-request)
                                               {:purpose (or (:purpose opts) :step-up)
                                                :challenge (:authn.factor-request/challenge-ref factor-request)
                                                :rp-id (:rp-id opts)
                                                :subject (:authn.factor-request/subject factor-request)
                                                :created-at (:created-at opts)}))
             out (faceid/attest port request)]
         (factor factor-request
                 (:faceid/ok? out)
                 {:subject (:faceid/subject out)
                  :evidence-ref (:faceid/evidence-ref out)
                  :assurance (or (:assurance opts) :single-factor)
                  :at (:faceid/attested-at out)}))))))

(defn touchid-verifier
  ([port] (touchid-verifier port {}))
  ([port opts]
   (reify p/IFactorVerifier
     (verify-factor! [_ factor-request response]
       (let [request (or (:touchid/request response)
                         (:touchid/request opts)
                         (touchid-model/request (:authn.factor-request/id factor-request)
                                                {:purpose (or (:purpose opts) :step-up)
                                                 :challenge (:authn.factor-request/challenge-ref factor-request)
                                                 :rp-id (:rp-id opts)
                                                 :subject (:authn.factor-request/subject factor-request)
                                                 :created-at (:created-at opts)}))
             out (touchid/attest port request)]
         (factor factor-request
                 (:touchid/ok? out)
                 {:subject (:touchid/subject out)
                  :evidence-ref (:touchid/evidence-ref out)
                  :assurance (or (:assurance opts) :single-factor)
                  :at (:touchid/attested-at out)}))))))

(defn webauthn-verifier
  ([port] (webauthn-verifier port nil {}))
  ([port challenge-store opts]
   (reify p/IFactorVerifier
     (verify-factor! [_ factor-request response]
       (let [challenge (or (:webauthn/challenge response)
                           (:webauthn/challenge opts)
                           (webauthn-model/challenge (:authn.factor-request/challenge-ref factor-request)
                                                     :authentication
                                                     {:rp-id (:rp-id opts)
                                                      :user-handle (:authn.factor-request/subject factor-request)
                                                      :challenge (:challenge response)
                                                      :created-at (:created-at opts)}))
             assertion (:webauthn/assertion response)
             out (if challenge-store
                   (webauthn/authenticate-once port challenge-store challenge assertion)
                   (webauthn/authenticate port challenge assertion))]
         (factor factor-request
                 (:webauthn.assertion/ok? out)
                 {:evidence-ref (:webauthn.assertion/evidence-ref out)
                  :assurance (or (:assurance opts) :phishing-resistant)
                  :at (:webauthn.assertion/attested-at out)}))))))

(defn onetime-verifier
  ([port] (onetime-verifier port nil {}))
  ([port attempt-store opts]
   (reify p/IFactorVerifier
     (verify-factor! [_ factor-request response]
       (let [challenge (or (:onetime/challenge response)
                           (:onetime/challenge opts)
                           (onetime-model/challenge (:authn.factor-request/id factor-request)
                                                    (:authn.factor-request/type factor-request)
                                                    {:subject (:authn.factor-request/subject factor-request)
                                                     :purpose (:authn.factor-request/purpose factor-request)
                                                     :digest-ref (:authn.factor-request/challenge-ref factor-request)
                                                     :created-at (:created-at opts)}))
             code-response (response-value response :onetime/response response)
             out (if attempt-store
                   (onetime/verify-once port attempt-store challenge code-response)
                   (onetime/verify port challenge code-response))]
         (factor factor-request
                 (:onetime/ok? out)
                 {:assurance (or (:assurance opts) :single-factor)
                  :at (:onetime/verified-at out)}))))))

(defn oauth-verifier [port state-store opts]
  (reify p/IFactorVerifier
    (verify-factor! [_ factor-request response]
      (let [auth-request (or (:oauth/request response)
                             (:oauth/request opts)
                             (oauth-model/auth-request (:authn.factor-request/id factor-request)
                                                       {:client-id (:client-id opts)
                                                        :redirect-uri (:redirect-uri opts)
                                                        :scope (:scope opts)
                                                        :state (:state response)
                                                        :code-challenge (:code-challenge opts)
                                                        :created-at (:created-at opts)}))
            callback (or (:oauth/callback response)
                         (oauth-model/callback {:state (:state response)
                                                :code (:code response)
                                                :error (:error response)
                                                :received-at (:received-at response)}))
            out (oauth/exchange-callback port state-store auth-request callback opts)]
        (factor factor-request
                (:oauth.result/ok? out)
                {:evidence-ref (:oauth.result/access-token-ref out)
                 :assurance (or (:assurance opts) :single-factor)
                 :at (:received-at response)})))))

(defn oidc-verifier [port nonce-store opts]
  (reify p/IFactorVerifier
    (verify-factor! [_ factor-request response]
      (let [request (or (:oidc/request response) (:oidc/request opts))
            token-ref (or (:oidc/token-ref response) (:token-ref response))
            out (if nonce-store
                  (oidc/verify-once port nonce-store request token-ref)
                  (oidc/verify port request token-ref))]
        (factor factor-request
                (:oidc.id-token/ok? out)
                {:subject (:oidc.id-token/subject out)
                 :evidence-ref (:oidc.id-token/evidence-ref out)
                 :assurance (or (:assurance opts) :single-factor)
                 :at (:verified-at response)})))))

(defn saml-verifier [port relay-store opts]
  (reify p/IFactorVerifier
    (verify-factor! [_ factor-request response]
      (let [request (or (:saml/request response) (:saml/request opts))
            assertion-ref (or (:saml/assertion-ref response) (:assertion-ref response))
            out (if relay-store
                  (saml/verify-once relay-store port request assertion-ref)
                  (saml/verify port request assertion-ref))]
        (factor factor-request
                (:saml.assertion/ok? out)
                {:subject (:saml.assertion/subject out)
                 :evidence-ref (:saml.assertion/evidence-ref out)
                 :assurance (or (:assurance opts) :single-factor)
                 :at (:verified-at response)})))))
