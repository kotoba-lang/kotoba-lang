;; kotoba.wire.tcp — generic EDN-over-TCP socket I/O, on top of
;; kotoba.wire.framing + kotoba.wire.edn.
;;
;; .cljs, NOT .cljc: real `node:net` socket I/O, so this only runs under a
;; Node-hosted ClojureScript runtime (nbb). `kotoba.wire.framing` and
;; `kotoba.wire.edn` stay pure/portable `.cljc` on purpose (testable under
;; plain JVM `clojure -M:test`) — this namespace is the thin impure layer
;; that actually pushes their byte-vectors down a real socket and pulls
;; real socket bytes back into their byte-vectors.
;;
;; Deliberately protocol-agnostic: no bundles, no e164, no `:dtn/*`
;; anything — this namespace has no idea it's carrying DTN bundle maps
;; today. `kotoba-lang/dtn`'s `kotoba.dtn.transport.tcp` is this
;; namespace's first consumer (extracted FROM its own inline
;; Node-`Buffer`/framing/socket-pool code); `kotoba-lang/turn`'s future
;; relay I/O and `kotoba-lang/net`'s future gossip I/O are expected
;; consumers too, carrying entirely different EDN shapes over the exact
;; same plumbing.
(ns kotoba.wire.tcp
  (:require ["node:net" :as net]
            [kotoba.wire.framing :as framing]
            [kotoba.wire.edn :as wedn]))

(defn- buffer->byte-vec
  "Node Buffer (or any typed array) -> plain kotoba.bytes-shaped
  byte-vector (`vector<int 0..255>`)."
  [buf]
  (vec (js/Array.from buf)))

(defn- byte-vec->buffer
  "Plain kotoba.bytes-shaped byte-vector -> Node Buffer, ready to `.write`
  to a socket."
  [byte-vec]
  (js/Buffer.from (into-array byte-vec)))

(defn- make-frame-reader
  "Returns a function suitable as a socket 'data' listener: keeps a private
  per-connection remainder byte-vector (closed over in `remainder-atom`),
  appends each arriving chunk, runs `kotoba.wire.framing/defragment` to
  pull out every complete frame available so far, decodes them via
  `kotoba.wire.edn/decode-frames`, and calls `(on-message decoded-map
  socket)` once per decoded message, in order. A frame spanning multiple
  TCP packets, or several frames arriving in one packet, are both handled
  correctly by `defragment` itself — this function only owns carrying its
  `:remainder` forward from one 'data' event to the next."
  [socket on-message]
  (let [remainder-atom (atom [])]
    (fn [chunk]
      (let [combined (into @remainder-atom (buffer->byte-vec chunk))
            {:keys [frames remainder]} (framing/defragment combined)]
        (reset! remainder-atom remainder)
        (doseq [m (wedn/decode-frames frames)]
          (on-message m socket))))))

(defn start-server!
  "Start a generic length-prefixed-EDN-frame TCP server listening on
  `port`. For every accepted connection, accumulates bytes and decodes
  every complete frame (see `make-frame-reader`, above), calling
  `(on-message decoded-map socket)` once per decoded message, in the order
  received. Also attaches a no-op 'error' listener to each accepted socket
  (a client dropping mid-write, or a random port-scanner connecting and
  immediately resetting, is routine here, never an unhandled 'error' event
  that would crash the process) — a caller needing additional
  per-connection bookkeeping (e.g. tracking accepted sockets for graceful
  shutdown) can attach its own extra `'connection'` listener to the
  returned server; Node's `EventEmitter` dispatches an event to every
  registered listener in registration order, so this composes without
  `start-server!` itself needing to know about that bookkeeping.

  Returns the `net.Server` handle, already listening."
  [port on-message]
  (let [server (net/createServer
                (fn [socket]
                  (.on socket "data" (make-frame-reader socket on-message))
                  (.on socket "error" (fn [_e] nil))))]
    (.listen server port)
    server))

(defn connect-or-reuse!
  "Return an open `net.Socket` to `host:port`, reusing
  `socket-pool-atom`'s cached connection for `peer-key` if one is already
  open, or lazily opening (and caching) a new one otherwise.

  Synchronous by design, not a promise: Node buffers writes made to a
  freshly-`net/createConnection`'d socket internally until the underlying
  TCP handshake actually completes, so a caller can write to the returned
  socket immediately without waiting for a `'connect'` event first — this
  mirrors how `kotoba-lang/dtn`'s transport already used its own inline
  version of this exact pool before this extraction, so refactoring `dtn`
  onto this function is behavior-preserving.

  The cached entry is evicted (so the next `connect-or-reuse!` call for
  that `peer-key` opens a fresh connection) on the socket's own `'error'`
  or `'close'` event, so a dead connection is never handed back to a
  caller."
  [socket-pool-atom peer-key host port]
  (or (get @socket-pool-atom peer-key)
      (let [sock (net/createConnection #js {:host host :port port})]
        (.on sock "error" (fn [_e] (swap! socket-pool-atom dissoc peer-key)))
        (.on sock "close" (fn [] (swap! socket-pool-atom dissoc peer-key)))
        (swap! socket-pool-atom assoc peer-key sock)
        sock)))

(defn send-framed!
  "Encode `m` via `kotoba.wire.edn/encode` and write it to `socket`.
  `callback` (optional) is Node's usual `socket.write` completion
  callback: called with an `Error` on failure, or no args (`nil`) on
  success once the data is flushed to the OS — the seam a caller needs to
  turn 'did this send actually succeed' into e.g. a resolved Promise."
  ([socket m] (send-framed! socket m nil))
  ([socket m callback]
   (.write socket (byte-vec->buffer (wedn/encode m)) callback)))

(defn close-all!
  "Close (`.destroy`) every socket currently pooled in `socket-pool-atom`,
  then empty the pool (`reset!` to `{}`)."
  [socket-pool-atom]
  (doseq [[_peer-key sock] @socket-pool-atom] (.destroy sock))
  (reset! socket-pool-atom {}))
