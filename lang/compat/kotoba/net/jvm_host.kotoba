(ns kotoba.net.jvm-host
  "JVM host transport for kotoba.net — the ONE place that touches
  java.net.http. Capability-shaped like everything else in kotoba-net:
  `http-transport` returns an injected `transport!` fn
  ({:url :method :headers :body} -> {:status :body :headers}),
  so application code consumes it through `kotoba.net/http-get` and
  `http-post` and never sees java.* types.

  .clj deliberately: HTTP over sockets is a host effect with no ClojureScript
  meaning; the Node/browser counterpart lives with its own host."
  (:require [clojure.string :as str])
  (:import (java.net URI)
           (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
                          HttpResponse$BodyHandlers)
           (java.time Duration)))

(defn http-transport
  "Build a blocking JVM HTTP transport fn.

  Opts:
    :timeout-seconds — connect + request timeout (default 120)

  Returns `transport!` of shape
    ({:url string :method :get|:post :headers map :body string} -> {:status int :body string})
  Unsupported methods throw (fail-closed, never a silent default)."
  ([] (http-transport {}))
  ([{:keys [timeout-seconds] :or {timeout-seconds 120}}]
   (let [client (-> (HttpClient/newBuilder)
                    (.connectTimeout (Duration/ofSeconds timeout-seconds))
                    (.build))]
     (fn [{:keys [url method headers body]}]
       (let [builder (-> (HttpRequest/newBuilder (URI/create url))
                         (.timeout (Duration/ofSeconds timeout-seconds)))
             builder (reduce-kv (fn [b k v] (.header b (name k) (str v)))
                                builder (or headers {}))
             request (case method
                       :get (-> builder .GET .build)
                       :post (-> builder
                                 (.POST (HttpRequest$BodyPublishers/ofString (or body "")))
                                 (.build))
                       :put (-> builder
                                (.PUT (HttpRequest$BodyPublishers/ofString (or body "")))
                                (.build))
                       :patch (-> builder
                                  (.method "PATCH"
                                           (HttpRequest$BodyPublishers/ofString (or body "")))
                                  (.build))
                       :delete (-> builder .DELETE .build)
                       ;; fail-closed: an unknown method is an error, not a GET
                       (throw (ex-info "unsupported-http-method" {:method method})))]
         (let [resp (.send client request (HttpResponse$BodyHandlers/ofString))]
           {:status (.statusCode resp)
            :body (.body resp)}))))))
