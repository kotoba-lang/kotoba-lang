(ns authentication.adapters.factor
  (:require [authentication.model :as m]
            [authentication.ports :as p]))

(defprotocol IFactorClient
  (verify! [client payload opts]))

(defn- payload [factor-request response]
  {:request-id (:authn.factor-request/id factor-request)
   :type (:authn.factor-request/type factor-request)
   :subject (:authn.factor-request/subject factor-request)
   :challenge-ref (:authn.factor-request/challenge-ref factor-request)
   :purpose (:authn.factor-request/purpose factor-request)
   :response response})

(defn- factor-result [factor-request result]
  (m/factor (:authn.factor-request/id factor-request)
            (:authn.factor-request/type factor-request)
            (not (:error result))
            {:subject (:authn.factor-request/subject factor-request)
             :evidence-ref (:evidence-ref result)
             :assurance (:assurance result)
             :at (:verified-at result)}))

(defn verifier [client opts]
  (reify p/IFactorVerifier
    (verify-factor! [_ factor-request response]
      (factor-result factor-request
                     (verify! client (payload factor-request response) opts)))))
