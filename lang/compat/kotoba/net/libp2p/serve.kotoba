(ns kotoba.net.libp2p.serve
  "Answering, rather than only asking.

  A client dials, identifies and queries; every inbound stream it resets. That
  takes from the network without serving it, and for a DHT the asymmetry is not
  merely impolite: a node nobody can query holds no useful position in the
  keyspace, so its own lookups get worse answers than a serving peer's.

  This namespace is the protocol half of serving -- what to reply, given a
  request and a routing table. It opens no socket and holds no connection, so
  the answers are testable without a peer, which matters because the failure
  mode of a wrong answer is a peer quietly deciding you are useless rather than
  an error anyone sees."
  (:require [kad.message :as kad]
            [kotoba.net.libp2p.store :as store]
            [multiformats.multiaddr :as multiaddr]
            [kotoba.net.libp2p.handshake :as identity]
            [kotoba.net.libp2p.identify :as identify]
            [protobuf.wire :as pb]))

(def default-agent-version "kotoba-libp2p/0.1")

(def ping-protocol "/ipfs/ping/1.0.0")
(def ping-size
  "32 octets, echoed exactly. The size is fixed by the spec, and a peer that
  echoes a different length has not implemented ping."
  32)

(def supported-protocols
  "What this node answers. Advertised in identify and accepted by the listener;
  the two must agree, because a peer that believes an advertisement and gets
  `na` has been lied to."
  [identify/protocol "/ipfs/kad/1.0.0" ping-protocol])

(defn identify-response
  "Our own identify snapshot.

  `listen-addrs` are what we believe we listen on -- a claim, exactly as it is
  when a peer sends it to us, and labelled as one everywhere it is read.

  They go on the wire as multiaddr OCTETS. Passing the human string is the
  mistake that is easy to make and hard to see: protobuf will happily length-
  prefix a string into a `bytes` field, the message parses on our side, and the
  peer reports only `error reading identify message` -- which is what go-libp2p
  said about this exact bug."
  [{:keys [identity-public-key listen-addrs observed-addr agent-version protocols]}]
  (pb/encode identify/schema
             (cond-> {:public-key (identity/public-key-protobuf identity-public-key)
                      :protocols (vec (or protocols supported-protocols))
                      :protocol-version "ipfs/0.1.0"
                      ;; `or`, not a destructuring default: `:or` applies when a
                      ;; key is ABSENT, and a node built without an agent
                      ;; version passes the key with an explicit nil. That
                      ;; encodes as an empty string, and the peer reports
                      ;; `AgentVersion: ""` -- which looks like our identify is
                      ;; broken rather than like a default that did not fire.
                      :agent-version (or agent-version default-agent-version)
                      :listen-addrs (mapv #(if (string? %) (multiaddr/->octets %) (vec %))
                                          (or listen-addrs []))}
               observed-addr (assoc :observed-addr
                                    (if (string? observed-addr)
                                      (multiaddr/->octets observed-addr)
                                      (vec observed-addr))))))

(defn find-node-response
  "Answer FIND_NODE with the closest peers we know.

  `closest` is supplied by the caller from its routing table rather than
  computed here: which peers are closest is a fact about the table's contents
  and the XOR metric, and `kad.table` already owns both."
  [request closest]
  (kad/encode {:type (:type request)
               :key (:key request)
               :closer-peers (vec closest)}))

(defn respond
  "The reply to one decoded kad request, and the store it leaves behind.

  Returns `{:reply <octets or nil> :store s}`. The store is returned rather
  than mutated because what a node agrees to keep is the most consequential
  thing it does, and threading it makes every acceptance a visible decision
  instead of a side effect.

  Closer peers accompany a value or provider answer rather than replacing it:
  a requester that got only the record would have no way to continue if ours
  is stale, and one that got only peers would re-ask the network for something
  we already hold."
  [request {:keys [closest store now-ms peer-id peer-addrs]}]
  (let [type (:type request)
        key (:key request)
        peers (closest key)]
    (condp = type
      (kad/message-type :find-node)
      {:reply (find-node-response request peers) :store store}

      (kad/message-type :get-value)
      (let [record (store/get-record store key)]
        {:reply (kad/encode (cond-> {:type type :key (vec key) :closer-peers (vec peers)}
                              record (assoc :record {:key (vec key)
                                                     :value (:value record)})))
         :store store})

      (kad/message-type :put-value)
      (let [{:keys [store stored?]} (store/put-record store key
                                                      (get-in request [:record :value] [])
                                                      now-ms)]
        ;; go-libp2p expects the request echoed back on success. A refusal is
        ;; silent by design: the requester is free to store it elsewhere, and
        ;; inventing an error code the protocol does not define would be worse
        ;; than saying nothing.
        {:reply (when stored? (kad/encode request)) :store store})

      (kad/message-type :get-providers)
      (let [found (store/providers store key now-ms)]
        {:reply (kad/encode {:type type :key (vec key)
                             :closer-peers (vec peers)
                             :provider-peers (mapv (fn [p] {:id (:id p)
                                                            :addrs (vec (:addrs p))
                                                            :connection 0})
                                                   found)})
         :store store})

      (kad/message-type :add-provider)
      ;; The peer id comes from the CONNECTION, never from the message. A node
      ;; that took it from the payload would let anyone advertise anyone else
      ;; as a provider, which is a free way to direct traffic at a third party.
      (let [{:keys [store]} (store/add-provider store key peer-id (or peer-addrs []) now-ms)]
        {:reply nil :store store})

      {:reply nil :store store})))
