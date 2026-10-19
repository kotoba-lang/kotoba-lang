;; murakumo.task.worker — resident remote shells, one per node slot.
;;
;; ADR-2607256000/2607256500 left the task plane at a ~139ms per-task floor even
;; with connection multiplexing: every task still opened a fresh ssh CHANNEL and
;; started a remote shell. The gap list called for a resident worker on
;; kotoba-server — but only 5 of 11 fleet nodes currently run kotoba-server, so
;; that path would help barely half the fleet and would tie batch execution to
;; the mesh being healthy.
;;
;; This is the version that works on every reachable node today: keep ONE
;; `ssh host bash -s` alive per slot and feed it framed commands on stdin. The
;; cost per task collapses to a write plus a read on a channel that is already
;; open; the ssh handshake, channel open and shell start are paid once.
;;
;; Framing: each task is written as a subshell followed by the shared
;; `__murakumo_rc=` sentinel (murakumo.tunnel owns it) and an `__murakumo_end=<id>`
;; delimiter. The reader consumes the stream up to that delimiter, so a task's
;; output cannot bleed into the next one's.
;;
;; Honest trade-offs, both surfaced in the result map:
;;   - stderr is merged into stdout (`:stderr-merged? true`); one stream cannot
;;     be split back apart reliably without a second channel.
;;   - a task that hangs cannot be killed individually — the worker is killed and
;;     respawned, and the task is reported as :timeout? true. Remaining queue
;;     work continues on the fresh worker.

(ns murakumo.task.worker
  (:require ["node:child_process" :as cp]
            [kotoba.lang.text :as str]
            [murakumo.tunnel :as tunnel]))

(def end-marker "__murakumo_end=")

(defn frame
  "Remote shell text for one task: run it, report its status in band, then emit
   the end delimiter for `id`."
  [id cmd]
  (str "( " cmd "\n) 2>&1; __mrc=$?; "
       "echo \"" tunnel/rc-marker "$__mrc\"; "
       "echo \"" end-marker id "\"\n"))

(defn take-completed
  "If `buf` holds the end delimiter for `id`, return [task-output remaining-buf];
   otherwise nil. Pure — this is the framing rule the reader is tested on."
  [buf id]
  (let [m (str end-marker id)
        i (str/index-of (str buf) m)]
    (when i
      [(subs (str buf) 0 i) (subs (str buf) (+ i (count m)))])))

;; --- worker process ---------------------------------------------------------

(declare drain!)

(defn- fail-pending! [w reason]
  (when-let [{:keys [resolve]} @(:pending w)]
    (reset! (:pending w) nil)
    (resolve {:error reason})))

(defn spawn!
  "Start a resident remote shell on `host`. `:wrap? false` because the framing
   here provides the exit sentinel per task, not per connection."
  [host opts]
  (let [argv (tunnel/ssh-argv host "bash -s"
                              {:connect-timeout-s (or (:connect-timeout-s opts) 8)
                               :control-path (:control-path opts)
                               :wrap? false})
        child (.spawn cp (first argv) (to-array (rest argv))
                      #js {:stdio #js ["pipe" "pipe" "pipe"]})
        w {:host host :child child :buf (atom "") :pending (atom nil)
           :stderr (atom "") :dead (atom nil)}]
    (.on (.-stdout child) "data" (fn [d] (swap! (:buf w) str (.toString d)) (drain! w)))
    (.on (.-stderr child) "data" (fn [d] (swap! (:stderr w) str (.toString d))))
    (.on child "close" (fn [code]
                         (reset! (:dead w) (or code 0))
                         (fail-pending! w (str "worker on " host " exited (" code ") "
                                               (str/trim @(:stderr w))))))
    (.on child "error" (fn [e]
                         (reset! (:dead w) -1)
                         (fail-pending! w (str (.-message e)))))
    ;; A worker we killed on timeout tears its stdin down asynchronously; without
    ;; this handler the next write raises an unhandled EPIPE and takes the whole
    ;; run with it (observed live before this line existed).
    (.on (.-stdin child) "error"
         (fn [e]
           (reset! (:dead w) -1)
           (fail-pending! w (str "worker stdin on " host ": " (.-message e)))))
    w))

(defn- drain! [w]
  (when-let [{:keys [id resolve]} @(:pending w)]
    (when-let [[chunk remaining] (take-completed @(:buf w) id)]
      (reset! (:buf w) remaining)
      (reset! (:pending w) nil)
      (resolve {:raw chunk}))))

(defn kill! [w]
  ;; Mark dead SYNCHRONOUSLY: the process' close event arrives a tick later, and
  ;; the pool must not hand the next task to a worker that is already gone.
  (reset! (:dead w) :killed)
  (when-let [c (:child w)]
    (try (.kill c "SIGKILL") (catch :default _ nil))))

(defn exec!
  "Send one task to a worker. Resolves {:raw out} or {:error msg}. One task in
   flight per worker — concurrency comes from running several workers."
  [w id cmd]
  (js/Promise.
   (fn [resolve _]
     (if @(:dead w)
       (resolve {:error (str "worker on " (:host w) " is not running")})
       (do (reset! (:pending w) {:id id :resolve resolve})
           (try
             (.write (.-stdin (:child w)) (frame id cmd))
             (drain! w)
             (catch :default e
               (reset! (:dead w) -1)
               (fail-pending! w (str "worker write on " (:host w) ": " (.-message e))))))))))

(defn run-task!
  "Run one assignment on a live worker, honouring the task timeout by killing
   the worker (the only way to stop a hung remote command on a shared channel)."
  [w {:keys [task node host]} opts]
  (let [t0 (js/Date.now)
        timeout-ms (or (:timeout-ms task) (:timeout-ms opts) 120000)]
    (js/Promise.
     (fn [resolve _]
       (let [done (atom false)
             base {:task task :node node :host host :stderr "" :stderr-merged? true
                   :started-ms t0}
             finish (fn [m]
                      (when-not @done
                        (reset! done true)
                        (resolve (merge base {:duration-ms (- (js/Date.now) t0)} m))))
             timer (js/setTimeout
                    (fn []
                      (when-not @done
                        (kill! w)
                        (finish {:exit nil :timeout? true :stdout ""})))
                    timeout-ms)]
         (-> (exec! w (:id task) (:cmd task))
             (.then (fn [r]
                      (js/clearTimeout timer)
                      (if (:error r)
                        (finish {:exit nil :timeout? false :stdout "" :error (:error r)})
                        (let [[clean rc] (tunnel/parse-rc (:raw r))]
                          (finish {:exit rc :timeout? false :stdout clean})))))))))))

;; --- per-node pool ----------------------------------------------------------

(defn- take-next! [state]
  (let [[a] (:queue @state)]
    (when a
      (swap! state update :queue #(vec (rest %)))
      a)))

(defn run-node-queue
  "Drain one node's assignments over `slot-count` resident shells. A worker that
   died (timeout kill, dropped connection) is respawned for the next task, so a
   single hung task costs one connection, not the batch."
  [host assignments slot-count opts]
  (let [state (atom {:queue (vec assignments) :results []})
        n (max 1 (min slot-count (count assignments)))
        drain (fn drain [w]
                (if-let [a (take-next! state)]
                  (let [w (if @(:dead w) (spawn! host opts) w)]
                    (-> (run-task! w a opts)
                        (.then (fn [r]
                                 (swap! state update :results conj r)
                                 (when-let [cb (:on-result opts)] (cb r))
                                 (drain w)))))
                  (do (kill! w) (js/Promise.resolve nil))))]
    (-> (js/Promise.all (to-array (mapv (fn [_] (drain (spawn! host opts))) (range n))))
        (.then (fn [_] (:results @state))))))
