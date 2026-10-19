(ns kotoba.net.libp2p.fetch
  "The libp2p Fetch protocol (`/libp2p/fetch/0.0.1`).

  Fetch is deliberately small: one length-delimited protobuf request and one
  length-delimited protobuf response on a short-lived stream.  It is used by
  the IPNS PubSub Router to recover the best persistent record from a peer that
  has just subscribed; it is not a general block-transfer protocol."
  (:require [kotoba.net.libp2p.connection :as connection]
            [kotoba.net.libp2p.dial :as dial]
            [protobuf.wire :as pb]))

(def protocol "/libp2p/fetch/0.0.1")

(def request-schema
  {1 {:name :identifier :type :bytes}})

(def response-schema
  {1 {:name :status :type :enum}
   2 {:name :data :type :bytes}})

(def status-codes {:ok 0 :not-found 1 :error 2})
(def code->status (into {} (map (fn [[k v]] [v k]) status-codes)))

(def default-max-message-size (* 1024 1024))

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn- varint [n]
  (loop [n n out []]
    (if (< n 128)
      (conj out n)
      (recur (quot n 128) (conj out (bit-or 0x80 (bit-and n 0x7f)))))))

(defn- read-varint [port]
  (loop [shift 0 acc 0 seen 0]
    (when (>= seen 9) (fail! :fetch/varint-too-long {}))
    (let [octets ((:read! port) 1)]
      (when-not (= 1 (count octets)) (fail! :fetch/truncated-varint {}))
      (let [b (bit-and (first octets) 0xff)
            acc (bit-or acc (bit-shift-left (bit-and b 0x7f) shift))]
        (if (zero? (bit-and b 0x80))
          acc
          (recur (+ shift 7) acc (inc seen)))))))

(defn read-message
  ([port] (read-message port default-max-message-size))
  ([port max-message-size]
   (let [length (read-varint port)]
     (when (> length max-message-size)
       (fail! :fetch/message-too-large {:size length :limit max-message-size}))
     (vec ((:read! port) length)))))

(defn write-message [port octets]
  (let [octets (vec octets)]
    ((:write! port) (into (varint (count octets)) octets))))

(defn encode-request [identifier]
  (let [identifier (vec identifier)]
    (when (empty? identifier) (fail! :fetch/empty-identifier {}))
    (pb/encode request-schema {:identifier identifier})))

(defn decode-request [octets]
  (let [request (pb/decode request-schema octets)]
    (when (empty? (:identifier request))
      (fail! :fetch/empty-identifier {}))
    {:identifier (vec (:identifier request))}))

(defn encode-response [{:keys [status data]}]
  (let [code (get status-codes status)]
    (when-not (some? code) (fail! :fetch/unknown-status {:status status}))
    (pb/encode response-schema
               ;; Proto3 omits an enum's zero value. Accepting both forms and
               ;; emitting the omitted form matches generated implementations.
               (cond-> {}
                 (not= status :ok) (assoc :status code)
                 (= status :ok) (assoc :data (vec (or data [])))))))

(defn decode-response [octets]
  (let [wire (pb/decode response-schema octets)
        status (get code->status (or (:status wire) 0))]
    (when-not status (fail! :fetch/unknown-status {:status (:status wire)}))
    (cond-> {:status status}
      (= status :ok) (assoc :data (vec (or (:data wire) []))))))

(defn handler
  "Create a node protocol handler.

  LOOKUP receives `{:identifier … :peer-id …}` and returns response bytes or
  nil. Exceptions are represented by the protocol's ERROR status; their text
  is intentionally not disclosed to the remote peer."
  ([lookup] (handler lookup {}))
  ([lookup {:keys [max-message-size] :or {max-message-size default-max-message-size}}]
   (fn [{:keys [port peer-id]}]
     (try
       (let [request (decode-request (read-message port max-message-size))
             data (lookup (assoc request :peer-id (vec peer-id)))]
         (write-message port (encode-response (if (nil? data)
                                                {:status :not-found}
                                                {:status :ok :data data}))))
       (catch Exception _
         (write-message port (encode-response {:status :error})))
       (finally
         (when-let [close! (:close! port)] (close!)))))))

(defn request!
  "Fetch IDENTIFIER from ADDRESS over an authenticated libp2p connection."
  ([address identity identifier] (request! address identity identifier {}))
  ([address identity identifier {:keys [max-message-size]
                                 :or {max-message-size default-max-message-size}}]
   (let [conn (dial/dial! address identity)]
     (try
       (let [stream (connection/stream! (:secure conn) (:session conn) protocol)]
         (write-message stream (encode-request identifier))
         (decode-response (read-message stream max-message-size)))
       (finally ((:close! conn)))))))
