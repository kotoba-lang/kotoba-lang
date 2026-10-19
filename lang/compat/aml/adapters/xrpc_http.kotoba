(ns aml.adapters.xrpc-http
  (:require [aml.adapters.etzhayyim :as etzhayyim]
            [clojure.edn :as edn])
  (:import [java.net URI URLEncoder]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.nio.charset StandardCharsets]))

(defn- endpoint [base-url nsid]
  (str base-url "/xrpc/" (URLEncoder/encode nsid StandardCharsets/UTF_8)))

(defn- request [url body headers]
  (let [builder (doto (HttpRequest/newBuilder (URI/create url))
                  (.POST (HttpRequest$BodyPublishers/ofString body)))]
    (doseq [[k v] headers]
      (.header builder k v))
    (.build builder)))

(defn- send! [client req decode]
  (let [resp (.send client req (HttpResponse$BodyHandlers/ofString))
        status (.statusCode resp)
        body (.body resp)]
    (if (<= 200 status 299)
      (decode body)
      {:error :http/status
       :status status
       :body body})))

(defn xrpc-client [base-url opts]
  (let [client (or (:client opts) (HttpClient/newHttpClient))
        encode-json (or (:encode-json opts) pr-str)
        decode-json (or (:decode-json opts) edn/read-string)]
    (reify etzhayyim/IXrpcClient
      (invoke! [_ nsid payload]
        (send! client
               (request (endpoint base-url nsid)
                        (encode-json payload)
                        (merge {"Content-Type" "application/json"}
                               (:headers opts)
                               (:headers (meta payload))))
               decode-json)))))
