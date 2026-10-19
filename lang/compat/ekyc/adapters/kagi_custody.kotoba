(ns ekyc.adapters.kagi-custody
  (:require [ekyc.adapters.provider :as provider]))

(defprotocol IKagiStore
  (put-object! [store payload opts]))

(defn custody-key [payload]
  (str "ekyc/" (:session-id payload) "/" (name (:check payload))))

(defn kagi-custody [store opts]
  (reify provider/IEvidenceCustody
    (commit-evidence! [_ payload call-opts]
      (let [opts (merge opts call-opts)
            key (or (:key payload) (custody-key payload))
            object (put-object! store (assoc payload :kagi/key key) opts)]
        {:evidence-ref (or (:kagi/ref object)
                           (:ref object)
                           (str "kagi://" key))
         :hash (:hash object)
         :stored-at (:stored-at object)}))))

(defn memory-kagi-store
  ([] (memory-kagi-store (atom {})))
  ([state]
   (reify IKagiStore
     (put-object! [_ payload _opts]
       (let [key (:kagi/key payload)
             object {:kagi/ref (str "kagi://" key)
                     :hash (str "sha256:" (hash payload))
                     :stored-at (:stored-at payload)
                     :payload payload}]
         (swap! state assoc key object)
         object)))))
