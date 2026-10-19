(ns fileprovider.model
  "Portable policy for Finder/File Provider residency and synchronization.

  Apple owns placeholders and byte materialisation. This model owns every
  decision about when those operations are allowed, so the Swift extension is
  only a transport adapter and Cloud Itonami can enforce the same state machine
  on every platform.")

(def schedules #{:continuous :manual :paused})
(def residencies #{:online-only :automatic :pinned})
(def local-states #{:placeholder :downloading :materialized :dirty
                    :uploading :conflict :error})

(def defaults
  {:sync/schedule :continuous
   :sync/residency :automatic
   :sync/local-state :placeholder
   :sync/remote-verified? true
   :sync/pending-local? false})

(defn valid?
  [{:keys [:sync/schedule :sync/residency :sync/local-state] :as item}]
  (and (map? item)
       (contains? schedules schedule)
       (contains? residencies residency)
       (contains? local-states local-state)
       (boolean? (:sync/remote-verified? item))
       (boolean? (:sync/pending-local? item))))

(defn set-schedule [item schedule]
  {:pre [(contains? schedules schedule)]}
  (assoc item :sync/schedule schedule))

(defn set-residency [item residency]
  {:pre [(contains? residencies residency)]}
  (assoc item :sync/residency residency))

(defn should-poll?
  "Continuous sync alone runs a background poll. Manual still permits an
  explicit sync-now; paused permits neither network direction."
  [item]
  (= :continuous (:sync/schedule item)))

(defn sync-now-allowed? [item]
  (not= :paused (:sync/schedule item)))

(defn should-materialize?
  "Pinned items materialise proactively. Automatic and online-only items
  materialise only in response to an open/download request."
  [item event]
  (and (not= :paused (:sync/schedule item))
       (or (= :pinned (:sync/residency item))
           (contains? #{:open :download-request} event))))

(defn can-evict?
  "Never discard the only known-good bytes or an unuploaded local edit."
  [{:keys [:sync/residency :sync/local-state :sync/remote-verified?
           :sync/pending-local?]}]
  (and (not= :pinned residency)
       remote-verified?
       (not pending-local?)
       (= :materialized local-state)))

(defn command
  "Translate one event into platform-neutral effects. Effects are data; the
  Swift shim or Cloud Itonami performs them and reports the result back."
  [item event]
  (case event
    :open (cond
            (= :paused (:sync/schedule item)) [:fail-paused]
            (= :placeholder (:sync/local-state item)) [:download]
            :else [])
    :download-request (if (should-materialize? item event) [:download] [:fail-paused])
    :sync-now (if (sync-now-allowed? item) [:scan-local :fetch-remote] [:fail-paused])
    :disk-pressure (if (can-evict? item) [:evict] [])
    :pin (if (= :placeholder (:sync/local-state item)) [:download] [])
    :unpin (if (can-evict? (assoc item :sync/residency :automatic)) [:evict] [])
    []))

(defn transition [item event]
  (case event
    :download-started (assoc item :sync/local-state :downloading)
    :download-succeeded (assoc item :sync/local-state :materialized
                               :sync/remote-verified? true)
    :local-edited (assoc item :sync/local-state :dirty
                         :sync/pending-local? true)
    :upload-started (assoc item :sync/local-state :uploading)
    :upload-succeeded (assoc item :sync/local-state :materialized
                             :sync/pending-local? false
                             :sync/remote-verified? true)
    :remote-conflict (assoc item :sync/local-state :conflict)
    :evicted (assoc item :sync/local-state :placeholder)
    :failed (assoc item :sync/local-state :error)
    item))

(defn badge
  "Stable badge vocabulary shared by Finder and the web UI."
  [{:keys [:sync/local-state :sync/residency]}]
  (cond
    (= :conflict local-state) :conflict
    (= :error local-state) :error
    (= :uploading local-state) :uploading
    (= :downloading local-state) :downloading
    (= :dirty local-state) :pending-upload
    (= :placeholder local-state) :cloud
    (= :pinned residency) :available-offline
    :else :available))
