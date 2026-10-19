(ns kekkai.node.dns-server
  "MagicDNS on the wire: a UDP DNS listener that answers from the netmap.

  Wire codec and resolver protocol are `org-ietf-dns`'s (`nameserver.wire`,
  `nameserver.resolver` — both portable `.cljc`), so this file is only the socket
  and the resolver chain. `nameserver.server` already does this on the JVM; the
  agent is a ClojureScript process, so it needs the nbb equivalent.

  Binding: the default port is **5354, not 53**. Port 53 needs root, and a
  resident agent that demands root to answer names is a bad trade — point the
  system resolver at this port for the tailnet suffix instead (on macOS that is
  a file in `/etc/resolver/<suffix>` containing `port 5354`, which is how split
  DNS is wired on that platform anyway)."
  (:require [kekkai.node.magicdns :as magicdns]
            [kekkai.node.udp :as udp]
            [nameserver.resolver :as resolver]
            [nameserver.wire :as wire]))

(defn- respond
  "Decode a query, resolve it, encode the response -> bytes, or nil.
   A malformed query gets no reply at all rather than a guessed one."
  [resolver bytes]
  (try
    (let [msg (wire/decode-message bytes)
          question (first (:dns/questions msg))
          {:keys [status aa? answers authority additional]}
          (resolver/resolve-question resolver question)]
      (wire/encode-message
       {:dns/id (:dns/id msg)
        :dns/qr :response
        :dns/opcode (:dns/opcode msg)
        :dns/aa? (boolean aa?)
        :dns/rd? (:dns/rd? msg)
        :dns/ra? false
        :dns/rcode (case status
                     (:ok :nodata) :noerror
                     :nxdomain :nxdomain
                     :refused :refused
                     :servfail)
        :dns/questions [question]
        :dns/answers (vec answers)
        :dns/authority (vec authority)
        :dns/additional (vec additional)}))
    (catch :default _ nil)))

(defn resolver-chain
  "MagicDNS first, then any caller-supplied resolvers. Names outside the tailnet
   suffix are `:refused` by the MagicDNS resolver precisely so the chain can
   continue — see `kekkai.node.magicdns`."
  [netmap-fn tailnet ttl extra-resolvers]
  (resolver/chain-resolver
   (into [(magicdns/netmap-resolver netmap-fn tailnet (or ttl magicdns/default-ttl))]
         (or extra-resolvers []))))

(defn start
  "-> Promise of `{:sock :stop}`. `netmap-fn` is a 0-arity function returning the
   current netmap, so answers follow a netmap swap with no cache to invalidate."
  [{:keys [port host netmap-fn tailnet ttl extra-resolvers on-event]
    :or {port 5354}}]
  (let [chain (resolver-chain netmap-fn tailnet ttl extra-resolvers)
        emit (or on-event (fn [e] (println (str "[dns] " (pr-str e)))))
        sock-ref (atom nil)]
    (-> (udp/socket
         {:port port :host host
          :on-message (fn [bytes from]
                        (when-let [out (respond chain bytes)]
                          (udp/send! @sock-ref out from)))})
        (.then (fn [sock]
                 (reset! sock-ref sock)
                 (emit {:event :listening :port (udp/local-port sock)
                        :suffix (magicdns/search-domain tailnet)})
                 {:sock sock
                  :stop (fn [] (udp/close! sock) (emit {:event :stopped}))})))))
