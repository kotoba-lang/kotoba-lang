(ns kagi.witness-service
  "Independently deployable witness core. Network transports exchange only signed
  checkpoints through submit!/gossip-snapshot; no secret or private key crosses it."
  (:require [kagi.persist :as persist]
            [kagi.witness :as witness])
  (:import [java.nio.file Files Paths StandardCopyOption CopyOption]
           [java.nio.charset StandardCharsets]
           [java.nio.channels FileChannel]
           [java.nio.file StandardOpenOption]))

(defprotocol WitnessService
  (submit! [service checkpoint])
  (gossip-snapshot [service])
  (conflicts [service]))

(defonce ^:private path-locks (atom {}))

(defn- process-lock [path]
  (or (get @path-locks path)
      (get (swap! path-locks #(if (contains? % path) % (assoc % path (Object.)))) path)))

(defn- load-state [path]
  (let [p (Paths/get path (make-array String 0))]
    (if (Files/exists p (make-array java.nio.file.LinkOption 0))
      (persist/<-edn (Files/readString p StandardCharsets/UTF_8))
      {:version 1 :checkpoints {} :conflicts []})))

(defn- save-state! [path state]
  (let [p (Paths/get path (make-array String 0))
        parent (.getParent (.toAbsolutePath p))
        _ (Files/createDirectories parent (make-array java.nio.file.attribute.FileAttribute 0))
        tmp (Files/createTempFile parent ".witness-" ".edn"
                                  (make-array java.nio.file.attribute.FileAttribute 0))]
    (Files/writeString tmp (persist/->edn state) StandardCharsets/UTF_8
                       (into-array java.nio.file.OpenOption []))
    (try
      (Files/move tmp p (into-array CopyOption [StandardCopyOption/ATOMIC_MOVE
                                                 StandardCopyOption/REPLACE_EXISTING]))
      (catch java.nio.file.AtomicMoveNotSupportedException _
        (Files/move tmp p (into-array CopyOption [StandardCopyOption/REPLACE_EXISTING]))))))

(defn- with-file-lock [path f]
  (let [lock-path (Paths/get (str path ".lock") (make-array String 0))
        parent (.getParent (.toAbsolutePath lock-path))]
    (Files/createDirectories parent (make-array java.nio.file.attribute.FileAttribute 0))
    (locking (process-lock path)
      (with-open [channel (FileChannel/open
                           lock-path
                           (into-array StandardOpenOption
                                       [StandardOpenOption/CREATE StandardOpenOption/WRITE]))
                  _lock (.lock channel)]
        (f)))))

(defrecord FileWitnessService [path crypto public-of lock]
  WitnessService
  (gossip-snapshot [_] (vec (vals (:checkpoints (load-state path)))))
  (conflicts [_] (:conflicts (load-state path)))
  (submit! [_ cp]
    (locking lock
      (with-file-lock
       path
       (fn []
        (let [pub (public-of (:checkpoint/witness cp))]
        (when-not (and pub (witness/valid-checkpoint? crypto cp pub))
          (throw (ex-info "invalid or unknown witness checkpoint"
                          {:witness (:checkpoint/witness cp)
                           :checkpoint/id (:checkpoint/id cp)})))
        (let [state (load-state path)
              same-witness (->> (vals (:checkpoints state))
                                (filter #(= (:checkpoint/witness cp)
                                            (:checkpoint/witness %))))
              same-seq (filter #(= (:checkpoint/seq cp) (:checkpoint/seq %))
                               same-witness)
              latest (when (seq same-witness)
                       (apply max-key :checkpoint/seq same-witness))
              idempotent? (some #(= (:checkpoint/id cp) (:checkpoint/id %)) same-seq)
              equivocation? (and (seq same-seq) (not idempotent?))
              expected-seq (if latest (inc (:checkpoint/seq latest)) 1)]
          (when (and (not idempotent?) (not equivocation?)
                     (not= expected-seq (:checkpoint/seq cp)))
            (throw (ex-info "witness checkpoint sequence gap or rollback"
                            {:expected expected-seq :actual (:checkpoint/seq cp)})))
          (when (and latest (not idempotent?) (not equivocation?)
                     (not= (:checkpoint/id latest) (:checkpoint/previous cp)))
            (throw (ex-info "witness checkpoint previous link mismatch"
                            {:expected (:checkpoint/id latest)
                             :actual (:checkpoint/previous cp)})))
          (if idempotent?
            {:accepted? (empty? (:conflicts state))
             :split-view? (boolean (seq (:conflicts state)))
             :idempotent? true
             :checkpoint/id (:checkpoint/id cp)}
            (let [merged (witness/merge-gossip (vals (:checkpoints state)) [cp])
                  next-state {:version 1
                              :checkpoints (into {} (map (juxt :checkpoint/id identity))
                                                 (:checkpoints merged))
                              :conflicts (:conflicts merged)}]
              (save-state! path next-state)
              {:accepted? (not (:split-view? merged))
               :split-view? (:split-view? merged)
               :idempotent? false
               :checkpoint/id (:checkpoint/id cp)})))))))))

(defn file-service [path crypto public-of]
  (->FileWitnessService path crypto public-of (Object.)))
