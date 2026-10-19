(ns webauthn.ports)

(defprotocol IWebAuthn
  (register! [port challenge attestation])
  (authenticate! [port challenge assertion])
  (derive-prf! [port envelope]))

(defprotocol IChallengeStore
  (consume-challenge! [store challenge-id]
    "Atomically mark challenge-id consumed. Return true only the first time."))

(defn memory-challenge-store
  ([] (memory-challenge-store #{}))
  ([used]
   (let [state (atom (set used))]
     (reify IChallengeStore
       (consume-challenge! [_ challenge-id]
         (let [accepted? (atom false)]
           (swap! state
                  (fn [s]
                    (if (contains? s challenge-id)
                      s
                      (do (reset! accepted? true)
                          (conj s challenge-id)))))
           @accepted?))))))
