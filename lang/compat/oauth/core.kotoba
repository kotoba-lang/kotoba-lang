(ns oauth.core
  (:require [clojure.string :as str]
            [oauth.model :as m]
            [oauth.ports :as p]))

(defn problems [record]
  (cond-> []
    (and (:oauth.token/grant-type record)
         (not (contains? m/grant-types (:oauth.token/grant-type record))))
    (conj {:oauth.problem/code :unknown-grant-type})

    (and (= :authorization-code (:oauth.token/grant-type record))
         (empty? (:oauth.token/code record)))
    (conj {:oauth.problem/code :authorization-code/missing-code})

    (and (= :authorization-code (:oauth.token/grant-type record))
         (empty? (:oauth.token/code-verifier-ref record)))
    (conj {:oauth.problem/code :authorization-code/missing-pkce-verifier})

    (and (= :refresh-token (:oauth.token/grant-type record))
         (empty? (:oauth.token/refresh-token-ref record)))
    (conj {:oauth.problem/code :refresh-token/missing-ref})))

(defn auth-request-problems [record]
  (cond-> []
    (empty? (:oauth.request/state record))
    (conj {:oauth.problem/code :auth-request/missing-state})

    (empty? (:oauth.request/code-challenge record))
    (conj {:oauth.problem/code :auth-request/missing-pkce-challenge})))

(defn exchange [port token-request]
  (when-let [ps (seq (problems token-request))]
    (throw (ex-info "invalid OAuth token request" {:oauth/problems ps})))
  (p/exchange-token! port token-request))

(defn normalize-introspection [token-ref response]
  (m/introspection-result (or (:active response)
                              (:oauth.introspection/active? response))
                          {:token-ref token-ref
                           :client-id (or (:client_id response)
                                          (:client-id response)
                                          (:oauth.introspection/client-id response))
                           :subject (or (:sub response)
                                        (:subject response)
                                        (:oauth.introspection/subject response))
                           :scope (cond
                                    (set? (:scope response)) (:scope response)
                                    (string? (:scope response)) (set (remove empty? (str/split (:scope response) #"\s+")))
                                    (sequential? (:scope response)) (set (:scope response))
                                    :else (:oauth.introspection/scope response))
                           :expires-at (or (:exp response)
                                           (:expires_at response)
                                           (:oauth.introspection/expires-at response))
                           :issued-at (or (:iat response)
                                          (:oauth.introspection/issued-at response))}))

(defn introspect [port token-ref]
  (normalize-introspection token-ref (p/introspect! port token-ref)))

(defn require-active-token [port token-ref]
  (let [out (introspect port token-ref)]
    (when-not (:oauth.introspection/active? out)
      (throw (ex-info "OAuth token is inactive" {:oauth/token-ref token-ref
                                                 :oauth/introspection out})))
    out))

(defn exchange-callback [port state-store auth-request callback opts]
  (when-let [ps (seq (auth-request-problems auth-request))]
    (throw (ex-info "invalid OAuth authorization request" {:oauth/problems ps})))
  (when-not (= (:oauth.request/state auth-request) (:oauth.callback/state callback))
    (throw (ex-info "OAuth state mismatch" {:oauth/request auth-request :oauth/callback callback})))
  (when-not (p/consume-state! state-store (:oauth.callback/state callback))
    (throw (ex-info "OAuth state replay" {:oauth/state (:oauth.callback/state callback)})))
  (when (:oauth.callback/error callback)
    (throw (ex-info "OAuth callback error" {:oauth/callback callback})))
  (exchange port (m/token-request :authorization-code
                                  {:code (:oauth.callback/code callback)
                                   :client-id (:oauth.request/client-id auth-request)
                                   :redirect-uri (:oauth.request/redirect-uri auth-request)
                                   :code-verifier-ref (:code-verifier-ref opts)})))
