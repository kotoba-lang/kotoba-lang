;; murakumo.overlay.transport — transport adapter boundary.

(ns murakumo.overlay.transport
  (:require [kotoba.lang.text :as str]
            [murakumo.config :as config]
            [murakumo.overlay.runtime :as runtime])
  (:import [java.io ByteArrayOutputStream]
           [java.net InetSocketAddress Socket SocketTimeoutException]
           [java.util.concurrent TimeUnit]))

(def default-timeout-ms 1000)
(def default-adapter-timeout-ms 10000)

(def transports
  {:relay {:kind :relay
           :status :native
           :driver :tcp-relay
           :stream? true
           :multiplex? true}
   :quic {:kind :quic
          :status :external-adapter
          :driver-env "MURAKUMO_QUIC_DRIVER"
          :stream? true
          :multiplex? true}
   :webrtc {:kind :webrtc
            :status :external-adapter
            :driver-env "MURAKUMO_WEBRTC_DRIVER"
            :stream? true
            :multiplex? true}
   :webtransport {:kind :webtransport
                  :status :external-adapter
                  :driver-env "MURAKUMO_WEBTRANSPORT_DRIVER"
                  :stream? true
                  :multiplex? true}})

(defn transport-records []
  (mapv (fn [[kind spec]] (assoc spec :transport kind)) transports))

(defn adapter-command
  "External adapter driver command from exact driver-env name (config inject)."
  ([kind] (adapter-command kind #(System/getenv %)))
  ([kind getenv]
   (when-let [env-name (:driver-env (get transports kind))]
     (config/adapter-driver-command env-name getenv))))

(defn split-command [command]
  (when-not (str/blank? (str command))
    (str/split (str/trim (str command)) #"\s+")))

(defn adapter-request [session endpoint-key action]
  (let [{:keys [kind] :as connect} (runtime/dial-connect-spec session endpoint-key)
        spec (get transports kind)]
    {:type "murakumo.overlay.adapter-request"
     :version 1
     :action action
     :transport kind
     :status (:status spec)
     :connect connect
     :session (select-keys session [:type :overlay :node :name :principal :direct :relay])}))

(defn adapter-plan
  ([session endpoint-key action]
   (let [request (adapter-request session endpoint-key action)
         kind (:transport request)
         command (adapter-command kind)]
     (adapter-plan session endpoint-key action command)))
  ([session endpoint-key action command]
   (let [request (adapter-request session endpoint-key action)
         kind (:transport request)
         argv (when-let [cmd (split-command command)]
                (vec (concat cmd [(name action) "--request-edn" (pr-str request)])))]
     {:type "murakumo.overlay.adapter-plan"
      :transport kind
      :command command
      :argv argv
      :ready? (boolean argv)
      :request request})))

(defn adapter-supervisor-plan
  ([session endpoint-key] (adapter-supervisor-plan session endpoint-key {}))
  ([session endpoint-key {:keys [action restart max-restarts command]
                          :or {action :serve restart :always max-restarts 3}}]
   (let [plan (if command
                (adapter-plan session endpoint-key action command)
                (adapter-plan session endpoint-key action))]
     {:type "murakumo.overlay.adapter-supervisor"
      :ok? (:ready? plan)
      :transport (:transport plan)
      :restart restart
      :max-restarts max-restarts
      :plan plan})))

(defn- slurp-stream [stream]
  (with-open [out (ByteArrayOutputStream.)]
    (.transferTo stream out)
    (.toString out "UTF-8")))

(defn run-adapter!
  "Run one external adapter command with a structured EDN request.
   The command receives: <action> --request-edn '<request>'."
  ([plan] (run-adapter! plan default-adapter-timeout-ms))
  ([plan timeout-ms]
   (if-not (:argv plan)
     {:ok? false
      :type "murakumo.overlay.adapter-result"
      :mode :adapter-missing
      :reason :adapter-not-configured
      :transport (:transport plan)}
     (let [process (.start (ProcessBuilder. ^java.util.List (:argv plan)))
           finished? (.waitFor process timeout-ms TimeUnit/MILLISECONDS)]
       (if-not finished?
         (do
           (.destroyForcibly process)
           {:ok? false
            :type "murakumo.overlay.adapter-result"
            :mode :timeout
            :reason :adapter-timeout
            :transport (:transport plan)
            :timeout-ms timeout-ms})
         (let [exit (.exitValue process)]
           {:ok? (zero? exit)
            :type "murakumo.overlay.adapter-result"
            :mode :exited
            :transport (:transport plan)
            :exit exit
            :out (str/trim (slurp-stream (.getInputStream process)))
            :err (str/trim (slurp-stream (.getErrorStream process)))
            :argv (:argv plan)}))))))

(defn adapter-check!
  ([session endpoint-key] (adapter-check! session endpoint-key nil))
  ([session endpoint-key command]
   (let [plan (if command
                (adapter-plan session endpoint-key :check command)
                (adapter-plan session endpoint-key :check))]
     (assoc (run-adapter! plan)
            :plan (select-keys plan [:type :transport :ready? :request])))))

(defn probe-socket! [{:keys [host port timeout-ms] :or {timeout-ms default-timeout-ms}}]
  (if (or (str/blank? (str host)) (nil? port))
    {:ok? false
     :mode :invalid-endpoint
     :reason :invalid-endpoint}
    (with-open [socket (Socket.)]
      (try
        (.connect socket (InetSocketAddress. host port) timeout-ms)
        {:ok? true
         :mode :connected
         :remote-address (str (.getRemoteSocketAddress socket))}
        (catch SocketTimeoutException _
          {:ok? false
           :mode :connect-failed
           :reason :timeout})
        (catch Exception e
          {:ok? false
           :mode :connect-failed
           :reason :connect-error
           :message (.getMessage e)})))))

(defn direct-probe!
  "Probe a direct QUIC/WebRTC/WebTransport endpoint boundary.
   Actual QUIC/WebRTC framing is delegated to an external adapter command until a
   JVM/babashka-safe implementation is linked."
  [session endpoint-key]
  (let [{:keys [kind] :as connect} (runtime/dial-connect-spec session endpoint-key)
        spec (get transports kind)
        adapter (adapter-command kind)
        socket-result (probe-socket! connect)]
    (merge {:type "murakumo.overlay.transport-probe"
            :transport kind
            :status (:status spec)
            :adapter adapter
            :connect connect
            :adapter-ready? (boolean adapter)}
           socket-result)))
