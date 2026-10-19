(ns kotoba.net.ipns-router
  "Host bridge from `ipns.pubsub` effects to Kotoba's existing gossip plane.

  This makes IPNS record propagation usable by the current transport-neutral
  host while keeping the interoperability boundary explicit: these envelopes
  are not the libp2p GossipSub protobuf or libp2p Fetch wire protocol. A
  standards-wire adapter can execute the same `ipns.pubsub` effects later."
  (:require [ipns.pubsub :as ipns]
            [kotoba.net.gossip :as gossip]
            [kotoba.net.transport.envelope :as envelope]))

(defn open
  "Create one hosted IPNS router over an existing gossip peer-state."
  [node-id peer-state ipns-name]
  {:node-id node-id
   :gossip-state peer-state
   :seen-cache (gossip/empty-seen-cache 1024)
   :router (ipns/init ipns-name)})

(defn- publish-command [state topic bytes]
  (let [payload (vec bytes)
        routed (gossip/route-message
                (:gossip-state state) (:seen-cache state)
                {:topic topic :payload payload
                 :from (:node-id state) :self (:node-id state)})]
    [(assoc state :seen-cache (:seen-cache routed))
     (mapv (fn [{:keys [to payload]}]
             {:op :send
              :to to
              :message (envelope/gossip-envelope (:node-id state) topic payload)})
           (:forward routed))]))

(defn apply-effects
  "Interpret router effects into outbound host commands. Persistence stays an
  explicit command for the caller's durable store."
  [state effects]
  (reduce
   (fn [{:keys [state commands]} effect]
     (case (:op effect)
       :pubsub/publish
       (let [[next-state sends] (publish-command state (:topic effect) (:bytes effect))]
         {:state next-state :commands (into commands sends)})
       :fetch/request
       {:state state
        :commands (conj commands {:op :send :to (:peer effect)
                                  :message {:kind :ipns-fetch
                                            :key (vec (:key effect))}})}
       :fetch/respond
       {:state state
        :commands (conj commands {:op :send :to (:peer effect)
                                  :message {:kind :ipns-fetch-result
                                            :key (vec (:key effect))
                                            :bytes (vec (:bytes effect))}})}
       :persist/put
       {:state state :commands (conj commands effect)}
       :pubsub/subscribe
       {:state state :commands (conj commands effect)}
       :fetch/serve
       {:state state :commands (conj commands effect)}
       {:state state :commands commands}))
   {:state state :commands []}
   effects))

(defn start
  "Return subscription/persistence commands required when the host starts."
  [state]
  (apply-effects state (ipns/start-effects (:router state))))

(defn peer-subscribed
  "Record a peer subscription and request its persistent best record."
  [state peer]
  (let [topic (get-in state [:router :topic])
        state' (update state :gossip-state gossip/subscribe peer topic)]
    (apply-effects state' [(ipns/peer-subscribed (:router state') peer)])))

(defn ingest
  "Validate/select an inbound PubSub or Fetch record and route resulting
  persist/republish commands."
  [state bytes validation-options]
  (let [result (ipns/ingest (:router state) bytes validation-options)
        state' (assoc state :router (:state result))
        applied (apply-effects state' (:effects result))]
    (assoc applied :accepted? (:accepted? result) :reason (:reason result))))

(defn local-publish
  "Validate a local update and fan it out through the same path as relays."
  [state bytes validation-options]
  (ingest state bytes validation-options))

(defn fetch-request
  "Answer a peer's persistence fetch from the hosted router's current state."
  [state peer]
  (if-let [effect (ipns/fetch-request (:router state) peer)]
    (apply-effects state [effect])
    {:state state :commands []}))
