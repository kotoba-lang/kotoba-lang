(ns kagi.rotation-scheduler
  "Deterministic, durable rotation scheduler. It proposes due work; execution
  remains behind Governor and is protected by renewable ownership leases."
  (:require [kagi.persist :as persist])
  (:import [java.time Instant Duration]
           [java.nio.file Files Paths StandardCopyOption CopyOption
            StandardOpenOption OpenOption]
           [java.nio.channels FileChannel]
           [java.nio.charset StandardCharsets]))

(def default-periods
  {:item-dek (Duration/ofDays 90)
   :high-value-item-dek (Duration/ofDays 30)
   :recipient-kem (Duration/ofDays 90)
   :identity-signing (Duration/ofDays 365)
   :high-value-identity-signing (Duration/ofDays 90)
   :vmk (Duration/ofDays 365)
   :high-value-vmk (Duration/ofDays 182)
   :device-unlock (Duration/ofDays 365)})

(defn due?
  [created-at period now]
  (not (.isBefore (Instant/parse now)
                  (.plus (Instant/parse created-at) ^Duration period))))

(defn due-rotations
  "Return idempotent proposal maps. last-rotated-at wins over created-at."
  [records now & [{:keys [periods] :or {periods default-periods}}]]
  (->> records
       (keep (fn [r]
               (let [class (if (:key/high-value? r)
                             (keyword (str "high-value-" (name (:key/class r))))
                             (:key/class r))
                     period (get periods class)
                     since (or (:key/last-rotated-at r) (:key/created-at r))]
                 (when (and (= :active (:key/state r)) period since
                            (due? since period now))
                   {:rotation/job-id (str (:key/id r) "/" (:key/epoch r))
                    :rotation/key-id (:key/id r)
                    :rotation/purpose (:key/purpose r)
                    :rotation/from-epoch (:key/epoch r)
                    :rotation/reason :scheduled
                    :rotation/due-at (str (.plus (Instant/parse since) period))}))))
       (sort-by :rotation/due-at)
       vec))

(defprotocol JobStore
  (enqueue! [store proposals now])
  (claim! [store owner now lease-seconds])
  (complete! [store job-id owner now result])
  (fail! [store job-id owner now error max-attempts])
  (jobs [store]))

(defn- empty-state [] {:version 1 :jobs {}})

(defn- path-of [path]
  (Paths/get path (make-array String 0)))

(defn- read-state [path]
  (let [p (path-of path)]
    (if (Files/exists p (make-array java.nio.file.LinkOption 0))
      (persist/<-edn (Files/readString p StandardCharsets/UTF_8))
      (empty-state))))

(defn- atomic-write! [path state]
  (let [p (path-of path)
        parent (.getParent (.toAbsolutePath p))
        _ (Files/createDirectories parent (make-array java.nio.file.attribute.FileAttribute 0))
        tmp (Files/createTempFile parent ".rotation-jobs-" ".edn"
                                  (make-array java.nio.file.attribute.FileAttribute 0))]
    (Files/writeString tmp (persist/->edn state) StandardCharsets/UTF_8
                       (into-array OpenOption []))
    (try
      (Files/move tmp p (into-array CopyOption [StandardCopyOption/ATOMIC_MOVE
                                                 StandardCopyOption/REPLACE_EXISTING]))
      (catch java.nio.file.AtomicMoveNotSupportedException _
        (Files/move tmp p (into-array CopyOption [StandardCopyOption/REPLACE_EXISTING]))))))

(defn- transact! [path f]
  (let [lock-path (path-of (str path ".lock"))
        parent (.getParent (.toAbsolutePath lock-path))]
    (Files/createDirectories parent (make-array java.nio.file.attribute.FileAttribute 0))
    (with-open [channel (FileChannel/open lock-path
                                          (into-array StandardOpenOption
                                                      [StandardOpenOption/CREATE
                                                       StandardOpenOption/WRITE]))
                _lock (.lock channel)]
      (let [[state result] (f (read-state path))]
        (atomic-write! path state)
        result))))

(defn- available? [job now]
  (and (contains? #{:queued :retry :running} (:job/status job))
       (or (nil? (:job/next-at job))
           (not (pos? (compare (:job/next-at job) now))))
       (or (not= :running (:job/status job))
           (not (pos? (compare (:job/lease-until job) now))))))

(defrecord FileJobStore [path]
  JobStore
  (jobs [_] (vals (:jobs (read-state path))))
  (enqueue! [_ proposals now]
    (transact!
     path
     (fn [state]
       (let [next (reduce (fn [s proposal]
                            (update s :jobs
                                    #(if (contains? % (:rotation/job-id proposal))
                                       %
                                       (assoc % (:rotation/job-id proposal)
                                              (merge proposal {:job/status :queued
                                                               :job/attempts 0
                                                               :job/enqueued-at now})))))
                          state proposals)]
         [next (count (:jobs next))]))))
  (claim! [_ owner now lease-seconds]
    (transact!
     path
     (fn [state]
       (if-let [job (->> (vals (:jobs state))
                         (filter #(available? % now))
                         (sort-by (juxt :rotation/due-at :rotation/job-id))
                         first)]
         (let [claimed (assoc job :job/status :running :job/owner owner
                                  :job/claimed-at now
                                  :job/lease-until
                                  (str (.plusSeconds (Instant/parse now)
                                                     (long lease-seconds))))]
           [(assoc-in state [:jobs (:rotation/job-id job)] claimed) claimed])
         [state nil]))))
  (complete! [_ job-id owner now result]
    (transact!
     path
     (fn [state]
       (let [job (get-in state [:jobs job-id])]
         (when-not (and (= :running (:job/status job)) (= owner (:job/owner job)))
           (throw (ex-info "rotation job completion lease mismatch" {:job-id job-id})))
         (let [done (-> job
                        (assoc :job/status :complete :job/completed-at now :job/result result)
                        (dissoc :job/owner :job/lease-until))]
           [(assoc-in state [:jobs job-id] done) done])))))
  (fail! [_ job-id owner now error max-attempts]
    (transact!
     path
     (fn [state]
       (let [job (get-in state [:jobs job-id])]
         (when-not (and (= :running (:job/status job)) (= owner (:job/owner job)))
           (throw (ex-info "rotation job failure lease mismatch" {:job-id job-id})))
         (let [attempts (inc (:job/attempts job))
               dead? (>= attempts max-attempts)
               delay-seconds (* 60 (bit-shift-left 1 (min 10 (dec attempts))))
               failed (cond-> (-> job
                                  (assoc :job/status (if dead? :dead-letter :retry)
                                         :job/attempts attempts :job/last-error (str error)
                                         :job/failed-at now)
                                  (dissoc :job/owner :job/lease-until))
                        (not dead?) (assoc :job/next-at
                                           (str (.plusSeconds (Instant/parse now)
                                                              delay-seconds))))]
           [(assoc-in state [:jobs job-id] failed) failed]))))))

(defn file-job-store [path] (->FileJobStore path))

(defn run-due!
  "Persist due proposals, then claim and execute them one at a time. Worker is
  expected to submit a governed rotation request; exceptions are durably
  retried with bounded exponential backoff."
  [store records now {:keys [owner worker lease-seconds max-attempts max-jobs periods]
                      :or {lease-seconds 300 max-attempts 5 max-jobs 10}}]
  (when-not (and (seq owner) (ifn? worker) (pos-int? max-jobs))
    (throw (ex-info "scheduler owner, worker and positive max-jobs are required" {})))
  (enqueue! store (due-rotations records now {:periods (or periods default-periods)}) now)
  (loop [results []]
    (if (>= (count results) max-jobs)
      results
      (if-let [job (claim! store owner now lease-seconds)]
      (let [outcome (try
                      {:ok? true :value (worker job)}
                      (catch Exception e {:ok? false :error e}))]
        (if (:ok? outcome)
          (recur (conj results (complete! store (:rotation/job-id job) owner now
                                          (:value outcome))))
          (recur (conj results (fail! store (:rotation/job-id job) owner now
                                      (.getMessage ^Exception (:error outcome))
                                      max-attempts)))))
        results))))
