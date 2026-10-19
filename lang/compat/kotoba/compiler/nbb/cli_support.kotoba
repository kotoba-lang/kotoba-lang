(ns kotoba.compiler.nbb.cli-support
  "Shared, backend-free support for the split nbb compiler entrypoints.

  Keeping policy decoding and the CLI error contract here is important: the
  Wasm/native split may reduce namespace load time, but must not create two
  subtly different admission boundaries."
  (:require ["node:path" :as node-path]
            ["node:perf_hooks" :refer [performance]]
            [kotoba.compiler.diagnostic :as diagnostic]
            [kotoba.compiler.kotoba-reader :as kr]
            [kotoba.compiler.nbb.io :as io]
            [kotoba.compiler.source-path :as source-path]))

;; Opt-in phase timings go to stderr behind a stable marker. Normal stdout and
;; artifact bytes remain unchanged.
(def ^:private timing-enabled?
  (= "1" (aget js/process.env "KOTOBA_COMPILER_TIMING")))
(def ^:private timing-origin-ms (volatile! (.now performance)))
(def ^:private timing-phases (volatile! []))

(defn- reset-timing! []
  (vreset! timing-origin-ms (.now performance))
  (vreset! timing-phases []))

(defn timed [phase f]
  (if-not timing-enabled?
    (f)
    (let [started (.now performance)]
      (try
        (f)
        (finally
          (vswap! timing-phases conj
                  {:phase phase :milliseconds (- (.now performance) started)}))))))

(defn- timing-report []
  (when timing-enabled?
    {:format "kotoba.compiler-timing/v1"
     :totalMilliseconds (- (.now performance) @timing-origin-ms)
     :phases @timing-phases}))

(defn- emit-timing! [report]
  (when report
    (.error js/console
            (str "KOTOBA_TIMING " (.stringify js/JSON (clj->js report))))))

(defn option [args flag] (second (drop-while #(not= flag %) args)))

;; Repeated flags. `--source-path` may appear more than once and each
;; occurrence is a separate package root, so taking only the first would
;; silently compile against a subset of the roots the caller named -- the
;; graph would still link if the missing root happened to be unused, which is
;; the failure that looks like a pass.
(defn options [args flag]
  (into [] (keep (fn [[a b]] (when (= a flag) b)))
        (partition 2 1 args)))

(defn usage-error! [message]
  (throw (ex-info message {:phase :usage})))

(defn source! [path] (source-path/admit! path))

;; These bounds mirror `kotoba.compiler.bounded-edn`. Policy files are the one
;; untrusted-ish EDN side channel on this fast path; source has its own reader
;; and admission contract.
(def ^:private max-policy-depth 128)
(def ^:private max-policy-token-chars 4096)
(def ^:private max-policy-nodes 200000)
(def ^:private max-policy-string-chars (* 1024 1024))

(defn- validate-edn-shape! [value]
  (let [nodes (volatile! 0)]
    (letfn [(walk [x]
              (when (> (vswap! nodes inc) max-policy-nodes)
                (throw (ex-info "EDN value contains too many nodes"
                                {:phase :decode :limit max-policy-nodes})))
              (when (and (string? x) (> (count x) max-policy-string-chars))
                (throw (ex-info "EDN string exceeds limit"
                                {:phase :decode :limit max-policy-string-chars})))
              (cond
                (map? x) (doseq [[k v] x] (walk k) (walk v))
                (coll? x) (doseq [item x] (walk item))))]
      (walk value)
      value)))

(defn- read-edn-form! [text]
  (let [forms (kr/read-forms text {:max-depth max-policy-depth
                                   :max-token-chars max-policy-token-chars})]
    (when (empty? forms)
      (throw (ex-info "EDN input is empty" {:phase :decode})))
    (when (> (count forms) 1)
      (throw (ex-info "EDN input contains trailing forms" {:phase :decode})))
    (validate-edn-shape! (first forms))))

;; The module lock is the other EDN file this route is handed. Same reader and
;; same bounds as `--policy`: it arrives from the caller, and a lock is
;; supposed to make a build MORE constrained, so it must not be the one input
;; read without limits.
(defn read-edn-file! [path]
  (read-edn-form! (io/read-text-file path)))

(defn read-policy-material [args]
  (if-let [path (option args "--policy")]
    {:present? true :text (io/read-text-file path)}
    {:present? false :text ""}))

(defn parse-policy-material
  "Decode policy EDN. This is decoding only: a `:allow` grant written with a
  catalog NAME (`[:cap/call :hash/sha256]`) is canonicalised to its wire id by
  `kotoba.compiler.capability-names/wire-policy`, which the source-consuming
  entrypoints apply immediately around this call.

  Why not here, where the single decode boundary is. `capability-names` reads
  the catalog through `kotoba.sema`, and requiring `kotoba.sema` from this
  namespace would pull the whole frontend into the load closure of every
  entrypoint that requires this one -- including `output-set-cli`, which never
  reads Kotoba source. Measured 2026-09-01 under nbb on this workstation:
  loading `kotoba.compiler.diagnostic` alone took 2.1s wall, loading
  `kotoba.sema` took 15.8s. `verify-output-set` would have paid that to gain
  nothing."
  [material]
  (if (:present? material)
    (read-edn-form! (:text material))
    {}))

(defn read-policy [args]
  (parse-policy-material (read-policy-material args)))

(defn emit-metadata
  "Build metadata carried outside capability policy, matching the JVM CLI's
  compile-source call. Keep the integer as BigInt so provenance hashes and
  Wasm i64 emission stay exact beyond JavaScript's safe-integer range."
  [args]
  (if (some #(= "--fuel" %) args)
    (let [text (option args "--fuel")]
      (when-not (and text (re-matches #"[0-9]+" text))
        (throw (ex-info "--fuel must be a positive decimal integer"
                        {:phase :usage :fuel text})))
      (let [fuel (js/BigInt text)]
        (when (zero? fuel)
          (throw (ex-info "--fuel must be a positive decimal integer"
                          {:phase :usage :fuel text})))
        {:fuel fuel}))
    {}))

(def ^:private declarative-policy-keys
  "Policy controls consumed by the compiler rather than capability admission.
  Keep this aligned with `kotoba.compiler.core/capability-policy`: admission is
  deliberately closed over its own key set and must not reject known compiler
  controls as malformed grants."
  #{:language-profile :budgets})

(defn capability-policy [policy]
  (apply dissoc policy declarative-policy-keys))

(defn analyze-options [policy]
  (cond-> {}
    (:language-profile policy)
    (assoc :language-profile (:language-profile policy))))

(defn- exit-code [phase]
  (case phase
    :usage 64
    ;; :project-link joined this list when the linker became reachable from
    ;; here. The JVM CLI has always mapped it to 65; without it a rejected
    ;; graph exited 70 (internal), so "your source paths are wrong" and "the
    ;; compiler broke" were the same answer on this route and a different
    ;; answer than the JVM gave for the same input.
    ;; :module-lock joins for the same reason :project-link did. A lock that
    ;; does not pin a dependency, or a block that does not hash to its name,
    ;; is a caller telling the compiler something wrong -- not the compiler
    ;; breaking. Without this both answered 70, which is the code reserved for
    ;; "internal compiler error", so a CI job could not tell the two apart.
    ;; `kotoba.compiler.cli/exit-code` carries the same entry, so the JVM and
    ;; Node routes still answer identically.
    (:decode :read :subset :admission :verify :project-link :module-lock) 65
    ;; `kotoba.compiler.cli/exit-code` carries this entry too. Neither 0 nor
    ;; 65: a REFUSED definition is an answer (listed with its marker, exit 0),
    ;; and this is the other thing -- no identity could be computed at all.
    :definition-identity 66
    (:signature :trust) 77
    :output 74
    70))

(defn- error-report [error source-name]
  (let [data (ex-data error)
        phase (or (:phase data) :internal)
        ;; Same refinement the JVM CLI applies (kotoba.compiler.diagnostic).
        ;; This path links a project when `--source-path` is given, so a
        ;; module rejected here for declaring `(:require ...)` is a caller who
        ;; did NOT name their source roots -- still exactly the caller who
        ;; needs to be told which invocation does.
        refined (when (not= phase :internal) (diagnostic/refine error))]
    (cond-> {:format :kotoba.cli-error/v1
             :ok false
             :error phase
             :diagnostic (diagnostic/from-error error source-name)
             :message (if (= phase :internal)
                        "internal compiler error"
                        (diagnostic/refined-message error (.-message error)))}
      (:details refined) (assoc :details (:details refined)))))

(defn- invoke* [run! args reset?]
  (when reset? (reset-timing!))
  (try
    (let [value (timed "command" #(run! (vec args)))]
      {:status 0
       :stdout (str (pr-str value) "\n")
       :stderr ""
       :timing (timing-report)})
    (catch :default error
      (let [source (second args)
            source-name (when (source-path/source-kind source)
                          (.basename node-path source))
            report (error-report error source-name)]
        {:status (exit-code (:error report))
         :stdout ""
         :stderr (str (pr-str report) "\n")
         :timing (timing-report)}))))

(defn invoke
  "Runs one already-loaded compiler command without terminating the process."
  [run! args]
  (invoke* run! args true))

(defn execute! [run!]
  (let [{:keys [status stdout stderr timing]}
        (invoke* run! (vec *command-line-args*) false)]
    (emit-timing! timing)
    (when (seq stdout) (.write js/process.stdout stdout))
    (when (seq stderr) (.write js/process.stderr stderr))
    (when-not (zero? status) (.exit js/process status))))

(def ^:private max-worker-line-bytes (* 64 1024))
(def ^:private max-worker-args 64)
(def ^:private max-worker-arg-chars 4096)

(defn- configured-max-requests []
  (let [text (or (aget js/process.env "KOTOBA_WORKER_MAX_REQUESTS") "1000")
        value (js/Number text)]
    (if (and (js/Number.isSafeInteger value) (<= 1 value 100000))
      value
      1000)))

(defn- write-worker-response! [response]
  (.write js/process.stdout
          (str (.stringify js/JSON (clj->js response)) "\n")))

(defn- request! [line]
  (when (> (.byteLength js/Buffer line "utf8") max-worker-line-bytes)
    (usage-error! "worker request exceeds byte limit"))
  (let [request (js->clj (.parse js/JSON line) :keywordize-keys true)
        args (:args request)]
    (when-not (map? request)
      (usage-error! "worker request must be a JSON object"))
    (when-not (or (nil? (:id request))
                  (string? (:id request))
                  (number? (:id request)))
      (usage-error! "worker request id must be a string, number, or null"))
    (when-not (and (vector? args)
                   (<= (count args) max-worker-args)
                   (every? #(and (string? %) (<= (count %) max-worker-arg-chars)) args))
      (usage-error! "worker args must be a bounded array of strings"))
    request))

(defn serve!
  "Serves bounded, sequential NDJSON requests until shutdown or the request
  ceiling. Each response contains the ordinary CLI stdout/stderr contract, so
  callers do not need a second error decoder."
  [run! target]
  (let [requests (volatile! 0)
        maximum (configured-max-requests)
        pending (volatile! (.alloc js/Buffer 0))
        closed? (volatile! false)
        close! (fn []
                 (when-not @closed?
                   (vreset! closed? true)
                   (.pause js/process.stdin)
                   (js/setImmediate #(.exit js/process 0))))
        handle-line!
        (fn [line]
          (let [count (vswap! requests inc)]
            (if (> count maximum)
              (do
                (write-worker-response! {:format "kotoba.compiler-worker/v1"
                                         :type "result" :id nil :status 64
                                         :stdout ""
                                         :stderr "worker request limit exceeded\n"})
                (close!))
              (try
                (let [request (request! line)
                      id (:id request)]
                  (if (= "shutdown" (:op request))
                    (do
                      (write-worker-response! {:format "kotoba.compiler-worker/v1"
                                               :type "shutdown" :id id :status 0})
                      (close!))
                    (let [result (invoke run! (:args request))]
                      (write-worker-response!
                       (assoc result :format "kotoba.compiler-worker/v1"
                                     :type "result" :id id))
                      ;; Recover from bounded caller errors, but fail-stop after
                      ;; an internal compiler failure that may have tainted state.
                      (when (= 70 (:status result)) (close!)))))
                (catch :default error
                  (write-worker-response! {:format "kotoba.compiler-worker/v1"
                                           :type "result" :id nil :status 64
                                           :stdout ""
                                           :stderr (str (.-message error) "\n")}))))))]
    (write-worker-response! {:format "kotoba.compiler-worker/v1"
                             :type "ready" :target (name target)
                             :maxRequests maximum})
    (.on js/process.stdin "data"
         (fn [chunk]
           (when-not @closed?
             (vreset! pending (.concat js/Buffer #js [@pending chunk]))
             (loop []
               (let [newline (.indexOf @pending 10)]
                 (if (neg? newline)
                   (when (> (.-length @pending) max-worker-line-bytes)
                     (write-worker-response! {:format "kotoba.compiler-worker/v1"
                                              :type "result" :id nil :status 64
                                              :stdout ""
                                              :stderr "worker request exceeds byte limit\n"})
                     (close!))
                   (let [line-buffer (.subarray @pending 0 newline)
                         line-buffer (if (and (pos? (.-length line-buffer))
                                              (= 13 (aget line-buffer
                                                          (dec (.-length line-buffer)))))
                                       (.subarray line-buffer 0 (dec (.-length line-buffer)))
                                       line-buffer)]
                     (vreset! pending (.subarray @pending (inc newline)))
                     (if (> (.-length line-buffer) max-worker-line-bytes)
                       (do
                         (write-worker-response! {:format "kotoba.compiler-worker/v1"
                                                  :type "result" :id nil :status 64
                                                  :stdout ""
                                                  :stderr "worker request exceeds byte limit\n"})
                         (close!))
                       (do
                         (handle-line! (.toString line-buffer "utf8"))
                         (when-not @closed? (recur)))))))))))
    (.on js/process.stdin "end"
         (fn []
           (when-not @closed?
             (when (pos? (.-length @pending))
               (write-worker-response! {:format "kotoba.compiler-worker/v1"
                                        :type "result" :id nil :status 64
                                        :stdout ""
                                        :stderr "unterminated worker request\n"}))
             (close!))))))
