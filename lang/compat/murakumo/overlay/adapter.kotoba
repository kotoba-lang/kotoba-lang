;; murakumo.overlay.adapter — reference external transport adapter driver.

(ns murakumo.overlay.adapter
  (:require [kotoba.lang.edn :as edn]
            [kotoba.lang.text :as str]
            [murakumo.overlay.transport :as transport])
  (:import [java.net InetAddress InetSocketAddress ServerSocket Socket SocketTimeoutException]))

(def default-timeout-ms 1000)
(def default-accept-timeout-ms 10000)

(defn parse-argv [args]
  (let [[action & flags] args]
    (loop [opts {:action (keyword action)}
           flags flags]
      (if (empty? flags)
        opts
        (let [[flag value & more] flags]
          (cond
            (= "--request-edn" flag)
            (recur (assoc opts :request (edn/read-string value)) more)

            (= "--timeout-ms" flag)
            (recur (assoc opts :timeout-ms (Long/parseLong (str value))) more)

            :else
            (recur (update opts :extra (fnil conj []) flag) (cons value more))))))))

(defn valid-request? [request]
  (boolean
   (and (map? request)
        (= "murakumo.overlay.adapter-request" (:type request))
        (= 1 (:version request))
        (:transport request)
        (:connect request))))

(defn result [request action mode ok? extra]
  (merge {:ok? (boolean ok?)
          :type "murakumo.overlay.adapter-driver-result"
          :mode mode
          :action action
          :transport (:transport request)
          :connect (:connect request)}
         extra))

(defn missing-request-result [action]
  {:ok? false
   :type "murakumo.overlay.adapter-driver-result"
   :mode :invalid-request
   :action action
   :reason :missing-or-invalid-request})

(defn socket-reader [socket]
  (java.io.BufferedReader.
   (java.io.InputStreamReader. (.getInputStream socket) "UTF-8")))

(defn socket-writer [socket]
  (java.io.OutputStreamWriter. (.getOutputStream socket) "UTF-8"))

(defn read-edn-line [reader]
  (try
    (when-let [line (.readLine reader)]
      (edn/read-string line))
    (catch SocketTimeoutException _ nil)))

(defn write-line! [writer value]
  (.write writer (str value "\n"))
  (.flush writer))

(defn check! [request timeout-ms]
  (let [probe (transport/probe-socket! (assoc (:connect request)
                                             :timeout-ms timeout-ms))]
    (result request :check (:mode probe) (:ok? probe)
            (dissoc probe :mode :ok?))))

(defn dial! [request timeout-ms]
  (let [{:keys [host port] :as connect} (:connect request)]
    (if (or (str/blank? (str host)) (nil? port))
      (result request :dial :invalid-endpoint false {:reason :invalid-endpoint})
      (with-open [socket (Socket.)]
        (try
          (.connect socket (InetSocketAddress. host port) timeout-ms)
          (.setSoTimeout socket timeout-ms)
          (let [reader (socket-reader socket)
                writer (socket-writer socket)
                hello {:type "murakumo.overlay.adapter-hello"
                       :transport (:transport request)
                       :overlay (get-in request [:session :overlay])
                       :node (get-in request [:session :node])
                       :name (get-in request [:session :name])
                       :target (:path connect)}]
            (write-line! writer (pr-str hello))
            (let [ack (read-edn-line reader)]
              (result request :dial :adapter-stream
                      (= "murakumo.overlay.adapter-ack" (:type ack))
                      {:hello hello
                       :ack ack})))
          (catch SocketTimeoutException _
            (result request :dial :connect-failed false {:reason :timeout}))
          (catch Exception e
            (result request :dial :connect-failed false
                    {:reason :connect-error
                     :message (.getMessage e)})))))))

(defn listen-spec [request]
  (let [{:keys [host port]} (:connect request)]
    {:bind-host (if (str/blank? (str host)) "127.0.0.1" host)
     :port (or port 0)}))

(defn open-listener! [request]
  (let [{:keys [bind-host port] :as spec} (listen-spec request)
        server (ServerSocket. port 50 (InetAddress/getByName bind-host))]
    {:ok? true
     :type "murakumo.overlay.adapter-listener"
     :mode :listening
     :transport (:transport request)
     :listen (assoc spec :bound-port (.getLocalPort server))
     :server server}))

(defn close-listener! [listener]
  (when-let [server (:server listener)]
    (.close server))
  (assoc (dissoc listener :server) :mode :closed))

(defn handle-connection! [request socket]
  (.setSoTimeout socket default-timeout-ms)
  (let [reader (socket-reader socket)
        writer (socket-writer socket)
        hello (read-edn-line reader)
        ack {:type "murakumo.overlay.adapter-ack"
             :transport (:transport request)
             :accepted? (= "murakumo.overlay.adapter-hello" (:type hello))
             :hello hello}]
    (write-line! writer (pr-str ack))
    ack))

(defn serve-once! [request]
  (let [{:keys [server] :as listener} (open-listener! request)]
    (try
      (println (pr-str (dissoc listener :server)))
      (flush)
      (.setSoTimeout server default-accept-timeout-ms)
      (with-open [socket (.accept server)]
        (let [ack (handle-connection! request socket)]
          (result request :serve-once :served (:accepted? ack)
                  {:listener (dissoc listener :server)
                   :ack ack})))
      (catch SocketTimeoutException _
        (result request :serve-once :accept-timeout false
                {:listener (dissoc listener :server)
                 :reason :timeout}))
      (finally
        (close-listener! listener)))))

(defn serve! [request]
  (let [{:keys [server] :as listener} (open-listener! request)]
    (println (pr-str (dissoc listener :server)))
    (flush)
    (.setSoTimeout server 1000)
    (loop []
      (try
        (with-open [socket (.accept server)]
          (handle-connection! request socket))
        (catch SocketTimeoutException _ nil))
      (when-not (.isClosed server)
        (recur)))))

(defn execute [opts]
  (let [{:keys [action request timeout-ms]} opts
        timeout-ms (or timeout-ms default-timeout-ms)]
    (if-not (valid-request? request)
      (missing-request-result action)
      (case action
        :check (check! request timeout-ms)
        :dial (dial! request timeout-ms)
        :serve-once (serve-once! request)
        :serve (serve! request)
        (result request action :unknown-action false {:reason :unknown-action})))))

(defn -main [& args]
  (let [result (execute (parse-argv args))]
    (when result
      (println (pr-str result)))
    (when (false? (:ok? result))
      (System/exit 2))))
