(ns kotoba.net.libp2p.gossipsub-probe
  "Operator probe for a reference GossipSub v1.1 peer."
  (:require [ed25519.core :as ed]
            [kotoba.net.libp2p.gossipsub-host :as host]
            [kotoba.net.libp2p.handshake :as handshake]
            [kotoba.net.libp2p.keys :as keys]
            [multiformats.core :as mf])
  (:import [java.security SecureRandom]))

(defn- probe-identity []
  (let [seed (byte-array 32)
        _ (.nextBytes (SecureRandom.) seed)
        public-key (ed/pubkey-from-seed seed)]
    {:identity-public-key public-key
     :sign-fn #(ed/sign seed (byte-array (map unchecked-byte %)))
     :verify-fn (keys/verifier)
     :peer-id (handshake/peer-id mf/sha256 (handshake/public-key-protobuf public-key))}))

(defn probe!
  [address topic payload]
  (let [subscription (promise)
        router (host/host {:on-peer-subscribed #(deliver subscription %)})
        peer (host/connect! router address (probe-identity) {})]
    (try
      (host/subscribe! router topic)
      (let [remote-subscription (deref subscription 3000 nil)]
        (host/heartbeat! router)
        (let [published (host/publish! router topic (.getBytes ^String payload "UTF-8"))]
          ;; A successful socket write is not yet a reference-peer delivery;
          ;; leave the long-lived stream up for the remote event loop to read.
          (Thread/sleep 500)
          {:peer-id peer
           :remote-subscribed? (= topic (:topic remote-subscription))
           :mesh-member? (contains? (get-in @(:router router) [:mesh topic]) peer)
           :message-id (:message-id published)
           :sent (count (filter #(= :send (:op %)) (:effects published)))}))
      (finally (host/close! router)))))

(defn -main [& [address topic payload]]
  (when-not (and address topic payload)
    (throw (ex-info "usage: <multiaddr> <topic> <payload>" {})))
  (prn (probe! address topic payload)))
