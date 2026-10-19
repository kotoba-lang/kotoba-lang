(ns kagi.passkey-bridge
  "One-shot loopback-only browser bridge for WebAuthn PRF registration."
  (:require [clojure.data.json :as json]
            [kagi.crypto :as crypto])
  (:import [com.sun.net.httpserver HttpExchange HttpHandler HttpServer]
           [java.io ByteArrayOutputStream]
           [java.net InetAddress InetSocketAddress]
           [java.nio.charset StandardCharsets]
           [java.util Base64]
           [java.util.concurrent Executors ThreadFactory TimeUnit]))

(defn- token [p]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) (crypto/rand-bytes p 32)))

(defn- respond! [^HttpExchange exchange status content-type body]
  (let [body (.getBytes ^String body StandardCharsets/UTF_8)]
    (.set (.getResponseHeaders exchange) "content-type" content-type)
    (.set (.getResponseHeaders exchange) "cache-control" "no-store")
    (.set (.getResponseHeaders exchange) "content-security-policy"
          "default-src 'none'; script-src 'self'; connect-src 'self'; style-src 'none'; base-uri 'none'; frame-ancestors 'none'")
    (.sendResponseHeaders exchange status (alength body))
    (with-open [out (.getResponseBody exchange)] (.write out body))))

(defn- resource-text [name]
  (if-let [resource (.getResource (clojure.lang.RT/baseLoader) name)]
    (slurp resource)
    (throw (ex-info "missing bridge resource" {:resource name}))))

(defn- read-body [exchange]
  (with-open [in (.getRequestBody exchange)
              out (ByteArrayOutputStream.)]
    (let [buffer (byte-array 4096)]
      (loop [total 0]
        (let [n (.read in buffer)]
          (if (neg? n)
            (.toString out "UTF-8")
            (let [next (+ total n)]
              (when (> next 65536)
                (throw (ex-info "passkey bridge body too large" {:max-bytes 65536})))
              (.write out buffer 0 n)
              (recur next))))))))

(defn start!
  "Start on 127.0.0.1 and return {:url :token :await :stop}. on-input runs once."
  [p {:keys [on-input timeout-seconds] :or {timeout-seconds 120}}]
  (let [server (HttpServer/create (InetSocketAddress. (InetAddress/getByName "127.0.0.1") 0) 0)
        port (.getPort (.getAddress server))
        origin (str "http://127.0.0.1:" port)
        secret-token (token p)
        delivered (promise)
        consumed? (atom false)
        executor (Executors/newFixedThreadPool 2)]
    (.setExecutor server executor)
    (.createContext server "/"
                    (reify HttpHandler
                      (handle [_ exchange]
                        (let [path (.getPath (.getRequestURI exchange))]
                          (cond
                            (= path "/") (respond! exchange 200 "text/html; charset=utf-8"
                                                    (resource-text "passkey.html"))
                            (= path "/passkey-app.mjs") (respond! exchange 200 "text/javascript; charset=utf-8"
                                                                  (resource-text "passkey-app.mjs"))
                            (= path "/passkey-prf.mjs") (respond! exchange 200 "text/javascript; charset=utf-8"
                                                                  (resource-text "passkey-prf.mjs"))
                            :else (respond! exchange 404 "text/plain" "not found"))))))
    (.createContext server "/bridge"
                    (reify HttpHandler
                      (handle [_ exchange]
                        (let [headers (.getRequestHeaders exchange)
                              valid? (and (= "POST" (.getRequestMethod exchange))
                                          (= origin (.getFirst headers "Origin"))
                                          (= secret-token (.getFirst headers "X-Kagi-Token"))
                                          (compare-and-set! consumed? false true))]
                          (if-not valid?
                            (respond! exchange 403 "application/json" "{\"ok\":false}")
                            (try
                              (let [input (json/read-str (read-body exchange)
                                                         :key-fn keyword)
                                    result (on-input input)]
                                (respond! exchange 200 "application/json" "{\"ok\":true}")
                                (deliver delivered result))
                              (catch Exception _error
                                (reset! consumed? false)
                                (respond! exchange 400 "application/json" "{\"ok\":false}"))))))))
    (.start server)
    (let [stop #(do (.stop server 0) (.shutdownNow executor))]
      (.schedule (Executors/newSingleThreadScheduledExecutor
                  (reify ThreadFactory
                    (newThread [_ r]
                      (doto (Thread. r "kagi-passkey-timeout") (.setDaemon true)))))
                 ^Runnable (fn [] (deliver delivered ::timeout) (stop))
                 timeout-seconds TimeUnit/SECONDS)
      {:url (str origin "/#" secret-token) :origin origin :token secret-token
       :await #(deref delivered (* 1000 (inc timeout-seconds)) ::timeout) :stop stop})))
