(ns kotoba.net.libp2p.gossipsub-host
  "Stateful socket host for the pure GossipSub v1.1 router.

  Each peer has an independently negotiated outbound writer; inbound streams
  are installed as node protocol handlers.  Effects are executed only after a
  pure state transition has committed, so callbacks never observe half an RPC."
  (:require [kotoba.net.libp2p.gossipsub :as gs]
            [kotoba.net.libp2p.node :as node]
            [kotoba.net.libp2p.pubsub-stream :as stream]))

(defn host
  [{:keys [params validator on-deliver on-peer-subscribed on-peer-exchange clock]
    :or {validator (fn [_ _] :accept)
         on-deliver (fn [_] nil)
         on-peer-subscribed (fn [_] nil)
         on-peer-exchange (fn [_] nil)
         clock #(System/currentTimeMillis)}}]
  {:router (atom (gs/init (or params {})))
   :writers (atom {})
   :targets (atom {})
   :validator validator
   :on-deliver on-deliver
   :on-peer-subscribed on-peer-subscribed
   :on-peer-exchange on-peer-exchange
   :clock clock
   :lock (Object.)})

(declare execute!)

(defn- transition! [host f]
  (let [result (locking (:lock host)
                 (let [result (f @(:router host))]
                   (reset! (:router host) (:state result))
                   result))]
    (execute! host (:effects result))
    result))

(defn execute! [host effects]
  (doseq [effect effects]
    (case (:op effect)
      :send (when-let [writer (get @(:writers host) (:peer effect))]
              (try ((:write! writer) (:rpc effect))
                   (catch Exception _
                     ((:close! writer))
                     (swap! (:writers host) dissoc (:peer effect))
                     (swap! (:router host) gs/remove-peer (:peer effect)))))
      :deliver ((:on-deliver host) effect)
      :peer-subscribed ((:on-peer-subscribed host) effect)
      :peer-exchange ((:on-peer-exchange host) effect)
      nil))
  effects)

(defn- receive-rpc! [host {:keys [peer-id rpc]}]
  (transition!
   host
   (fn [state]
     (let [state (if (contains? (:peers state) peer-id)
                   state
                   (gs/add-peer state peer-id {:outbound? false}))]
       (gs/receive state peer-id rpc ((:clock host)) (:validator host))))))

(defn protocol-handler [host]
  (stream/handler #(receive-rpc! host %)))

(defn connect!
  "Open this host's outbound stream. Returns the authenticated remote peer id."
  [host address identity options]
  (doseq [[peer writer] @(:writers host)
          :when (and (= address (:address writer)) (not ((:open? writer))))]
    ((:close! writer))
    (swap! (:writers host) dissoc peer)
    (swap! (:router host) gs/remove-peer peer))
  (let [writer (stream/open-writer! address identity {:on-rpc #(receive-rpc! host %)})
        peer-id (node/peer-id-of (:peer writer))]
    (swap! (:writers host) assoc peer-id writer)
    (swap! (:router host) gs/add-peer peer-id (assoc options :outbound? true))
    ;; Gossipsub's connection hello is the full current subscription snapshot,
    ;; not only future changes. Without it a restarted peer remains invisible
    ;; until every topic is unsubscribed and joined again.
    (when-let [topics (seq (:subscriptions @(:router host)))]
      ((:write! writer) {:subscriptions (mapv #(hash-map :topic % :subscribe? true) topics)}))
    peer-id))

(defn add-target!
  "Remember a peer address and keep its outbound stream recoverable."
  [host address identity options]
  (swap! (:targets host) assoc address {:identity identity :options options})
  (connect! host address identity options))

(defn maintain!
  "Reconnect configured targets whose socket/reader has ended."
  [host]
  (mapv
   (fn [[address {:keys [identity options]}]]
     (let [live (some (fn [[peer writer]]
                        (when (and (= address (:address writer)) ((:open? writer))) peer))
                      @(:writers host))]
       (if live
         {:address address :peer-id live :reconnected? false}
         (try {:address address :peer-id (connect! host address identity options)
               :reconnected? true}
              (catch Exception e {:address address :failed (.getMessage e)})))))
   @(:targets host)))

(defn disconnect! [host peer-id]
  (when-let [writer (get @(:writers host) peer-id)] ((:close! writer)))
  (swap! (:writers host) dissoc peer-id)
  (swap! (:router host) gs/remove-peer peer-id)
  nil)

(defn subscribe! [host topic]
  (transition! host #(gs/subscribe % topic ((:clock host)))))

(defn unsubscribe! [host topic]
  (transition! host #(gs/unsubscribe % topic ((:clock host)))))

(defn publish! [host topic data]
  (transition! host #(gs/publish % {:topic topic :data (vec data)} ((:clock host)))))

(defn heartbeat! [host]
  (transition! host #(gs/heartbeat % ((:clock host)))))

(defn close! [host]
  (doseq [[_ writer] @(:writers host)] ((:close! writer)))
  (reset! (:writers host) {})
  (reset! (:targets host) {})
  nil)
