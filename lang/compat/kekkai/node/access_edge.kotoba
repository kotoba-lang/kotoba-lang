(ns kekkai.node.access-edge
  "Private HTTP client and connector over the first-party Kekkai data plane."
  (:require [clojure.string :as str]
            [kekkai.node.agent :as agent]
            [kekkai.node.application :as application]
            [kekkai.node.netmap :as netmap]
            ["node:fs" :as fs]
            ["node:http" :as http]))

(def max-http-body-bytes (* 1024 1024))
(def max-pending-requests 256)
(def forwarded-request-headers #{"accept" "content-type" "if-none-match" "range"})
(def forwarded-response-headers
  #{"content-type" "content-length" "etag" "last-modified" "content-range"})

(defn- request-bytes [request]
  (js/Promise.
   (fn [resolve reject]
     (let [chunks (array)
           size (atom 0)]
       (.on request "data"
            (fn [chunk]
              (swap! size + (.-length chunk))
              (if (> @size max-http-body-bytes)
                (do (.destroy request)
                    (reject (js/Error. "HTTP request body exceeds limit")))
                (.push chunks chunk))))
       (.on request "end" #(resolve (.concat js/Buffer chunks)))
       (.on request "error" reject)))))

(defn- selected-headers [headers allowed]
  (into {}
        (keep (fn [[name value]]
                (when (and (contains? allowed (str/lower-case name))
                           (string? value))
                  [(str/lower-case name) value])))
        (js->clj headers)))

(defn- send-message! [handle peer message reply?]
  (doseq [frame (application/encode message)]
    (swap! (:state handle)
           (if reply? agent/reply-to agent/send-to)
           peer frame)))

(defn- service [config name]
  (get (:services config) name))

(defn- safe-upstream-url [base-url path]
  (when-not (and (string? path) (str/starts-with? path "/")
                 (not (str/starts-with? path "//")))
    (throw (js/Error. "invalid private application path")))
  (let [base (js/URL. base-url)
        target (js/URL. path base)]
    (when-not (= (.-origin base) (.-origin target))
      (throw (js/Error. "private application origin change denied")))
    (.toString target)))

(defn- connector-request!
  [config handle peer message]
  (let [{:keys [requestId serviceName method path headers bodyBase64 port]} message
        definition (service config serviceName)
        state @(:state handle)
        self-id (:self-id state)]
    (when-not definition
      (throw (js/Error. "private application service is not configured")))
    (when-not (= (:port definition) port)
      (throw (js/Error. "private application port mismatch")))
    (when-not (netmap/permitted? (:netmap state) peer self-id
                                 :private-http port)
      (throw (js/Error. "private application edge is not authorized")))
    (-> (js/fetch
         (safe-upstream-url (:base-url definition) path)
         (clj->js {:method method
                   :headers headers
                   :redirect "manual"
                   :body (when-not (#{"GET" "HEAD"} method)
                           (.from js/Buffer (or bodyBase64 "") "base64"))}))
        (.then
         (fn [response]
           (-> (.arrayBuffer response)
               (.then
                (fn [body]
                  (let [buffer (.from js/Buffer body)]
                    (when (> (.-length buffer) max-http-body-bytes)
                      (throw (js/Error. "upstream response exceeds limit")))
                    (send-message!
                     handle peer
                     {:messageType "response" :requestId requestId
                      :status (.-status response)
                      :headers
                      (into {}
                            (keep
                             (fn [[name value]]
                               (when (contains? forwarded-response-headers
                                                (str/lower-case name))
                                 [(str/lower-case name) value])))
                            (js->clj (js/Object.fromEntries
                                     (.entries (.-headers response)))))
                      :bodyBase64 (.toString buffer "base64")}
                     true))))))))))

(defn- respond-error! [response status message]
  (.writeHead response status #js {"content-type" "application/json"
                                   "cache-control" "no-store"})
  (.end response (js/JSON.stringify #js {:error message})))

(defn- client-request! [config handle pending request response]
  (let [url (js/URL. (.-url request) "http://127.0.0.1")
        parts (vec (remove str/blank? (str/split (.-pathname url) #"/")))
        service-name (first parts)
        definition (service config service-name)]
    (if-not definition
      (respond-error! response 404 "private application service not found")
      (if (>= (count @pending) max-pending-requests)
        (respond-error! response 503 "private application client is busy")
        (-> (request-bytes request)
          (.then
           (fn [body]
             (let [state @(:state handle)
                   self-id (:self-id state)
                   peer (:peer definition)
                   port (:port definition)
                   request-id (str (random-uuid))
                   app-path (str "/" (str/join "/" (rest parts))
                                 (.-search url))]
               (when-not (netmap/permitted? (:netmap state) self-id peer
                                            :private-http port)
                 (throw (js/Error. "private application edge is not authorized")))
               (swap! pending assoc request-id
                      {:response response
                       :timer (js/setTimeout
                               #(when-let [{:keys [response]} (get @pending request-id)]
                                  (swap! pending dissoc request-id)
                                  (respond-error! response 504
                                                  "private application timeout"))
                               (or (:request-timeout-ms config) 30000))})
               (send-message!
                handle peer
                {:messageType "request" :requestId request-id
                 :serviceName service-name :method (.-method request)
                 :path app-path
                 :headers (selected-headers (.-headers request)
                                            forwarded-request-headers)
                 :port port :bodyBase64 (.toString body "base64")}
                false))))
            (.catch #(respond-error! response 403 (.-message %))))))))

(defn- client-response! [pending message]
  (when-let [{:keys [response timer]} (get @pending (:requestId message))]
    (js/clearTimeout timer)
    (swap! pending dissoc (:requestId message))
    (.writeHead response (:status message) (clj->js (:headers message)))
    (.end response (.from js/Buffer (or (:bodyBase64 message) "") "base64"))))

(defn start [config]
  (let [assemblies (atom (application/empty-reassembly))
        pending (atom {})
        handle (atom nil)
        on-event
        (fn [event]
          (when (= :data (:event event))
            (let [{next-state :state message :message error :error peer :peer}
                  (application/accept @assemblies (:peer event) (:payload event))]
              (reset! assemblies next-state)
              (when error
                (println (str "[access-edge] dropped frame from " peer ": " error)))
              (when message
                (case [(:mode config) (:messageType message)]
                  [:connector "request"]
                  (-> (connector-request! config @handle peer message)
                      (.catch
                       (fn [failure]
                         (send-message!
                          @handle peer
                          {:messageType "response"
                           :requestId (:requestId message) :status 502
                           :headers {"content-type" "application/json"}
                           :bodyBase64
                           (.toString
                            (.from js/Buffer
                                   (js/JSON.stringify
                                    #js {:error (.-message failure)}))
                            "base64")}
                          true))))

                  [:client "response"] (client-response! pending message)
                  nil)))))]
    (-> (agent/start {:config config :on-event on-event})
        (.then
         (fn [agent-handle]
           (reset! handle agent-handle)
           (if (= :client (:mode config))
             (let [server
                   (.createServer
                    http #(client-request! config agent-handle pending %1 %2))
                   port (or (:http-listen-port config) 8080)]
               (.listen server port "127.0.0.1"
                        #(println (str "Kekkai private-app client on 127.0.0.1:"
                                       port)))
               (assoc agent-handle
                      :http-server server
                      :stop (fn []
                              (.close server)
                              ((:stop agent-handle)))))
             agent-handle))))))

(defn -main [& args]
  (let [path (or (first args) "kekkai-access-edge.edn")
        config (cljs.reader/read-string (.readFileSync fs path "utf8"))]
    (-> (start config)
        (.catch
         (fn [error]
           (js/console.error (str "access edge failed: " (ex-message error)))
           (set! (.-exitCode js/process) 1))))))
