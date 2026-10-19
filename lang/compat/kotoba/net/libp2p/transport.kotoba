(ns kotoba.net.libp2p.transport
  "Bounded host registry for native libp2p transport backends.

  QUIC, WebTransport, and WebRTC already own security and stream
  multiplexing; they must return a connection, not be disguised as the raw
  byte port consumed by TCP/Noise/Yamux. Backends are injected by the host and
  must attest the authenticated remote PeerId."
  (:require [clojure.string :as str]))

(def kinds #{:tcp :quic-v1 :webtransport :webrtc-direct :webrtc :relay})

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn- tokens [address]
  (->> (str/split (str address) #"/") (remove str/blank?) vec))

(defn- token? [parts token]
  (boolean (some #{token} parts)))

(defn kind
  "Return the most-specific transport kind named by a multiaddr string."
  [address]
  (let [parts (tokens address)]
    (cond
      (token? parts "webtransport") :webtransport
      (token? parts "webrtc-direct") :webrtc-direct
      (token? parts "webrtc") :webrtc
      (token? parts "p2p-circuit") :relay
      (token? parts "quic-v1") :quic-v1
      (token? parts "tcp") :tcp
      :else nil)))

(defn target-peer-id
  "The final `/p2p/<id>` component is the destination; earlier ones may name
  relay hops."
  [address]
  (let [parts (tokens address)]
    (last (keep-indexed (fn [i part]
                          (when (and (= "p2p" part) (< (inc i) (count parts)))
                            (nth parts (inc i))))
                        parts))))

(defn registry
  "Create a bounded transport registry.

  Each backend is `(fn [address options] connection)`. A connection must have
  `:peer-id`, `:authenticated? true`, `:open-stream!`, and `:close!`."
  [max-candidates backends]
  (when-not (and (integer? max-candidates) (pos? max-candidates))
    (fail! :transport/invalid-max-candidates {:value max-candidates}))
  (doseq [[kind backend] backends]
    (when-not (and (contains? kinds kind) (fn? backend))
      (fail! :transport/invalid-backend {:kind kind})))
  {:max-candidates max-candidates :backends backends})

(defn- verified-connection [address connection]
  (let [expected (target-peer-id address)]
    (when-not (and (map? connection)
                   (true? (:authenticated? connection))
                   (some? (:peer-id connection))
                   (fn? (:open-stream! connection))
                   (fn? (:close! connection)))
      (when-let [close! (:close! connection)] (close!))
      (fail! :transport/unverified-connection {:address address}))
    (when (and expected (not= expected (:peer-id connection)))
      ((:close! connection))
      (fail! :transport/peer-id-mismatch
             {:address address :expected expected :actual (:peer-id connection)}))
    (assoc connection :address address :transport (kind address))))

(defn dial!
  "Try at most the registry's candidate budget in caller preference order.

  Unsupported addresses are skipped. Backend failures are retained in the
  final exception without making a weaker transport authoritative."
  ([registry addresses] (dial! registry addresses {}))
  ([registry addresses options]
   (loop [remaining (seq addresses) attempts 0 failures []]
     (when (>= attempts (:max-candidates registry))
       (fail! :transport/candidate-limit
              {:attempts attempts :failures failures}))
     (if-let [address (first remaining)]
       (let [transport (kind address)
             backend (get-in registry [:backends transport])]
         (if-not backend
           (recur (next remaining) attempts failures)
           (let [attempt
                 (try
                   {:connection (verified-connection address
                                                     (backend address options))}
                   (catch Exception error
                     {:failure {:address address
                                :transport transport
                                :problem (or (:problem (ex-data error))
                                             :transport/backend-failed)}}))]
             (if-let [connection (:connection attempt)]
               connection
               (recur (next remaining) (inc attempts)
                      (conj failures (:failure attempt)))))))
       (fail! :transport/no-reachable-address
              {:attempts attempts :failures failures})))))
