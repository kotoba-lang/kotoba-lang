(ns kekkai.node.agent
  "The resident node agent — the component `kekkai`'s charter deliberately leaves
  to the node: it pulls the netmap the control plane published, establishes
  authenticated sessions to the peers that netmap authorizes, punches or relays
  its way to each of them, and serves MagicDNS for the result.

  It actuates the data plane; it never decides policy. Admission, edges and
  routes are the control plane's; if the netmap does not grant it, this agent
  does not do it, and it has no override.

  Everything that decides anything is in the pure namespaces (`netmap`, `peer`,
  `disco`, `relay`, `magicdns`). This file is the loop: one UDP socket, one relay
  client, N peers, a timer, and a DNS listener."
  (:require [clojure.string :as str]
            [kekkai.node.dns-server :as dns-server]
            [kekkai.node.endpoint :as ep]
            [kekkai.node.netmap :as netmap]
            [kekkai.node.peer :as peer]
            [kekkai.node.relay :as relay]
            [kekkai.node.signed-netmap :as signed-netmap]
            [kekkai.node.stun :as stun]
            [kekkai.node.udp :as udp]
            [kotoba.bytes :as b]
            [noise.core :as noise]
            [noise.provider.node :as provider]
            ["node:fs" :as fs]))

(defn now-ms [] (.getTime (js/Date.)))
(defn now-s [] (js/Math.floor (/ (now-ms) 1000)))

(defn load-netmap
  "Read and authenticate a netmap. Production defaults to a signed envelope.
   Raw EDN is available only through the explicit test/development opt-in."
  [path {:keys [netmap-authority-spki-b64 allow-unsigned-netmap?]}]
  (let [text (.readFileSync fs path "utf8")]
    (if allow-unsigned-netmap?
      (cljs.reader/read-string text)
      (signed-netmap/verify-envelope text netmap-authority-spki-b64))))

(defn- static-keypair [{:keys [priv pub]}]
  {:priv (b/unhex priv) :pub (b/unhex pub)})

;; ── state ───────────────────────────────────────────────────────────────────

(defn state
  [{:keys [config nm]}]
  (let [suite (noise/suite (provider/ports))
        static (static-keypair (:static config))
        self-id (or (:node/id (:netmap/self nm)) (:node/id config))]
    {:suite suite
     :static static
     :self-id self-id
     :config config
     :netmap nm
     :prologue (b/utf8-encode (netmap/prologue-string nm))
     :peers {}            ; peer-id -> peer state
     :by-key {}           ; key-hex -> peer-id
     :relay nil           ; relay client state
     :local-candidates []
     :sock nil}))

(defn- peer-for-key [st key-hex] (get (:peers st) (get (:by-key st) key-hex)))

(defn sync-peers
  "Reconcile the peer table against the netmap: add peers the netmap authorizes
   for the overlay capability, drop peers it no longer does. Dropping is the
   important half — a revoked peer must lose its session, not merely stop being
   dialled."
  [st now-ms*]
  (let [nm (:netmap st)
        sessionable (netmap/sessionable nm (quot now-ms* 1000))
        wanted (into {} (map (fn [{:keys [peer]}] [(:node/id peer) peer]))
                     sessionable)
        relay-region (some-> (first (netmap/relays nm)) :relay/region)
        peers (into {}
                    (for [[pid p] wanted]
                      [pid (or (get (:peers st) pid)
                               (peer/peer {:suite (:suite st)
                                           :static (:static st)
                                           :peer-key (b/unhex (netmap/peer-key p))
                                           :peer-id pid
                                           :prologue (:prologue st)
                                           :relay-region relay-region
                                           :candidates (netmap/endpoints p)
                                           :initiator? (peer/should-initiate?
                                                        (:self-id st) pid)
                                           :disco-config (:disco (:config st))
                                           :policy (:policy (:config st))
                                           :now now-ms*}))]))
        removed (remove (set (keys wanted)) (keys (:peers st)))]
    (-> st
        (assoc :peers peers)
        (assoc :by-key (into {} (map (fn [[pid p]] [(b/hex (:peer-key p)) pid])) peers))
        (assoc :denied (netmap/denials nm :overlay (quot now-ms* 1000)))
        (assoc :dropped-peers (vec removed)))))

;; ── datagram routing ────────────────────────────────────────────────────────

(defn- send-out!
  "One outgoing peer datagram, routed either straight to an endpoint or wrapped
   for the relay. This is the only place that knows the difference."
  [st {:keys [route endpoint bytes peer-key]}]
  (let [sock (:sock st)]
    (case route
      :direct (do (when endpoint (udp/send! sock bytes (ep/->str endpoint))) st)
      :relay (if (and (:relay st) (relay/connected? (:relay st)))
               (let [[rc dg] (relay/client-send (:relay st) peer-key bytes (now-ms))]
                 (udp/send! sock (:bytes dg) (:to dg))
                 (assoc st :relay rc))
               st)                    ; no relay yet; the tick will retry
      st)))

(defn- apply-peer-out!
  [st peer-id {:keys [state send events] :as _out} on-event]
  (let [st (assoc-in st [:peers peer-id] state)
        st (reduce send-out! st send)]
    (doseq [e events] (on-event (assoc e :peer peer-id)))
    st))

(defn- handle-peer-datagram [st peer-id bytes from-endpoint on-event]
  (let [p (get-in st [:peers peer-id])
        out (peer/on-datagram p {:bytes bytes :now-s (now-s) :now-ms (now-ms)
                                 :from-endpoint from-endpoint})
        st (apply-peer-out! st peer-id out on-event)]
    ;; answer disco pings, and fold a peer's candidate list into disco
    (reduce (fn [st {:keys [event tx-id endpoint candidates]}]
              (case event
                :ping (let [[p dg] (peer/send-pong (get-in st [:peers peer-id])
                                                   endpoint tx-id (now-s))]
                        (send-out! (assoc-in st [:peers peer-id] p) dg))
                :call-me-maybe (update-in st [:peers peer-id] peer/note-call-me-maybe
                                          candidates (now-ms))
                st))
            st
            (:events out))))

(defn on-datagram
  "Dispatch an inbound datagram: relay frames to the relay client, peer frames to
   the peer they authenticate as. A datagram that matches nothing is dropped and
   counted — never guessed at."
  [st bytes from on-event]
  (let [relay-frame (relay/decode-frame bytes)
        peer-frame (peer/decode-frame bytes)]
    (cond
      ;; from the relay socket we expect relay frames
      (and (:relay st) (= from (:relay-addr (:relay st))) (:type relay-frame))
      (let [{:keys [state send events]} (relay/client-on-datagram
                                         (:relay st) {:bytes bytes :now (now-ms)})
            st (assoc st :relay state)
            _ (doseq [{:keys [to bytes]} send] (udp/send! (:sock st) bytes to))]
        (reduce (fn [st {:keys [event src payload] :as e}]
                  (on-event e)
                  (if (and (#{:packet :signal} event) (peer-for-key st src))
                    (let [pid (get (:by-key st) src)]
                      ;; relayed: no direct endpoint to learn from
                      (handle-peer-datagram st pid payload nil on-event))
                    st))
                st events))

      ;; a direct peer frame: we do not know which peer until it decrypts, so try
      ;; the established ones first (cheap: the frame either decrypts or not)
      (:type peer-frame)
      (let [candidates (keys (:peers st))
            endpoint (ep/parse from :reflexive)]
        (loop [ids candidates]
          (if (empty? ids)
            (do (on-event {:event :dropped :from from :reason :no-matching-peer}) st)
            (let [pid (first ids)
                  before (get-in st [:peers pid])
                  st' (handle-peer-datagram st pid bytes endpoint on-event)
                  after (get-in st' [:peers pid])]
              (if (not= before after)
                st'
                (recur (rest ids)))))))

      :else (do (on-event {:event :dropped :from from :reason :unrecognized}) st))))

;; ── tick ────────────────────────────────────────────────────────────────────

(defn tick
  [st on-event]
  (let [now-s* (now-s)
        ;; relay: connect / keepalive
        st (cond
             (nil? (:relay st)) st
             (relay/client-hello-due? (:relay st) (now-ms))
             (let [[rc dg] (relay/client-hello (:relay st) (now-ms))]
               (udp/send! (:sock st) (:bytes dg) (:to dg))
               (assoc st :relay rc))
             (relay/client-keepalive-due? (:relay st) (now-ms))
             (let [[rc dg] (relay/client-ping (:relay st) [] (now-ms))]
               (udp/send! (:sock st) (:bytes dg) (:to dg))
               (assoc st :relay rc))
             :else st)]
    (reduce (fn [st pid]
              (let [out (peer/tick (get-in st [:peers pid])
                                   {:now-s now-s* :now-ms (now-ms)
                                    :local-candidates (:local-candidates st)})]
                (apply-peer-out! st pid out on-event)))
            st
            (keys (:peers st)))))

(defn send-to
  "Send application bytes to an authorized peer over whatever path is active.
   -> state. Throws if the peer is unknown (not in the netmap, or not granted the
   overlay capability) or if its session is not established yet — a silent no-op
   here would look exactly like packet loss."
  [st peer-id payload]
  (let [self-id (:self-id st)
        _ (when-not (netmap/reachable? (:netmap st) self-id peer-id :overlay)
            (throw (ex-info "outbound overlay edge is not authorized"
                            {:from self-id :to peer-id})))
        p (or (get-in st [:peers peer-id])
              (throw (ex-info "no such authorized peer" {:peer peer-id
                                                         :known (vec (keys (:peers st)))})))
        [p dg] (peer/send-data p payload (now-s))]
    (send-out! (assoc-in st [:peers peer-id] p) dg)))

(defn reply-to
  "Send response bytes on an authenticated inbound overlay edge without
  creating reverse application authority."
  [st peer-id payload]
  (let [self-id (:self-id st)
        _ (when-not (netmap/reachable? (:netmap st) peer-id self-id :overlay)
            (throw (ex-info "inbound overlay edge is not authorized"
                            {:from peer-id :to self-id})))
        p (or (get-in st [:peers peer-id])
              (throw (ex-info "no authenticated inbound peer"
                              {:peer peer-id
                               :known (vec (keys (:peers st)))})))
        [p dg] (peer/send-data p payload (now-s))]
    (send-out! (assoc-in st [:peers peer-id] p) dg)))

(defn status
  [st]
  {:node (:self-id st)
   :netmap-version (:netmap/version (:netmap st))
   :relay (when (:relay st)
            {:region (:region (:relay st)) :connected? (relay/connected? (:relay st))})
   :local-candidates (:local-candidates st)
   :peers (mapv #(peer/summary % (now-ms)) (vals (:peers st)))
   :denied (:denied st)})

;; ── process ─────────────────────────────────────────────────────────────────

(defn start
  "-> Promise of `{:state-atom :stop :status}`.

   `config`:
     :node/id     this node's id (must match the netmap's :netmap/self)
     :static      {:priv hex :pub hex} — this node's X25519 identity
     :netmap-file path to the netmap EDN
     :listen-port UDP port for the overlay (0 = ephemeral)
     :stun-servers [\"host:port\" …] for reflexive candidates
     :dns         {:enabled? true :port 5354}
     :tick-ms     default 1000"
  [{:keys [config on-event] :or {on-event (fn [e] (println (str "[agent] " (pr-str e))))}}]
  (let [nm (load-netmap (:netmap-file config) config)
        problems (netmap/validate nm)]
    (when (seq problems)
      (throw (ex-info "netmap is not usable" {:problems problems})))
    (let [st (atom (state {:config config :nm nm}))
          tick-ms (or (:tick-ms config) 1000)]
      (-> (udp/socket
           {:port (or (:listen-port config) 0)
            ;; A datagram handler that throws must neither crash the process nor
            ;; silently swallow the packet: an exception here previously lost a
            ;; relay handshake response with no trace of why (found in the E2E).
            :on-message (fn [bytes from]
                          (try
                            (swap! st on-datagram bytes from on-event)
                            (catch :default e
                              (on-event {:event :handler-error :from from
                                         :reason (ex-message e)
                                         :stack (some-> e .-stack)}))))})
          (.then
           (fn [sock]
             (swap! st assoc :sock sock)
             (swap! st assoc :local-candidates
                    (udp/local-candidates (udp/local-port sock)))
             ;; relay client, if the netmap publishes a relay
             (when-let [r (first (netmap/relays nm))]
               (swap! st assoc :relay
                      (relay/client {:suite (:suite @st) :static (:static @st)
                                     :relay-key (b/unhex (:relay/key r))
                                     :relay-addr (relay/relay-addr r)
                                     :region (:relay/region r)
                                     :prologue (:prologue @st)})))
             (swap! st sync-peers (now-ms))
             (on-event {:event :started :node (:self-id @st)
                        :port (udp/local-port sock)
                        :peers (vec (keys (:peers @st)))
                        :denied (:denied @st)})
             ;; reflexive candidates on the *same* socket the data plane uses
             (when (seq (:stun-servers config))
               (-> (stun/candidates {:servers (:stun-servers config) :sock sock})
                   (.then (fn [{:keys [candidates symmetric?]}]
                            (swap! st update :local-candidates into candidates)
                            (on-event {:event :reflexive :candidates candidates
                                       :symmetric-nat? symmetric?})))))
             (let [timer (js/setInterval #(swap! st tick on-event) tick-ms)
                   base {:state st
                         :status #(status @st)
                         :stop (fn []
                                 (js/clearInterval timer)
                                 (udp/close! sock)
                                 (on-event {:event :stopped}))}]
               (if-not (get-in config [:dns :enabled?])
                 base
                 ;; await the DNS listener so the caller gets its actual port —
                 ;; it may be ephemeral, and a caller that has to scrape the log
                 ;; for it would be relying on log formatting
                 (-> (dns-server/start {:port (get-in config [:dns :port])
                                        :netmap-fn #(:netmap @st)
                                        :tailnet (:netmap/tailnet nm)
                                        :on-event on-event})
                     (.then (fn [d]
                              (assoc base
                                     :dns-port (udp/local-port (:sock d))
                                     :stop (fn [] ((:stop base)) ((:stop d)))))))))))))))

(defn -main [& args]
  (let [path (or (first args) "kekkai-node.edn")
        config (cljs.reader/read-string (.readFileSync fs path "utf8"))]
    (-> (start {:config config})
        (.then (fn [{:keys [status]}]
                 (js/setInterval #(println (str "[status] " (pr-str (status)))) 30000)))
        (.catch (fn [e] (js/console.error (str "agent failed: " (ex-message e)))
                  (set! (.-exitCode js/process) 1))))))
