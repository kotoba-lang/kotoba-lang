(ns oauth.model)

(def grant-types #{:authorization-code :client-credentials :refresh-token :device-code})

(defn auth-request [id opts]
  {:oauth.request/id id
   :oauth.request/client-id (:client-id opts)
   :oauth.request/redirect-uri (:redirect-uri opts)
   :oauth.request/scope (set (:scope opts))
   :oauth.request/state (:state opts)
   :oauth.request/code-challenge (:code-challenge opts)
   :oauth.request/created-at (:created-at opts)})

(defn token-request [grant-type opts]
  {:oauth.token/grant-type grant-type
   :oauth.token/code (:code opts)
   :oauth.token/refresh-token-ref (:refresh-token-ref opts)
   :oauth.token/client-id (:client-id opts)
   :oauth.token/redirect-uri (:redirect-uri opts)
   :oauth.token/code-verifier-ref (:code-verifier-ref opts)})

(defn token-result [ok? opts]
  {:oauth.result/ok? (boolean ok?)
   :oauth.result/access-token-ref (:access-token-ref opts)
   :oauth.result/refresh-token-ref (:refresh-token-ref opts)
   :oauth.result/scope (set (:scope opts))
   :oauth.result/expires-at (:expires-at opts)})

(defn introspection-result [active? opts]
  {:oauth.introspection/active? (boolean active?)
   :oauth.introspection/token-ref (:token-ref opts)
   :oauth.introspection/client-id (:client-id opts)
   :oauth.introspection/subject (:subject opts)
   :oauth.introspection/scope (set (:scope opts))
   :oauth.introspection/expires-at (:expires-at opts)
   :oauth.introspection/issued-at (:issued-at opts)})

(defn provider-metadata [issuer opts]
  {:oauth.provider/issuer issuer
   :oauth.provider/authorization-endpoint (:authorization-endpoint opts)
   :oauth.provider/token-endpoint (:token-endpoint opts)
   :oauth.provider/introspection-endpoint (:introspection-endpoint opts)
   :oauth.provider/jwks-uri (:jwks-uri opts)
   :oauth.provider/scopes-supported (set (:scopes-supported opts))
   :oauth.provider/grant-types-supported (set (:grant-types-supported opts))
   :oauth.provider/fetched-at (:fetched-at opts)})

(defn callback [opts]
  {:oauth.callback/state (:state opts)
   :oauth.callback/code (:code opts)
   :oauth.callback/error (:error opts)
   :oauth.callback/received-at (:received-at opts)})
