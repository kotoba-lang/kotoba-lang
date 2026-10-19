(ns kotoba.net.libp2p.node
  "A libp2p node: it dials, it listens, it answers, and it keeps a routing table.

  The difference between this and the dialer is not features but posture. A
  client asks the network for answers and gives none back; for a DHT that is
  not merely impolite, because a node nobody can reach occupies no useful
  position in the keyspace and its own lookups get worse answers than a serving
  peer's. Listening, answering `/ipfs/kad/1.0.0`, and remembering who answered
  are one change, not three.

  `kad.table` owns the routing table and its eviction rule; `kad.lookup` owns
  the iterative search. Neither is re-implemented here -- this supplies the
  socket and the loop they were written to be driven by."
  (:require [clojure.set :as set]
            [kad.key :as kad-key]
            [multiformats.multiaddr :as multiaddr]
            [multiformats.core :as mf]
            [kad.lookup :as lookup]
            [kad.message :as kad]
            [kad.table :as table]
            [libp2p.yamux :as yamux]
            [kotoba.net.libp2p.connection :as connection]
            [kotoba.net.libp2p.dial :as dial]
            [kotoba.net.libp2p.handshake :as handshake]
            [kotoba.net.libp2p.identify :as identify]
            [kotoba.net.libp2p.keys :as keys]
            [kotoba.net.libp2p.serve :as serve]
            [kotoba.net.libp2p.socket :as socket]
            [kotoba.net.libp2p.store :as store])
  (:import [java.net InetSocketAddress ServerSocket Socket]))

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn- varint [n]
  (loop [n n out []]
    (if (< n 128) (conj out n) (recur (quot n 128) (conj out (bit-or 0x80 (bit-and n 0x7F)))))))

(defn- read-varint [port]
  (loop [shift 0 acc 0]
    (let [b (nth ((:read! port) 1) 0)
          acc (bit-or acc (bit-shift-left (bit-and b 0x7F) shift))]
      (if (zero? (bit-and b 0x80)) acc (recur (+ shift 7) acc)))))

(defn- read-message [port]
  ((:read! port) (read-varint port)))

(defn- write-message [port octets]
  ((:write! port) (into (varint (count octets)) octets)))

;; ---------------------------------------------------------------------------
;; Node state

(defn peer-id-of
  "The libp2p peer id a verified handshake identity denotes."
  [peer]
  (handshake/peer-id mf/sha256 (:identity-key-protobuf peer)))

(defn dht-key
  "Peer id octets -> DHT key. The hash is injected into `kad.key` by design,
  so that namespace needs no crypto for one function; supplying it here keeps
  every DHT key in this node derived the same way."
  [peer-id]
  (kad-key/dht-key mf/sha256 (vec peer-id)))

(defn node
  "Create a node. `identity` supplies the keys; nothing is generated here.

  The routing table starts empty and stays empty until peers are LEARNED --
  seeding it from a hardcoded list would make every node's view of the network
  identical at startup, which is the opposite of what a DHT wants."
  [{:keys [identity-public-key sign-fn verify-fn peer-id agent-version store-options
           protocol-handlers]}]
  (when-not (and identity-public-key sign-fn peer-id)
    (fail! :node/identity-required {}))
  (when-not (and (or (nil? protocol-handlers) (map? protocol-handlers))
                 (every? string? (keys protocol-handlers))
                 (every? fn? (vals protocol-handlers)))
    (fail! :node/invalid-protocol-handlers {}))
  {:identity {:identity-public-key identity-public-key
              :sign-fn sign-fn
              :verify-fn (or verify-fn (keys/verifier))
              :peer-id peer-id
              :agent-version agent-version}
   :table (atom (table/create (dht-key peer-id)))
   :store (atom (store/store (or store-options {})))
   :protocol-handlers (or protocol-handlers {})
   :connections (atom {})
   :listen-addrs (atom [])})

(declare probe! find-node! query!)

(defn- remember!
  "Note a peer as seen. `kad.table` decides whether it is kept: a full bucket
  is not evicted on arrival, it returns a probe instruction, and that rule is
  the eclipse defence rather than an optimisation."
  [{:keys [table] :as node} peer-id addrs]
  (let [id (vec peer-id)
        {next-table :table probe :probe}
        (table/note-seen @table
                         {:peer/id id :peer/dht-key (dht-key id) :peer/addrs (vec addrs)}
                         (System/currentTimeMillis))]
    (reset! table next-table)
    ;; Probing is asynchronous: a full bucket must not make the request that
    ;; caused it wait on a round trip to a third party.
    (when probe (future (try (probe! node probe) (catch Exception _ nil))))
    {:probe probe}))

(defn known-peers [{:keys [table]}] (table/all-peers @table))

(defn- probe!
  "Act on a full bucket rather than only reporting it.

  `note-seen` returns a probe instruction because Kademlia does not evict on
  arrival: it pings the least recently seen incumbent, and the newcomer is
  admitted only if that peer is gone. Returning the instruction and doing
  nothing with it -- which is what this did before -- keeps the safe half of
  the rule (the incumbent stays) and loses the other half: a table full of dead
  peers never makes room, and every lookup routed through it goes nowhere."
  [node peer]
  (let [address (first (:peer/addrs peer))
        alive? (and address
                    (try
                      (let [connection (dial/dial! address (:identity node))]
                        (try
                          (let [stream (connection/stream! (:secure connection)
                                                           (:session connection)
                                                           serve/ping-protocol)
                                nonce (vec (repeatedly serve/ping-size #(rand-int 256)))]
                            ((:write! stream) nonce)
                            (= nonce ((:read! stream) serve/ping-size)))
                          (finally ((:close! connection)))))
                      (catch Exception _ false)))]
    (swap! (:table node)
           (fn [table]
             (if alive?
               (table/probe-alive table (:peer/id peer))
               (table/probe-dead table (:peer/id peer)))))
    {:peer-id (:peer/id peer) :alive? alive?}))

(defn- addr->octets
  "A multiaddr on the wire is octets. The table keeps the readable string
  because that is what gets dialed, so the conversion happens here -- and it
  has to, because protobuf will length-prefix a string into a `bytes` field
  without complaint and the peer will report only that it could not read the
  message. That is exactly how this was wrong twice: once in identify's
  `listenAddrs` and once here."
  [address]
  (if (string? address) (multiaddr/->octets address) (vec address)))

(defn- octets->addr [octets]
  (try (multiaddr/->string (vec octets)) (catch Exception _ nil)))

(defn closest-peers
  "The peers this node would offer for a key. The whole of what it can serve."
  [{:keys [table]} key n]
  (->> (table/closest @table (dht-key key) n)
       (mapv (fn [peer] {:id (vec (:peer/id peer))
                         :addrs (mapv addr->octets (or (:peer/addrs peer) []))
                         :connection 0}))))

;; ---------------------------------------------------------------------------
;; Serving

(defn serve-streams!
  "Answer inbound streams until the connection ends.

  Lives here, not in `connection`, because it needs threads: that file is
  `.cljc` and a serving loop written with `future` and `Thread/sleep` would
  make it portable in name only.

  This is what turns a client into a participant. `handle` is
  `(fn [protocol port] …)`; it is called once the protocol is negotiated, and
  a protocol the caller does not support is refused with `na` by
  `accept-negotiation` rather than by silence -- a peer that gets no answer
  waits out a timeout it cannot distinguish from a hang.

  Runs on its own thread because a serving connection is inherently concurrent:
  the peer may open a stream at any moment, including while we are waiting for
  a reply on one of ours."
  [secure session supported handle]
  (let [inboxes (volatile! {})
        closed (volatile! #{})
        connection-closed? (volatile! false)]
    (loop []
      (let [frame (try (connection/read-yamux-frame secure)
                       (catch Exception _ nil))]
        (if-not frame
          (vreset! connection-closed? true)
          (let [id (:stream-id frame)
                flags (:flags frame)]
            (cond
              (= :ping (:type frame))
              (when-not (contains? flags :ack)
                ((:write! secure) (yamux/ping (:length frame) :ack? true)))

              (and (contains? flags :syn) (not (contains? @inboxes id)))
              (let [accepted (yamux/accept-stream @session id)]
                (vreset! session (:session accepted))
                (vswap! inboxes assoc id [])
                ((:write! secure) (:out accepted))
                ;; The stream is served on its own thread; the frame loop must
                ;; keep reading, or a handler that waits for more input on its
                ;; own stream would deadlock the connection it is reading from.
                (let [port {:read! (fn [n]
                                     (loop []
                                       (when (< (count (get @inboxes id)) n)
                                         (when (or @connection-closed? (contains? @closed id))
                                           (fail! :stream/closed-early
                                                  {:stream-id id :wanted n
                                                   :available (count (get @inboxes id))}))
                                         (Thread/sleep 2)
                                         (recur)))
                                     (let [taken (vec (take n (get @inboxes id)))]
                                       (vswap! inboxes update id #(vec (drop n %)))
                                       taken))
                            :write! (fn [octets]
                                      ((:write! secure)
                                       (yamux/data-frame id #{} (vec octets))))
                            :close! (fn []
                                      ;; FIN, not RST: we are done sending and
                                      ;; the peer may still send to us. A
                                      ;; one-message protocol like identify
                                      ;; reads until the writer says it has
                                      ;; finished, and without this it waits.
                                      (let [closed (yamux/close-stream @session id)]
                                        (vreset! session (:session closed))
                                        ((:write! secure) (:out closed))))}]
                  (future
                    (try
                      (handle (connection/accept-negotiation port supported) port)
                      (catch Exception _ nil)))))

              (= :data (:type frame))
              (do
                (when (contains? @inboxes id)
                  (vswap! inboxes update id into (:payload frame)))
                (when (or (contains? flags :fin) (contains? flags :rst))
                  (vswap! closed conj id)))

              :else nil)
            (recur)))))))

(defn- handle-stream
  "Answer one inbound stream on a negotiated protocol."
  [node peer protocol port]
  (condp = protocol
    identify/protocol
    (do (write-message port (serve/identify-response
                             (assoc (:identity node)
                                    :listen-addrs @(:listen-addrs node)
                                    :protocols (set/union
                                                (set serve/supported-protocols)
                                                (set (keys (:protocol-handlers node)))))))
        ;; Identify is one message. Saying so is part of the protocol.
        (when-let [close! (:close! port)] (close!)))

    "/ipfs/kad/1.0.0"
    ;; A kad stream carries a sequence of messages, not one.
    (loop []
      (let [request (kad/decode (read-message port))
            {:keys [reply store]} (serve/respond
                                   request
                                   {:closest #(closest-peers node % 20)
                                    :store @(:store node)
                                    :now-ms (System/currentTimeMillis)
                                    :peer-id (peer-id-of peer)
                                    :peer-addrs []})]
        (reset! (:store node) store)
        (if reply
          (do (write-message port reply) (recur))
          ;; No reply is a real outcome -- a refused PUT_VALUE, an
          ;; ADD_PROVIDER -- and the caller must be able to tell it apart from
          ;; a slow answer. Closing says "nothing is coming"; staying silent
          ;; makes the requester wait out a timeout that looks like a hang.
          (when-let [close! (:close! port)] (close!)))))

    serve/ping-protocol
    ;; Echo exactly what arrived, forever: ping is a liveness probe and a peer
    ;; may send several on one stream.
    (loop []
      ((:write! port) ((:read! port) serve/ping-size))
      (recur))

    (when-let [handler (get (:protocol-handlers node) protocol)]
      (handler {:protocol protocol
                :port port
                :peer peer
                :peer-id (peer-id-of peer)}))))

(defn- serve-connection!
  "Run one accepted connection: handshake as responder, then answer streams."
  [node ^Socket socket read-timeout-ms on-close]
  (future
    (try
      (let [connected (socket/wrap socket read-timeout-ms)
            suite (dial/noise-suite)
            {:keys [port peer]} (connection/accept-handshake!
                                 (:port connected)
                                 (assoc (:identity node)
                                        :suite suite
                                        :static ((:dh-generate suite))))
            session (connection/accept! port)]
        (remember! node (peer-id-of peer) [])
        (swap! (:connections node) assoc (peer-id-of peer) {:peer peer})
        (serve-streams! port session
                        (set/union (set serve/supported-protocols)
                                   (set (keys (:protocol-handlers node))))
                                   (fn [protocol stream]
                                     (handle-stream node peer protocol stream))))
      (catch Exception _ nil)
      (finally
        (try (.close socket) (catch Exception _ nil))
        (on-close socket)))))

(defn listen!
  "Start accepting connections. Returns `{:port :stop!}`."
  [node {:keys [port host read-timeout-ms]
         :or {port 0 host "127.0.0.1"
              read-timeout-ms socket/default-read-timeout-ms}}]
  (let [server (ServerSocket.)
        active (atom #{})]
    (.bind server (InetSocketAddress. ^String host ^int (int port)))
    (reset! (:listen-addrs node)
            [(str "/ip4/" host "/tcp/" (.getLocalPort server))])
    (let [running (atom true)
          accepting (future
                      (while @running
                        (try (let [socket (.accept server)]
                               (swap! active conj socket)
                               (serve-connection! node socket read-timeout-ms
                                                  #(swap! active disj %)))
                             (catch Exception _ nil))))]
      {:port (.getLocalPort server)
       :host host
       :stop! (fn []
                (reset! running false)
                (try (.close server) (catch Exception _ nil))
                ;; Closing only the accept socket leaves every authenticated
                ;; stream alive. Qualification exposed this as a node that had
                ;; announced completion but kept receiving publications.
                (doseq [^Socket socket @active]
                  (try (.close socket) (catch Exception _ nil)))
                (reset! active #{})
                (future-cancel accepting))})))

;; ---------------------------------------------------------------------------
;; Querying

(def replies-to
  "Which requests a peer answers. ADD_PROVIDER does not, by design, and a
  caller that waited for one would hang on every successful announcement."
  #{(kad/message-type :find-node)
    (kad/message-type :get-value)
    (kad/message-type :get-providers)
    (kad/message-type :put-value)})

(defn query!
  "Open a kad stream to ADDRESS and send one request, returning the reply.

  Every peer named in the reply is remembered: a lookup that discarded what it
  learned would re-discover the same network on every query, which is most of
  what a routing table exists to avoid.

  Returns nil when the request expects no answer, or when the peer closed
  instead of answering -- a refusal, which is a result rather than a fault."
  [node address request]
  (let [connection (dial/dial! address (:identity node))]
    (try
      (let [stream (connection/stream! (:secure connection) (:session connection)
                                       "/ipfs/kad/1.0.0")]
        (write-message stream (kad/encode request))
        (let [reply (when (contains? replies-to (:type request))
                      (try (kad/decode (read-message stream))
                           (catch Exception _ nil)))]
          (when reply
            (doseq [peer (:closer-peers reply)]
              ;; Addresses arrive as octets and are kept as strings: a table
              ;; entry exists to be dialed, and the dialer takes a multiaddr.
              (remember! node (:id peer) (keep octets->addr (:addrs peer)))))
          (remember! node (peer-id-of (:peer connection)) [])
          reply))
      (finally ((:close! connection))))))

(defn announce!
  "Tell the network we provide KEY, and store the claim ourselves.

  Sent to the peers closest to the key rather than to everyone we know: that is
  what makes a provider record findable by someone who does not know us, since
  a requester walks toward the key and only meets the peers near it.

  Re-announcing is not optional. Provider records expire, so a node that
  announces once and stops is advertised until the TTL and then silently is
  not -- the block is still there and nobody is told."
  ([node key] (announce! node key {}))
  ([node key {:keys [replicas] :or {replicas 10}}]
   (let [self (get-in node [:identity :peer-id])
         targets (take replicas (table/closest @(:table node) (dht-key key)))
         sent (doall
               (for [peer targets
                     :let [address (first (:peer/addrs peer))]
                     :when address]
                 (try
                   (query! node address
                           {:type (kad/message-type :add-provider)
                            :key (vec key)
                            :provider-peers [{:id (vec self)
                                              :addrs (mapv addr->octets @(:listen-addrs node))
                                              :connection 0}]})
                   {:peer (:peer/id peer) :sent? true}
                   (catch Exception _ {:peer (:peer/id peer) :sent? false}))))]
     ;; We are a provider of what we announce, and a node that told the network
     ;; but not itself would answer GET_PROVIDERS without naming itself.
     (swap! (:store node) #(:store (store/add-provider % key self @(:listen-addrs node)
                                                       (System/currentTimeMillis))))
     {:key (vec key) :announced (count (filter :sent? sent)) :attempted (count sent)})))

(defn republish!
  "Re-announce everything we provide, and re-put every record we hold.

  The interval belongs to the caller: a node that scheduled its own timer would
  be a second thing to shut down, and the right period depends on the TTL a
  deployment chose rather than on this library."
  [node]
  (let [store @(:store node)
        keys (vec (keys (:store/providers store)))
        self (get-in node [:identity :peer-id])]
    {:providers (mapv (fn [key]
                        ;; Only what WE provide. Re-announcing someone else's
                        ;; claim would put our name on a block we do not have.
                        (when (some #(= (vec self) (vec (:id %)))
                                    (store/providers store key (System/currentTimeMillis)))
                          (announce! node key)))
                      keys)}))

(defn refresh-buckets!
  "Look up a random key in each bucket's range, to keep the table fresh.

  Without this a table only learns from traffic it happens to see, which for a
  quiet node is almost none: buckets never fill, lookups route through the few
  peers that once talked to us, and the node's answers get worse the longer it
  runs. Kademlia's own answer is to refresh a bucket that has gone unused, and
  a random key inside its range is what makes the lookup land there.

  Returns what each refresh found, so a caller can see the table growing rather
  than trust that it did."
  ([node] (refresh-buckets! node {}))
  ([node {:keys [buckets] :or {buckets 4}}]
   (mapv (fn [_]
           (let [target (vec (repeatedly 32 #(rand-int 256)))]
             (try (assoc (find-node! node target {:max-rounds 2}) :target-random? true)
                  (catch Exception e {:failed (.getMessage e)}))))
         (range buckets))))

(defn find-node!
  "Iterative FIND_NODE for TARGET, driven by `kad.lookup`.

  The state machine decides who to ask next and when to stop; this supplies the
  network. Keeping them apart is what makes α-parallelism and the termination
  rule testable with no network at all -- and it is why a peer that does not
  answer is recorded as failed rather than retried forever."
  ([node target] (find-node! node target {}))
  ([node target {:keys [alpha k max-rounds] :or {alpha 3 k 20 max-rounds 8}}]
   (let [target-key (dht-key target)
         seed (table/closest @(:table node) target-key k)]
     (loop [state (lookup/start target-key seed {:alpha alpha :k k})
            round 0]
       (let [queries (lookup/next-queries state)]
         (if (or (empty? queries) (>= round max-rounds) (lookup/done? state))
           {:closest (vec (take k (:lookup/shortlist state)))
            :rounds round
            :queried (count (:lookup/queried state))
            :failed (count (:lookup/failed state))}
           (let [answers
                 (doall
                  (for [peer queries]
                    (if-let [address (first (:peer/addrs peer))]
                      (try
                        (let [reply (query! node address (kad/find-node (vec target)))]
                          {:peer peer
                           :closer (mapv (fn [p]
                                           (let [id (vec (:id p))]
                                             {:peer/id id
                                              :peer/dht-key (dht-key id)
                                              :peer/addrs (vec (or (:addrs p) []))}))
                                         (:closer-peers reply))})
                        (catch Exception _ {:peer peer :failed? true}))
                      ;; Known by id but not by address: nothing to dial, and
                      ;; pretending otherwise would spin the lookup on it.
                      {:peer peer :failed? true})))
                 next-state (reduce (fn [st {:keys [peer closer failed?]}]
                                      (if failed?
                                        (lookup/failure st (:peer/id peer))
                                        (lookup/response st (:peer/id peer) closer)))
                                    state
                                    answers)]
             (recur (lookup/round state next-state) (inc round)))))))))
