(ns inga.net.ws
  "The WebSocket driver: the thin part.

  `inga.net` decides policy — when to dial, what to queue, when to give up.
  `inga.wire` decides what a message is. This opens sockets and moves bytes,
  and deliberately decides nothing, because everything it could decide is
  already decided somewhere testable.

  ## Why WebSocket

  It is the only transport available in all three places this engine runs:
  Node, a browser, and a Cloudflare Worker (where a Durable Object can hold
  connections). WebTransport has no server side in Workers; js-libp2p does not
  run there at all; the QUIC overlay this repo was going to reuse is JVM-only,
  which would have pinned every validator to the JVM and excluded the Worker
  the sequencer already runs in. ADR-2608021030.

  ## `WebSocket` is a global, not an import

  Node has provided it since v22, browsers always have, and Workers do. Taking
  it from `js/WebSocket` rather than requiring a package is what keeps this
  file loadable in all three — a `(:require [\"ws\" :as ws])` would break the
  browser and the Worker, which is the entire reason for choosing WebSocket.

  A caller may pass its own constructor for a runtime that lacks the global,
  or for a test that wants a fake.

  ## Failure is reported, never thrown

  A socket error reaches the policy layer as a closed session, and a malformed
  frame reaches it as a strike. Nothing here throws into a caller's event loop,
  because an exception in a socket callback is an exception nobody is in a
  position to catch."
  (:require [inga.net :as net]
            [inga.wire :as wire]))

(defn websocket-ctor
  "The platform's WebSocket, or nil when there is none."
  []
  (when (exists? js/WebSocket) js/WebSocket))

(defn now-ms [] (.getTime (js/Date.)))

(defn make-node
  "A driver over a peer map.

  `opts`:
    :peers      seq of peer ids
    :url-of     (fn [peer] \"wss://...\")
    :on-message (fn [peer msg]) — msg is already decoded by inga.wire
    :on-reject  (fn [peer reason]) — optional; a refused frame
    :params     inga.net params
    :ctor       optional WebSocket constructor (tests, exotic runtimes)

  Returns a map of operations. State lives in one atom rather than in
  closures, so a caller can inspect it — a driver whose state is invisible is
  a driver nobody can debug in production."
  [{:keys [peers url-of on-message on-reject params ctor]
    :or {params net/default-params on-reject (fn [_ _])}}]
  (let [ctor (or ctor (websocket-ctor))
        state (atom {:peers (net/peer-set peers) :sockets {}})]
    (when-not ctor
      (throw (ex-info "inga.net.ws: no WebSocket available in this runtime" {})))
    (letfn [(update-peer! [p f & args]
              (swap! state update-in [:peers p] #(apply f % args)))

            (flush! [p]
              (let [s (get-in @state [:peers p])
                    [msgs s'] (net/drain s)
                    sock (get-in @state [:sockets p])]
                (when (seq msgs)
                  (swap! state assoc-in [:peers p] s')
                  (doseq [m msgs]
                    ;; a send that throws (socket closed between drain and
                    ;; send) is a closed session, not a crash
                    (try (.send sock (js/JSON.stringify (clj->js m)))
                         (catch :default _ (close! p)))))))

            (close! [p]
              (when-let [sock (get-in @state [:sockets p])]
                (try (.close sock) (catch :default _ nil)))
              (swap! state update :sockets dissoc p)
              (update-peer! p net/on-closed (now-ms) params))

            (receive! [p raw]
              (let [parsed (try (js->clj (js/JSON.parse raw))
                                (catch :default _ ::unparseable))
                    [msg reason] (if (= ::unparseable parsed)
                                   [nil :not-a-map]
                                   (wire/decode parsed))]
                (if msg
                  (do (update-peer! p net/on-good-message params)
                      (on-message p msg))
                  (do (update-peer! p net/on-bad-message params)
                      (on-reject p reason)
                      (when (net/dropped? (get-in @state [:peers p]))
                        (close! p))))))

            (dial! [p]
              (update-peer! p net/on-connecting)
              (let [sock (new ctor (url-of p))]
                (swap! state assoc-in [:sockets p] sock)
                (set! (.-onopen sock) (fn [_] (update-peer! p net/on-open) (flush! p)))
                (set! (.-onmessage sock) (fn [e] (receive! p (.-data e))))
                (set! (.-onerror sock) (fn [_] (close! p)))
                (set! (.-onclose sock) (fn [_] (close! p)))))]

      {:state state

       :tick!
       (fn []
         (let [now (now-ms)]
           (doseq [p (net/due-for-attempt (:peers @state) now)]
             (dial! p))
           (doseq [p (net/live-peers (:peers @state))]
             (flush! p))))

       :broadcast!
       (fn [msg]
         (let [encoded (wire/encode msg)]
           (swap! state update :peers net/broadcast encoded params)
           (doseq [p (net/live-peers (:peers @state))] (flush! p))))

       :send!
       (fn [p msg]
         (swap! state update-in [:peers p] net/enqueue (wire/encode msg) params)
         (flush! p))

       :live (fn [] (net/live-peers (:peers @state)))

       :close-all!
       (fn [] (doseq [p (keys (:sockets @state))] (close! p)))})))
