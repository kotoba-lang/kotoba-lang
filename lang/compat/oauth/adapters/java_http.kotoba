(ns oauth.adapters.java-http
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [oauth.adapters.http :as http])
  (:import [java.net URI URLEncoder]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.nio.charset StandardCharsets]))

(defn- form-encode [form]
  (->> form
       (remove (comp nil? val))
       (map (fn [[k v]]
              (str (URLEncoder/encode (name k) StandardCharsets/UTF_8)
                   "="
                   (URLEncoder/encode (str v) StandardCharsets/UTF_8))))
       (str/join "&")))

(defn- request
  ([url headers]
   (let [builder (doto (HttpRequest/newBuilder (URI/create url))
                   (.GET))]
     (doseq [[k v] headers]
       (.header builder k v))
     (.build builder)))
  ([url body headers]
   (let [builder (doto (HttpRequest/newBuilder (URI/create url))
                   (.POST (HttpRequest$BodyPublishers/ofString body)))]
     (doseq [[k v] headers]
       (.header builder k v))
     (.build builder))))

(defn- send! [client req decode]
  (let [resp (.send client req (HttpResponse$BodyHandlers/ofString))
        status (.statusCode resp)
        body (.body resp)]
    (if (<= 200 status 299)
      (decode body)
      {:error :http/status
       :status status
       :body body})))

(defn java-http-client
  ([] (java-http-client {}))
  ([opts]
   (let [client (or (:client opts) (HttpClient/newHttpClient))
         encode-json (or (:encode-json opts) pr-str)
         decode-json (or (:decode-json opts) edn/read-string)]
     (reify http/IHttpClient
       (get-json! [_ url call-opts]
         (send! client
                (request url (merge {"Accept" "application/json"}
                                    (:headers opts)
                                    (:headers call-opts)))
                decode-json))
       (post-form! [_ url form call-opts]
         (send! client
                (request url (form-encode form)
                         (merge {"Content-Type" "application/x-www-form-urlencoded"}
                                (:headers opts)
                                (:headers call-opts)))
                decode-json))
       (post-json! [_ url body call-opts]
         (send! client
                (request url (encode-json body)
                         (merge {"Content-Type" "application/json"}
                                (:headers opts)
                                (:headers call-opts)))
                decode-json))))))
