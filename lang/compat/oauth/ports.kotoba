(ns oauth.ports)

(defprotocol IOAuth
  (exchange-token! [port token-request])
  (introspect! [port token-ref]))

(defprotocol IStateStore
  (consume-state! [store state]
    "Atomically consume an OAuth state value. Return true only the first time."))

(defn memory-state-store
  ([] (memory-state-store #{}))
  ([used]
   (let [state (atom (set used))]
     (reify IStateStore
       (consume-state! [_ state-value]
         (let [accepted? (atom false)]
           (swap! state
                  (fn [s]
                    (if (contains? s state-value)
                      s
                      (do (reset! accepted? true)
                          (conj s state-value)))))
           @accepted?))))))
