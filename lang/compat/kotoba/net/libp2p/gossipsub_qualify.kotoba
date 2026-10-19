(ns kotoba.net.libp2p.gossipsub-qualify
  "Multi-machine GossipSub qualification process.

  Configuration is one EDN map argument.  It listens, maintains outbound
  streams to every configured peer address, heartbeats once per second, and
  optionally publishes uniquely named payloads on a schedule.  Every material
  transition is emitted as one EDN line for an orchestrator to sign/archive."
  (:require [clojure.edn :as edn]
            [ed25519.core :as ed]
            [kotoba.net.libp2p.gossipsub-host :as host]
            [kotoba.net.libp2p.handshake :as handshake]
            [kotoba.net.libp2p.keys :as keys]
            [kotoba.net.libp2p.node :as node]
            [kotoba.net.libp2p.pubsub :as pubsub]
            [multiformats.core :as mf])
  (:import [java.security SecureRandom MessageDigest]))

(defn- qualification-identity []
  (let [seed (byte-array 32) _ (.nextBytes (SecureRandom.) seed)
        public-key (ed/pubkey-from-seed seed)]
    {:identity-public-key public-key
     :sign-fn #(ed/sign seed (byte-array (map unchecked-byte %)))
     :verify-fn (keys/verifier)
     ;; GossipSub streams are intentionally long-lived. Socket close and the
     ;; qualification duration bound their lifetime, not idle read time.
     :read-timeout-ms 0
     :peer-id (handshake/peer-id mf/sha256 (handshake/public-key-protobuf public-key))}))

(defn- digest [data]
  (apply str (map #(format "%02x" (bit-and % 0xff))
                  (.digest (doto (MessageDigest/getInstance "SHA-256")
                             (.update (byte-array (map unchecked-byte data))))))))

(defn- emit! [event]
  (locking *out* (prn (assoc event :at-ms (System/currentTimeMillis))) (flush)))

(defn qualify! [{:keys [node-id host port peers topic publish duration-ms]
             :or {host "0.0.0.0" duration-ms 30000}}]
  (let [id (qualification-identity)
        deliveries (atom [])
        subscriptions (atom #{})
        router (host/host
                {:on-deliver (fn [effect]
                               (let [event {:event :delivered :node node-id
                                            :sha256 (digest (get-in effect [:message :data]))
                                            :from (mf/base58btc (:peer effect))}]
                                 (swap! deliveries conj event) (emit! event)))
                 :on-peer-subscribed (fn [effect]
                                       (swap! subscriptions conj (:peer effect))
                                       (emit! {:event :peer-subscribed :node node-id
                                               :peer (mf/base58btc (:peer effect))}))})
        n (node/node (assoc id :protocol-handlers
                            {pubsub/gossipsub-v1-1 (host/protocol-handler router)}))
        listener (node/listen! n {:host host :port port :read-timeout-ms 0})
        start (System/currentTimeMillis)]
    (try
      (emit! {:event :ready :node node-id :port (:port listener)
              :peer-id (mf/base58btc (:peer-id id))})
      (doseq [address peers]
        (try (host/add-target! router address id {})
             (catch Exception e (emit! {:event :connect-failed :node node-id
                                        :address address :error (.getMessage e)}))))
      (host/subscribe! router topic)
      (loop [published #{}]
        (let [elapsed (- (System/currentTimeMillis) start)
              due (remove #(contains? published (:id %))
                          (filter #(<= (:after-ms %) elapsed) publish))]
          (doseq [{:keys [id payload]} due]
            (let [result (host/publish! router topic (.getBytes ^String payload "UTF-8"))]
              (emit! {:event :published :node node-id :id id
                      :sha256 (digest (.getBytes ^String payload "UTF-8"))
                      :sent (count (filter #(= :send (:op %)) (:effects result)))})))
          (host/maintain! router)
          (host/heartbeat! router)
          (if (< elapsed duration-ms)
            (do (Thread/sleep 1000) (recur (into published (map :id due))))
            (let [receipt {:event :complete :node node-id
                           :deliveries (count @deliveries)
                           :unique-deliveries (count (set (map :sha256 @deliveries)))
                           :subscriptions (count @subscriptions)}]
              (emit! receipt) receipt))))
      (finally (host/close! router) ((:stop! listener))))))

(defn -main [& [config]]
  (when-not config (throw (ex-info "usage: '<edn-config>'" {})))
  (try
    (qualify! (edn/read-string config))
    (finally
      ;; Clojure's future executor otherwise keeps a completed qualification
      ;; process alive after every socket has closed.
      (shutdown-agents))))
