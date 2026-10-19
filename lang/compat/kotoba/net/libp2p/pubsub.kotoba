(ns kotoba.net.libp2p.pubsub
  "The interoperable protobuf envelope used by libp2p PubSub/GossipSub.

  This namespace owns the wire contract, not the mesh algorithm.  IPNS records
  use StrictNoSign: their own IPNS signature is authoritative, while PubSub's
  optional `from`, `seqno`, `signature`, and `key` fields must be absent."
  (:require [protobuf.wire :as pb]))

(def gossipsub-v1-1 "/meshsub/1.1.0")
(def max-rpc-size (* 1024 1024))

(def subscription-schema
  {1 {:name :subscribe? :type :bool}
   2 {:name :topic :type :string}})

(def message-schema
  {1 {:name :from :type :bytes}
   2 {:name :data :type :bytes}
   3 {:name :seqno :type :bytes}
   4 {:name :topic :type :string}
   5 {:name :signature :type :bytes}
   6 {:name :key :type :bytes}})

(def peer-info-schema
  {1 {:name :peer-id :type :bytes}
   2 {:name :signed-peer-record :type :bytes}})

(def ihave-schema
  {1 {:name :topic :type :string}
   ;; Implementations use arbitrary message-id bytes here.  The historical Go
   ;; proto says string, but both are wire type 2 and bytes avoids invalid UTF-8
   ;; coercion explicitly called out in its current schema.
   2 {:name :message-ids :type :bytes :repeated true}})

(def iwant-schema
  {1 {:name :message-ids :type :bytes :repeated true}})

(def graft-schema
  {1 {:name :topic :type :string}})

(def prune-schema
  {1 {:name :topic :type :string}
   2 {:name :peers :type :message :schema peer-info-schema :repeated true}
   3 {:name :backoff-seconds :type :uint64}})

(def idontwant-schema
  {1 {:name :message-ids :type :bytes :repeated true}})

(def control-schema
  {1 {:name :ihave :type :message :schema ihave-schema :repeated true}
   2 {:name :iwant :type :message :schema iwant-schema :repeated true}
   3 {:name :graft :type :message :schema graft-schema :repeated true}
   4 {:name :prune :type :message :schema prune-schema :repeated true}
   5 {:name :idontwant :type :message :schema idontwant-schema :repeated true}})

(def rpc-schema
  {1 {:name :subscriptions :type :message :schema subscription-schema :repeated true}
   2 {:name :messages :type :message :schema message-schema :repeated true}
   3 {:name :control :type :message :schema control-schema}})

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn subscription [topic subscribe?]
  {:topic topic :subscribe? (boolean subscribe?)})

(defn strict-no-sign-message [topic data]
  {:topic topic :data (vec data)})

(defn strict-no-sign? [message]
  (and (string? (:topic message))
       (some? (:data message))
       (every? #(nil? (get message %)) [:from :seqno :signature :key])))

(defn- normalize-message-ids [items]
  (mapv (fn [item] (update item :message-ids #(mapv vec (or % [])))) (or items [])))

(defn- normalize-control [control]
  (when control
    {:ihave (normalize-message-ids (:ihave control))
     :iwant (normalize-message-ids (:iwant control))
     :graft (vec (or (:graft control) []))
     :prune (mapv (fn [prune]
                    (update prune :peers
                            #(mapv (fn [peer]
                                     (cond-> (update peer :peer-id vec)
                                       (:signed-peer-record peer)
                                       (update :signed-peer-record vec)))
                                   (or % []))))
                  (or (:prune control) []))
     :idontwant (normalize-message-ids (:idontwant control))}))

(defn encode-rpc [{:keys [subscriptions messages control]}]
  (when-not (every? strict-no-sign? (or messages []))
    (fail! :pubsub/not-strict-no-sign {}))
  (let [wire (pb/encode rpc-schema
                        {:subscriptions (vec (or subscriptions []))
                         :messages (vec (or messages []))
                         :control (normalize-control control)})]
    (when (> (count wire) max-rpc-size)
      (fail! :pubsub/rpc-too-large {:size (count wire) :limit max-rpc-size}))
    wire))

(defn decode-rpc [octets]
  (when (> (count octets) max-rpc-size)
    (fail! :pubsub/rpc-too-large {:size (count octets) :limit max-rpc-size}))
  (let [rpc (pb/decode rpc-schema octets)
        messages (mapv #(update % :data vec) (or (:messages rpc) []))]
    (when-not (every? strict-no-sign? messages)
      (fail! :pubsub/not-strict-no-sign {}))
    (cond-> {:subscriptions (vec (or (:subscriptions rpc) []))
             :messages messages}
      (:control rpc) (assoc :control (normalize-control (:control rpc))))))
