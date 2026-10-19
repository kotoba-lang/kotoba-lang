(ns inga.net.server
  "Accepting inbound peers, as opposed to dialling out.

  `inga.net.ws` dials. A validator also has to be dialled: with only outbound
  connections a set of replicas behind anything that does not accept inbound
  never forms a mesh, and the ones that cannot dial out at all — a Cloudflare
  Worker, a browser tab — could never be reached.

  This is the other half, and it is deliberately the same policy underneath.
  An accepted peer gets `inga.net`'s backoff-free session (it is already open),
  the same bounded queue, and the same strike accounting, so a hostile peer
  costs the same whether it called us or we called it. A server path with its
  own looser rules is the usual way a careful client gets undone.

  ## Two shapes of socket

  A Cloudflare Durable Object accepts with `WebSocketPair` and returns the
  client half in a 101 response; a Node `ws` server hands over an already-open
  socket. Both end up as an object with `send`, `close` and an event
  registration, so `attach!` takes the socket and does not care which produced
  it — the runtime difference lives in the caller, which is the only place it
  can be resolved anyway."
  (:require [inga.net :as net]
            [inga.wire :as wire]))

(defn attach!
  "Wire an already-open socket into a session registry.

  `registry` is an atom holding `{peer-id session}`. `opts` mirrors
  `inga.net.ws/make-node`: `:on-message`, `:on-reject`, `:params`, plus
  `:on-close`. Returns a map with `:send!` and `:close!` for this peer.

  `add-listener` is passed in because a DO socket uses `addEventListener` and
  a Node `ws` socket uses `on` — the shapes differ by one word and inventing a
  wrapper that hides it would be more code than passing the function."
  [registry peer sock
   {:keys [on-message on-reject on-close params add-listener]
    :or {params net/default-params
         on-reject (fn [_ _]) on-close (fn [_])
         add-listener (fn [s ev f] (.addEventListener s ev f))}}]
  (swap! registry assoc peer (net/on-open (net/session peer)))
  (letfn [(update! [f & args] (swap! registry update peer #(apply f % args)))

          (close! []
            (try (.close sock) (catch :default _ nil))
            (swap! registry dissoc peer)
            (on-close peer))

          (send! [msg]
            ;; queue then drain, so the bound applies to an inbound peer
            ;; exactly as it does to an outbound one
            (update! net/enqueue (wire/encode msg) params)
            (let [[msgs s'] (net/drain (get @registry peer))]
              (when (seq msgs)
                (swap! registry assoc peer s')
                (doseq [m msgs]
                  (try (.send sock (js/JSON.stringify (clj->js m)))
                       (catch :default _ (close!)))))))]

    (add-listener sock "message"
      (fn [e]
        (let [raw (or (.-data e) e)
              parsed (try (js->clj (js/JSON.parse (str raw)))
                          (catch :default _ ::unparseable))
              [msg reason] (if (= ::unparseable parsed)
                             [nil :not-a-map]
                             (wire/decode parsed))]
          (if msg
            (do (update! net/on-good-message params)
                (on-message peer msg))
            (do (update! net/on-bad-message params)
                (on-reject peer reason)
                ;; the same threshold as an outbound peer: a hostile peer must
                ;; not cost less by having called us
                (when (net/dropped? (get @registry peer)) (close!)))))))

    (add-listener sock "close" (fn [_] (swap! registry dissoc peer) (on-close peer)))
    (add-listener sock "error" (fn [_] (close!)))

    {:send! send! :close! close!}))

(defn peer-count [registry] (count @registry))

(defn live [registry] (vec (sort (keys @registry))))
