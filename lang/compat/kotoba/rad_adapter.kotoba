(ns kotoba.rad-adapter
  "Host adapter for the :rad CLI command.

  The command shape is owned by kotoba-lang/kotoba-lang (`lang/cli.edn` +
  `kotoba.cli/dispatch`). This namespace consumes the `:command/planned`
  result for `rad` and turns it into steps over a Kotoba package:

  - :new    scaffolds a package (draft package.edn + main source) via fs steps
  - :build  emits the wasm artifact by re-dispatching `wasm emit` in-process
  - :test   checks the main source by re-dispatching `check` in-process
  - :export emits the wasm artifact to an explicit --output path

  Planning is pure; filesystem writes and launcher dispatch happen only
  through an injected host port, so the core makes no direct host calls."
  (:require [clojure.string :as str]))

(defprotocol IRadHost
  "Host-supplied capabilities for rad workflows."
  (-mkdirs [this path])
  (-write-file [this path content])
  (-dispatch [this argv]))

(def operations #{:new :build :test :export})

(defn request-operation
  "Resolve the rad operation from a parsed CLI request: the first positional
  subcommand wins, then the --op option."
  [request]
  (let [raw (or (first (:positionals request))
                (get-in request [:options :op]))]
    (when raw
      (keyword (name (cond-> raw (string? raw) str/trim))))))

(defn request-project [request]
  (let [p (get-in request [:options :project])]
    (if (and (string? p) (not (str/blank? p)))
      (str/replace p #"/+$" "")
      ".")))

(defn request-output [request]
  (let [o (or (get-in request [:options :output])
              (get-in request [:options :o]))]
    (when (string? o) o)))

(defn project-name
  "Package name derived from the project directory basename."
  [project]
  (let [base (last (str/split project #"/"))]
    (if (or (nil? base) (str/blank? base) (= "." base))
      "kotoba-app"
      base)))

(defn- munge-name [n]
  (str/replace n "-" "_"))

(defn main-source-path [project]
  (str project "/src/" (munge-name (project-name project)) ".kotoba"))

(defn default-output-path [project]
  (str project "/target/" (munge-name (project-name project)) ".wasm"))

(defn lock-path
  "Path to the scaffolded zero-dependency package lock every `:build`/
  `:export` step passes to `wasm emit --package-lock` (F-001 made that
  option mandatory with no opt-out; a freshly scaffolded app has no
  dependencies to lock, so the scaffold itself provides the vacuous
  `:deps []` lock kotoba-lang/kotoba-lang admits for that case)."
  [project]
  (str project "/kotoba.lock.edn"))

(defn scaffold-files
  "Pure scaffold: relative path -> content for a new Kotoba package. The
  package.edn is a draft manifest — content pins (CIDs) and signatures are
  added by publish tooling, not by scaffolding."
  [project template]
  (let [name (project-name project)
        munged (munge-name name)]
    {(str "src/" munged ".kotoba")
     (str "(ns " name ")\n\n(defn main []\n  (+ 40 2))\n")

     "package.edn"
     (str "{:kotoba.package/name \"" name "\"\n"
          " :kotoba.package/version \"0.1.0\"\n"
          " :kotoba.package/template " (pr-str (or template "app")) "\n"
          " :kotoba.package/capabilities []\n"
          " ;; Draft manifest. :kotoba.package/repo-rid, :kotoba.package/source\n"
          " ;; CIDs, and :kotoba.package/signatures are added by publish tooling\n"
          " ;; (see kotoba-lang/kotoba-lang lang/package.edn).\n"
          " :kotoba.package/draft? true}\n")

     "kotoba.lock.edn"
     (str "{:kotoba.lock/version 1\n"
          " ;; No dependencies yet -- add :deps entries as this package takes\n"
          " ;; on real ones. `wasm emit --package-lock` (F-001, mandatory) needs\n"
          " ;; this file to exist even for a zero-dependency build.\n"
          " :deps []}\n")

     "README.md"
     (str "# " name "\n\nScaffolded by `kotoba rad new`.\n\n"
          "```sh\nkotoba rad test --project .\nkotoba rad build --project .\n```\n")}))

(defn plan
  "Pure plan: turn a parsed :rad request into steps
  [{:kind :fs/mkdirs|:fs/write|:dispatch ...} ...] or {:error ...}."
  [request]
  (let [op (request-operation request)
        project (request-project request)
        template (get-in request [:options :template])
        profile (or (get-in request [:options :profile]) "dev")
        output (request-output request)
        src (main-source-path project)]
    (cond
      (nil? op)
      {:error :rad/missing-operation
       :expected (sort operations)}

      (not (operations op))
      {:error :rad/unknown-operation
       :operation op
       :expected (sort operations)}

      :else
      (case op
        :new
        {:operation op
         :project project
         :steps (into [{:kind :fs/mkdirs :id :rad/src-dir :path (str project "/src")}]
                      (map (fn [[path content]]
                             {:kind :fs/write
                              :id (keyword "rad" (str "write-" (str/replace path #"[/.]" "-")))
                              :path (str project "/" path)
                              :content content}))
                      (sort-by key (scaffold-files project template)))}

        :build
        (let [out (or output (default-output-path project))]
          {:operation op
           :project project
           :profile profile
           :steps [{:kind :fs/mkdirs :id :rad/target-dir
                    :path (or (re-find #".*(?=/)" out) ".")}
                   {:kind :dispatch :id :rad/wasm-emit
                    :argv ["wasm" "emit" src "--package-lock" (lock-path project) "--output" out]}]})

        :test
        {:operation op
         :project project
         :profile profile
         :steps [{:kind :dispatch :id :rad/check
                  :argv ["check" src]}]}

        :export
        (if-not output
          {:error :rad/missing-output
           :operation op
           :expected "-o/--output"}
          {:operation op
           :project project
           :steps [{:kind :fs/mkdirs :id :rad/output-dir
                    :path (or (re-find #".*(?=/)" output) ".")}
                   {:kind :dispatch :id :rad/wasm-emit
                    :argv ["wasm" "emit" src "--package-lock" (lock-path project) "--output" output]}]})))))

(defn- run-step [host {:keys [kind id path content argv]}]
  (case kind
    :fs/mkdirs (do (-mkdirs host path)
                   {:id id :kind kind :path path :ok? true})
    :fs/write (do (-write-file host path content)
                  {:id id :kind kind :path path :ok? true})
    :dispatch (let [result (-dispatch host argv)]
                {:id id :kind kind :argv argv
                 :ok? (boolean (:kotoba.cli/ok? result))
                 :result result})))

(defn- run-steps [host steps]
  (reduce (fn [{:keys [executed]} step]
            (let [step-result (run-step host step)]
              (if (:ok? step-result)
                {:executed (conj executed step-result) :failed nil}
                (reduced {:executed (conj executed step-result)
                          :failed step-result}))))
          {:executed [] :failed nil}
          steps))

(defn execute!
  "Execute a `:command/planned` result for :rad through the injected host
  port. Returns a kotoba.cli-shaped result map."
  [host planned-result]
  (let [request (get-in planned-result [:kotoba.cli/data :request])
        planned (plan request)]
    (if (:error planned)
      {:kotoba.cli/ok? false
       :kotoba.cli/code (:error planned)
       :kotoba.cli/message "rad adapter could not plan the request"
       :kotoba.cli/data (assoc (dissoc planned :error) :request request)}
      (let [{:keys [executed failed]} (run-steps host (:steps planned))
            data (-> planned
                     (dissoc :steps)
                     (assoc :steps executed))]
        (if failed
          {:kotoba.cli/ok? false
           :kotoba.cli/code :rad/step-failed
           :kotoba.cli/message (str "rad step " (name (:id failed)) " failed")
           :kotoba.cli/data data}
          {:kotoba.cli/ok? true
           :kotoba.cli/code :rad/executed
           :kotoba.cli/data data})))))
