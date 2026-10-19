(ns kekkai.node.stream-edge
  "TCP forwarding over the kekkai overlay: sockets on both ends,
  `kekkai.node.stream` in between.

  This is the piece that makes `ssh judah` work without a TUN device. The
  forwarder listens on a local port; each accepted connection becomes one
  stream; the far side connects to a loopback service and pipes it back. To the
  user it is `ssh -p 2222 localhost`, and to `sshd` it is a connection from
  127.0.0.1 — neither end knows about Noise, NAT traversal or relays.

      ssh ──▶ 127.0.0.1:2222 ──┐                    ┌──▶ 127.0.0.1:22 (sshd)
                    forwarder  │  kekkai overlay    │  service
                               └── stream frames ───┘

  ## The two authorisation checks are not redundant

  The forwarder checks `netmap/permitted?` before opening a stream, and the
  service checks it again before connecting to anything. Removing either one
  would be a mistake of a different kind:

  - without the forwarder's check, a local user learns whether a remote service
    exists by watching how it is refused, and a stream is opened across the
    overlay for a connection policy already forbids;
  - without the service's check, the *only* thing standing between a peer and
    loopback is the peer's own opinion about what it is allowed to do. That is
    the shape `kekkai.node.netmap`'s docstring calls out about `fleet.edn`:
    'anyone who can edit it and reach a node gets treated as fleet'.

  The one that matters for safety is the service's. The forwarder's is there so
  a refusal is fast and legible.

  ## Backpressure is real here, not advisory

  A socket delivers bytes as fast as the kernel has them, and this transport is
  measured at 100–600 datagrams/s. `pause`/`resume` on the TCP socket is what
  keeps a fast local writer from turning into an unbounded queue in this
  process — the stream's window bounds what is *in flight*, not what a caller
  has handed over."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [kekkai.node.agent :as agent]
            [kekkai.node.netmap :as netmap]
            [kekkai.node.stream :as stream]
            ["node:net" :as net]))

(def ^:const max-queued-bytes
  "How much un-segmented data may sit in one stream before the socket is
  paused. Two windows: enough that a normal round trip never stalls the reader,
  small enough that a stalled stream cannot hold megabytes per connection."
  (* 2 stream/default-window-bytes))

(def ^:const tick-ms 50)

(defn- now-ms [] (.getTime (js/Date.)))

;; ── sending frames over the peer session ────────────────────────────────────

(defn- emit!
  "Encode and send stream frames to `peer`. -> nil, or the reason it could not.

  `reply?` picks `agent/reply-to` over `agent/send-to`, which is the difference
  between answering on an authenticated inbound edge and claiming outbound
  application authority — the service side must not acquire the latter merely
  by having been contacted.

  **Failure is returned, not thrown.** `agent/send-to` throws when the peer's
  session is not established, which is an ordinary condition here: a local
  client can connect to the forwarder before the overlay has finished its
  handshake. Letting that escape turns a connection that should have been
  refused into an unhandled rejection that takes the whole agent down — which
  is what it did on the first real fleet run."
  [handle peer frames reply?]
  (try
    (doseq [f frames]
      (swap! (:state handle)
             (if reply? agent/reply-to agent/send-to)
             peer (stream/encode f)))
    nil
    (catch :default e
      (if (str/includes? (str (ex-message e)) "not established")
        :peer-session-not-established
        :peer-unreachable))))

;; ── one live stream: state machine + socket ─────────────────────────────────

(defn- close-socket! [{:keys [socket]}]
  (when (and socket (not (.-destroyed socket)))
    (.end socket)))

(defn- pump!
  "Apply a state-machine result: send its frames, write its bytes, run its
  events."
  [registry handle key {:keys [state frames delivered events]} reply?]
  (let [entry (get @registry key)]
    (swap! registry assoc-in [key :stream] state)
    (emit! handle (:peer entry) frames reply?)
    (when (seq delivered)
      (let [socket (:socket entry)]
        (when (and socket (not (.-destroyed socket)))
          (.write socket (.from js/Buffer (clj->js delivered))))))
    (doseq [e events]
      (when (#{:stream-reset :stream-peer-fin} (:event e))
        (case (:event e)
          ;; A reset is not a clean close: destroy rather than end, so the local
          ;; side sees a broken connection instead of a successful EOF it will
          ;; mistake for a complete response.
          :stream-reset (when-let [s (:socket entry)] (.destroy s))
          ;; Half-close. `end` rather than `destroy`: the local side may still
          ;; have bytes to send, and ssh does.
          :stream-peer-fin (close-socket! entry))))
    (when (stream/closed? (:stream (get @registry key)))
      (close-socket! (get @registry key))
      (swap! registry dissoc key))))

(defn- attach-socket!
  "Wire a TCP socket to a stream entry: data in, backpressure, close."
  [registry handle key reply?]
  (let [{:keys [socket]} (get @registry key)]
    (.on socket "data"
         (fn [chunk]
           (let [entry (get @registry key)]
             (when entry
               (let [r (stream/send-bytes (:stream entry) (vec (js/Uint8Array. chunk))
                                          (now-ms))]
                 (pump! registry handle key (assoc r :delivered [] :events []) reply?)
                 (when (> (count (:pending (:state r))) max-queued-bytes)
                   (.pause socket)))))))
    (.on socket "end"
         (fn []
           (when-let [entry (get @registry key)]
             (pump! registry handle key
                    (assoc (stream/close (:stream entry) (now-ms))
                           :delivered [] :events [])
                    reply?))))
    (.on socket "error"
         (fn [_e]
           (when-let [entry (get @registry key)]
             (pump! registry handle key
                    (assoc (stream/reset (:stream entry) :local-socket-error)
                           :delivered [] :events [])
                    reply?)
             (swap! registry dissoc key))))))

(def principals-key
  "Where the source-port -> proven-peer table lives inside the registry.

  A keyword, while every stream entry is keyed by a `[peer stream-id]` vector,
  so the two keyspaces cannot collide."
  ::principals)

(defn principal-of
  "Which peer opened the stream behind the loopback connection whose SOURCE
  port is `local-port`, or nil.

  This is the answer `handle-open!` already has and used to throw away. It
  authorises a **proven** peer key and then connects to the service with an
  ordinary loopback socket, so the service sees `127.0.0.1` and every
  downstream authorisation that derives a principal from the peer address
  fails closed — measured, `test/principal_e2e.cljs`.

  The source port is the join. A co-located service reads `socket.remotePort`
  on the connection it is already holding and asks here; nothing is injected
  into the byte stream, so this works for a protocol the agent does not parse,
  including HTTP, and cannot corrupt a service that does not know about it.
  That last property is why this is not a PROXY-protocol header: a header is
  only safe where every service on the port opts in, and the failure when one
  does not is a mangled first request rather than a refusal.

  Two things it is NOT. It is not a secret — any local process may ask which
  peer holds a port, which is metadata about a host the operator already
  controls. And it is not stable beyond the connection: the entry lives
  exactly as long as the socket, because source ports are reused and an
  expired entry would answer for somebody else's connection."
  [registry local-port]
  (get-in @registry [principals-key local-port]))

;; ── inbound frames ──────────────────────────────────────────────────────────

(defn- handle-open!
  "An inbound OPEN: authorise, then connect to the loopback service.

  Refusals are named on the wire (`stream/refuse`) rather than answered with
  silence or a generic error, for the reason `netmap/denials` exists: an
  operator who cannot tell 'no grant' from 'nothing listening' cannot fix
  either."
  [registry handle peer frame]
  (let [{:keys [request state frames refused]}
        (stream/responder {:frame frame :now-ms (now-ms)})
        self-id (:self-id @(:state handle))
        nm (:netmap @(:state handle))]
    (cond
      refused (emit! handle peer frames true)

      ;; The check that actually matters. `permitted?` folds capability and port
      ;; together, so neither can be satisfied without the other.
      (not (netmap/permitted? nm peer self-id
                              (:capability request) (:port request)))
      (emit! handle peer [(stream/refuse (:stream-id frame) :edge-not-authorized)]
             true)

      :else
      (let [key [peer (:stream-id frame)]
            socket (.connect net #js {:port (:port request) :host "127.0.0.1"})]
        (swap! registry assoc key {:peer peer :stream state :socket socket
                                   :reply? true})
        (.on socket "connect"
             (fn []
               ;; Recorded here rather than before `connect`, because the
               ;; source port does not exist until the socket is bound —
               ;; reading `localPort` earlier yields nil and would file every
               ;; stream under one key.
               (swap! registry assoc-in [principals-key (.-localPort socket)]
                      {:peer peer
                       :capability (:capability request)
                       :port (:port request)})
               (emit! handle peer frames true)))
        ;; The entry dies with the socket. Source ports are reused, and an
        ;; entry that outlived its connection would name the wrong peer for
        ;; somebody else's — an authorisation answer that is wrong rather than
        ;; missing.
        (.on socket "close"
             (fn [] (swap! registry update principals-key dissoc (.-localPort socket))))
        (.on socket "error"
             (fn [_]
               ;; Named separately from :edge-not-authorized: 'you may not' and
               ;; 'nobody is listening' send an operator to different places.
               (emit! handle peer
                      [(stream/refuse (:stream-id frame) :service-unreachable)]
                      true)
               (swap! registry update principals-key dissoc (.-localPort socket))
               (swap! registry dissoc key)))
        (attach-socket! registry handle key true)))))

(defn on-peer-data
  "Feed one `:data` event from the agent into the stream registry.

  Returns true when the payload was a stream frame, so a caller multiplexing
  this with `kekkai.node.application` can tell whether to try that decoder next
  — rather than both silently consuming each other's frames."
  [registry handle {:keys [peer payload]}]
  (if-let [frame (stream/decode payload)]
    (let [key [peer (:stream-id frame)]]
      (cond
        (get @registry key)
        (let [entry (get @registry key)
              r (stream/on-frame (:stream entry) frame (now-ms))]
          (pump! registry handle key r (:reply? entry))
          ;; Resume a socket paused for backpressure once the queue drained.
          (when-let [s (:socket (get @registry key))]
            (when (and (.-isPaused s)
                       (<= (count (:pending (:stream (get @registry key))))
                           max-queued-bytes))
              (.resume s)))
          true)

        (= :open (:kind frame)) (do (handle-open! registry handle peer frame) true)

        ;; A frame for a stream this side does not have. Answering with a reset
        ;; stops the peer retransmitting into a void; staying silent would make
        ;; it retry for the full give-up window.
        (not= :rst (:kind frame))
        (do (emit! handle peer [(stream/refuse (:stream-id frame) :no-such-stream)]
                   true)
            true)

        :else true))
    false))

(defn tick!
  "Drive retransmission for every live stream. Call on a timer."
  [registry handle]
  (doseq [[key entry] @registry]
    (when (get @registry key)
      (pump! registry handle key
             (assoc (stream/tick (:stream entry) (now-ms)) :delivered [])
             (:reply? entry)))))

;; ── the forwarder ───────────────────────────────────────────────────────────

(defn forward!
  "Listen on `listen-port` and forward each connection to `peer`'s `port`.

  -> the `net.Server`. `capability` is what the netmap must grant for that port
  (`:ssh` for 22); it is not derived from the port number, because a policy that
  inferred capabilities from ports would grant `:ssh` to anything that happened
  to listen on 22."
  [registry handle {:keys [listen-port peer port capability listen-host]
                    :or {capability :ssh listen-host "127.0.0.1"}}]
  (let [next-id (atom 0)
        server
        (.createServer
         net
         (fn [socket]
           (let [st @(:state handle)
                 self-id (:self-id st)
                 nm (:netmap st)]
             (if-not (netmap/permitted? nm self-id peer capability port)
               ;; Refused locally and immediately. The stream is never opened,
               ;; so a forbidden connection costs no overlay traffic.
               (.destroy socket)
               (let [id (swap! next-id inc)
                     key [peer id]
                     {:keys [state frames]}
                     (stream/initiator {:stream-id id :capability capability
                                        :port port :now-ms (now-ms)})]
                 (swap! registry assoc key {:peer peer :stream state
                                            :socket socket :reply? false})
                 (if-let [failure (emit! handle peer frames false)]
                   ;; The overlay is not ready (or the peer is gone). Refusing
                   ;; the local connection is the honest answer; keeping it open
                   ;; against a stream that was never opened would present to
                   ;; the user as a hang.
                   (do (println (str "kekkai forward refused: " (name failure)
                                     " (" peer ":" port ")"))
                       (swap! registry dissoc key)
                       (.destroy socket))
                   (attach-socket! registry handle key false)))))))]
    (.listen server listen-port listen-host)
    server))

;; ── standalone entry ────────────────────────────────────────────────────────

(defn start
  "Run an agent with stream forwarding attached.

  `:forwards` is a vector of `{:listen-port :peer :port :capability}`. A node
  with none still serves inbound streams — being a service is not something a
  node opts into here, it is what the netmap already grants."
  [config]
  (let [registry (atom {})
        handle (atom nil)
        on-event (fn [event]
                   (when (= :data (:event event))
                     (on-peer-data registry @handle event)))]
    (-> (agent/start {:config config :on-event on-event})
        (.then
         (fn [agent-handle]
           (reset! handle agent-handle)
           (let [timer (js/setInterval #(tick! registry agent-handle) tick-ms)
                 servers (mapv #(forward! registry agent-handle %)
                               (:forwards config))]
             (doseq [f (:forwards config)]
               (println (str "kekkai forward 127.0.0.1:" (:listen-port f)
                             " -> " (:peer f) ":" (:port f)
                             " (" (name (or (:capability f) :ssh)) ")")))
             (assoc agent-handle
                    :stream-registry registry
                    :stop (fn []
                            (js/clearInterval timer)
                            (doseq [s servers] (.close s))
                            ((:stop agent-handle))))))))))

(defn -main [& args]
  (let [path (or (first args) "kekkai-node.edn")
        config (reader/read-string
                (.readFileSync (js/require "node:fs") path "utf8"))]
    (-> (start config)
        (.catch (fn [e]
                  (js/console.error (str "stream edge failed: " (str e)))
                  (set! (.-exitCode js/process) 1))))))
