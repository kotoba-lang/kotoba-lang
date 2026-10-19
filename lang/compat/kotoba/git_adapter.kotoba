(ns kotoba.git-adapter
  "Host adapter for the :git CLI command.

  The command shape is owned by kotoba-lang/kotoba-lang (`lang/cli.edn` +
  `kotoba.cli/dispatch`). This namespace consumes the `:command/planned`
  result for `git` and turns it into process steps. Planning and output
  parsing are pure; execution happens only through an injected process
  port, so the core makes no direct host calls."
  (:require [clojure.string :as str]))

(defprotocol IProcess
  "Host-supplied process capability. `-run` takes a full argv vector and
  returns {:exit int :out string :err string}."
  (-run [this argv]))

(def operations #{:init :status :commit :sync})

(defn request-operation
  "Resolve the git operation from a parsed CLI request: the first positional
  subcommand wins, then the --op option."
  [request]
  (let [raw (or (first (:positionals request))
                (get-in request [:options :op]))]
    (when raw
      (keyword (name (cond-> raw (string? raw) str/trim))))))

(defn request-repo [request]
  (or (get-in request [:options :repo]) "."))

(defn request-message [request]
  (or (get-in request [:options :message])
      (get-in request [:options :m])))

(defn plan
  "Pure plan: turn a parsed :git request into process steps
  [{:id kw :argv [\"git\" ...]} ...] or a failure map {:error ...}."
  [request]
  (let [op (request-operation request)
        repo (request-repo request)
        ref (get-in request [:options :ref])]
    (cond
      (nil? op)
      {:error :git/missing-operation
       :expected (sort operations)}

      (not (operations op))
      {:error :git/unknown-operation
       :operation op
       :expected (sort operations)}

      :else
      (case op
        :init
        {:operation op
         :repo repo
         :steps [{:id :git/init :argv ["git" "init" repo]}]}

        :status
        {:operation op
         :repo repo
         :steps [{:id :git/status
                  :argv ["git" "-C" repo "status" "--porcelain=v1" "--branch"]}]}

        :commit
        (if-not (request-message request)
          {:error :git/missing-message
           :operation op
           :expected "-m/--message"}
          {:operation op
           :repo repo
           :steps [{:id :git/add :argv ["git" "-C" repo "add" "-A"]}
                   {:id :git/commit
                    :argv ["git" "-C" repo "commit" "-m" (request-message request)]}]})

        :sync
        {:operation op
         :repo repo
         :steps [{:id :git/fetch
                  :argv ["git" "-C" repo "fetch" "--depth" "1" "origin"]}
                 {:id :git/merge
                  :argv (if ref
                          ["git" "-C" repo "merge" "--ff-only" ref]
                          ["git" "-C" repo "merge" "--ff-only" "FETCH_HEAD"])}]}))))

(defn parse-status
  "Pure parser for `git status --porcelain=v1 --branch` output."
  [out]
  (let [lines (remove str/blank? (str/split-lines (or out "")))
        branch-line (first (filter #(str/starts-with? % "## ") lines))
        entries (->> lines
                     (remove #(str/starts-with? % "## "))
                     (mapv (fn [line]
                             {:xy (subs line 0 (min 2 (count line)))
                              :path (str/trim (subs line (min 3 (count line))))})))]
    {:branch (some-> branch-line (subs 3) (str/split #"\.\.\.") first)
     :entries entries
     :clean? (empty? entries)}))

(defn- run-steps
  "Run plan steps in order through the port; stop at the first non-zero exit.
  Returns {:executed [...] :failed step-result-or-nil}."
  [port steps]
  (reduce (fn [{:keys [executed]} {:keys [id argv]}]
            (let [{:keys [exit out err]} (-run port argv)
                  step-result {:id id :argv argv :exit exit
                               :out (str out) :err (str err)}]
              (if (zero? exit)
                {:executed (conj executed step-result) :failed nil}
                (reduced {:executed (conj executed step-result)
                          :failed step-result}))))
          {:executed [] :failed nil}
          steps))

(defn execute!
  "Execute a `:command/planned` result for :git through the injected process
  port. Returns a kotoba.cli-shaped result map."
  [port planned-result]
  (let [request (get-in planned-result [:kotoba.cli/data :request])
        planned (plan request)]
    (if (:error planned)
      {:kotoba.cli/ok? false
       :kotoba.cli/code (:error planned)
       :kotoba.cli/message "git adapter could not plan the request"
       :kotoba.cli/data (assoc (dissoc planned :error) :request request)}
      (let [{:keys [executed failed]} (run-steps port (:steps planned))
            base {:operation (:operation planned)
                  :repo (:repo planned)
                  :steps executed}
            data (if (= :status (:operation planned))
                   (assoc base :status (parse-status (:out (first executed))))
                   base)]
        (if failed
          {:kotoba.cli/ok? false
           :kotoba.cli/code :git/step-failed
           :kotoba.cli/message (str "git step " (name (:id failed)) " failed")
           :kotoba.cli/data data}
          {:kotoba.cli/ok? true
           :kotoba.cli/code :git/executed
           :kotoba.cli/data data})))))
