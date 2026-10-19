(ns kotoba.net.libp2p.dcutr
  "Direct Connection Upgrade through Relay (`/libp2p/dcutr`)."
  (:require [protobuf.wire :as pb]))

(def protocol "/libp2p/dcutr")
(def max-message-size 4096)
(def max-connect-attempts 3)
(def max-observed-addrs 32)
(def schema {1 {:name :type :type :enum}
             2 {:name :observed-addrs :type :bytes :repeated true}})
(def type->code {:connect 100 :sync 300})
(def code->type {100 :connect 300 :sync})

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn encode-message [{:keys [type observed-addrs]}]
  (when-not (contains? type->code type)
    (fail! :dcutr/unknown-message-type {:type type}))
  (let [addrs (mapv vec (or observed-addrs []))]
    (when (or (> (count addrs) max-observed-addrs)
              (and (= :connect type) (empty? addrs))
              (and (= :sync type) (seq addrs)))
      (fail! :dcutr/invalid-observed-addrs {:type type :count (count addrs)}))
    (let [wire (pb/encode schema {:type (type->code type)
                                  :observed-addrs addrs})]
      (when (> (count wire) max-message-size)
        (fail! :dcutr/message-too-large {:size (count wire)}))
      wire)))

(defn decode-message [octets]
  (when (> (count octets) max-message-size)
    (fail! :dcutr/message-too-large {:size (count octets)}))
  (let [wire (pb/decode schema octets)
        type (code->type (:type wire))
        message {:type type
                 :observed-addrs (mapv vec (or (:observed-addrs wire) []))}]
    (when-not type (fail! :dcutr/unknown-message-type {:code (:type wire)}))
    (encode-message message)
    message))

(defn- varint [n]
  (loop [n n out []]
    (if (< n 128) (conj out n)
        (recur (quot n 128) (conj out (bit-or 0x80 (bit-and n 0x7f)))))))

(defn- read-length [port]
  (loop [shift 0 acc 0 seen 0]
    (when (>= seen 4) (fail! :dcutr/varint-too-long {}))
    (let [b (bit-and (first ((:read! port) 1)) 0xff)
          acc (bit-or acc (bit-shift-left (bit-and b 0x7f) shift))]
      (if (zero? (bit-and b 0x80)) acc
          (recur (+ shift 7) acc (inc seen))))))

(defn write-message! [port message]
  (let [wire (encode-message message)]
    ((:write! port) (into (varint (count wire)) wire))))

(defn read-message! [port]
  (let [length (read-length port)]
    (when (> length max-message-size)
      (fail! :dcutr/message-too-large {:size length}))
    (decode-message ((:read! port) length))))

(defn- dial-direct! [dial-fn expected-peer-id addrs]
  (loop [remaining (seq (take max-connect-attempts addrs)) failures []]
    (if-let [address (first remaining)]
      (let [attempt
            (try
              (let [connection (dial-fn address)]
                (when-not (and (true? (:authenticated? connection))
                               (= expected-peer-id (:peer-id connection)))
                  (when-let [close! (:close! connection)] (close!))
                  (fail! :dcutr/peer-id-mismatch
                         {:expected expected-peer-id :actual (:peer-id connection)}))
                {:connection connection})
              (catch Exception error
                {:failure {:address address
                           :problem (or (:problem (ex-data error))
                                        :dcutr/dial-failed)}}))]
        (if-let [connection (:connection attempt)]
          connection
          (recur (next remaining) (conj failures (:failure attempt)))))
      (fail! :dcutr/direct-connect-failed {:failures failures}))))

(defn initiate!
  "Run CONNECT/CONNECT/SYNC over an authenticated relay stream, then dial."
  [port own-observed-addrs expected-peer-id accept-addr? dial-fn]
  (write-message! port {:type :connect :observed-addrs own-observed-addrs})
  (let [remote (read-message! port)]
    (when-not (= :connect (:type remote))
      (fail! :dcutr/expected-connect {:got (:type remote)}))
    (let [accepted (filterv accept-addr? (:observed-addrs remote))]
      (when (empty? accepted) (fail! :dcutr/no-admitted-addresses {}))
      (write-message! port {:type :sync})
      (dial-direct! dial-fn expected-peer-id accepted))))

(defn respond!
  "Reply CONNECT, wait for SYNC, then dial the authenticated relay peer."
  [port own-observed-addrs expected-peer-id accept-addr? dial-fn]
  (let [remote (read-message! port)]
    (when-not (= :connect (:type remote))
      (fail! :dcutr/expected-connect {:got (:type remote)}))
    (write-message! port {:type :connect :observed-addrs own-observed-addrs})
    (let [sync (read-message! port)]
      (when-not (= :sync (:type sync))
        (fail! :dcutr/expected-sync {:got (:type sync)}))
      (let [accepted (filterv accept-addr? (:observed-addrs remote))]
        (when (empty? accepted) (fail! :dcutr/no-admitted-addresses {}))
        (dial-direct! dial-fn expected-peer-id accepted)))))
