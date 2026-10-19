(ns kotoba.shell.tamaki-web-data
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.set :as set]
            [clojure.string]
            [kotoba.shell.tamaki-observer :as observer]
            [kotoba.tamaki.actor :as actor]
            [kotoba.tamaki.business :as business]
            [kotoba.tamaki.evolution :as evolution]
            [kotoba.tamaki.result :as result]))

(def active-statuses #{:queued :leased :running})
(defonce git-tree-cache (atom {}))
(defonce event-stream-cache
  (atom {:path nil :offset 0 :events []}))
(declare run-project workspace-path)

(def legacy-project-aliases
  "Paths retained in durable AgentRun history after a repository authority
  moved. Projection canonicalizes them before repository lookup so the 2D
  workspace and complete 3D Git tree observe the same living organism."
  {"orgs/kotoba-lang/tamaki" "orgs/etzhayyim/tamaki"})

(defn canonical-project-path [path]
  (get legacy-project-aliases path path))

(defn read-events-incrementally
  "Read only complete records appended since the previous projection.
  The event store is one EDN value per line. An incomplete final write is left
  for the next pass, and truncation/rotation deterministically resets the
  cache."
  [state-dir]
  (let [file (io/file state-dir "events.edn")
        path (.getCanonicalPath file)
        length (if (.isFile file) (.length file) 0)
        cached @event-stream-cache
        reset? (or (not= path (:path cached))
                   (< length (:offset cached)))
        base (if reset? {:path path :offset 0 :events []} cached)
        offset (:offset base)]
    (if (= length offset)
      (:events base)
      (with-open [raf (java.io.RandomAccessFile. file "r")]
        (.seek raf offset)
        (let [bytes (byte-array (- length offset))]
          (.readFully raf bytes)
          (let [last-newline
                (loop [i (dec (alength bytes))]
                  (cond
                    (neg? i) -1
                    (= 10 (aget bytes i)) i
                    :else (recur (dec i))))]
            (if (neg? last-newline)
              (:events base)
              (let [text (String. bytes 0 (inc last-newline)
                                  java.nio.charset.StandardCharsets/UTF_8)
                    appended (->> (clojure.string/split-lines text)
                                  (remove clojure.string/blank?)
                                  (mapv edn/read-string))
                    next-state {:path path
                                :offset (+ offset last-newline 1)
                                :events (into (:events base) appended)}]
                (reset! event-stream-cache next-state)
                (:events next-state)))))))))

(defn- organism-specs []
  (let [directory (io/file (observer/workspace-root)
                           "orgs" "etzhayyim" "tamaki" "organisms")]
    (->> (or (.listFiles directory) (make-array java.io.File 0))
         (filter #(and (.isFile %)
                       (.endsWith (.getName %) ".edn")))
         (mapv (fn [file]
                 (let [spec (edn/read-string (slurp file))]
                   (select-keys
                    spec
                    [:organism/id :organism/org :organism/objective
                     :organism/responsibilities :organism/repos
                     :organism/governor :organism/budget
                     :organism/authority]))))
         (sort-by (comp str :organism/id))
         vec)))

(defn- git-lines [directory & args]
  (let [{:keys [exit out]} (apply shell/sh "git" "-C"
                                  (.getAbsolutePath (io/file directory))
                                  args)]
    (when (zero? exit)
      (clojure.string/split-lines out))))

(defn- parse-ref [line]
  (let [[sha ref] (clojure.string/split line #"\t" 2)]
    {:name ref :sha sha
     :kind (cond
             (clojure.string/starts-with? ref "refs/heads/") "branch"
             (clojure.string/starts-with? ref "refs/remotes/") "remote-branch"
             (clojure.string/starts-with? ref "refs/tags/") "tag"
             :else "ref")}))

(defn- parse-commit [line]
  (let [[sha & parents] (clojure.string/split line #" ")]
    {:sha sha :parents (vec parents)}))

(defn- path-node [line]
  (let [[metadata path] (clojure.string/split line #"\t" 2)
        [mode type sha] (clojure.string/split metadata #" ")]
    {:path path :sha sha :mode mode :type type
     :parent (some-> path io/file .getParent
                     (clojure.string/replace java.io.File/separator "/"))}))

(defn- git-object-tree
  "Read the complete locally fetched Git object graph. No sampling is used:
  commits contains every object reachable from every local/remote ref and files
  contains every entry at HEAD. The cache key changes when any ref moves."
  [workspace project]
  (let [directory (io/file workspace project)
        refs (git-lines directory "for-each-ref"
                        "--format=%(objectname)%09%(refname)")
        signature (hash refs)]
    (when (and (.isDirectory directory) (seq refs))
      (if-let [cached (get @git-tree-cache [project signature])]
        cached
        (let [head (first (git-lines directory "rev-parse" "HEAD"))
              merged-branches
              (set (or (git-lines directory "for-each-ref" "--merged=HEAD"
                                  "--format=%(refname)" "refs/heads")
                       []))
              pruning-candidates
              (->> refs
                   (map parse-ref)
                   (filter #(and (= "branch" (:kind %))
                                 (contains? merged-branches (:name %))
                                 (not (contains?
                                       #{"refs/heads/main" "refs/heads/master"}
                                       (:name %)))))
                   (mapv #(assoc % :reason "merged-into-head"
                                :action "propose-prune"
                                :requires-approval true)))
              value {:project project
                     :head head
                     :refs (mapv parse-ref refs)
                     :pruning-candidates pruning-candidates
                     :commits (mapv parse-commit
                                    (or (git-lines directory "rev-list"
                                                   "--all" "--parents")
                                        []))
                     :files (mapv path-node
                                  (or (git-lines directory "ls-tree" "-r" "-t"
                                                 "--full-tree" "HEAD")
                                      []))}]
          (swap! git-tree-cache
                 (fn [cache] (-> cache
                                 (assoc [project signature] value)
                                 (select-keys
                                  (take-last 12 (keys
                                                 (assoc cache
                                                        [project signature]
                                                        value)))))))
          value)))))

(defn- active-git-trees [registry runs]
  (let [workspace (observer/workspace-root)
        projects (->> (concat (map run-project runs)
                              (map :path (:active-repos registry)))
                      (map (fn [project]
                             (let [file (io/file project)]
                               (if (.isAbsolute file)
                                 (workspace-path project)
                                 project))))
                      (filter #(and (not (-> % io/file .isAbsolute))
                                    (-> (io/file workspace %) .isDirectory)))
                      distinct
                      (take 8))]
    (->> projects
         (keep #(git-object-tree workspace %))
         vec)))

(defn- workspace-path [path]
  (let [root (some-> (or (System/getenv "KOTOBA_WORKSPACE_ROOT")
                         (observer/workspace-root))
                     io/file .getAbsolutePath)
        absolute (some-> path io/file .getAbsolutePath)
        relative (if (and root absolute
                          (.startsWith absolute
                                       (str root java.io.File/separator)))
                   (subs absolute (inc (count root)))
                   path)]
    (canonical-project-path relative)))

(defn- run-project [run]
  (workspace-path
   (or (:agent.run/source-project run) (:agent.run/project run))))

(defn- run-context [events run]
  (let [receipts (filter #(= (:agent.run/id run) (:tamaki.event/run %)) events)]
    {:issue (or (some #(get-in % [:tamaki.event/data :issue/id]) receipts)
                (some->> (:agent.run/goal run)
                         (re-find #"[0-9a-f]{40}")))
     :loop (some #(get-in % [:tamaki.event/data :loop/id]) receipts)}))

(defn- discover-project-topologies [registry]
  (let [root (io/file (observer/workspace-root))
        candidates (->> (:repos registry)
                        (map #(io/file root (:path %) "docs"
                                      "revenue-growth-project.edn"))
                        (filter #(.isFile %))
                        (take 20))]
    (mapv
     (fn [file]
       (let [project (edn/read-string (slurp file))]
         {:id (str (:project/id project))
          :objective (:project/objective project)
          :metric (str (:project/metric project))
          :reverse-topology
          (mapv #(mapv str %) (:project/reverse-topology project))
          :execution-waves
          (mapv #(mapv str %) (:project/execution-waves project))
          :issues
          (mapv (fn [issue]
                  {:key (str (:issue/key issue))
                   :rad (:issue/rad issue)
                   :repo (:issue/repo issue)
                   :status (some-> (:issue/status issue) name)
                   :blockers (mapv str (:issue/blockers issue))})
                (:project/issues project))}))
     candidates)))

(defonce project-topologies (atom nil))

(defn live-objective-topologies
  "Project durable loop objectives and their selected Radicle issues as a
  walkable graph. The objective depends on its issue frontier; issue blockers
  remain explicit edges, so the UI can render blocker-first execution order."
  [events runs campaigns]
  (let [run-by-id (into {} (map (juxt :agent.run/id identity)) runs)
        selected (->> events
                      (filter #(= :issue/prioritized
                                  (:tamaki.event/kind %)))
                      (keep (fn [event]
                              (let [run (get run-by-id
                                             (:tamaki.event/run event))
                                    data (:tamaki.event/data event)
                                    issue (get-in data
                                                  [:issue/selection :issue])]
                                (when (and run issue)
                                  {:loop (:loop (run-context events run))
                                   :run (:agent.run/id run)
                                   :project (run-project run)
                                   :at (:tamaki.event/at event)
                                   :issue issue}))))
                      (group-by :project))]
    (->> campaigns
         (filter #(= :active (:tamaki.loop/status %)))
         (mapv
          (fn [campaign]
            (let [project (workspace-path (:tamaki.loop/project campaign))
                  objective-key (str "objective/" (:tamaki.loop/id campaign))
                  observations (get (group-by :loop (mapcat val selected))
                                    (:tamaki.loop/id campaign))
                  issues (->> observations
                              (map :issue)
                              (reduce (fn [result issue]
                                        (assoc result (:issue/id issue) issue))
                                      {})
                              vals
                              vec)
                  issue-keys (mapv (comp str :issue/id) issues)]
              {:id (str (:tamaki.loop/id campaign))
               :objective (:tamaki.loop/objective campaign)
               :metric "issue topology walk"
               :reverse-topology
               (cond-> []
                 (seq issue-keys) (conj issue-keys)
                 true (conj [objective-key]))
               :execution-waves (cond-> []
                                  (seq issue-keys) (conj issue-keys)
                                  true (conj [objective-key]))
               :issues
               (into
                [{:key objective-key :rad nil :repo project
                  :kind "objective" :status "active"
                  :title (:tamaki.loop/objective campaign)
                  :blockers issue-keys}]
                (map (fn [issue]
                       {:key (str (:issue/id issue))
                        :rad (:issue/id issue)
                        :repo project
                        :kind "issue"
                        :title (:issue/title issue)
                        :status (some-> (:issue/status issue) name)
                        :blockers (mapv str (:issue/blockers issue))})
                     issues))
               :walks
               (mapv (fn [{:keys [run at issue]}]
                       {:actor run :at at :from objective-key
                        :to (str (:issue/id issue))})
                     observations)})))
         (filterv #(seq (:issues %))))))

(defn actor-states [runs]
  (let [root (.getParentFile (io/file (observer/state-dir)))
        private-root (or (System/getenv "TAMAKI_CONTROL_ROOT")
                         (str (observer/workspace-root)
                              "/projects/.tamaki/tamaki-control"))
        dirs [(io/file root "actors") (io/file private-root "actors")]]
    (->> dirs
         (mapcat #(or (.listFiles %) (make-array java.io.File 0)))
         (filter #(and (.isFile %) (.endsWith (.getName %) ".edn")))
         (keep (fn [file]
                 (let [value (edn/read-string (slurp file))
                       project (:actor/project value)
                       value (if (and project
                                      (not (.isAbsolute (io/file project))))
                               (assoc value :actor/project
                                      (.getCanonicalPath
                                       (io/file root project)))
                               value)]
                   (when (:actor/id value)
                     (let [spec (actor/validate-spec value)
                           plan (actor/reconcile-plan spec runs)]
                       {:id (str (:actor/id spec))
                        :type (str (:actor/type spec))
                        :project (workspace-path (:actor/project spec))
                        :objective (:actor/objective spec)
                        :desired (:desired plan)
                        :running (:running plan)
                        :queued (:queued plan)
                        :blocked (:blocked plan)
                        :spawn (:spawn plan)
                        :hil-policy
                        (into {}
                              (map (fn [[gate decision]]
                                     [(name gate) (name decision)]))
                                 (:actor/hil-policy spec))})))))
         (reduce (fn [actors actor] (assoc actors (:id actor) actor)) {})
         vals
         (sort-by :id)
         vec)))

(def dynamics-window-ms 3600000)

(defn business-targets []
  (let [tamaki (io/file (observer/workspace-root)
                        "orgs" "etzhayyim" "tamaki")
        private (io/file tamaki "actors" "revenue-targets.edn")
        example (io/file tamaki "examples" "revenue-targets.example.edn")
        path (if (.isFile private) private example)]
    (if (.isFile path) (business/read-targets (.getAbsolutePath path)) {})))

(defn- prefixed-business-dynamics [events]
  (let [dynamics (business/stock-flow events (business-targets))
        prefix #(if (= "environment" %) % (str "business-" %))]
    (-> dynamics
        (update :stocks
                #(mapv (fn [stock] (update stock :id prefix)) %))
        (update :flows
                #(mapv (fn [flow]
                         (-> flow
                             (update :id prefix)
                             (update :from prefix)
                             (update :to prefix)))
                       %)))))

(defn system-dynamics
  "Project durable events into observable stocks and hourly flows.
  The model intentionally uses only persisted facts, so the UI never invents
  progress from an agent's prose."
  [events runs observed-at]
  (let [kind #(= % (:tamaki.event/kind %2))
        issue-id #(get-in % [:tamaki.event/data :issue/id])
        patch-id #(get-in % [:tamaki.event/data :patch/id])
        discovered (set (keep issue-id (filter (partial kind :issue/discovered)
                                               events)))
        integrated-issues
        (set (keep issue-id (filter (partial kind :patch/integrated) events)))
        patches (set (keep patch-id (filter (partial kind :patch/created) events)))
        integrated-patches
        (set (keep patch-id (filter (partial kind :patch/integrated) events)))
        result-patch
        (fn [result-id]
          (when result-id
            (clojure.string/replace (str result-id) #"^result/" "")))
        evaluated-patches
        (set (keep #(result-patch
                    (get-in % [:tamaki.event/data :evaluation/result]))
                   (filter (partial kind :result/evaluated) events)))
        validated-patches
        (set (keep #(result-patch
                    (get-in % [:tamaki.event/data :validation/result]))
                   (filter (partial kind :result/validated) events)))
        regressed-patches
        (set (keep #(result-patch
                    (get-in % [:tamaki.event/data :validation/result]))
                   (filter (partial kind :result/regressed) events)))
        integrated-unvalidated
        (set/difference integrated-patches validated-patches)
        validated-value
        (set/difference validated-patches regressed-patches)
        evaluation-debt
        (set/difference integrated-patches evaluated-patches)
        evaluation-scores
        (keep #(get-in % [:tamaki.event/data :evaluation/score])
              (filter (partial kind :result/evaluated) events))
        validated-scores
        (keep #(get-in % [:tamaki.event/data
                          :validation/observed-score])
              (filter (partial kind :result/validated) events))
        mean (fn [values]
               (if (seq values)
                 (/ (reduce + (map double values))
                    (double (count values)))
                 0.0))
        active-runs (count (filter #(active-statuses (:agent.run/status %)) runs))
        recent (filter #(>= (or (:tamaki.event/at %) 0)
                            (- observed-at dynamics-window-ms))
                       events)
        rate (fn [event-kind]
               (count (filter (partial kind event-kind) recent)))
        starts (rate :run/started)
        successes (rate :run/succeeded)
        failures (+ (rate :run/failed) (rate :loop/cycle-failed))
        attempts (+ successes failures)
        backlog (count (remove integrated-issues discovered))
        review-queue (count (remove integrated-patches patches))
        flows [{:id "discover" :label "discover"
                :from "environment" :to "backlog"
                :rate (rate :issue/discovered)}
               {:id "start" :label "start"
                :from "backlog" :to "wip" :rate starts}
               {:id "patch" :label "patch"
                :from "wip" :to "review" :rate (rate :patch/created)}
               {:id "integrate" :label "merge"
                :from "review" :to "integrated"
                :rate (rate :patch/integrated)}
               {:id "evaluate" :label "evaluate"
                :from "integrated" :to "integrated-unvalidated"
                :rate (rate :result/evaluated)}
               {:id "validate" :label "validate"
                :from "integrated-unvalidated" :to "validated-value"
                :rate (rate :result/validated)}
               {:id "regress" :label "regress"
                :from "validated-value" :to "regression-debt"
                :rate (rate :result/regressed)}]
        stocks [{:id "backlog" :label "Issue backlog" :value backlog
                 :unit "issues" :color "#ffb34d"}
                {:id "wip" :label "Agent WIP" :value active-runs
                 :unit "runs" :color "#49ee91"}
                {:id "review" :label "Review queue" :value review-queue
                 :unit "patches" :color "#54a8ff"}
                {:id "integrated" :label "Integrated value"
                 :value (count integrated-patches)
                 :unit "patches" :color "#d06cff"}
                {:id "integrated-unvalidated"
                 :label "Integrated, unvalidated"
                 :value (count integrated-unvalidated)
                 :unit "results" :color "#c58aff"}
                {:id "validated-value" :label "Validated value"
                 :value (count validated-value)
                 :unit "results" :color "#73f4a1"}
                {:id "evaluation-debt" :label "Evaluation debt"
                 :value (count evaluation-debt)
                 :unit "results" :color "#ffcf66"}
                {:id "regression-debt" :label "Regression debt"
                 :value (count regressed-patches)
                 :unit "results" :color "#ff6b7d"}]
        bottleneck (apply max-key
                          :value
                          (filter #(contains?
                                    #{"backlog" "wip" "review"
                                      "evaluation-debt"
                                      "integrated-unvalidated"}
                                    (:id %))
                                  stocks))
        business-dynamics (prefixed-business-dynamics events)
        business-observed? (= :observed (:status business-dynamics))]
    {:window-ms dynamics-window-ms
     :stocks (cond-> stocks
               business-observed?
               (into (:stocks business-dynamics)))
     :flows (cond-> flows
              business-observed?
              (into (:flows business-dynamics)))
     :bottleneck (:id bottleneck)
     :failure-pressure (if (pos? attempts) (/ failures (double attempts)) 0.0)
     :backlog-delta (- (rate :issue/discovered) (rate :patch/integrated))
     :throughput (rate :patch/integrated)
     :validation-throughput (rate :result/validated)
     :validated-value (count validated-value)
     :evaluation-debt (count evaluation-debt)
     :regression-debt (count regressed-patches)
     :evaluation-score (mean evaluation-scores)
     :result-control-score (mean validated-scores)
     :business-status (:status business-dynamics)
     :business-control-score (:control-score business-dynamics)
     :business-kpis (:kpis business-dynamics)
     :business-targets (:targets business-dynamics)
     :observed-at observed-at}))

(defn finance-dashboard
  "Project only explicit accounting observations. The dashboard deliberately
  leaves unknown fields nil instead of turning missing books into zero."
  [events]
  (let [rows (->> events
                  (filter #(= :finance/observed (:tamaki.event/kind %)))
                  (sort-by :tamaki.event/at)
                  (reduce (fn [latest event]
                            (let [row (:tamaki.event/data event)
                                  owner-ref (or (get-in row [:owner :ref])
                                                (:org row)
                                                :unassigned)]
                              (assoc latest owner-ref row)))
                          {}))
        add-known (fn [xs]
                    (let [known (filter number? xs)]
                      (when (seq known) (reduce + known))))
        total-section
        (fn [section]
          (->> rows vals
               (map section)
               (reduce
                (fn [totals statement]
                  (reduce-kv
                   (fn [m k v] (update m k #(add-known [% v])))
                   totals (or statement {})))
                {})))
        pl (total-section :pl)
        bs (total-section :bs)
        cf (total-section :cf)
        gross-profit (when (and (number? (:revenue pl))
                                (number? (:cost-of-sales pl)))
                       (- (:revenue pl) (:cost-of-sales pl)))
        operating-profit (when (and (number? gross-profit)
                                    (number? (:operating-expenses pl)))
                           (- gross-profit (:operating-expenses pl)))
        balance-delta (when (and (number? (:assets bs))
                                 (number? (:liabilities bs))
                                 (number? (:equity bs)))
                        (- (:assets bs) (:liabilities bs) (:equity bs)))
        segments
        (->> (vals rows)
             (group-by (fn [row]
                         (cond
                           (= :crypto (:asset-kind row)) :crypto
                           (= :personal (get-in row [:owner :kind])) :personal
                           :else :corporate)))
             (map (fn [[segment segment-rows]]
                    [segment
                     {:observations (count segment-rows)
                      :assets (add-known (map #(get-in % [:bs :assets])
                                              segment-rows))
                      :revenue (add-known (map #(get-in % [:pl :revenue])
                                               segment-rows))}]))
             (into {}))]
    {:status (if (seq rows) :observed :unavailable)
     :currency (or (:currency (last (sort-by :observed-at (vals rows)))) :JPY)
     :period (:period (last (sort-by :observed-at (vals rows))))
     :organizations (count rows)
     :pl (assoc pl :gross-profit gross-profit
                   :operating-profit operating-profit)
     :bs (assoc bs :balance-delta balance-delta)
     :cf cf
     :segments segments
     :by-org rows}))

(defn- repo-stats [events runs campaigns]
  (let [run-by-id (into {} (map (juxt :agent.run/id identity)) runs)
        project-of #(some-> (get run-by-id (:tamaki.event/run %)) run-project)]
    (->> events
         (group-by project-of)
         (keep
          (fn [[project repo-events]]
            (when project
              (let [issues (set (keep #(get-in % [:tamaki.event/data :issue/id])
                                      (filter (comp #{:issue/discovered}
                                                    :tamaki.event/kind)
                                              repo-events)))
                    solved (set (keep #(get-in % [:tamaki.event/data :issue/id])
                                      (filter (comp #{:patch/integrated}
                                                    :tamaki.event/kind)
                                              repo-events)))
                    patches (count (filter (comp #{:patch/created}
                                                 :tamaki.event/kind)
                                           repo-events))
                    integrated (count (filter (comp #{:patch/integrated}
                                                    :tamaki.event/kind)
                                              repo-events))]
                {:path project
                 :issues-open (count (remove solved issues))
                 :patches-open (max 0 (- patches integrated))
                 :loops (count (filter #(= project
                                          (workspace-path
                                           (:tamaki.loop/project %)))
                                       campaigns))
                 :wip (count (filter
                              #(and (= project (run-project %))
                                    (active-statuses (:agent.run/status %)))
                              runs))}))))
         vec)))

(defn activity-feed
  "Join durable events to their AgentRun identity and stream. Explicit
  activity metadata wins; lifecycle events and older activity records receive
  deterministic fallbacks so the UI can always attribute and filter them."
  [events runs]
  (let [run-by-id (into {} (map (juxt :agent.run/id identity)) runs)]
    (->> events
         (sort-by :tamaki.event/at >)
         (take 80)
         (mapv
          (fn [event]
            (let [data (:tamaki.event/data event)
                  run-id (:tamaki.event/run event)
                  run (get run-by-id run-id)
                  kind (or (:activity/kind data) (:tamaki.event/kind event))
                  stream (or (:activity/stream data)
                             (cond
                               (= :agent/activity (:tamaki.event/kind event))
                               (if (= "tool" (namespace kind)) :tool
                                   (if (= "model" (namespace kind)) :model
                                       :output))
                               (some? run) :lifecycle
                               :else :system))
                  agent-id (or (:activity/agent data)
                               (:agent.run/id run)
                               "system")]
              {:id (:tamaki.event/id event)
               :at (:tamaki.event/at event)
               :run run-id
               :agent-id (str agent-id)
               :agent-runner (or (:agent.run/runner run) "system")
               :agent-model (or (:agent.run/model run) "default")
               :agent-worker (or (:activity/worker data)
                                 (:agent.run/worker run))
               :stream (name stream)
               :kind kind
               :state (:activity/state data)
               :text (:activity/text data)
               :issue (:issue/id data)
               :patch (:patch/id data)}))))))

(defn web-snapshot []
  ;; Read and fold the append-only stream once per frame. The old path read
  ;; the entire file in observer/snapshot and immediately read it again here.
  (let [events (read-events-incrementally (observer/state-dir))
        state (observer/snapshot events)
        registry (:registry state)
        runs (:runs state)
        campaigns (:campaigns state)
        ao-fleet (some->> events
                          (filter #(= :ao.fleet/reconciled
                                      (:tamaki.event/kind %)))
                          last
                          :tamaki.event/data)
        agents (->> runs
                    (filter #(active-statuses (:agent.run/status %)))
                    (mapv (fn [run]
                            (merge
                             {:id (:agent.run/id run)
                              :ao (:agent.run/organism run)
                              :status (:agent.run/status run)
                              :model (:agent.run/model run)
                              :runner (:agent.run/runner run)
                              :project (run-project run)
                              :execution-project (:agent.run/project run)
                              :goal (:agent.run/goal run)
                              :parent (:agent.run/parent run)}
                             (run-context events run)))))]
    {:observed-at (:observed-at state)
     :counts (select-keys registry [:total :west :github :rad :local])
     :orgs (:orgs registry)
     :repos (mapv #(select-keys % [:name :path :remote :west? :github? :rad?
                                   :local? :sync])
                  (:repos registry))
     :dependencies (:dependencies registry)
     :organisms (organism-specs)
     :ao-fleet ao-fleet
     :git-trees (active-git-trees registry runs)
     :projects
     (into (or @project-topologies
               (reset! project-topologies
                       (discover-project-topologies registry)))
           (live-objective-topologies events runs campaigns))
     :agents agents
     :actors (actor-states runs)
     :loops (mapv (fn [campaign]
                    {:id (:tamaki.loop/id campaign)
                     :ao (:tamaki.loop/ao campaign)
                     :status (:tamaki.loop/status campaign)
                     :project (workspace-path (:tamaki.loop/project campaign))
                     :objective (:tamaki.loop/objective campaign)
                     :model (:tamaki.loop/model campaign)
                     :runner (:tamaki.loop/runner campaign)
                     :cycles (:tamaki.loop/cycles campaign)
                     :max-cycles (:tamaki.loop/max-cycles campaign)})
                  campaigns)
     :repo-stats (repo-stats events runs campaigns)
     :system-dynamics (system-dynamics events runs (:observed-at state))
     :finance (finance-dashboard events)
     :active-repos
     (mapv (fn [{:keys [path issue runs]}]
             {:path path :issue issue
              :runs (mapv #(select-keys % [:agent.run/id :agent.run/status
                                           :agent.run/model :agent.run/goal])
                          runs)})
           (:active-repos registry))
     :decisions (:decisions state)
     :campaigns (:campaigns state)
     :activity (activity-feed events runs)
     :results (mapv #(update % :result/project workspace-path)
                    (result/result-graphs events runs
                                          (evolution/candidates events)))
     :evaluations (->> events
                       (filter #(= :result/evaluated
                                   (:tamaki.event/kind %)))
                       (mapv :tamaki.event/data))
     :tournaments (->> events
                       (filter #(= :result/tournament-recorded
                                   (:tamaki.event/kind %)))
                       (mapv :tamaki.event/data))
     :validations (->> events
                       (filter #(contains? #{:result/validated
                                             :result/regressed}
                                           (:tamaki.event/kind %)))
                       (mapv :tamaki.event/data))
     :model-usage
     (let [run-by-id (into {} (map (juxt :agent.run/id identity)) runs)]
       (->> events
            (filter #(= :agent/activity (:tamaki.event/kind %)))
            (keep (fn [event]
                    (let [data (:tamaki.event/data event)
                          run (get run-by-id (:tamaki.event/run event))]
                      (when (some #(contains? data %)
                                  [:usage/input :usage/output
                                   :usage/cache-read :usage/cache-write])
                        {:provider (or (:agent.run/runner run)
                                       (some-> (:agent.run/model run)
                                               (clojure.string/split #":")
                                               first)
                                       "unknown")
                         :input (or (:usage/input data) 0)
                         :output (or (:usage/output data) 0)
                         :cache-read (or (:usage/cache-read data) 0)
                         :cache-write (or (:usage/cache-write data) 0)}))))
            (group-by :provider)
            (map (fn [[provider rows]]
                   {:provider provider
                    :input (reduce + (map :input rows))
                    :output (reduce + (map :output rows))
                    :cache-read (reduce + (map :cache-read rows))
                    :cache-write (reduce + (map :cache-write rows))
                    :remaining :unknown}))
            vec))}))

(defn write-snapshot! [target]
  (let [target-file (io/file target)
        next-file (io/file (str target ".next"))]
    (.mkdirs (.getParentFile target-file))
    (let [snapshot (web-snapshot)]
      (spit next-file (json/write-str snapshot))
      (spit (io/file (.getParentFile target-file) "topology.edn")
            (pr-str (select-keys snapshot
                                 [:repos :dependencies :organisms :ao-fleet
                                  :agents :loops
                                  :repo-stats :system-dynamics :finance]))))
    (java.nio.file.Files/move
     (.toPath next-file) (.toPath target-file)
     (into-array java.nio.file.CopyOption
                 [java.nio.file.StandardCopyOption/REPLACE_EXISTING
                  java.nio.file.StandardCopyOption/ATOMIC_MOVE]))))

(defn -main [& [target]]
  (when-not target
    (throw (ex-info "snapshot target is required" {})))
  (let [interval-ms
        (or (some-> (System/getenv "TAMAKI_SNAPSHOT_INTERVAL_MS")
                    parse-long)
            5000)]
   (loop []
    (try
      (write-snapshot! target)
      (catch Exception e
        (binding [*out* *err*] (println "snapshot failed:" (.getMessage e)))))
    (Thread/sleep (max 1000 interval-ms))
    (recur))))
