(ns kotoba.lang.package-registry-network
  "Verified network adapter for the pure package registry kernel."
  (:require [clojure.edn :as edn]
            [multiformats.core :as mf]
            [kotoba.lang.package-registry :as registry])
  (:import (java.net URI) (java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers)
           (java.time Duration)))

(defn lock-from-requests-network
  ([cid requests] (lock-from-requests-network cid requests {}))
  ([cid requests {:keys [gateway-base timeout-ms]
                  :or {gateway-base "http://127.0.0.1:8080/ipfs/" timeout-ms 10000}}]
   (try
     (let [request (-> (HttpRequest/newBuilder) (.uri (URI/create (str gateway-base cid)))
                       (.timeout (Duration/ofMillis timeout-ms)) (.GET) (.build))
           response (.send (HttpClient/newHttpClient) request (HttpResponse$BodyHandlers/ofByteArray))
           bytes (.body response)]
       (cond
         (not= 200 (.statusCode response)) {:ok? false :problems [{:problem :registry/fetch-http-status}]}
         (not= cid (mf/cidv1-raw bytes)) {:ok? false :problems [{:problem :registry/cid-mismatch}]}
         :else (registry/lock-from-requests (edn/read-string (String. ^bytes bytes "UTF-8")) requests)))
     (catch Exception e {:ok? false :problems [{:problem :registry/fetch-failed :message (ex-message e)}]}))))
