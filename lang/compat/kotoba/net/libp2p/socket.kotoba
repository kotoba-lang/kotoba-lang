(ns kotoba.net.libp2p.socket
  "The socket the rest of the stack deliberately does not own.

  Mechanism only: open a TCP connection, read exactly n octets, write octets,
  close. No framing, no protocol, no decisions -- everything that decides
  anything lives in `connection` and the pure layers below it, and this file
  exists so those can be tested against an in-memory pair instead of a network.

  `read-exactly` is the whole of the care here. A stream read returns *up to* n
  octets, and every layer above frames by exact counts; a driver that treated a
  short read as the whole frame would desynchronize on the first packet
  boundary that fell mid-header, which is intermittent and looks like a peer
  bug."
  (:require [multiformats.multiaddr :as multiaddr])
  (:import [java.io InputStream OutputStream]
           [java.net InetSocketAddress Socket]))

(def default-connect-timeout-ms 15000)
(def default-read-timeout-ms 30000)

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn dial-address
  "The host and port a multiaddr names, for the transports this can speak.

  Returns nil for anything else -- QUIC, WebTransport, a relay circuit -- so a
  caller trying several of a peer's addresses skips what it cannot use rather
  than guessing."
  [address]
  (let [components (multiaddr/components address)
        by-name (into {} components)
        host (or (get by-name "ip4") (get by-name "ip6")
                 (get by-name "dns") (get by-name "dns4") (get by-name "dns6"))
        port (get by-name "tcp")
        names (set (map first components))]
    (when (and host port
               (not (contains? names "quic"))
               (not (contains? names "quic-v1"))
               (not (contains? names "p2p-circuit"))
               (not (contains? names "ws"))
               (not (contains? names "wss")))
      {:host host :port (Integer/parseInt (str port))
       :peer-id (multiaddr/peer-id address)})))

(defn connect!
  "Open a TCP connection and return `{:port :close!}`.

  `:port` is the `{:read! :write!}` pair every layer above is written against."
  ([host port] (connect! host port {}))
  ([host port {:keys [connect-timeout-ms read-timeout-ms]
               :or {connect-timeout-ms default-connect-timeout-ms
                    read-timeout-ms default-read-timeout-ms}}]
   (let [socket (Socket.)]
     (.connect socket (InetSocketAddress. ^String host ^int (int port)) (int connect-timeout-ms))
     (.setSoTimeout socket (int read-timeout-ms))
     (.setTcpNoDelay socket true)
     (let [in (.getInputStream socket)
           out (.getOutputStream socket)]
       {:socket socket
        :close! (fn [] (try (.close socket) (catch Exception _ nil)))
        :port
        {:read!
         (fn [n]
           (let [buffer (byte-array n)]
             (loop [read 0]
               (if (= read n)
                 (vec (map #(bit-and % 0xff) buffer))
                 (let [got (.read ^InputStream in buffer read (- n read))]
                   (when (neg? got)
                     (fail! :socket/closed-early {:wanted n :got read}))
                   (recur (+ read got)))))))
         :write!
         (fn [octets]
           (.write ^OutputStream out (byte-array (map unchecked-byte octets)))
           (.flush ^OutputStream out))}}))))

(defn wrap
  "Wrap an already-accepted socket in the same `{:read! :write!}` port a dialed
  connection uses. A listener and a dialer differ in who connected, not in how
  bytes move."
  ([socket] (wrap socket default-read-timeout-ms))
  ([^Socket socket read-timeout-ms]
   (.setSoTimeout socket (int read-timeout-ms))
   (.setTcpNoDelay socket true)
   (let [in (.getInputStream socket)
         out (.getOutputStream socket)]
     {:socket socket
      :close! (fn [] (try (.close socket) (catch Exception _ nil)))
      :port {:read! (fn [n]
                      (let [buffer (byte-array n)]
                        (loop [read 0]
                          (if (= read n)
                            (vec (map #(bit-and % 0xff) buffer))
                            (let [got (.read ^InputStream in buffer read (- n read))]
                              (when (neg? got)
                                (fail! :socket/closed-early {:wanted n :got read}))
                              (recur (+ read got)))))))
             :write! (fn [octets]
                       (.write ^OutputStream out (byte-array (map unchecked-byte octets)))
                       (.flush ^OutputStream out))}})))

(defn pair
  "Two ports wired to each other in memory, for testing a dialer against a
  listener without a network."
  []
  (let [a->b (java.util.concurrent.LinkedBlockingQueue.)
        b->a (java.util.concurrent.LinkedBlockingQueue.)
        make (fn [in out]
               {:read! (fn [n] (vec (repeatedly n #(.take ^java.util.concurrent.LinkedBlockingQueue in))))
                :write! (fn [octets] (doseq [o octets]
                                       (.put ^java.util.concurrent.LinkedBlockingQueue out
                                             (bit-and (int o) 0xff))))})]
    {:a (make b->a a->b) :b (make a->b b->a)}))
