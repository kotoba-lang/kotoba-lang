(ns oauth.adapters.token-signature
  (:require [oauth.model :as m]))

(defprotocol ITokenSignatureVerifier
  (verify-token-signature! [verifier token-ref opts]))

(defn validate-token
  ([verifier token-ref] (validate-token verifier token-ref {}))
  ([verifier token-ref opts]
   (let [claims (verify-token-signature! verifier token-ref opts)]
     (m/introspection-result (and (not (:error claims))
                                  (not (:revoked? claims)))
                             {:token-ref token-ref
                              :client-id (:client-id claims)
                              :subject (:subject claims)
                              :scope (:scope claims)
                              :expires-at (:expires-at claims)
                              :issued-at (:issued-at claims)}))))

(defn static-token-verifier [claims]
  (reify ITokenSignatureVerifier
    (verify-token-signature! [_ _token-ref _opts] claims)))
