(ns kekkai.node.packet-bridge
  "Local-only framed bridge between a privileged OS TUN adapter and Kekkai."
  (:require [kekkai.node.agent :as agent]
            [kekkai.node.packet :as packet]
            ["node:fs" :as fs]
            ["node:net" :as net]))

(defn- uint32 [value]
  (let [buffer (.alloc js/Buffer 4)]
    (.writeUInt32BE buffer value 0)
    buffer))

(defn- write-packet! [socket bytes]
  (let [body (.from js/Buffer (clj->js bytes))]
    (.write socket (.concat js/Buffer #js [(uint32 (.-length body)) body]))))

(defn- consume!
  [buffer on-packet]
  (loop [offset 0]
    (if (> (+ offset 4) (.-length buffer))
      (.subarray buffer offset)
      (let [length (.readUInt32BE buffer offset)
            end (+ offset 4 length)]
        (cond
          (or (zero? length) (> length packet/max-packet-bytes))
          (throw (js/Error. "invalid TUN packet length"))

          (> end (.-length buffer))
          (.subarray buffer offset)

          :else
          (do (on-packet (vec (js/Uint8Array.
                               (.subarray buffer (+ offset 4) end))))
              (recur end)))))))

(defn- prepare-socket-path! [socket-path]
  (when (and (not= "win32" (.-platform js/process))
             (.existsSync fs socket-path))
    (let [stat (.lstatSync fs socket-path)]
      (when-not (.isSocket stat)
        (throw (js/Error. "packet bridge path exists and is not a socket")))
      (.unlinkSync fs socket-path))))

(defn start
  [{:keys [config on-event]
    :or {on-event (fn [event] (println (pr-str event)))}}]
  (let [socket-path (get-in config [:packet-bridge :path])
        client (atom nil)
        handle (atom nil)
        emit (fn [event] (on-event event))
        agent-event
        (fn [event]
          (if (and (= :data (:event event))
                   (packet/unframe (:payload event)))
            (let [decoded (packet/unframe (:payload event))
                  state (some-> @handle :state deref)]
              (if (and state
                       (packet/inbound-authorized?
                        (:netmap state) (:peer event))
                       @client)
                (write-packet! @client (:packet/bytes decoded))
                (emit {:event :packet-dropped :peer (:peer event)
                       :reason :unauthorized-or-no-tun})))
            (emit event)))]
    (when-not (seq socket-path)
      (throw (js/Error. "packet-bridge :path is required")))
    (prepare-socket-path! socket-path)
    (-> (agent/start {:config config :on-event agent-event})
        (.then
         (fn [agent-handle]
           (reset! handle agent-handle)
           (let [server
                 (.createServer
                  net
                  (fn [socket]
                    (if @client
                      (.destroy socket (js/Error. "packet bridge already connected"))
                      (let [buffer (atom (.alloc js/Buffer 0))]
                        (reset! client socket)
                        (emit {:event :packet-bridge-connected})
                        (.on socket "data"
                             (fn [chunk]
                               (try
                                 (swap! buffer
                                        (fn [current]
                                          (consume!
                                           (.concat js/Buffer
                                                    #js [current chunk])
                                           (fn [bytes]
                                             (if-let [parsed (packet/parse bytes)]
                                               (let [state @(:state agent-handle)
                                                     peer
                                                     (packet/route-peer
                                                      (:netmap state) parsed
                                                      (agent/now-s))]
                                                 (if peer
                                                   (swap! (:state agent-handle)
                                                          agent/send-to peer
                                                          (packet/frame
                                                           (:packet/bytes parsed)))
                                                   (emit
                                                    {:event :packet-dropped
                                                     :reason :no-signed-route})))
                                               (emit {:event :packet-dropped
                                                      :reason :malformed-ip}))))))
                                 (catch :default error
                                   (emit {:event :packet-bridge-error
                                          :reason (ex-message error)})
                                   (.destroy socket)))))
                        (.on socket "close"
                             #(when (= socket @client)
                                (reset! client nil)
                                (emit {:event :packet-bridge-disconnected})))))))]
             (.listen server socket-path
                      #(emit {:event :packet-bridge-listening
                              :path socket-path}))
             (assoc agent-handle
                    :packet-server server
                    :stop
                    (fn []
                      (when-let [socket @client] (.destroy socket))
                      (.close server)
                      ((:stop agent-handle))
                      (when (and (not= "win32" (.-platform js/process))
                                 (.existsSync fs socket-path)
                                 (.isSocket (.lstatSync fs socket-path)))
                        (.unlinkSync fs socket-path))))))))))

(defn -main [& args]
  (let [path (or (first args) "kekkai-tun-agent.edn")
        config (cljs.reader/read-string (.readFileSync fs path "utf8"))]
    (-> (start {:config config})
        (.catch
         (fn [error]
           (js/console.error (str "packet bridge failed: " (ex-message error)))
           (set! (.-exitCode js/process) 1))))))
