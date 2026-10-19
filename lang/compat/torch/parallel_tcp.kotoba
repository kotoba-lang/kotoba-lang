(ns torch.parallel-tcp
  "Bounded length-prefixed activation transport for tailnet/LAN ranks."
  (:require [clojure.edn :as edn])
  (:import [java.io DataInputStream DataOutputStream]
           [java.net InetAddress ServerSocket Socket]
           [java.nio.charset StandardCharsets]))

(def max-envelope-bytes (* 64 1024 1024))

(defn- valid-envelope? [x]
  (and (= :kotoba.tensor/v1 (:protocol x))
       (nat-int? (:source x)) (nat-int? (:destination x))
       (nat-int? (:microbatch x)) (vector? (:payload x))
       (every? number? (:payload x))))

(defn write-envelope! [stream envelope]
  (when-not (valid-envelope? envelope)
    (throw (ex-info "invalid activation envelope" {:envelope envelope})))
  (let [bytes (.getBytes (pr-str envelope) StandardCharsets/UTF_8)]
    (when (> (alength bytes) max-envelope-bytes)
      (throw (ex-info "activation envelope exceeds wire limit"
                      {:bytes (alength bytes) :limit max-envelope-bytes})))
    (doto (DataOutputStream. stream)
      (.writeInt (alength bytes)) (.write bytes) (.flush))))

(defn read-envelope! [stream]
  (let [in (DataInputStream. stream)
        length (.readInt in)]
    (when-not (<= 1 length max-envelope-bytes)
      (throw (ex-info "invalid activation envelope length" {:bytes length})))
    (let [bytes (byte-array length)]
      (.readFully in bytes)
      (let [envelope (edn/read-string (String. bytes StandardCharsets/UTF_8))]
        (when-not (valid-envelope? envelope)
          (throw (ex-info "invalid activation envelope" {})))
        envelope))))

(defn server-socket
  ([port] (server-socket "127.0.0.1" port))
  ([host port]
   (ServerSocket. port 16 (InetAddress/getByName host))))

(defn receive-once! [^ServerSocket server]
  (with-open [socket (.accept server)]
    (read-envelope! (.getInputStream socket))))

(defn send! [host port envelope]
  (with-open [socket (Socket. host port)]
    (.setTcpNoDelay socket true)
    (write-envelope! (.getOutputStream socket) envelope))
  true)
