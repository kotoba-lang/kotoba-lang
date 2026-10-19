(ns kotobase.protocols.store
  "Protocol projection host operations. No dependency on kotobase/IStore."
  (:refer-clojure :exclude [get]))

(defn put [store coll k v] ((:put store) coll k v))
(defn get [store coll k] ((:get store) coll k))
(defn list-keys [store coll] ((:list store) coll))
(defn append [store stream event] ((:append store) stream event))
(defn read-events [store stream since] ((:read store) stream since))

(defn memory-store []
  (let [state (atom {:docs {} :streams {} :seq 0})]
    {:put (fn [coll k v] (swap! state assoc-in [:docs coll k] v) v)
     :get (fn [coll k] (get-in @state [:docs coll k]))
     :list (fn [coll] (vec (keys (get-in @state [:docs coll]))))
     :append (fn [stream event]
               (let [result (volatile! nil)]
                 (swap! state
                        (fn [{:keys [seq] :as current}]
                          (let [event' (assoc event :seq (inc seq))]
                            (vreset! result event')
                            (-> current
                                (assoc :seq (inc seq))
                                (update-in [:streams stream] (fnil conj []) event')))))
                 @result))
     :read (fn [stream since]
             (->> (get-in @state [:streams stream])
                  (filter #(> (:seq %) (or since 0)))
                  (sort-by :seq)
                  vec))}))
