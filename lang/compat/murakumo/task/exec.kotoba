;; murakumo.task.exec — nbb execution shell for the fleet task plane.
;;
;; The impure half of murakumo.task.plan: it turns a pure assignment
;; ({:task :node :host}) into a real remote process over Tailscale SSH, bounds
;; concurrency per node to that node's slot count, and times every task.
;;
;; Script host is nbb (ADR-2607173000: bb is retired, no new .mjs/.sh) — the
;; pure planner it drives stays .cljc so the same decisions also run on the JVM,
;; in the CF Worker, and inside a kotoba WASM component.

(ns murakumo.task.exec
  (:require ["node:child_process" :as cp]
            ["node:fs" :as fs]
            ["node:os" :as os]
            ["node:path" :as path]
            [kotoba.lang.text :as str]
            [murakumo.task.plan :as plan]
            [murakumo.task.worker :as worker]
            [murakumo.tunnel :as tunnel]))

;; The ssh dialect (BatchMode, in-band exit sentinel, ControlMaster reuse) is
;; NOT defined here: murakumo.tunnel owns it so the bb/JVM control plane and
;; this nbb task plane cannot drift apart.

;; A ControlPath is a unix socket path, capped at ~104 bytes by sockaddr_un —
;; and macOS's os.tmpdir() alone is ~50 of them (/var/folders/…/T/). Adding
;; ssh's 40-char %C hash blows the limit and EVERY connection fails. Prefer a
;; short base and refuse to enable multiplexing if it still would not fit.
(def ^:private control-path-max 104)
(def ^:private control-name-budget 44)   ; "/" + 40-char %C hash + slack

(defn- short-tmp-base []
  (or (first (filter (fn [d] (and d (.existsSync fs d) (< (count d) 24)))
                     ["/tmp" (.tmpdir os)]))
      (.tmpdir os)))

(defn control-dir!
  "Create a private directory for this run's multiplexed control sockets.
   Returns nil when multiplexing is disabled or would not fit in a socket path."
  [opts]
  (when-not (false? (:multiplex? opts))
    (let [dir (.mkdtempSync fs (.join path (short-tmp-base) "mk-"))]
      (if (< (+ (count dir) control-name-budget) control-path-max)
        dir
        (do (.rmSync fs dir #js {:recursive true :force true})
            (binding [*print-fn* *print-err-fn*]
              (println "note: ssh multiplexing disabled — no short enough socket path available"))
            nil)))))

(defn control-path
  "ControlPath template for a run. %C is ssh's hash of (host, port, user), so
   one socket per node, and the whole run's sockets live in one removable dir."
  [dir]
  (when dir (.join path dir "%C")))

(defn run-one
  "Execute one assignment remotely. Returns a Promise of a result map
   {:task :node :host :exit :timeout? :stdout :stderr :started-ms :duration-ms}.
   Never rejects — a spawn error or timeout is reported as data, because a
   partially-failed fan-out is a normal outcome the planner must be able to fold."
  [{:keys [task node host]} opts]
  (js/Promise.
   (fn [resolve _reject]
     (let [t0 (js/Date.now)
           timeout-ms (or (:timeout-ms task) (:timeout-ms opts) 120000)
           argv (tunnel/ssh-argv host (:cmd task)
                                 {:connect-timeout-s (or (:connect-timeout-s opts) 8)
                                  :control-path (:control-path opts)})
           child (.spawn cp (first argv) (to-array (rest argv))
                         #js {:stdio #js ["ignore" "pipe" "pipe"]})
           out (atom "")
           err (atom "")
           state (atom nil)
           finish (fn [m]
                    (let [[clean rc] (tunnel/parse-rc @out)]
                      (resolve (merge {:task task :node node :host host
                                       :stdout clean
                                       :remote-rc rc
                                       :stderr (str/trim @err)
                                       :started-ms t0
                                       :duration-ms (- (js/Date.now) t0)}
                                      m))))
           timer (js/setTimeout (fn []
                                  (when (nil? @state)
                                    (reset! state :timeout)
                                    (.kill child "SIGKILL")))
                                timeout-ms)]
       (.on (.-stdout child) "data" (fn [d] (swap! out str (.toString d))))
       (.on (.-stderr child) "data" (fn [d] (swap! err str (.toString d))))
       (.on child "error"
            (fn [e]
              (js/clearTimeout timer)
              (when (nil? @state)
                (reset! state :error)
                (finish {:exit nil :timeout? false :error (str (.-message e))}))))
       (.on child "close"
            (fn [code]
              (js/clearTimeout timer)
              (when (not= :error @state)
                ;; The in-band sentinel wins when present: ssh's own exit code
                ;; is unreliable on this fleet (see wrap-cmd).
                (let [[_ rc] (tunnel/parse-rc @out)]
                  (finish {:exit (when-not (= :timeout @state) (or rc code))
                           :ssh-exit code
                           :timeout? (= :timeout @state)})))))))))

(defn- take-next!
  "Pop the next assignment off a node's queue. Safe without locking: nbb is
   single-threaded, so the read and the swap! happen in one turn of the loop."
  [state]
  (let [[a] (:queue @state)]
    (when a
      (swap! state update :queue #(vec (rest %)))
      a)))

(defn- worker
  "One concurrency slot on one node: drain that node's queue serially."
  [state opts]
  (if-let [a (take-next! state)]
    (.then (run-one a opts)
           (fn [r]
             (swap! state update :results conj r)
             (when-let [cb (:on-result opts)] (cb r))
             (worker state opts)))
    (js/Promise.resolve nil)))

(defn run-node-queue
  "Run one node's assignments with at most `slot-count` concurrent SSH sessions."
  [assignments slot-count opts]
  (let [state (atom {:queue (vec assignments) :results []})
        n (max 1 (min slot-count (count assignments)))]
    (-> (js/Promise.all (to-array (mapv (fn [_] (worker state opts)) (range n))))
        (.then (fn [_] (:results @state))))))

(defn run-plan
  "Execute a whole plan: every node drains its own queue concurrently, each
   bounded by its slot count. Returns a Promise of the result vector.

   Two transports, same result shape:
     resident workers (default) — one `ssh host bash -s` per slot, tasks framed
       on its stdin; the ssh handshake and shell start are paid once per slot
     one ssh per task (`:worker? false`) — simplest, isolates stderr, and is the
       fallback when a node's shell cannot host a resident session"
  [plan nodes opts]
  (let [by-name (into {} (map (juxt :name identity)) nodes)
        groups (group-by :node (:assignments plan))
        ps (mapv (fn [[node-name as]]
                   (let [slot-count (plan/slots (get by-name node-name) opts)]
                     (if (false? (:worker? opts))
                       (run-node-queue as slot-count opts)
                       (worker/run-node-queue (:host (first as)) as slot-count opts))))
                 groups)]
    (if (empty? ps)
      (js/Promise.resolve [])
      (-> (js/Promise.all (to-array ps))
          (.then (fn [rss] (vec (apply concat (array-seq rss)))))))))

(defn close-masters!
  "Shut this run's multiplexed master connections down and remove the socket
   dir, instead of leaving masters idling until ControlPersist expires.
   Best-effort: a failure here never fails the run."
  [dir hosts]
  (if-not dir
    (js/Promise.resolve nil)
    (let [cpath (control-path dir)
          ps (mapv (fn [h]
                     (js/Promise.
                      (fn [resolve _]
                        (let [argv (tunnel/close-master-argv h cpath)
                              child (.spawn cp (first argv) (to-array (rest argv))
                                            #js {:stdio "ignore"})]
                          (.on child "close" (fn [_] (resolve nil)))
                          (.on child "error" (fn [_] (resolve nil)))))))
                   (distinct (remove nil? hosts)))]
      (-> (js/Promise.all (to-array ps))
          (.then (fn [_]
                   (try (.rmSync fs dir #js {:recursive true :force true})
                        (catch :default _ nil))
                   nil))))))

;; --- node probing -----------------------------------------------------------

(defn probe-cmd
  "Portable across macOS (sysctl) and Linux (nproc//proc/meminfo). Prints
   `<cores> <mem-bytes> <load1> <hostname>` on the first line, then the node's
   own kotoba-server /health body on a `health:` line — so one round trip
   answers both `can I place work here` and `is the mesh node alive here`."
  [port]
  (str "cores=$(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 1); "
       "mem=$(sysctl -n hw.memsize 2>/dev/null || awk '/MemTotal/{print $2*1024}' /proc/meminfo 2>/dev/null || echo 0); "
       "load=$(uptime | sed 's/.*averages*: //' | awk '{print $1}' | tr -d ','); "
       "echo \"$cores $mem $load $(hostname)\"; "
       "echo \"health:$(curl -s -m 3 http://localhost:" port "/health 2>/dev/null | tr -d '\\n' | head -c 200)\""))

(defn parse-probe
  "Parse the probe payload into node metadata. Health is reported honestly:
   :mesh-up? is false when the node answered but kotoba-server did not."
  [s]
  (let [lines (str/split-lines (str/trim (str s)))
        health-line (first (filter #(str/starts-with? % "health:") lines))
        health (some-> health-line (subs 7) str/trim)
        [cores mem load host] (str/split (str/trim (or (first (remove #(str/starts-with? % "health:") lines)) "")) #"\s+")
        n (fn [x] (let [v (js/parseFloat x)] (when-not (js/isNaN v) v)))]
    ;; NB: `int` is a 32-bit bit-or in ClojureScript, which silently mangles
    ;; byte counts (17179869184 | 0 = 0). Round instead.
    {:cores (some-> (n cores) js/Math.round)
     :mem-bytes (some-> (n mem) js/Math.round)
     :load1 (n load)
     :hostname host
     :mesh-up? (boolean (seq health))
     :mesh-health (when (seq health) health)}))

(defn probe
  "SSH every node once to learn cores / memory / load / hostname. Returns a
   Promise of nodes enriched with those keys plus :online? (false when the
   probe failed — the planner then refuses to place work there)."
  [nodes opts]
  (let [ps (mapv (fn [node]
                   (-> (run-one {:task {:cmd (probe-cmd (or (:port node) (:fleet-port opts) 8077))
                                        :id (str "probe-" (:name node))}
                                 :node (:name node) :host (:host node)}
                                (assoc opts :timeout-ms (or (:probe-timeout-ms opts) 15000)))
                       (.then (fn [r]
                                (if (and (= 0 (:exit r)) (seq (:stdout r)))
                                  (merge node (parse-probe (:stdout r))
                                         {:online? true :probe-ms (:duration-ms r)})
                                  (merge node {:online? false
                                               :probe-ms (:duration-ms r)
                                               :probe-error (or (:error r)
                                                                (when (:timeout? r) "timeout")
                                                                (:stderr r)
                                                                (str "exit " (:exit r)))}))))))
                 nodes)]
    (-> (js/Promise.all (to-array ps))
        (.then (fn [rs] (vec (array-seq rs)))))))
