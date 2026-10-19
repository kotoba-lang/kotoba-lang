(ns oauth.adapters.discovery
  (:require [oauth.adapters.http :as http]
            [oauth.model :as m]
            [clojure.string :as str]))

(defn- metadata-url [issuer]
  (str (str/replace issuer #"/+$" "") "/.well-known/oauth-authorization-server"))

(defn- metadata [issuer response opts]
  (m/provider-metadata issuer
                       {:authorization-endpoint (or (:authorization_endpoint response)
                                                    (:authorization-endpoint response))
                        :token-endpoint (or (:token_endpoint response)
                                            (:token-endpoint response))
                        :introspection-endpoint (or (:introspection_endpoint response)
                                                   (:introspection-endpoint response))
                        :jwks-uri (or (:jwks_uri response)
                                      (:jwks-uri response))
                        :scopes-supported (or (:scopes_supported response)
                                              (:scopes-supported response))
                        :grant-types-supported (or (:grant_types_supported response)
                                                   (:grant-types-supported response))
                        :fetched-at (:fetched-at opts)}))

(defn discover [client issuer opts]
  (let [url (or (:metadata-url opts) (metadata-url issuer))]
    (metadata issuer (http/get-json! client url opts) opts)))

(defn cached-discoverer
  ([client] (cached-discoverer client {}))
  ([client opts]
   (let [cache (atom {})]
     (fn [issuer call-opts]
       (let [opts (merge opts call-opts)]
         (if-let [cached (and (not (:refresh? opts)) (get @cache issuer))]
           cached
           (let [m (discover client issuer opts)]
             (swap! cache assoc issuer m)
             m)))))))
