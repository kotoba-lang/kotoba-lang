(ns kotoba.net.libp2p.pubsub-stream
  "Long-lived unidirectional GossipSub streams.

  A peer owns one writer and one independently negotiated reader.  Each RPC is
  unsigned-varint length framed, as required by libp2p PubSub; the stream stays
  open across subscription, control, and publish RPCs."
  (:require [kotoba.net.libp2p.dial :as dial]
            [kotoba.net.libp2p.identify :as identify]
            [kotoba.net.libp2p.mux :as mux]
            [kotoba.net.libp2p.node :as node]
            [kotoba.net.libp2p.pubsub :as pubsub]
            [kotoba.net.libp2p.serve :as serve]))

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn- varint [n]
  (loop [n n out []]
    (if (< n 128) (conj out n)
        (recur (quot n 128) (conj out (bit-or 0x80 (bit-and n 0x7f)))))))

(defn- read-varint [port]
  (loop [shift 0 acc 0 seen 0]
    (when (>= seen 9) (fail! :pubsub/varint-too-long {}))
    (let [b (bit-and (first ((:read! port) 1)) 0xff)
          acc (bit-or acc (bit-shift-left (bit-and b 0x7f) shift))]
      (if (zero? (bit-and b 0x80)) acc
          (recur (+ shift 7) acc (inc seen))))))

(defn read-rpc [port]
  (let [length (read-varint port)]
    (when (> length pubsub/max-rpc-size)
      (fail! :pubsub/rpc-too-large {:size length :limit pubsub/max-rpc-size}))
    (pubsub/decode-rpc ((:read! port) length))))

(defn write-rpc [port rpc]
  (let [wire (pubsub/encode-rpc rpc)]
    ((:write! port) (into (varint (count wire)) wire))))

(defn handler
  "Create a node handler. ON-RPC receives the authenticated peer and every RPC
  in arrival order until the remote writer closes."
  [on-rpc]
  (fn [{:keys [port peer peer-id]}]
    (loop []
      (when (try
              (on-rpc {:peer peer :peer-id (vec peer-id) :rpc (read-rpc port)})
              true
              (catch Exception e
                (if (= :stream/closed-early (:problem (ex-data e)))
                  false
                  (throw e))))
        (recur)))))

(defn open-writer!
  "Open and retain one outbound GossipSub stream.  The returned writer is safe
  for serialized calls; callers own ordering and call `:close!` at shutdown."
  ([address identity] (open-writer! address identity {}))
  ([address identity {:keys [on-rpc] :or {on-rpc (fn [_] nil)}}]
  (let [conn (dial/dial! address identity)
        supported #{pubsub/gossipsub-v1-1 identify/protocol serve/ping-protocol}
        driver (mux/start!
                (:secure conn) (:session conn) supported
                (fn [protocol port]
                  (condp = protocol
                    identify/protocol
                    (do (let [wire (serve/identify-response
                                    (assoc identity :listen-addrs []
                                           :protocols supported))]
                          ((:write! port) (into (varint (count wire)) wire)))
                        ((:close! port)))

                    serve/ping-protocol
                    (loop []
                      (when (try
                              ((:write! port) ((:read! port) serve/ping-size))
                              true
                              (catch Exception _ false))
                        (recur)))

                    pubsub/gossipsub-v1-1
                    ((handler (fn [item]
                                (on-rpc (assoc item
                                               :peer (:peer conn)
                                               :peer-id (node/peer-id-of (:peer conn))))))
                     {:port port :peer (:peer conn)
                      :peer-id (node/peer-id-of (:peer conn))}))))
        stream (try
                 (mux/open-stream! driver pubsub/gossipsub-v1-1)
                 (catch Exception e (mux/stop! driver) ((:close! conn)) (throw e)))
        lock (Object.)]
    {:peer (:peer conn)
     :address address
     :open? (fn [] (not @(:connection-closed? driver)))
     :write! (fn [rpc] (locking lock (write-rpc stream rpc)))
     :close! (fn [] (mux/stop! driver) ((:close! conn)))})))
