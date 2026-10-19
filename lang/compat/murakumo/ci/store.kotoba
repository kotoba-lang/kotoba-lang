(ns murakumo.ci.store
  "Kotobase-compatible persistence seam for broker snapshots and events.
   The host injects the four IStore-shaped functions, keeping this namespace
   independent of a concrete network client."
  (:require [murakumo.canonical :as canonical]
            [murakumo.identity :as identity]))

(def broker-bucket "murakumo-ci-brokers")
(def event-stream "murakumo-ci-events")
(def default-key "default")

(defn memory-store []
  (let [docs (atom {}) streams (atom {})]
    {:put! (fn [bucket key value] (swap! docs assoc-in [bucket key] value) value)
     :get (fn [bucket key] (get-in @docs [bucket key]))
     :append! (fn [stream value]
                (let [items (get @streams stream [])
                      event-id (:ci.event/id value)
                      existing (when event-id
                                 (first (filter #(= event-id (:ci.event/id %)) items)))]
                  (if existing
                    existing
                    (let [item (assoc value :seq (inc (count items)))]
                      (swap! streams update stream (fnil conj []) item)
                      item))))
     :read (fn [stream since]
             (filterv #(> (:seq %) since) (get @streams stream [])))}))

(defn checkpoint!
  "Persist current broker state and append only events not present at the last
   checkpoint. Returns checkpoint metadata."
  ([store state] (checkpoint! store default-key state))
  ([{:keys [put! get append!]} key state]
   (let [previous (get broker-bucket key)
         emitted (or (:ci.store/emitted-events previous) 0)
         events (:murakumo.ci/events state)
         indexed (map-indexed vector events)
         delta (drop emitted indexed)]
     (doseq [[idx ev] delta]
       (append! event-stream
                (assoc ev :ci.broker/key key
                       :ci.event/id
                       (identity/graph-cid (canonical/string [key idx ev])))))
     (let [record {:ci.store/version 1
                   :ci.store/emitted-events (count events)
                   :ci.store/state state}]
       (put! broker-bucket key record)
       {:events-appended (count delta) :events-total (count events)}))))

(defn restore
  ([store] (restore store default-key))
  ([{:keys [get]} key]
   (get-in (get broker-bucket key) [:ci.store/state])))

(defn events-since
  ([store since] (events-since store default-key since))
  ([{:keys [read]} key since]
   (->> (read event-stream since)
        (filter #(= key (:ci.broker/key %)))
        (reduce (fn [{:keys [seen out]} ev]
                  (if (contains? seen (:ci.event/id ev))
                    {:seen seen :out out}
                    {:seen (conj seen (:ci.event/id ev)) :out (conj out ev)}))
                {:seen #{} :out []})
        :out)))
