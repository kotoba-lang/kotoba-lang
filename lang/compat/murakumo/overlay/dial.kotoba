;; murakumo.overlay.dial — host-side overlay dial reachability checks.

(ns murakumo.overlay.dial
  (:require [kotoba.lang.edn :as edn]
            [kotoba.lang.text :as str]
            [murakumo.identity :as identity]
            [murakumo.overlay.crypto :as crypto]
            [murakumo.overlay.runtime :as runtime])
  (:import [java.net InetSocketAddress Socket SocketTimeoutException]))

(def default-timeout-ms 1000)
(def default-relay-frame "murakumo overlay ping")

(defn frame-digest [session connect index payload]
  (identity/sha256-hex
   (pr-str {:overlay (:overlay session)
            :node (:node session)
            :name (:name session)
            :target (:path connect)
            :transport (:transport connect)
            :seq index
            :payload payload})))

(defn frame-mac [auth-key digest]
  (when auth-key
    (identity/sha256-hex (str auth-key ":" digest))))

(defn relay-hello [session connect]
  {:type "murakumo.overlay.relay-hello"
   :overlay (:overlay session)
   :node (:node session)
   :name (:name session)
   :principal (:principal session)
   :target (:path connect)
   :transport (:transport connect)})

(defn relay-frame [session connect index payload]
  (let [digest (frame-digest session connect index payload)]
    (cond-> {:type "murakumo.overlay.relay-frame"
             :overlay (:overlay session)
             :node (:node session)
             :name (:name session)
             :target (:path connect)
             :transport (:transport connect)
             :seq index
             :digest digest}
      (:auth-key session) (merge (crypto/seal (:auth-key session) payload)
                                 {:sealed? true
                                  :mac (frame-mac (:auth-key session) digest)})
      (not (:auth-key session)) (assoc :payload payload))))

(defn frame-list [{:keys [frame frames]}]
  (cond
    (seq frames) frames
    (sequential? frame) (vec frame)
    (string? frame) [frame]
    :else [default-relay-frame]))

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

(defn relay-handshake! [socket session connect frame-options]
  (let [reader (socket-reader socket)
        writer (socket-writer socket)
        frames (frame-list frame-options)]
    (write-line! writer (pr-str (relay-hello session connect)))
    (let [ack (read-edn-line reader)
          frame-acks (mapv (fn [[index frame]]
                             (write-line! writer (pr-str (relay-frame session connect index frame)))
                             (read-edn-line reader))
                           (map-indexed vector frames))]
      {:ack ack
       :frames frames
       :frame-acks frame-acks})))

(defn check!
  "Probe the socket boundary for a direct or relayed dial session."
  ([session] (check! session {}))
  ([session {:keys [endpoint timeout-ms frame frames]
             :or {timeout-ms default-timeout-ms}}]
   (let [{:keys [host port] :as connect} (runtime/dial-connect-spec session endpoint)]
     (if (or (nil? host) (nil? port))
       {:ok? false
        :type "murakumo.overlay.dial-check"
        :mode :connect-failed
        :reason :invalid-endpoint
        :connect connect}
       (let [address (InetSocketAddress. host port)]
         (with-open [socket (Socket.)]
           (try
             (.connect socket address timeout-ms)
             (let [connect (assoc connect
                                  :timeout-ms timeout-ms
                                  :remote-address (str (.getRemoteSocketAddress socket)))
                   ack (when (= :relay (:endpoint connect))
                         (relay-handshake! socket session connect
                                           {:frame frame
                                            :frames frames}))
                   relay-ack (:ack ack)
                   frame-acks (:frame-acks ack)]
               {:ok? (if relay-ack
                       (and (= "murakumo.overlay.relay-ack" (:type relay-ack))
                            (seq frame-acks)
                            (every? #(= "murakumo.overlay.relay-frame-ack" (:type %))
                                    frame-acks)
                            (every? :accepted? frame-acks)
                            (every? :digest-ok? frame-acks)
                            (every? :mac-ok? frame-acks)
                            (every? :open-ok? frame-acks))
                       true)
                :type "murakumo.overlay.dial-check"
                :mode (if relay-ack :relay-stream :connected)
                :connect connect
                :relay-ack relay-ack
                :relay-frame-acks frame-acks
                :relay-frame-ack (last frame-acks)})
             (catch SocketTimeoutException _
               {:ok? false
                :type "murakumo.overlay.dial-check"
                :mode :connect-failed
                :reason :timeout
                :connect (assoc connect :timeout-ms timeout-ms)})
             (catch Exception e
               {:ok? false
                :type "murakumo.overlay.dial-check"
                :mode :connect-failed
                :reason :connect-error
                :message (.getMessage e)
                :connect (assoc connect :timeout-ms timeout-ms)}))))))))
