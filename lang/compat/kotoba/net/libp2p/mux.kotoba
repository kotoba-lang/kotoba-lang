(ns kotoba.net.libp2p.mux
  "Concurrent Yamux stream driver for a dialed connection.

  One reader owns the encrypted socket and dispatches frames to per-stream
  inboxes.  This is required by PubSub: while we keep an outbound writer open,
  the remote peer independently opens its inbound-to-us writer on the same
  authenticated connection."
  (:require [kotoba.net.libp2p.connection :as connection]
            [libp2p.yamux :as yamux]))

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn start!
  [secure session supported handle]
  (let [state {:secure secure :session session :supported (set supported)
               :handle handle :inboxes (atom {}) :closed (atom #{})
               :connection-closed? (atom false) :lock (Object.)}
        write! (fn [frame] (locking (:lock state) ((:write! secure) frame)))
        port-for
        (fn port-for [stream-id]
          {:read! (fn [n]
                    (loop []
                      (let [available (count (get @(:inboxes state) stream-id))]
                        (when (< available n)
                          (when (or @(:connection-closed? state)
                                    (contains? @(:closed state) stream-id))
                            (fail! :stream/closed-early
                                   {:stream-id stream-id :wanted n :available available}))
                          (Thread/sleep 2)
                          (recur))))
                    (let [taken (vec (take n (get @(:inboxes state) stream-id)))]
                      (swap! (:inboxes state) update stream-id #(vec (drop n %)))
                      taken))
           :write! #(write! (yamux/data-frame stream-id #{} (vec %)))
           :close! (fn []
                     (locking (:lock state)
                       (let [closed-stream (yamux/close-stream @session stream-id)]
                         (vreset! session (:session closed-stream))
                         ((:write! secure) (:out closed-stream)))))})
        reader
        (future
          (try
            (loop []
              (let [frame (connection/read-yamux-frame secure)
                    id (:stream-id frame) flags (:flags frame)]
                (cond
                  (= :ping (:type frame))
                  (when-not (contains? flags :ack)
                    (write! (yamux/ping (:length frame) :ack? true)))

                  (= :go-away (:type frame))
                  (reset! (:connection-closed? state) true)

                  (and (contains? flags :syn) (not (contains? @(:inboxes state) id)))
                  (let [accepted (locking (:lock state)
                                   (let [accepted (yamux/accept-stream @session id)]
                                     (vreset! session (:session accepted)) accepted))]
                    (swap! (:inboxes state) assoc id [])
                    (write! (:out accepted))
                    (future
                      (try
                        (let [port (port-for id)
                              protocol (connection/accept-negotiation port supported)]
                          (handle protocol port))
                        (catch Exception _ nil))))

                  (= :data (:type frame))
                  (do (when (contains? @(:inboxes state) id)
                        (swap! (:inboxes state) update id into (:payload frame)))
                      (when (or (contains? flags :fin) (contains? flags :rst))
                        (swap! (:closed state) conj id)))

                  :else nil)
                (when-not @(:connection-closed? state) (recur))))
            (catch Exception _ (reset! (:connection-closed? state) true))))]
    (assoc state :reader reader :port-for port-for :write-frame! write!)))

(defn open-stream! [mux protocol]
  (let [{:keys [stream-id out]}
        (locking (:lock mux)
          (let [opened (yamux/open-stream @(:session mux))]
            (vreset! (:session mux) (:session opened)) opened))]
    (swap! (:inboxes mux) assoc stream-id [])
    ((:write-frame! mux) out)
    (let [port ((:port-for mux) stream-id)]
      (connection/negotiate port protocol)
      (assoc port :stream-id stream-id))))

(defn stop! [mux]
  (reset! (:connection-closed? mux) true)
  (future-cancel (:reader mux))
  nil)
