(ns kotoba.compiler.nbb.classpath
  "JVM-free dependency-closure resolution for `bin/kotoba`'s nbb fast path.

  The fast path's *compile* code has never touched the JVM. Its *classpath*
  did: `bin/kotoba` shelled out to `clojure -Spath` purely to locate the
  pinned git dependencies in `~/.gitlibs`. That one call is what made the
  whole path need a JDK — measured directly by stubbing `clojure` out on a
  cold cache, which produced `Could not find namespace: kotoba.artifact.core`
  rather than a resolution error, because the failure returned an empty
  classpath and the caller carried on with a truncated one.

  This resolves the same closure from a checked-in lock and `git`, which is
  all a set of `:git/sha` pins actually needs. A commit id is a content
  address, so the resolution is reproducible without re-deriving it; that is
  also why the lock exists at all rather than a resolver that re-implements
  `tools.deps`. Transitive pins genuinely conflict here — the root pins
  `kotoba-kir` at one commit and three siblings pin three others — and
  choosing between them is `tools.deps`' job, done once at authoring time by
  `scripts/lock-classpath.cljs`. The lock records the answer, not the
  algorithm.

  Maven jars are deliberately absent from the lock. nbb cannot load a `.jar`,
  so they were never part of what this path resolves; recording machine-local
  `~/.m2` paths would make the lock unreproducible for no gain.

  Fail-closed throughout: a lock that does not match `deps.edn`, a checkout at
  the wrong commit, or a missing dependency directory is an error, never a
  shorter classpath."
  (:require ["node:child_process" :as child]
            ["node:crypto" :as crypto]
            ["node:fs" :as fs]
            ["node:os" :as os]
            ["node:path" :as node-path]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def lock-version 1)
(def lock-file-name "deps-lock.edn")

(defn sha256-hex [^js buffer]
  (-> (.createHash crypto "sha256") (.update buffer) (.digest "hex")))

(defn deps-digest
  "The digest of `deps.edn`'s bytes. The lock is bound to it, so editing a pin
  without regenerating the lock is an error rather than a silently stale
  closure."
  [root]
  (sha256-hex (.readFileSync fs (.join node-path root "deps.edn"))))

(defn gitlibs-root []
  (or (.-GITLIBS js/process.env)
      (.join node-path (.homedir os) ".gitlibs")))

(defn checkout-dir
  "The directory `tools.deps` uses for COORDINATE at SHA. Shared on purpose:
  when a JVM run has already fetched a dependency, this resolves with no
  network at all, and when this path fetches one first, a later JVM run finds
  it already present."
  [coordinate sha]
  (let [[group artifact] (str/split coordinate #"/")]
    (.join node-path (gitlibs-root) "libs" group (or artifact group) sha)))

(defn- git! [args opts]
  (.spawnSync child "git" (clj->js args)
              (clj->js (merge {:encoding "utf8" :maxBuffer 8388608} opts))))

(defn- git-ok? [result]
  (and (not (.-error result)) (zero? (or (.-status result) 1))))

(defn head-sha
  "The commit a checkout is actually at, or nil when it is not a git
  checkout. This is the content-address check: a directory named after a
  commit is not evidence that it holds that commit."
  [dir]
  (let [result (git! ["-C" dir "rev-parse" "HEAD"] {})]
    (when (git-ok? result) (str/trim (.-stdout result)))))

(defn materialize!
  "Fetch COORDINATE at SHA into its checkout directory. Full history, per the
  workspace's no-shallow rule -- a graft boundary would make the very
  ancestry check below unreliable."
  [{:keys [coordinate git-url git-sha]}]
  (let [dir (checkout-dir coordinate git-sha)]
    (when-not (.existsSync fs dir)
      (.mkdirSync fs dir #js {:recursive true})
      (let [complete? (atom false)]
        (try
          (let [clone (git! ["clone" "--quiet" "--no-checkout" git-url dir] {})]
            (when-not (git-ok? clone)
              (throw (ex-info "git clone failed"
                              {:phase :fetch :coordinate coordinate :url git-url
                               :stderr (some-> (.-stderr clone) str/trim)}))))
          (let [checkout (git! ["-C" dir "checkout" "--quiet" git-sha] {})]
            (when-not (git-ok? checkout)
              (throw (ex-info "git checkout of the pinned commit failed"
                              {:phase :fetch :coordinate coordinate :sha git-sha
                               :stderr (some-> (.-stderr checkout) str/trim)}))))
          (reset! complete? true)
          (finally
            ;; `git clone` leaves its destination behind on transport failure.
            ;; Remove only the fresh, exact content-addressed directory we
            ;; created so the launcher's bounded retry can actually retry.
            (when-not @complete?
              (.rmSync fs dir #js {:recursive true :force true}))))))
    dir))

(defn- entry-directories [{:keys [coordinate git-sha paths] :as entry}]
  (let [dir (materialize! entry)
        actual (head-sha dir)]
    (when-not (= git-sha actual)
      (throw (ex-info "dependency checkout is not at its pinned commit"
                      {:phase :verify :coordinate coordinate
                       :expected git-sha :actual actual :dir dir})))
    (let [resolved (mapv #(.normalize node-path (.join node-path dir %)) paths)
          missing (vec (remove #(.existsSync fs %) resolved))]
      (when (seq missing)
        (throw (ex-info "dependency path recorded in the lock does not exist"
                        {:phase :verify :coordinate coordinate :missing missing})))
      resolved)))

(defn read-lock [root]
  (let [file (.join node-path root lock-file-name)]
    (when (.existsSync fs file)
      (edn/read-string (.readFileSync fs file "utf8")))))

(defn resolve-directories
  "Every source directory the nbb fast path needs, from the lock. Throws with
  a `:phase` on any disagreement."
  [root]
  (let [lock (read-lock root)]
    (when-not lock
      (throw (ex-info "no dependency lock; run scripts/lock-classpath.cljs"
                      {:phase :lock :expected (.join node-path root lock-file-name)})))
    (when-not (= lock-version (:lock/version lock))
      (throw (ex-info "unsupported dependency lock version"
                      {:phase :lock :actual (:lock/version lock)})))
    (let [digest (deps-digest root)]
      (when-not (= digest (:lock/deps-digest lock))
        (throw (ex-info "deps.edn changed since the lock was generated; run scripts/lock-classpath.cljs"
                        {:phase :lock :expected (:lock/deps-digest lock) :actual digest}))))
    (when (empty? (:lock/entries lock))
      (throw (ex-info "dependency lock has no entries" {:phase :lock})))
    (into [] (mapcat entry-directories) (:lock/entries lock))))

(defn -main
  "Print the resolved dependency directories, one per line, or an EDN error on
  STDERR and a non-zero exit. `bin/kotoba` joins them with the platform
  separator; keeping the output one-per-line means a path containing the
  separator cannot be mistaken for two entries."
  [& args]
  (let [root (or (first args) (.cwd js/process))]
    (try
      (doseq [dir (resolve-directories root)] (println dir))
      (catch :default error
        (binding [*print-fn* *print-err-fn*]
          (prn (merge {:format :kotoba.classpath-error/v1 :ok false
                       :message (ex-message error)}
                      (ex-data error))))
        (.exit js/process 1)))))
