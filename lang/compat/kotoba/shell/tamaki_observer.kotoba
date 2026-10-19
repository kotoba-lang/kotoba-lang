(ns kotoba.shell.tamaki-observer
  "Read-only native projection of Tamaki's durable event stream."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [kotoba.tamaki.loop :as agent-loop]
            [kotoba.tamaki.model :as model]
            [kotoba.tamaki.store :as store]))

(defn state-dir []
  (or (System/getenv "TAMAKI_STATE_DIR")
      "../../../../etzhayyim/tamaki/.tamaki"))

(defn workspace-root []
  (or (System/getenv "KOTOBA_WORKSPACE_ROOT")
      (-> (io/file (state-dir)) .getCanonicalFile .getParentFile
          .getParentFile .getParentFile .getParentFile .getPath)))

(defn west-manifest []
  (or (System/getenv "KOTOBA_WEST_MANIFEST")
      (str (workspace-root) "/manifest/west.yml")))

(defn- yaml-value [line key]
  (some-> (re-find (re-pattern (str "^\\s+" key ":\\s*(.+)\\s*$")) line)
          second
          (str/replace #"^['\"]|['\"]$" "")))

(defn- git-head [root path]
  (let [git-dir (io/file root path ".git")
        head-file (io/file git-dir "HEAD")]
    (when (.isFile head-file)
      (let [head (str/trim (slurp head-file))]
        (if-let [ref (second (re-find #"^ref:\s+(.+)$" head))]
          (let [ref-file (io/file git-dir ref)]
            (when (.isFile ref-file) (str/trim (slurp ref-file))))
          head)))))

(defn read-west-projects
  "Reads the generated west manifest without adding a YAML dependency. The
  project stanza is deliberately flat except for userdata/rad-rid."
  ([] (read-west-projects (west-manifest) (workspace-root)))
  ([manifest root]
   (if-not (.isFile (io/file manifest))
     []
     (let [finish #(when (:path %) (assoc % :west? true :github? (boolean (:remote %))))
           projects
           (reduce
            (fn [{:keys [current projects] :as acc} line]
              (if-let [name (some-> (re-find #"^\s{4}- name:\s*(.+)\s*$" line) second)]
                {:current {:name name}
                 :projects (cond-> projects current (conj (finish current)))}
                (let [field (cond
                              (yaml-value line "remote") :remote
                              (yaml-value line "revision") :revision
                              (yaml-value line "path") :path
                              (yaml-value line "rad-rid") :rad-rid)]
                  (if field
                    (assoc acc :current
                           (assoc current field
                                  (yaml-value line (clojure.core/name field))))
                    acc))))
            {:current nil :projects []}
            (-> (slurp manifest)
                (str/split #"\n  projects:\n" 2)
                last
                str/split-lines))
           all (cond-> (:projects projects)
                 (:current projects) (conj (finish (:current projects))))]
       (->> all
            (keep identity)
            (mapv
             (fn [project]
               (let [local? (.isDirectory (io/file root (:path project) ".git"))
                     head (when local? (git-head root (:path project)))]
                 (assoc project
                        :rad? (boolean (:rad-rid project))
                        :local? local?
                        :head head
                        :sync (cond
                                (not local?) :remote
                                (or (= head (:revision project))
                                    (not (re-matches #"[0-9a-f]{40}"
                                                     (str (:revision project)))))
                                :synced
                                :else :different))))
             ))))))

(defn- git-remotes [root path]
  (let [config (io/file root path ".git/config")
        text (if (.isFile config) (slurp config) "")]
    {:github? (str/includes? text "github.com")
     :rad-rid (some-> (re-find #"rad://([^\s]+)" text) second (->> (str "rad:")))
     :rad? (str/includes? text "rad://")}))

(defn- scan-repository-inventory []
  (let [west (read-west-projects)
        index-file (System/getenv "KOTOBA_REPO_INDEX")
        local-paths (if (and index-file (.isFile (io/file index-file)))
                      (->> (str/split-lines (slurp index-file))
                           (remove str/blank?)
                           set)
                      #{})
        west-by-path (into {} (map (juxt :path identity)) west)
        local-only (remove west-by-path local-paths)]
    (->> (concat
          west
          (map (fn [path]
                 (merge {:name (last (str/split path #"/"))
                         :path path
                         :remote (second (str/split path #"/"))
                         :west? false
                         :local? true}
                        (git-remotes (workspace-root) path)))
               local-only))
         (sort-by :path)
         vec)))

(def inventory-cache-ttl-ms 60000)
(defonce repository-inventory-cache (atom nil))

(defn read-repository-inventory
  "Cache the expensive WEST/local repository scan. Repository membership is
  control-plane state and does not need to be reread on every five-second
  activity frame."
  []
  (let [now (System/currentTimeMillis)
        cached @repository-inventory-cache]
    (if (and cached (< (- now (:at cached)) inventory-cache-ttl-ms))
      (:value cached)
      (let [value (scan-repository-inventory)]
        (reset! repository-inventory-cache {:at now :value value})
        value))))

(defn- project-path [root project]
  (when project
    (let [canonical (try (.getCanonicalPath (io/file project))
                         (catch Exception _ (str project)))
          prefix (str (try (.getCanonicalPath (io/file root))
                           (catch Exception _ root)) "/")]
      (cond
        (str/starts-with? canonical prefix) (subs canonical (count prefix))
        (str/starts-with? (str project) "orgs/") (str project)
        :else (str project)))))

(defn- issue-label [run]
  (let [goal (str (:agent.run/goal run))
        radicle (some-> (re-find #"(?i)Radicle issue\s+([0-9a-f]{8,})" goal)
                        second)]
    (or (when radicle (subs radicle 0 (min 10 (count radicle))))
        (some-> (re-find #"(?i)GitHub issue\s+#?(\d+)" goal) second (->> (str "#")))
        (some-> (re-find #"(?i)issue\s+#?([0-9a-f]{6,}|\d+)" goal) second))))

(defn- active-dependencies [projects active-repos]
  (let [root (workspace-root)
        candidates ["deps.edn" "package.json" "Cargo.toml" "pyproject.toml"]
        by-name (group-by :name projects)]
    (->> active-repos
         (mapcat
          (fn [{:keys [path]}]
            (let [project-file (io/file path)
                  project-root (if (.isAbsolute project-file)
                                 project-file
                                 (io/file root path))
                  content (->> candidates
                               (map #(io/file project-root %))
                               (filter #(.isFile %))
                               (map slurp)
                               (str/join "\n"))]
              (when-not (str/blank? content)
                (->> by-name
                     (keep
                      (fn [[repo-name matches]]
                        (when (and (not= repo-name (last (str/split path #"/")))
                                   (str/includes? content repo-name))
                          {:from path :to (:path (first matches))}))))))))
         distinct
         (take 100)
         vec)))

(defn registry-summary [projects runs]
  (let [active-statuses #{:queued :leased :running}
        active-runs (filter #(contains? active-statuses (:agent.run/status %)) runs)
        by-path (into {} (map (juxt :path identity)) projects)
        active-repos
        (->> active-runs
             (group-by #(project-path (workspace-root) (:agent.run/project %)))
             (map (fn [[path repo-runs]]
                    {:path path
                     :registry (get by-path path)
                     :issue (some issue-label repo-runs)
                     :runs (sort-by :agent.run/updated-at > repo-runs)}))
             (sort-by #(apply max 0 (keep :agent.run/updated-at (:runs %))) >)
             vec)
        orgs (->> projects
                  (group-by :remote)
                  (map (fn [[org repos]]
                         {:org org
                          :total (count repos)
                          :rad (count (filter :rad? repos))
                          :local (count (filter :local? repos))
                          :synced (count (filter #(= :synced (:sync %)) repos))
                          :different (count (filter #(= :different (:sync %)) repos))
                          :active (count (filter
                                          (set (keep :path active-repos))
                                          (map :path repos)))}))
                  (sort-by :total >)
                  vec)]
    {:total (count projects)
     :west (count (filter :west? projects))
     :github (count (filter :github? projects))
     :rad (count (filter :rad? projects))
     :local (count (filter :local? projects))
     :repos projects
     :dependencies (active-dependencies projects active-repos)
     :orgs orgs
     :active-repos active-repos}))

(defn- newest-loop-log []
  (->> (or (.listFiles (io/file (state-dir))) (make-array java.io.File 0))
       (filter #(re-matches #"loop-.*\.log" (.getName %)))
       (sort-by #(.lastModified %) >)
       first))

(defn- tail-text [file max-bytes]
  (if-not file
    ""
    (with-open [raf (java.io.RandomAccessFile. file "r")]
      (let [length (.length raf)
            start (max 0 (- length max-bytes))
            bytes (byte-array (- length start))]
        (.seek raf start)
        (.readFully raf bytes)
        (String. bytes java.nio.charset.StandardCharsets/UTF_8)))))

(defn activity-lines []
  (->> (str/split-lines (tail-text (newest-loop-log) 65536))
       (filter #(or (str/starts-with? % "[tool:")
                    (str/starts-with? % "-- ")
                    (str/starts-with? % "{:patch/id")
                    (str/starts-with? % "{:loop/id")
                    (str/includes? % "cycle failed")))
       (map #(str/replace % #"\s+" " "))
       (take-last 12)
       vec))

(defn snapshot
  ([] (snapshot (store/read-local-events (state-dir))))
  ([events]
   (let [campaigns (agent-loop/campaigns events)
         runs (model/fold-events events)
         kinds (frequencies (map :tamaki.event/kind events))
         run-list (->> (vals runs)
                       (filter :agent.run/id)
                       (remove #(str/starts-with? (:agent.run/id %) "loop-"))
                       (sort-by :agent.run/updated-at >) vec)]
     {:events (count events)
      :activity (activity-lines)
      :observed-at (System/currentTimeMillis)
      :registry (registry-summary (read-repository-inventory) run-list)
      :campaigns (->> (vals campaigns)
                      (sort-by :tamaki.loop/updated-at >) vec)
      :runs run-list
      :active-loops (count (filter #(= :active (:tamaki.loop/status %))
                                   (vals campaigns)))
      :active-agents (count (filter #(contains? #{:queued :leased :running}
                                                (:agent.run/status %))
                                    (vals runs)))
      :model-activity
      (->> (vals runs)
           (filter #(contains? #{:queued :leased :running}
                               (:agent.run/status %)))
           (map #(or (:agent.run/model %) "default"))
           frequencies)
      :patches (get kinds :patch/created 0)
     :integrations (get kinds :patch/integrated 0)
      :decisions (->> events
                      (filter #(= :issue/prioritized (:tamaki.event/kind %)))
                      (take-last 5)
                      (mapv :tamaki.event/data))
      :independent-reviews
      (count (filter #(= :review/independent (:tamaki.event/kind %)) events))
      :latest-effect
      (some->> events
               (filter #(= :effect/measured (:tamaki.event/kind %)))
               last :tamaki.event/data)
      :failures (+ (get kinds :run/failed 0)
                   (get kinds :loop/cycle-failed 0))
      :latest-at (reduce max 0 (keep :tamaki.event/at events))})))

(defn- truncate [value n]
  (let [s (str value)]
    (if (> (count s) n) (str (subs s 0 (- n 1)) "…") s)))

(defn- ops-builder []
  (let [next-id (atom 0) ops (atom [])]
    {:element
     (fn [tag attrs children]
       (let [id (swap! next-id inc)]
         (swap! ops conj [:dom/create-element id tag])
         (doseq [[k v] attrs] (swap! ops conj [:dom/set-attr id k v]))
         (doseq [child children] (swap! ops conj [:dom/append-child id child]))
         id))
     :text
     (fn [value]
       (let [id (swap! next-id inc)]
         (swap! ops conj [:dom/create-text id (str value)])
         id))
     :finish (fn [root] (conj @ops [:dom/set-root root]))}))

(defn surface-ops [state]
  (let [{:keys [element text finish]} (ops-builder)
        label (fn [tag value] (element tag {} [(text value)]))
        metric (fn [name value]
                 (element :article {"class" "liquid-glass__panel"}
                          [(label :h3 name) (label :h1 value)]))
        registry (:registry state)
        badge-line
        (fn [repo]
          (str (when (:west? repo) "WEST  ")
               (when (:github? repo) "GITHUB  ")
               (when (:rad? repo) "RAD  ")
               (when (:local? repo) "LOCAL")))
        active-repo-card
        (fn [{:keys [path registry runs]}]
          (element :article {"class" "liquid-glass__panel"}
                   (into
                    [(label :h2 (or path "unmapped workspace"))
                     (label :p (if registry
                                 (badge-line registry)
                                 "UNREGISTERED · AgentRun only"))]
                    (mapcat
                     (fn [run]
                       [(label :h3
                               (str "● " (name (:agent.run/status run))
                                    " · " (or (:agent.run/model run) "default")))
                        (label :p
                               (str (:agent.run/id run)
                                    (when-let [parent (:agent.run/parent run)]
                                      (str " · reviewer of " parent))))
                        (label :p (truncate (:agent.run/goal run) 130))])
                     runs))))
        campaign-card
        (fn [campaign]
          (element :section {"class" "liquid-glass__panel"}
                   [(label :h2 (str "環 " (:tamaki.loop/id campaign)))
                    (label :p (truncate (:tamaki.loop/objective campaign) 120))
                    (label :p
                           (str "status " (name (:tamaki.loop/status campaign))
                                "  ·  model "
                                (or (:tamaki.loop/model campaign) "default")
                                "  ·  cycles " (:tamaki.loop/cycles campaign)
                                "/" (:tamaki.loop/max-cycles campaign)
                                "  ·  failures " (:tamaki.loop/failures campaign)
                                "/" (:tamaki.loop/max-failures campaign)))
                    (label :p (str "last " (name (or (:tamaki.loop/last-result campaign)
                                                     :waiting))))]))
        run-row
        (fn [run]
          (element :article {}
                   [(label :h3 (str (name (:agent.run/status run)) "  "
                                    (:agent.run/id run)))
                    (label :p
                           (str "model " (or (:agent.run/model run) "default")
                                (when-let [parent (:agent.run/parent run)]
                                  (str " · child review of " parent))))
                    (label :p (truncate (:agent.run/goal run) 150))]))
        header (element :header {}
                        [(element :section {}
                                  [(label :h1 "Tamaki Observatory")
                                   (label :p "Durable recursive improvement · Radicle canonical")])
                         (label :h3 (if (pos? (:latest-at state))
                                      "● event stream online"
                                      "○ waiting for events"))])
        metrics (element :summary {}
                         [(metric "All repos" (:total registry))
                          (metric "West" (:west registry))
                          (metric "GitHub" (:github registry))
                          (metric "Radicle" (:rad registry))
                          (metric "Active repos" (count (:active-repos registry)))
                          (metric "Active agents" (:active-agents state))])
        topology
        (element :figure
                 {"class" "repo-topology"
                  "data-orgs"
                  (str/join
                   ";"
                   (map (fn [{:keys [org total rad local active synced different]}]
                          (str org "," total "," rad "," local "," active ","
                               synced "," different))
                        (:orgs registry)))
                  "data-repos"
                  (let [active (into {} (map (juxt :path identity)
                                             (:active-repos registry)))]
                    (str/join
                     ";"
                     (map
                      (fn [{:keys [path name remote sync]}]
                        (let [activity (get active path)]
                          (str (str/replace (or remote "unknown") #"[,;]" "_") ","
                               (str/replace (or name path) #"[,;]" "_") ","
                               (if activity "1" "0") ","
                               (str/replace (or (:issue activity) "") #"[,;]" "_") ","
                               (clojure.core/name (or sync :unmanaged)))))
                      (:repos registry))))
                  "data-deps"
                  (str/join
                   ";"
                   (map (fn [{:keys [from to]}]
                          (str (str/replace (last (str/split from #"/")) #"[,;]" "_") ","
                               (str/replace (last (str/split to #"/")) #"[,;]" "_")))
                        (:dependencies registry)))
                  "data-blockers"
                  (str/join
                   ","
                   (map str
                        (get-in (last (:decisions state))
                                [:issue/selection :issue :issue/blockers])))}
                 [])
        active-repo-section
        (element :section {}
                 (into [(label :h2 "Active now · repo × agent × model")]
                       (if (seq (:active-repos registry))
                         (mapv active-repo-card (:active-repos registry))
                         [(label :p "No active AgentRun is mapped to a repository.")])) )
        registry-section
        (element :section {"class" "liquid-glass__panel"}
                 (into
                  [(label :h2 "Repository registry · complete workspace")
                   (label :p "org · west/github total · rad · local checkout · active")]
                  (mapv
                   (fn [{:keys [org total rad local active synced different]}]
                     (label :p
                            (format
                             "%-18s %,4d · RAD %,4d · SYNC %,4d · Δ %,4d · ACTIVE %d"
                             (or org "unknown") total rad synced different active)))
                   (:orgs registry))))
        model-section
        (element :section {"class" "liquid-glass__panel"}
                 (into [(label :h2 "Model activity")]
                       (if (seq (:model-activity state))
                         (mapv (fn [[model count]]
                                 (label :p (str model " · " count " running")))
                               (sort-by key (:model-activity state)))
                         [(label :p "No model is currently sampling.")])) )
        campaign-section
        (element :section {}
                 (into [(label :h2 "Growth campaigns")]
                       (if (seq (:campaigns state))
                         (mapv campaign-card (:campaigns state))
                         [(label :p "No durable campaign has been observed.")])))
        run-section
        (element :section {}
                 (into [(label :h2 "Recent AgentRuns")]
                       (if (seq (:runs state))
                         (mapv run-row (take 8 (:runs state)))
                         [(label :p "No AgentRun receipts yet.")])))
        activity-section
        (element :section {"class" "liquid-glass__panel"}
                 (into [(label :h2 "Live activity")
                        (label :p (str "updated "
                                       (java.time.Instant/ofEpochMilli
                                        (or (:observed-at state) 0))))]
                       (if (seq (:activity state))
                         (mapv #(label :p (truncate % 180)) (:activity state))
                         [(label :p "Waiting for agent tool activity…")])))
        decision-section
        (element :section {"class" "liquid-glass__panel"}
                 (into
                  [(label :h2 "Issue dynamics")
                   (label :p
                          (str "independent reviews "
                               (:independent-reviews state)
                               " · latest improved "
                               (boolean (get-in state
                                                [:latest-effect
                                                 :effect/improved?]))))]
                  (if (seq (:decisions state))
                    (mapv
                     (fn [decision]
                       (label
                        :p
                        (str (get-in decision [:issue/selection
                                              :issue :issue/id])
                             " · score "
                             (format "%.3f"
                                     (double
                                      (or (get-in decision
                                                  [:issue/selection :score])
                                          0)))
                             " · blockers "
                             (pr-str
                              (get-in decision [:issue/selection
                                                :issue :issue/blockers]))
                             " · feedback "
                             (format "%.2f"
                                     (double
                                      (or (get-in decision
                                                  [:issue/dynamics
                                                   :feedback-pressure])
                                          0))))))
                     (:decisions state))
                    [(label :p "Waiting for an issue ranking decision…")])))
        root (element :main {}
                      [header metrics topology active-repo-section registry-section
                       model-section activity-section decision-section
                       campaign-section run-section])]
    (finish root)))

(defn start []
  {:kotoba.app/surface-ops (surface-ops (snapshot))})
