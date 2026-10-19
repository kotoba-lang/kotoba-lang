(ns kotoba.net.transport.tcp
  "The real network-I/O adapter kotoba-net's own README explicitly
  deferred (\"Any real network I/O ... left to a future native adapter\"):
  a 'gossip node' that actually moves kotoba.net.gossip /
  kotoba.net.bitswap protocol messages between real peers over real TCP
  sockets. kotoba.net.gossip and kotoba.net.bitswap stay exactly what
  they always were -- pure, transport-independent decision functions --
  this namespace is a CONSUMER of them, not a modifier: every dedup /
  fanout / want-have / delta-sync decision below is made by calling
  straight into gossip/route-message, bitswap/respond-to-want, or
  bitswap/commits-since unchanged, then this namespace's only job is
  actually delivering the result over a socket.

  .cljs, NOT .cljc: real `node:net` socket I/O (via kotoba.wire.tcp), so
  this only runs under a Node-hosted ClojureScript runtime (nbb in this
  repo). The JVM `clojure -M:test` suite never loads `.cljs` files, so
  this namespace can never regress kotoba-net's existing pure `.cljc`
  test suite (see README.md for the current assertion count).

  WIRE FRAMING / SOCKET PLUMBING -- delegated entirely to
  [`kotoba-lang/wire`](https://github.com/kotoba-lang/wire) (Phase 2 of a
  shared-lib consolidation across kotoba-lang protocol libraries -- Phase
  1 was `kotoba-lang/bytes`, which `kotoba-lang/wire` itself depends on;
  `kotoba-lang/dtn`'s own TCP transport was refactored onto `wire` first
  and is the pattern this namespace follows). This namespace never
  hand-rolls length-prefix framing, Buffer accumulation/defragmentation,
  or an outbound socket pool -- it calls `kotoba.wire.tcp/start-server!` /
  `connect-or-reuse!` / `send-framed!` / `close-all!` for exactly that
  mechanics, the same functions `kotoba-lang/dtn`'s transport already
  consumes (this is the second real consumer of `kotoba-lang/wire`, and
  the entire point of depending on it instead of re-implementing the same
  plumbing a third time). One narrow exception, documented in full at
  `attach-response-reader!` below: `wire.tcp/start-server!` already wires
  an inbound (server-accepted) socket to auto-decode arriving frames, but
  exposes no equivalent for a socket THIS node dialed out itself via
  `connect-or-reuse!` -- so `want!`/`request-commits-since!` need to
  receive their response back over that same outbound socket, and the
  small helper that makes that possible is built from wire's own public
  pure codec functions (`kotoba.wire.framing/defragment` +
  `kotoba.wire.edn/decode-frames` -- the exact building blocks wire.tcp's
  own PRIVATE `make-frame-reader` uses internally), not a re-implementation
  of framing.

  WHAT'S GENUINELY REAL: message delivery over real TCP sockets; gossip
  fanout forwarding that actually re-delivers to real peer sockets
  (`publish!` for locally-originated messages, and automatic re-forwarding
  of inbound messages this node hasn't already seen); bitswap want/have
  request-response over a real socket round-trip (`want!`); bitswap
  delta-sync request-response over a real socket round-trip
  (`request-commits-since!`). Every one of these is provable end-to-end
  in `test/kotoba/net/transport/tcp_demo.cljs` (real sockets between real
  node handles, at least one scenario across real separate OS processes).

  WHAT STAYS OUT OF SCOPE (same as kotoba-net's own founding README, and
  the same limitation `kotoba-lang/dtn`'s own direct transport already
  has):
    - Peer discovery / DHT -- peers are configured up front via
      start-node!'s :peers option. This node never learns about a peer it
      wasn't told about.
    - NAT traversal.
    - QUIC transport or a Noise handshake -- this is plain TCP, cleartext,
      unauthenticated at the transport level. kotoba-net's README
      describes the ORIGINAL Rust crate this library's semantics were
      extracted from as libp2p-based (QUIC + Noise + GossipSub); this
      adapter deliberately does not attempt to reconstruct that stack --
      it is the honestly-scoped 'any transport that can carry these EDN
      messages' adapter the README asked for, not a libp2p re-implementation.
    - Any authentication / encryption of the peer connection itself (contrast
      kotoba-lang/dtn's optional :peer-secrets HMAC layer -- this namespace
      has no equivalent; every configured peer is trusted as given).

  ONE REAL GAP DISCOVERED IN kotoba.net.gossip WHILE WIRING THIS UP:

  `goog.crypt.Sha256` (kotoba.net.gossip/content-hash's :cljs branch) is a
  real Google Closure Library class available for free under a full
  Closure-Compiler build (shadow-cljs -- the environment gossip.cljc's own
  top-of-file comment documents it was written against) but NOT bundled by
  nbb (confirmed against a real nbb 1.4.210 install: it ships
  goog.string/goog.crypt shims but no Sha256). See
  `src/goog/crypt/Sha256.cljs` in this repo -- an nbb-classpath-only shim
  (Node's built-in `node:crypto`, the same 'no npm dep, use Node core
  crypto under nbb' pattern kotoba-lang/dtn's kotoba.dtn.auth already
  established for its own HMAC-SHA256) that supplies exactly the two
  methods gossip.cljc's content-hash calls, verified byte-for-byte against
  known SHA-256 test vectors. `clojure -M:test` and a shadow-cljs build
  are both unaffected -- this shim is never on either of those classpaths.

  A SECOND GAP THIS NAMESPACE USED TO WORK AROUND HAS SINCE BEEN FIXED AT
  THE SOURCE: `kotoba.net.gossip/route-message` used to build its internal
  exclude set via the LITERAL `#{from self}` syntax, which threw
  `Duplicate key` whenever `:from` and `:self` evaluated equal -- exactly
  what a locally-originated `publish!` naturally produces (there is no
  previous hop to exclude for a message this node itself originates, only
  self). This namespace used to route around it with a local `safe-from`
  helper that substituted `nil` for a colliding `:from` before calling
  `route-message`. gossip.cljc now builds that exclude set with
  `(hash-set from self)` instead, which silently dedupes equal values
  instead of throwing, so `safe-from` has been removed and every call-site
  below hands `route-message` its real `:from`/`:self` values directly."
  (:require [kotoba.wire.tcp :as wire]
            [kotoba.wire.framing :as framing]
            [kotoba.wire.edn :as wedn]
            [kotoba.net.gossip :as gossip]
            [kotoba.net.bitswap :as bitswap]
            [kotoba.net.transport.envelope :as envelope]))

;; ---------------------------------------------------------------------------
;; logging
;; ---------------------------------------------------------------------------

(defn- log!
  [node-handle-atom & parts]
  (println (str "[" (:node-id (deref node-handle-atom)) "] " (apply str parts))))

;; ---------------------------------------------------------------------------
;; response-reading on a self-dialed (outbound) socket -- see namespace
;; docstring for why this exists at all.
;; ---------------------------------------------------------------------------

(defn- attach-response-reader!
  "Wire a 'data' listener onto socket (an outbound socket this node itself
  opened via kotoba.wire.tcp/connect-or-reuse!) that defragments/decodes
  arriving frames and calls (on-message decoded-map) once per decoded
  message, in order -- built from the exact same public pure functions
  kotoba.wire.tcp's own (private) inbound frame-reader uses
  (kotoba.wire.framing/defragment + kotoba.wire.edn/decode-frames), just
  wired onto a socket wire.tcp's higher-level API doesn't already cover
  (see namespace docstring)."
  [socket on-message]
  (let [remainder-atom (atom [])]
    (.on socket "data"
         (fn [chunk]
           (let [incoming (vec (js/Array.from chunk))
                 combined (into @remainder-atom incoming)
                 {:keys [frames remainder]} (framing/defragment combined)]
             (reset! remainder-atom remainder)
             (doseq [m (wedn/decode-frames frames)]
               (on-message m)))))))

(defn- ensure-response-reader!
  "Idempotently attach-response-reader! onto the outbound socket this node
  has open to peer-id, the first time this node ever dials out to that
  peer. kotoba.wire.tcp/connect-or-reuse! POOLS sockets per peer-key, so a
  naive 'attach on every want!/request-commits-since! call' would stack a
  new 'data' listener on an already-open connection every time -- each
  stacked listener would then independently re-fire (double/triple/...
  dispatch, or misroute a response meant for an earlier call) on every
  later response. This node handle instead tracks which peer-ids already
  have a reader attached (:reader-attached, a set on the node handle) and
  only calls attach-response-reader! the first time per peer."
  [node-handle-atom peer-id sock]
  (when-not (contains? (:reader-attached (deref node-handle-atom)) peer-id)
    (swap! node-handle-atom update :reader-attached (fnil conj #{}) peer-id)
    (attach-response-reader!
     sock
     (fn [msg]
       (let [pending-key [peer-id (:kind msg)]]
         (when-let [resolve! (get (:pending (deref node-handle-atom)) pending-key)]
           (swap! node-handle-atom update :pending dissoc pending-key)
           (resolve! msg)))))))

;; ---------------------------------------------------------------------------
;; gossip delivery (shared by publish! and inbound re-forwarding)
;; ---------------------------------------------------------------------------

(defn- deliver-gossip!
  "Actually deliver a :gossip envelope for topic/payload to to-peer-id
  over a real socket: resolve to-peer-id's host:port from this node's own
  :peers config, connect-or-reuse! a socket to it, and send-framed! the
  envelope. This is the REAL fanout -- called once per entry in a
  kotoba.net.gossip/route-message :forward list, whether that list came
  from a locally-originated publish! or from re-routing an inbound
  message this node hadn't already seen."
  [node-handle-atom to-peer-id topic payload]
  (let [{:keys [node-id peers sockets-atom]} (deref node-handle-atom)
        {:keys [host port]} (envelope/resolve-peer peers to-peer-id)
        sock (wire/connect-or-reuse! sockets-atom to-peer-id host port)]
    (wire/send-framed! sock (envelope/gossip-envelope node-id topic payload))))

;; ---------------------------------------------------------------------------
;; inbound dispatch
;; ---------------------------------------------------------------------------

(defn- handle-gossip!
  "Run the inbound {:topic :payload :from} envelope through the pure
  kotoba.net.gossip/route-message (:self = this node's own id, computed
  locally -- never carried on the wire), update this node's :seen-cache to
  the result, and actually deliver to every :forward target over a real
  socket (deliver-gossip!).

  A message already present in the seen-cache produces an EMPTY :forward
  list (kotoba.net.gossip/route-message's own dedup semantics, confirmed
  by reading gossip.cljc directly rather than assumed) -- so a node in a
  mesh topology where a message can arrive via more than one path forwards
  it onward at most once no matter how many redundant copies arrive, and
  this is what actually terminates a cycle (see the 3-node mesh scenario
  in tcp_demo.cljs) rather than looping forever.

  :received-messages (demo/test observability only -- a real host would
  dispatch received payloads to its own application logic instead of
  accumulating them on the node handle) is appended to ONLY when this
  delivery was fresh -- detected by comparing the seen-cache BEFORE and
  AFTER route-message (mark-seen returns the cache UNCHANGED when the hash
  was already present, so a `not=` comparison is an exact, no-extra-hashing
  freshness check) -- specifically so a node that receives the same
  message twice over two different wire paths (the redundant-forward case
  above) shows it exactly once in :received-messages, not duplicated.

  :from is handed to route-message unmodified, including the case where a
  peer claims :from equal to this node's own id -- gossip.cljc's exclude
  set is built with (hash-set from self), which dedupes equal values
  instead of crashing, so no substitution is needed here."
  [node-handle-atom {:keys [topic payload from]}]
  (let [{:keys [node-id gossip-state seen-cache]} (deref node-handle-atom)
        msg {:topic topic :payload payload :from from :self node-id}
        {new-seen-cache :seen-cache forward :forward} (gossip/route-message gossip-state seen-cache msg)
        fresh? (not= seen-cache new-seen-cache)]
    (swap! node-handle-atom
           (fn [node]
             (cond-> (assoc node :seen-cache new-seen-cache)
               fresh? (update :received-messages conj {:topic topic :payload payload :from from}))))
    (when fresh?
      (log! node-handle-atom "NET-GOSSIP-RECV topic=" topic " from=" from " payload=" (pr-str payload)))
    (doseq [{:keys [to payload]} forward]
      (deliver-gossip! node-handle-atom to topic payload))))

(defn- handle-bitswap-want!
  "A peer is asking (over socket, the accepted inbound connection) which
  of :want-set this node has. The 'have' side is host-injected -- this
  transport layer doesn't know what content this node holds, so it was
  handed a :have-set option at start-node! time (a plain #{...} the node
  consults). Computes the pure kotoba.net.bitswap/respond-to-want
  intersection and replies on the SAME socket the request arrived on
  (full-duplex TCP -- no new outbound connection needed; see namespace
  docstring for why an OUTBOUND request/response pair needs the extra
  attach-response-reader! machinery but an INBOUND request answered
  in-place does not)."
  [node-handle-atom {:keys [from want-set]} socket]
  (let [{:keys [node-id have-set]} (deref node-handle-atom)
        have (bitswap/respond-to-want want-set have-set)]
    (log! node-handle-atom "NET-BITSWAP-WANT from=" from " want-set=" (pr-str want-set)
          " responding have=" (pr-str have))
    (wire/send-framed! socket (envelope/bitswap-have-envelope node-id have))))

(defn- handle-bitswap-commits-since!
  "A peer is asking (over the accepted inbound socket) for a delta-sync:
  every commit-log entry newer than its claimed :want-since. The
  commit-log is host-injected the same way :have-set is (a :commit-log
  option at start-node! time). Computes the pure
  kotoba.net.bitswap/commits-since result and replies on the same
  accepted socket."
  [node-handle-atom {:keys [from want-since]} socket]
  (let [{:keys [node-id commit-log]} (deref node-handle-atom)
        entries (bitswap/commits-since commit-log want-since)]
    (log! node-handle-atom "NET-BITSWAP-COMMITS-SINCE from=" from " want-since=" (pr-str want-since)
          " responding entries=" (pr-str entries))
    (wire/send-framed! socket (envelope/bitswap-commits-envelope node-id entries))))

(defn- handle-message!
  "Dispatch on :kind for every frame this node's server accepts. :gossip /
  :bitswap-want / :bitswap-commits-since are real inbound REQUESTS this
  node acts on. :bitswap-have / :bitswap-commits (RESPONSE kinds) never
  legitimately arrive here -- a response is read back over the exact
  outbound socket that sent the request, via attach-response-reader!, not
  through this server-side dispatch -- so an unrecognized/response :kind
  arriving on the server is only logged, never thrown (a stray/malformed
  peer should not be able to crash this node)."
  [node-handle-atom msg socket]
  (case (:kind msg)
    :gossip (handle-gossip! node-handle-atom msg)
    :bitswap-want (handle-bitswap-want! node-handle-atom msg socket)
    :bitswap-commits-since (handle-bitswap-commits-since! node-handle-atom msg socket)
    (log! node-handle-atom "tcp: ignoring inbound message with unrecognized/response :kind " (:kind msg))))

;; ---------------------------------------------------------------------------
;; node lifecycle
;; ---------------------------------------------------------------------------

(defn start-node!
  "Start a gossip/bitswap node listening for real TCP connections on
  port.

  opts: {:node-id id                         ; this node's own peer-id
         :port port                          ; TCP port to bind
         :peers {peer-id {:host \"...\"        ; known peers, optional
                          :port N
                          :topics #{...}} ...}
         :have-set #{cid ...}                ; optional, see handle-bitswap-want!
         :commit-log [{:seq n :cid c} ...]}  ; optional, see handle-bitswap-commits-since!

  Returns an atom (the 'node handle') holding:
    {:node-id id :port port :peers peers
     :gossip-state (kotoba.net.gossip peer-state, seeded from :peers -- see
                    kotoba.net.transport.envelope/register-peers)
     :seen-cache (kotoba.net.gossip/empty-seen-cache 1000)
     :received-messages []   ; demo/test observability ONLY -- see
                              ; handle-gossip!'s docstring; a real host would
                              ; dispatch received payloads to its own
                              ; application logic instead
     :have-set (or (:have-set opts) #{})
     :commit-log (or (:commit-log opts) [])
     :pending {}              ; [peer-id response-kind] -> resolve! fn,
                               ; for want!/request-commits-since! awaiting
                               ; a response on an outbound socket
     :reader-attached #{}     ; peer-ids whose outbound socket already has
                               ; attach-response-reader! wired (idempotency
                               ; guard -- see ensure-response-reader!)
     :sockets-atom (atom {})  ; peer-id -> open outbound socket, lazily
                               ; connected via kotoba.wire.tcp/connect-or-reuse!
     :accepted #{}            ; inbound sockets this node's server has
                               ; accepted (bookkeeping for stop-node!)
     :server <net/Server>}

  Each configured peer is registered into :gossip-state via
  kotoba.net.transport.envelope/register-peers -- see that function's
  docstring for exactly how :topics is read."
  [{:keys [node-id port peers have-set commit-log]}]
  (let [peers (or peers {})
        node-handle-atom (atom {:node-id node-id
                                 :port port
                                 :peers peers
                                 :gossip-state (envelope/register-peers peers)
                                 :seen-cache (gossip/empty-seen-cache 1000)
                                 :received-messages []
                                 :have-set (or have-set #{})
                                 :commit-log (or commit-log [])
                                 :pending {}
                                 :reader-attached #{}
                                 :sockets-atom (atom {})
                                 :accepted #{}
                                 :server nil})
        server (wire/start-server! port
                (fn [msg socket] (handle-message! node-handle-atom msg socket)))]
    ;; Additional bookkeeping listener on the SAME server/'connection'
    ;; event kotoba.wire.tcp/start-server! already wired up for
    ;; framing/decoding -- Node's EventEmitter dispatches to every
    ;; registered listener, so this composes cleanly without wire.tcp
    ;; needing to know about this node's own :accepted-socket tracking.
    (.on server "connection"
         (fn [socket]
           (swap! node-handle-atom update :accepted conj socket)
           (.on socket "close" (fn [] (swap! node-handle-atom update :accepted disj socket)))))
    (.on server "error" (fn [e] (log! node-handle-atom "tcp: server error " (.-message e))))
    (.on server "listening" (fn [] (log! node-handle-atom "tcp: listening on port " port)))
    (swap! node-handle-atom assoc :server server)
    node-handle-atom))

(defn stop-node!
  "Close node-handle-atom's server (stop accepting new connections, and
  proactively close any already-accepted inbound sockets so the server's
  'close' event fires promptly rather than waiting on remote peers) and
  destroy every cached outbound socket (kotoba.wire.tcp/close-all! on
  :sockets-atom). Returns a Promise resolved once the server has actually
  closed (so a caller can safely re-start-node! on the same port right
  after)."
  [node-handle-atom]
  (js/Promise.
   (fn [resolve _reject]
     (let [{:keys [server sockets-atom accepted]} (deref node-handle-atom)]
       (wire/close-all! sockets-atom)
       (doseq [sock accepted] (.destroy sock))
       (swap! node-handle-atom assoc :accepted #{})
       (if server
         (.close server (fn [_err]
                           (swap! node-handle-atom assoc :server nil)
                           (resolve true)))
         (resolve true))))))

;; ---------------------------------------------------------------------------
;; outbound / locally-originated
;; ---------------------------------------------------------------------------

(defn publish!
  "Locally originate a gossip message on topic with payload: run it
  through the SAME pure kotoba.net.gossip/route-message logic inbound
  messages use (:from = :self = this node's own id -- there is no
  'previous hop' to exclude for a message this node itself is
  originating, only itself), which both selects this node's real fanout
  peers for topic AND marks the message's content-hash seen on this
  node's own :seen-cache (so if this exact message loops back to this
  node later via gossip, it's correctly treated as an already-seen
  duplicate rather than re-published/re-forwarded). Then actually
  delivers to every fanout target over a real socket (deliver-gossip!,
  the identical delivery path handle-gossip! uses for re-forwarding an
  inbound message).

  Returns the :forward vector route-message computed (each entry
  {:to peer-id :payload payload}) -- the set of peers this call actually
  wrote to, for a caller/test that wants to confirm real fanout happened.

  :from is passed to route-message as node-id, genuinely equal to :self
  (this node IS both the originator and itself, by construction) --
  gossip.cljc's exclude set is built with (hash-set from self), which
  dedupes that equal pair instead of crashing, so route-message correctly
  excludes only this node's own id from the fanout candidates."
  [node-handle-atom topic payload]
  (let [{:keys [node-id gossip-state seen-cache]} (deref node-handle-atom)
        msg {:topic topic :payload payload :from node-id :self node-id}
        {new-seen-cache :seen-cache forward :forward} (gossip/route-message gossip-state seen-cache msg)]
    (swap! node-handle-atom assoc :seen-cache new-seen-cache)
    (doseq [{:keys [to payload]} forward]
      (deliver-gossip! node-handle-atom to topic payload))
    forward))

(defn want!
  "Ask peer-id (a specific, already-configured peer) which of cid-set it
  has: sends a real :bitswap-want request over a real (pooled) socket and
  returns a Promise<vector> resolving to the :have vector from that
  peer's real :bitswap-have response -- the intersection
  kotoba.net.bitswap/respond-to-want computed on the RESPONDING node,
  round-tripped back over the wire, not a locally-computed value."
  [node-handle-atom peer-id cid-set]
  (js/Promise.
   (fn [resolve _reject]
     (let [{:keys [node-id peers sockets-atom]} (deref node-handle-atom)
           {:keys [host port]} (envelope/resolve-peer peers peer-id)
           sock (wire/connect-or-reuse! sockets-atom peer-id host port)]
       (ensure-response-reader! node-handle-atom peer-id sock)
       (swap! node-handle-atom assoc-in [:pending [peer-id :bitswap-have]]
              (fn [response] (resolve (:have response))))
       (wire/send-framed! sock (envelope/bitswap-want-envelope node-id cid-set))))))

(defn request-commits-since!
  "Ask peer-id for a delta-sync since want-since (a
  kotoba.net.bitswap/make-want-since map): sends a real
  :bitswap-commits-since request over a real (pooled) socket and returns
  a Promise<vector> resolving to the :entries vector from that peer's
  real :bitswap-commits response -- the result
  kotoba.net.bitswap/commits-since computed on the RESPONDING node against
  its own commit-log, round-tripped back over the wire."
  [node-handle-atom peer-id want-since]
  (js/Promise.
   (fn [resolve _reject]
     (let [{:keys [node-id peers sockets-atom]} (deref node-handle-atom)
           {:keys [host port]} (envelope/resolve-peer peers peer-id)
           sock (wire/connect-or-reuse! sockets-atom peer-id host port)]
       (ensure-response-reader! node-handle-atom peer-id sock)
       (swap! node-handle-atom assoc-in [:pending [peer-id :bitswap-commits]]
              (fn [response] (resolve (:entries response))))
       (wire/send-framed! sock (envelope/bitswap-commits-since-envelope node-id want-since))))))
