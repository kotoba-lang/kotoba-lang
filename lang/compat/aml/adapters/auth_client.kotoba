(ns aml.adapters.auth-client
  (:require [aml.adapters.etzhayyim :as etzhayyim]))

(defn auth-headers [auth]
  (case (:method auth)
    :bearer {"Authorization" (str "Bearer " (:token auth))}
    :api-key {(or (:header auth) "X-API-Key") (:api-key auth)}
    :service-jwt {"Authorization" (str "Bearer " (:jwt auth))}
    {}))

(defn- merge-headers [payload headers]
  (with-meta payload
    (update (meta payload) :headers merge headers)))

(defn auth-client [client auth]
  (reify etzhayyim/IXrpcClient
    (invoke! [_ nsid payload]
      (etzhayyim/invoke! client nsid (merge-headers payload (auth-headers auth))))))

(defn redact-auth [auth]
  (-> auth
      (dissoc :token :api-key :jwt)
      (assoc :redacted? true)))
