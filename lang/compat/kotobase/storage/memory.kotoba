(ns kotobase.storage.memory
  "Reference implementation and oracle for the Kotobase storage contract."
  (:require [kotobase.storage.core :as storage]))

(defrecord MemoryStore [state]
  storage/IBlockStore
  (-put-blocks! [_ blocks]
    (swap! state update :blocks
           (fn [current]
             (reduce
              (fn [acc {:keys [cid bytes]}]
                (if-let [existing (get acc cid)]
                  (if (= (vec existing) (vec bytes))
                    acc
                    (throw
                     (ex-info "CID already has different bytes"
                              {:type :kotobase.storage/cid-collision
                               :cid cid})))
                  (assoc acc cid bytes)))
              current blocks)))
    (mapv :cid blocks))
  (-get-blocks [_ cids]
    (select-keys (:blocks @state) cids))

  storage/IRefStore
  (-read-ref [_ name] (get-in @state [:refs name]))
  (-compare-and-set-ref! [_ name expected next]
    (let [result (volatile! nil)]
      (swap! state
             (fn [current]
               (let [present (get-in current [:refs name])
                     actual (:cid present)]
                 (if (= actual expected)
                   (let [version (inc (or (:version present) 0))
                         ref {:cid next :version version}]
                     (vreset! result
                              {:published? true :current next
                               :version version})
                     (assoc-in current [:refs name] ref))
                   (do
                     (vreset! result
                              {:published? false :current actual
                               :version (:version present)})
                     current)))))
      @result))

  storage/IBackendCapabilities
  (-capabilities [_]
    #{:immutable-blocks :cid-addressed-read :conditional-ref
      :linearizable-ref :batch-get :batch-put}))

(defn memory-store []
  (->MemoryStore (atom {:blocks {} :refs {}})))

(defn snapshot [store] @(:state store))
