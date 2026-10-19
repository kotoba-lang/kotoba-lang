(ns oauth.adapters.http
  (:require [clojure.string :as str]
            [oauth.model :as m]
            [oauth.ports :as p]))

(defprotocol IHttpClient
  (get-json! [client url opts])
  (post-form! [client url form opts])
  (post-json! [client url body opts]))

(defn- token-form [token-request]
  (cond-> {:grant_type (name (:oauth.token/grant-type token-request))
           :client_id (:oauth.token/client-id token-request)
           :redirect_uri (:oauth.token/redirect-uri token-request)}
    (:oauth.token/code token-request)
    (assoc :code (:oauth.token/code token-request))
    (:oauth.token/code-verifier-ref token-request)
    (assoc :code_verifier_ref (:oauth.token/code-verifier-ref token-request))
    (:oauth.token/refresh-token-ref token-request)
    (assoc :refresh_token_ref (:oauth.token/refresh-token-ref token-request))))

(defn- scope-set [scope]
  (cond
    (set? scope) scope
    (string? scope) (set (remove empty? (str/split scope #"\s+")))
    (sequential? scope) (set scope)
    :else #{}))

(defn- token-result [response]
  (m/token-result (not (:error response))
                  {:access-token-ref (or (:access_token_ref response) (:access-token-ref response))
                   :refresh-token-ref (or (:refresh_token_ref response) (:refresh-token-ref response))
                   :scope (scope-set (:scope response))
                   :expires-at (:expires_at response)}))

(defn token-endpoint-port [client config]
  (reify p/IOAuth
    (exchange-token! [_ token-request]
      (token-result
       (post-form! client (:token-endpoint config) (token-form token-request)
                   {:client-auth (:client-auth config)})))
    (introspect! [_ token-ref]
      (post-json! client (:introspection-endpoint config)
                  {:token_ref token-ref}
                  {:client-auth (:client-auth config)}))))
