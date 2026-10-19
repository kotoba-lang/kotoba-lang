(ns kotoba.net.ipns-standard
  "Standard-wire host functions for the IPNS PubSub Router.

  The pure state machine remains in `ipns.pubsub`.  This adapter binds its
  persistent-record Fetch step to libp2p Fetch and formats publish/subscribe
  traffic as StrictNoSign PubSub RPCs."
  (:require [ipns.pubsub :as ipns]
            [kotoba.net.libp2p.fetch :as fetch]
            [kotoba.net.libp2p.pubsub :as pubsub]))

(defn fetch-handler
  "Serve only the router's exact binary routing key from ROUTER-ATOM."
  [router-atom]
  (fetch/handler
   (fn [{:keys [identifier]}]
     (let [router @router-atom
           key (get-in (ipns/start-effects router) [1 :key])]
       (when (= (vec key) (vec identifier))
         (:record-bytes router))))))

(defn subscription-rpc [router subscribe?]
  (pubsub/encode-rpc
   {:subscriptions [(pubsub/subscription (:topic router) subscribe?)]}))

(defn publication-rpc [router bytes]
  (pubsub/encode-rpc
   {:messages [(pubsub/strict-no-sign-message (:topic router) bytes)]}))

(defn fetch-peer!
  "Run the required Fetch-on-subscription step, then validate/select/persist
  the answer through `ipns.pubsub`.  NOT_FOUND is a successful empty result."
  [router-atom address identity validation-options]
  (let [router @router-atom
        key (get-in (ipns/start-effects router) [1 :key])
        response (fetch/request! address identity key)]
    (if (= :ok (:status response))
      (let [result (ipns/ingest router (:data response) validation-options)]
        (reset! router-atom (:state result))
        (assoc result :fetch-status :ok))
      {:state router :effects [] :accepted? false
       :fetch-status (:status response)
       :reason (:status response)})))
