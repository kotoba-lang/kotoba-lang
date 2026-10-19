(ns kekkai.node.relay-server
  "The relay process: a socket around `kekkai.node.relay`'s pure server core.

  Deployment note that belongs next to the code: this needs one publicly
  reachable UDP port and nothing else — no state to persist, no database, and it
  can be killed and restarted at will (clients re-handshake on the next send).
  It cannot read the traffic it carries, so 'where do we host a relay' is a
  capacity and latency question, not a trust question."
  (:require [kekkai.node.relay :as relay]
            [kekkai.node.udp :as udp]
            [noise.core :as noise]
            [noise.provider.node :as provider]))

(defn now-ms [] (.getTime (js/Date.)))

(defn start
  "-> Promise of `{:sock … :state … :stop (fn [])}`.
   `static` is `{:priv bytes :pub bytes}` — the relay identity published in the
   netmap as `:relay/key`."
  [{:keys [port host static region prologue tick-ms on-event]
    :or {tick-ms 15000 region "local"}}]
  (let [suite (noise/suite (provider/ports))
        st (atom (relay/server {:suite suite :static static :region region
                                :prologue (or prologue [])}))
        emit (or on-event (fn [e] (println (str "[relay] " (pr-str e)))))
        ;; the message handler needs the socket in order to reply, and the socket
        ;; does not exist until after the handler is installed — hence the ref
        sock-ref (atom nil)
        apply-out! (fn [sock {:keys [state send events]}]
                     (reset! st state)
                     (doseq [{:keys [to bytes]} send] (udp/send! sock bytes to))
                     (doseq [e events] (emit e)))]
    (-> (udp/socket
         {:port port :host host
          :on-message (fn [bytes from]
                        (apply-out! @sock-ref
                                    (relay/server-on-datagram
                                     @st {:from from :bytes bytes :now (now-ms)})))})
        (.then (fn [sock]
                 (reset! sock-ref sock)
                 (let [timer (js/setInterval
                              #(apply-out! sock (relay/server-tick @st (now-ms)))
                              tick-ms)]
                   (emit {:event :listening :port (udp/local-port sock) :region region})
                   {:sock sock
                    :state st
                    :stop (fn []
                            (js/clearInterval timer)
                            (udp/close! sock)
                            (emit {:event :stopped}))}))))))
