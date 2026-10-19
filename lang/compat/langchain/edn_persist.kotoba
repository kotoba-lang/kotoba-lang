(ns langchain.edn-persist
  "JVM host for the repository-first persistence contract.

  Transactions remain editable, local EDN while an agent is running.  The
  cloud host may later encrypt and publish the whole state file through Kagi,
  DataLad, and a Kotobase head.  Every append re-reads the file while holding a
  process-safe lock, so unrelated keys written directly by an agent are
  preserved instead of being replaced by a stale in-memory snapshot."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import [java.nio.channels FileChannel]
           [java.nio.charset StandardCharsets]
           [java.nio.file AtomicMoveNotSupportedException CopyOption Files
            OpenOption StandardCopyOption StandardOpenOption]))

(def ^:private streams-key :kotoba.agent/streams)
(def ^:private sequence-key :kotoba.agent/sequence)
(defonce ^:private local-locks (atom {}))

(defn- valid-stream? [stream]
  (and (string? stream) (seq stream) (<= (count stream) 1024)))

(defn- read-state [file]
  (if (.isFile file)
    (let [value (edn/read-string
                 {:readers {}
                  :default (fn [tag _]
                             (throw (ex-info "tagged repository EDN denied"
                                             {:type :langchain.edn-persist/tagged-edn
                                              :tag tag})))}
                 (slurp file))]
      (when-not (map? value)
        (throw (ex-info "repository EDN root must be a map"
                        {:type :langchain.edn-persist/invalid-root})))
      value)
    {}))

(defn- atomic-write! [file value]
  (let [parent (or (.getParentFile file) (io/file "."))
        _ (.mkdirs parent)
        tmp (.toFile (Files/createTempFile (.toPath parent) ".state-" ".edn"
                                           (make-array java.nio.file.attribute.FileAttribute 0)))
        bytes (.getBytes (str (pr-str value) "\n") StandardCharsets/UTF_8)]
    (try
      (Files/write (.toPath tmp) bytes
                   (into-array OpenOption [StandardOpenOption/WRITE
                                           StandardOpenOption/TRUNCATE_EXISTING]))
      (try
        (Files/move (.toPath tmp) (.toPath file)
                    (into-array CopyOption [StandardCopyOption/ATOMIC_MOVE
                                            StandardCopyOption/REPLACE_EXISTING]))
        (catch AtomicMoveNotSupportedException _
          (Files/move (.toPath tmp) (.toPath file)
                      (into-array CopyOption [StandardCopyOption/REPLACE_EXISTING]))))
      (finally
        (Files/deleteIfExists (.toPath tmp))))))

(defn with-state-lock
  "Run F while holding the canonical state file's JVM-local and process-wide
  lock. Repository hosts use this same boundary for direct EDN edits so an
  actor append cannot overwrite a cooperating agent edit (or vice versa)."
  [file f]
  (let [parent (or (.getParentFile file) (io/file "."))
        _ (.mkdirs parent)
        lock-file (io/file parent (str "." (.getName file) ".lock"))
        path (.getCanonicalPath lock-file)
        local-lock (get (swap! local-locks
                               #(if (contains? % path) % (assoc % path (Object.))))
                        path)]
    ;; FileChannel.lock coordinates processes but throws OverlappingFileLockException
    ;; for competing threads in this JVM. Serialize those threads first.
    (locking local-lock
      (with-open [channel (FileChannel/open
                           (.toPath lock-file)
                           (into-array OpenOption [StandardOpenOption/CREATE
                                                   StandardOpenOption/WRITE]))
                  _lock (.lock channel)]
        (f)))))

(defn host
  "Return a `langchain.persist/scoped` compatible append/read host backed by
  FILE. FILE is the same editable `state.edn` that the repository publisher
  encrypts. Existing non-persistence keys are retained on every append."
  [file]
  (let [file (io/file file)]
    {:append
     (fn [stream event]
       (when-not (valid-stream? stream)
         (throw (ex-info "invalid repository persistence stream"
                         {:type :langchain.edn-persist/invalid-stream
                          :stream stream})))
       (when-not (map? event)
         (throw (ex-info "repository persistence event must be a map"
                         {:type :langchain.edn-persist/invalid-event})))
       (with-state-lock
         file
         (fn []
           (let [state (read-state file)
                 sequence (inc (long (or (get state sequence-key) 0)))
                 stamped (assoc event :seq sequence)
                 updated (-> state
                             (assoc sequence-key sequence)
                             (update-in [streams-key stream] (fnil conj []) stamped))]
             (atomic-write! file updated)
             stamped))))
     :read
     (fn [stream since]
       (when-not (valid-stream? stream)
         (throw (ex-info "invalid repository persistence stream"
                         {:type :langchain.edn-persist/invalid-stream
                          :stream stream})))
       (with-state-lock
         file
         (fn []
           (let [since (long (or since 0))]
             (->> (get-in (read-state file) [streams-key stream] [])
                  (filter #(> (long (:seq %)) since))
                  (sort-by :seq)
                  vec)))))}))

(defn configured-persist
  "Build the connection persistence map from an environment-shaped map.
  `KOTOBA_REPOSITORY_STATE_FILE` is required; `KOTOBA_REPOSITORY_STREAM`
  overrides DEFAULT-STREAM. Keeping this contract here prevents every actor
  deployment from inventing a different repository coordinate convention."
  [environment default-stream]
  (let [file (not-empty (get environment "KOTOBA_REPOSITORY_STATE_FILE"))
        stream (or (not-empty (get environment "KOTOBA_REPOSITORY_STREAM"))
                   default-stream)]
    (when-not file
      (throw (ex-info "KOTOBA_REPOSITORY_STATE_FILE is required"
                      {:type :langchain.edn-persist/state-file-required})))
    (when-not (valid-stream? stream)
      (throw (ex-info "invalid repository persistence stream"
                      {:type :langchain.edn-persist/invalid-stream
                       :stream stream})))
    (let [{:keys [append read]} (host file)]
      {:append (fn [event] (append stream event))
       :read (fn [since] (read stream since))})))

(defn required-persist-from-env
  "Production entrypoint adapter for `langchain.db/create-conn`."
  [default-stream]
  (configured-persist (System/getenv) default-stream))
