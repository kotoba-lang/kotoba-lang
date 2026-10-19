(ns oauth.datom)

(defn auth-request-datoms [request]
  [{:db/id (:oauth.request/id request)
    :oauth.request/client-id (:oauth.request/client-id request)
    :oauth.request/redirect-uri (:oauth.request/redirect-uri request)
    :oauth.request/scope (:oauth.request/scope request)
    :oauth.request/state (:oauth.request/state request)
    :oauth.request/code-challenge (:oauth.request/code-challenge request)
    :oauth.request/created-at (:oauth.request/created-at request)}])

(defn token-result-datoms [result]
  [{:db/id (str "oauth:token-result:" (:oauth.result/access-token-ref result))
    :oauth.result/ok? (:oauth.result/ok? result)
    :oauth.result/access-token-ref (:oauth.result/access-token-ref result)
    :oauth.result/refresh-token-ref (:oauth.result/refresh-token-ref result)
    :oauth.result/scope (:oauth.result/scope result)
    :oauth.result/expires-at (:oauth.result/expires-at result)}])
