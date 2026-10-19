(ns kagi.rotation-store
  "Durable content-addressed rotation DAG with fork quarantine."
  (:require [kagi.persist :as persist]
            [kagi.rotation :as rotation])
  (:import [java.nio.file Files Paths StandardCopyOption CopyOption]
           [java.nio.charset StandardCharsets]))

(defprotocol RotationStore
  (events [store])
  (event [store id])
  (put-event! [store event])
  (quarantined [store]))

(defn empty-state [] {:version 1 :events {} :quarantined #{}})

(defn- read-state [path]
  (let [p (Paths/get path (make-array String 0))]
    (if (Files/exists p (make-array java.nio.file.LinkOption 0))
      (persist/<-edn (Files/readString p StandardCharsets/UTF_8))
      (empty-state))))

(defn- atomic-write! [path state]
  (let [p (Paths/get path (make-array String 0))
        parent (.getParent (.toAbsolutePath p))
        _ (Files/createDirectories parent (make-array java.nio.file.attribute.FileAttribute 0))
        tmp (Files/createTempFile parent ".rotation-dag-" ".edn"
                                  (make-array java.nio.file.attribute.FileAttribute 0))]
    (Files/writeString tmp (persist/->edn state) StandardCharsets/UTF_8
                       (into-array java.nio.file.OpenOption []))
    (try
      (Files/move tmp p (into-array CopyOption [StandardCopyOption/ATOMIC_MOVE
                                                 StandardCopyOption/REPLACE_EXISTING]))
      (catch java.nio.file.AtomicMoveNotSupportedException _
        (Files/move tmp p (into-array CopyOption [StandardCopyOption/REPLACE_EXISTING]))))))

(defn- validate-event! [state e]
  (when-not (= (:rotation/id e) (rotation/event-id (dissoc e :rotation/id)))
    (throw (ex-info "rotation event CID mismatch" {:rotation/id (:rotation/id e)})))
  (doseq [parent (:rotation/parents e)]
    (when-not (contains? (:events state) parent)
      (throw (ex-info "rotation parent missing" {:parent parent :rotation/id (:rotation/id e)}))))
  e)

(defrecord FileRotationStore [path lock]
  RotationStore
  (events [_] (vals (:events (read-state path))))
  (event [_ id] (get-in (read-state path) [:events id]))
  (quarantined [_] (:quarantined (read-state path)))
  (put-event! [_ e]
    (locking lock
      (let [state (read-state path)
            _ (validate-event! state e)
            peers (filter #(and (= (:rotation/subject %) (:rotation/subject e))
                                (= (:rotation/purpose %) (:rotation/purpose e))
                                (= (:rotation/from-epoch %) (:rotation/from-epoch e))
                                (not= (:rotation/id %) (:rotation/id e)))
                          (vals (:events state)))
            fork-ids (into #{(:rotation/id e)} (map :rotation/id) peers)
            next-state (cond-> (assoc-in state [:events (:rotation/id e)] e)
                         (seq peers) (update :quarantined into fork-ids))]
        (atomic-write! path next-state)
        {:stored? true :rotation/id (:rotation/id e)
         :quarantined? (contains? (:quarantined next-state) (:rotation/id e))}))))

(defn file-store [path] (->FileRotationStore path (Object.)))
