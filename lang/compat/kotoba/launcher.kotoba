(ns kotoba.launcher
  "Rust-free launcher for the CLJC Kotoba CLI authority.

  This is intentionally small: command semantics live in `kotoba.cli` from
  kotoba-lang/kotoba-lang. Host-specific launchers call into that namespace and
  render the returned data."
  (:require [cacao.core :as cacao-core]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [kotoba.cap-table :as cap-table]
            [kotoba.compiler.core :as compiler]
            [kotoba.compiler.project-files :as project-files]
            [kotoba.lang.capability-cacao :as capability-cacao]
            [kotoba.core.contracts :as core-contracts]
            [kotoba.cli :as cli]
            [kotoba.git-adapter :as git-adapter]
            [kotoba.host-providers :as host-providers]
            [kotoba.rad-adapter :as rad-adapter]
            [kotoba.package-admission :as package-admission]
            [kotoba.runtime :as runtime]
            [kotoba.semantic-code :as semantic-code]
            [kotoba.selfhost.contracts :as selfhost]
            [kotoba.wasm-exec :as wasm-exec])
  (:import [java.io ByteArrayOutputStream FileInputStream]
           [java.nio ByteBuffer]
           [java.nio.charset CodingErrorAction StandardCharsets]
           [java.nio.file Path])
  (:gen-class))

(defn result->exit
  "Process exit code for a `:kotoba.cli/ok?` result map: 0 when ok, 1 otherwise."
  [result]
  (if (:kotoba.cli/ok? result) 0 1))

(defn exception-chain
  "Bounded, path-free exception identity for stable CLI diagnostics.

  Native-image failures often surface only the message of a wrapper exception;
  retaining the exception and cause classes lets release smoke identify a
  missing runtime class without printing host stack traces or ambient paths."
  [^Throwable error]
  (loop [current error
         remaining 8
         result []]
    (if (or (nil? current) (zero? remaining))
      result
      (recur (.getCause current)
             (dec remaining)
             (conj result
                   (cond-> {:class (.getName (class current))}
                     (instance? ClassNotFoundException current)
                     (assoc :frames
                            (mapv (fn [^StackTraceElement frame]
                                    {:class (.getClassName frame)
                                     :method (.getMethodName frame)})
                                  (take 12 (.getStackTrace current))))
                     (some? (ex-message current))
                     (assoc :message (ex-message current))))))))

(defn json-requested?
  "True when argv carries the `--json` flag."
  [argv]
  (boolean (some #{"--json"} argv)))

(defn render-result
  "Render a CLI result map for stdout: JSON (namespace stripped from keys)
  when `json-output?`, else `pr-str` EDN."
  ([result] (render-result result false))
  ([result json-output?]
   (if json-output?
     (let [diagnostic-key :kotoba.cli/diagnostic
           result (update result diagnostic-key
                          (fn [diagnostic]
                            (when diagnostic
                              (into {}
                                    (map (fn [[k v]]
                                           [k (if (keyword? v)
                                                (subs (str v) 1)
                                                v)]))
                                    diagnostic))))]
       (json/write-str result :key-fn (fn [k]
                                      (if (keyword? k)
                                        (subs (str k) 1)
                                        (str k)))))
     (pr-str result))))

(defn command-name
  "The subcommand token — argv's first element."
  [argv]
  (first argv))

(declare source-plan source-extension accepted-source? selfhost-result runtime-result wasm-result cljs-result
         compile-result project-check-result package-result contract-exports)

(def source-commands
  #{"run" "check" "compile"})

(def value-options
  #{"--cacao"
    "--kind"
    "--lock"
    "--manifest"
    "--output"
    "--package-lock"
    "--policy"
    "--project"
    "--reader-target"
    "--receipt"
    "--source-path"
    "--target"
    "--trust"
    "--host-command"
    "--host-arg"
    "--provider-command"
    "--text"
    "-S"
    "-o"})

(defn option-value
  "The token immediately following the first occurrence of `option` in argv,
  or nil if `option` isn't present."
  [argv option]
  (some (fn [[current next]]
          (when (= current option) next))
        (partition-all 2 1 argv)))

(defn option-values
  "The tokens immediately following EVERY occurrence of `option` in argv."
  [argv option]
  (keep (fn [[current next]]
          (when (= current option) next))
        (partition-all 2 1 argv)))

(defn reader-target-option
  "The `--reader-target`/`--target` option value from argv, as a keyword."
  [argv]
  (some-> (or (option-value argv "--reader-target")
              (option-value argv "--target"))
          keyword))

(defn reader-target-provided?
  "True when argv already carries a reader target option."
  [argv]
  (boolean (some #{"--reader-target" "--target"} argv)))

(defn source-positionals
  "Positional (non-option) tokens in argv, after the command name — value
  options and the value that follows them are skipped, as are other tokens
  starting with `-`."
  [argv]
  (loop [tokens (rest argv)
         positionals []]
    (if-let [token (first tokens)]
      (cond
        (value-options token)
        (recur (nnext tokens) positionals)

        (str/starts-with? token "-")
        (recur (next tokens) positionals)

        :else
        (recur (next tokens) (conj positionals token)))
      positionals)))

(defn first-source-arg
  "The first positional argv token that names an accepted source, or nil."
  [argv]
  (some #(when (accepted-source? %) %) (source-positionals argv)))

(defn source-argv-plan
  "Return the launcher source plan for run/check argv, if argv names a source."
  [argv]
  (when (source-commands (command-name argv))
    (when-let [source (first-source-arg argv)]
      (source-plan source (reader-target-option argv)))))

(defn normalize-source-argv
  "Reflect launcher source classification into argv sent to the CLJC authority."
  [argv]
  (let [argv (vec argv)
        plan (source-argv-plan argv)]
    (if (and plan
             (not (:kotoba.source/data? plan))
             (not (reader-target-provided? argv)))
      (conj argv "--reader-target" (name (:kotoba.source/reader-target plan)))
      argv)))

(defn authority-request
  "Formal request metadata for the delegated CLJC authority call."
  [original-argv normalized-argv plan]
  {:kotoba.launcher/authority "kotoba-lang/kotoba-lang"
   :kotoba.launcher/original-argv original-argv
   :kotoba.launcher/normalized-argv normalized-argv
   :kotoba.launcher/reader-target-added? (not= original-argv normalized-argv)
   :kotoba.launcher/source-plan plan})

(declare dispatch)

(defn shell-process-port
  "JVM process capability for host adapters (kotoba.git-adapter/IProcess)."
  []
  (reify git-adapter/IProcess
    (-run [_ argv]
      (apply shell/sh argv))))

(defn rad-host-port
  "JVM host capabilities for the rad adapter: filesystem writes plus
  in-process launcher re-dispatch (kotoba.rad-adapter/IRadHost)."
  []
  (reify rad-adapter/IRadHost
    (-mkdirs [_ path]
      (.mkdirs (io/file path)))
    (-write-file [_ path content]
      (let [f (io/file path)]
        (some-> (.getParentFile f) .mkdirs)
        (spit f content)))
    (-dispatch [_ argv]
      (dispatch argv))))

(defn adapter-result
  "Execute host-adapter-backed commands (:git, :rad) from their CLJC-planned
  result. Non-adapter commands pass through unchanged."
  [command result]
  (if (= :command/planned (:kotoba.cli/code result))
    (case command
      "git" (git-adapter/execute! (shell-process-port) result)
      "rad" (rad-adapter/execute! (rad-host-port) result)
      result)
    result))

(defn read-cli-contract-resource
  "Load the Datomic tx-data encoded CLI contract from the classpath and
  reconstruct its namespaced entity map. Collection-valued attributes were
  serialized with pr-str by the contract repository's datomizer."
  [path]
  (let [tx-data (-> path io/resource slurp edn/read-string)]
    (into {}
          (map (fn [[k v]]
                 [k (if (string? v)
                      (try
                        (let [decoded (edn/read-string v)]
                          (if (coll? decoded) decoded v))
                        (catch Exception _ v))
                      v)]))
          (dissoc (first tx-data) :db/id))))

(defn dispatch
  "Dispatch argv through the CLJC authority and return a result map."
  [argv]
  (let [argv (vec argv)]
    (if-let [launcher-result (case (command-name argv)
                               "selfhost" (selfhost-result argv)
                               "check" (when (option-value argv "--project")
                                         (project-check-result argv))
                               "compile" (compile-result argv)
                               "wasm" (wasm-result argv)
                               "cljs" (cljs-result argv)
                               "package" (package-result argv)
                               nil)]
      launcher-result
      (let [contract (read-cli-contract-resource "lang/cli.edn")
            normalized-argv (normalize-source-argv argv)
            result (cli/dispatch contract normalized-argv)
            plan (source-argv-plan normalized-argv)]
        (if-let [executed (and plan
                               (runtime-result (command-name normalized-argv)
                                               result
                                               argv
                                               normalized-argv
                                               plan))]
          executed
          (if plan
          (update result :kotoba.cli/data
                  (fnil assoc {})
                  :kotoba.launcher/source-plan plan
                  :kotoba.launcher/authority-request
                  (authority-request argv normalized-argv plan))
          (adapter-result (command-name argv) result)))))))

(defn- write-bytes! [path bytes]
  (some-> (io/file path) .getParentFile .mkdirs)
  (with-open [out (io/output-stream path)]
    (.write out ^bytes bytes)))

(defn- reject-project-file! [message data]
  (throw (ex-info message (assoc data :phase :project-manifest))))

(defn- symlink-component? [^java.io.File base ^java.io.File candidate]
  (loop [current candidate]
    (cond
      (nil? current) true
      (= base current) false
      (not= (.getAbsoluteFile current) (.getCanonicalFile current)) true
      :else (recur (.getParentFile current)))))

(defn- symbolic-link-file? [^java.io.File file]
  (let [absolute (.getAbsoluteFile file)
        normalized (io/file (.getCanonicalFile (.getParentFile absolute))
                            (.getName absolute))]
    (not= (.getAbsoluteFile normalized) (.getCanonicalFile absolute))))

(defn- file-snapshot [^java.io.File file]
  [(.getPath (.getCanonicalFile file)) (.length file) (.lastModified file)])

(defn- read-bounded-bytes [^java.io.File file max-bytes data]
  (with-open [input (FileInputStream. file)
              output (ByteArrayOutputStream.)]
    (let [buffer (byte-array 8192)]
      (loop [total 0]
        (let [read (.read input buffer)]
          (if (neg? read)
            (.toByteArray output)
            (let [next-total (+ total read)]
              (when (> next-total max-bytes)
                (reject-project-file! "project file exceeds byte limit"
                                      (assoc data :bytes next-total)))
              (.write output buffer 0 read)
              (recur next-total))))))))

(defn- strict-utf8 [^bytes bytes data]
  (try
    (str (.decode (doto (.newDecoder StandardCharsets/UTF_8)
                    (.onMalformedInput CodingErrorAction/REPORT)
                    (.onUnmappableCharacter CodingErrorAction/REPORT))
                  (ByteBuffer/wrap bytes)))
    (catch java.nio.charset.CharacterCodingException _
      (reject-project-file! "project file is not strict UTF-8" data))))

(defn- read-stable-file [^java.io.File base-file relative max-bytes data]
  (let [^Path base-path (-> base-file .toPath .toAbsolutePath .normalize)
        lexical-file (io/file base-file relative)
        ^Path lexical-path (-> lexical-file .toPath .toAbsolutePath .normalize)]
    (when-not (.startsWith lexical-path base-path)
      (reject-project-file! "project module path escapes its manifest root" data))
    (when (symlink-component? base-file (.getAbsoluteFile lexical-file))
      (reject-project-file! "project file path contains a symbolic link" data))
    (let [canonical-before (.getCanonicalFile lexical-file)
          ^Path path (.toPath canonical-before)]
      (when-not (and (.startsWith path (.toPath base-file))
                     (.isFile canonical-before))
        (reject-project-file! "project module escapes its manifest root or is not a regular file" data))
      (let [before (file-snapshot canonical-before)]
        (when (> (.length canonical-before) max-bytes)
          (reject-project-file! "project file exceeds byte limit"
                                (assoc data :bytes (.length canonical-before))))
        (let [^bytes bytes (read-bounded-bytes canonical-before max-bytes data)
              canonical-after (.getCanonicalFile lexical-file)
              after (file-snapshot canonical-after)]
          (when (or (not= canonical-before canonical-after)
                    (not= before after)
                    (symlink-component? base-file (.getAbsoluteFile lexical-file)))
            (reject-project-file! "project file changed while being read" data))
          (strict-utf8 bytes data))))))

(defn- project-input [manifest-path]
  (let [manifest-file (io/file manifest-path)
        manifest-absolute (.getAbsoluteFile manifest-file)
        _ (when (symbolic-link-file? manifest-absolute)
            (reject-project-file! "project manifest must not be a symbolic link"
                                  {:input :manifest}))
        manifest-canonical (.getCanonicalFile manifest-file)
        manifest-base (.getParentFile manifest-canonical)
        manifest-text (read-stable-file manifest-base (.getName manifest-canonical)
                                        (* 1024 1024) {:input :manifest})
        manifest (edn/read-string
                  {:readers {}
                   :default (fn [tag _]
                              (reject-project-file! "tagged project manifest value rejected"
                                                    {:tag tag}))}
                  manifest-text)
        root (:kotoba.project/root manifest)
        modules (:kotoba.project/modules manifest)
        lock-relative (:kotoba.project/package-lock manifest)
        trust-relative (:kotoba.project/trust manifest)
        dependency-manifest-relatives (:kotoba.project/dependency-manifests manifest)
        base-file manifest-base
        _ (when-not (and (= #{:kotoba.project/root :kotoba.project/modules
                              :kotoba.project/package-lock :kotoba.project/trust
                              :kotoba.project/dependency-manifests}
                            (set (keys manifest)))
                         (simple-symbol? root) (map? modules)
                         (pos? (count modules)) (<= (count modules) 256))
            (throw (ex-info "invalid closed Kotoba project manifest"
                            {:phase :project-manifest})))
        _ (when-not (and (string? lock-relative) (string? trust-relative)
                         (map? dependency-manifest-relatives)
                         (every? string? (keys dependency-manifest-relatives))
                         (every? string? (vals dependency-manifest-relatives)))
            (throw (ex-info "project package lock, trust, and dependency manifests are required"
                            {:phase :project-manifest
                             :reason :missing-supply-chain-input})))
        read-edn (fn [relative input]
                   (let [text (read-stable-file base-file relative (* 1024 1024)
                                                {:input input})]
                     {:text text
                      :value (edn/read-string
                              {:readers {}
                               :default (fn [tag _]
                                          (reject-project-file!
                                           "tagged project supply-chain value rejected"
                                           {:input input :tag tag}))}
                              text)}))
        lock-input (read-edn lock-relative :package-lock)
        trust-input (read-edn trust-relative :trust-policy)
        dependency-inputs
        (into {}
              (map (fn [[name relative]]
                     [name (read-edn relative :dependency-manifest)]))
              dependency-manifest-relatives)
        package-receipt
        (package-admission/verify-project-lock
         {:lock (:value lock-input)
          :lock-path lock-relative
          :trust (:value trust-input)
          :dependency-manifests
          (into {} (map (fn [[name input]] [name (:value input)])) dependency-inputs)
          :dependency-manifest-paths dependency-manifest-relatives})
        _ (when-not (:kotoba.package/verified? package-receipt)
            (throw (ex-info "closed project package admission rejected"
                            {:phase :package-admission
                             :reason :package-rejected
                             :problems (:kotoba.package/problems package-receipt)})))
        supply-chain
        {:package-lock-digest (package-admission/sha256-text (:text lock-input))
         :trust-policy-digest (package-admission/sha256-text (:text trust-input))
         :package-receipt-digest (package-admission/receipt-digest package-receipt)}
        sources
        (into {}
              (map (fn [[namespace relative]]
                     (when-not (and (simple-symbol? namespace) (string? relative)
                                    (str/ends-with? relative ".kotoba")
                                    (not (.isAbsolute (io/file relative))))
                       (throw (ex-info "project modules require relative .kotoba paths"
                                       {:phase :project-manifest :module namespace})))
                     [namespace (read-stable-file base-file relative (* 1024 1024)
                                                  {:input :module :module namespace})]))
              modules)]
    {:root root :sources sources :manifest (.getPath manifest-file)
     :supply-chain supply-chain :package-receipt package-receipt}))

(defn compile-result
  "Compile Kotoba-owned source through kotoba-lang/compiler. Web output is
  restricted ESM emitted from checked KIR by kotoba-script; it never routes
  through the legacy ClojureScript backend."
  [argv]
  (let [project-path (option-value argv "--project")
        source-root (option-value argv "--source-path")
        entry (first-source-arg argv)
        extension (some-> entry source-extension)
        target-name (or (option-value argv "--target") "wasm")
        target (case target-name "web" :js-kotoba-v1 "wasm" :wasm32-kotoba-v1 nil)
        output (or (option-value argv "--output")
                   (option-value argv "-o")
                   (when (or entry project-path)
                     (let [input (or entry project-path)
                           suffix (if project-path ".edn" extension)]
                       (str (subs input 0 (- (count input) (count suffix)))
                            (if (= target-name "web") ".mjs" ".wasm")))))]
    (cond
      (and entry project-path)
      {:kotoba.cli/ok? false :kotoba.cli/code :compile/ambiguous-input}

      (and project-path source-root)
      {:kotoba.cli/ok? false :kotoba.cli/code :compile/ambiguous-project-source}

      (and (nil? entry) (nil? project-path))
      {:kotoba.cli/ok? false :kotoba.cli/code :compile/entry-required}

      (and project-path (not (str/ends-with? project-path ".edn")))
      {:kotoba.cli/ok? false :kotoba.cli/code :compile/invalid-project-manifest
       :kotoba.cli/data {:project project-path}}

      (and entry (not (#{".kotoba" ".cljk" ".cljc"} extension)))
      {:kotoba.cli/ok? false :kotoba.cli/code :compile/not-kotoba-source
       :kotoba.cli/data {:entry entry :extension extension}}

      (nil? target)
      {:kotoba.cli/ok? false :kotoba.cli/code :compile/unsupported-target
       :kotoba.cli/data {:target target-name :allowed ["web" "wasm"]}}

      :else
      (try
        (let [manifest-project (when project-path (project-input project-path))
              discovered-project (when source-root
                                   (project-files/load-closed-graph entry source-root))
              project (or manifest-project discovered-project)
              compiled (if project
                         (compiler/compile-project (:sources project) (:root project) target
                                                   {} (or (:supply-chain project) {}))
                         (compiler/compile-source (slurp entry) target))]
          (if (= target :js-kotoba-v1)
            (do
              (some-> (io/file output) .getParentFile .mkdirs)
              (spit output (:source compiled))
              (spit (str output ".manifest.edn") (pr-str (:manifest compiled))))
            (write-bytes! output (:bytes compiled)))
          {:kotoba.cli/ok? true :kotoba.cli/code :compile/emitted
           :kotoba.cli/data {:entry (or entry (:root project))
                             :project project-path :source-path source-root
                             :output output :target target-name
                             :backend (if (= target :js-kotoba-v1)
                                        :kotoba-script :kotoba-wasm)
                             :value-profile (:value-profile compiled)
                             :value-abi (:value-abi compiled)
                             :wasm-features (:wasm-features compiled)
                             :project-digest (:project-digest compiled)
                             :compatibility (:compatibility compiled)
                             :manifest (:manifest compiled)
                             :package-receipt (:package-receipt project)}})
        (catch Exception error
          {:kotoba.cli/ok? false :kotoba.cli/code :compile/failed
           :kotoba.cli/message (ex-message error)
           :kotoba.cli/data (assoc
                             (select-keys (ex-data error)
                                          [:phase :reason :target :module :dependency :problems])
                             :exception-chain (exception-chain error))})))))

(defn project-check-result
  "Check a closed Kotoba project through the exact compiler path used by
  `compile --project`, but do not write an artifact."
  [argv]
  (let [project-path (option-value argv "--project")
        entry (first-source-arg argv)
        target-name (or (option-value argv "--target") "web")
        target (case target-name "web" :js-kotoba-v1 "wasm" :wasm32-kotoba-v1 nil)]
    (cond
      entry
      {:kotoba.cli/ok? false :kotoba.cli/code :check/ambiguous-input}

      (not (str/ends-with? project-path ".edn"))
      {:kotoba.cli/ok? false :kotoba.cli/code :check/invalid-project-manifest
       :kotoba.cli/data {:project project-path}}

      (nil? target)
      {:kotoba.cli/ok? false :kotoba.cli/code :check/unsupported-target
       :kotoba.cli/data {:target target-name :allowed ["web" "wasm"]}}

      :else
      (try
        (let [project (project-input project-path)
              compiled (compiler/compile-project (:sources project) (:root project) target
                                                  {} (:supply-chain project))]
          {:kotoba.cli/ok? true :kotoba.cli/code :check/project-valid
           :kotoba.cli/data {:entry (:root project) :project project-path
                             :target target-name
                             :backend (if (= target :js-kotoba-v1)
                                        :kotoba-script :kotoba-wasm)
                             :project-digest (:project-digest compiled)
                             :module-order (get-in compiled [:project :kotoba.module/order])
                             :module-source-digests
                             (get-in compiled [:project :kotoba.module/source-digests])
                             :manifest (:manifest compiled)
                             :package-receipt (:package-receipt project)}})
        (catch Exception error
          {:kotoba.cli/ok? false :kotoba.cli/code :check/project-invalid
           :kotoba.cli/message (ex-message error)
           :kotoba.cli/data (assoc
                             (select-keys (ex-data error)
                                          [:phase :reason :target :module :dependency :problems])
                             :exception-chain (exception-chain error))})))))

(defn resource-edn
  "Load an EDN resource by classpath path."
  [path]
  (let [resource (io/resource path)]
    (when-not resource
      (throw (ex-info "missing Kotoba resource" {:path path})))
    (-> resource slurp edn/read-string)))

(defn source-contract
  "Load the Kotoba source-kind contract. `.cljs` is a compatibility source
  extension (profile v3, kotoba-lang/kotoba-lang): a single-target format
  like `.clj`, not the fully portable `.cljc`. The `:cljs` reader target is
  also reachable via `.cljc`'s wider `:reader-targets`."
  []
  (core-contracts/source-contract))

(defn source-extension
  "Return the lowercase extension for a path-like string, including the dot."
  [path]
  (core-contracts/source-extension path))

(defn source-kind
  "Classify a source path under the source contract."
  ([path] (source-kind (source-contract) path))
  ([contract path]
   (core-contracts/source-kind contract path)))

(defn accepted-source?
  "True when a path has an accepted Kotoba source/data extension."
  [path]
  (core-contracts/accepted-source? (source-contract) path))

(defn source-plan
  "Return launcher-owned source dispatch data before delegating to CLJC authority."
  ([path] (source-plan path nil))
  ([path reader-target]
   (core-contracts/source-plan (source-contract) path reader-target)))

(def selfhost-seed-names
  selfhost/seed-names)

(defn selfhost-seed
  "Load a Kotoba selfhost EDN seed from launcher resources."
  [name]
  (selfhost/load-seed name))

(defn selfhost-seeds
  "Load every canonical Kotoba selfhost EDN seed bundled with the launcher."
  []
  (selfhost/load-seeds))

(defn seed-summary
  "Return stable public metadata for a selfhost seed."
  [[name seed]]
  (selfhost/seed-summary name seed))

(defn selfhost-list-result
  "List bundled Kotoba selfhost seeds."
  []
  (let [seeds (selfhost-seeds)]
    {:kotoba.cli/ok? true
     :kotoba.cli/code :selfhost/listed
     :kotoba.cli/data (selfhost/list-data seeds)}))

(defn selfhost-seed-problems
  "Return validation problems for a single selfhost seed."
  [[name seed]]
  (selfhost/seed-problems name seed))

(defn selfhost-check-result
  "Validate bundled Kotoba selfhost seeds without invoking any Rust crate."
  []
  (let [seeds (selfhost-seeds)
        data (selfhost/check-data seeds)
        ok? (empty? (:kotoba.selfhost/problems data))]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :selfhost/valid :selfhost/invalid)
     :kotoba.cli/data data}))

(defn selfhost-result
  "Handle launcher-owned selfhost commands."
  [argv]
  (case (second argv)
    "list" (selfhost-list-result)
    "check" (selfhost-check-result)
    {:kotoba.cli/ok? false
     :kotoba.cli/code :selfhost/unknown-command
     :kotoba.cli/data {:kotoba.selfhost/command (second argv)
                       :kotoba.selfhost/commands ["list" "check"]}}))

(defn admission-options
  "Package-admission option paths carried by argv. `lock-option` names the
  option holding the lock path (`--lock` for `package verify`,
  `--package-lock` for safe builds)."
  [argv lock-option]
  {:lock-path (option-value argv lock-option)
   :manifest-path (option-value argv "--manifest")
   :trust-path (option-value argv "--trust")
   :key-register-path (option-value argv "--key-register")
   :receipt-path (option-value argv "--receipt")})

(defn package-verify-result
  "Verify a package lock through the kotoba.lang.package-contract admission
  gate and emit the package-verification receipt."
  [argv]
  (package-admission/cli-result (admission-options argv "--lock")))

(defn package-result
  "Handle launcher-owned package admission commands."
  [argv]
  (case (second argv)
    "verify" (package-verify-result argv)
    {:kotoba.cli/ok? false
     :kotoba.cli/code :package/unknown-command
     :kotoba.cli/data {:kotoba.package/command (second argv)
                       :kotoba.package/commands ["verify"]
                       :kotoba.package/usage package-admission/usage}}))

(defn policy-result
  "Load and parse the `--policy <path>` EDN file, if the option is present.
  Ok with nil data when the option is absent; ok? false with the exception
  message when the file can't be read/parsed.

  Loaded policies are passed through `host-providers/normalize-policy` so
  safe defaults (network URL allowlist required) apply unless the file
  explicitly opts out."
  [argv]
  (if-let [path (option-value argv "--policy")]
    (try
      {:kotoba.policy/ok? true
       :kotoba.policy/path path
       :kotoba.policy/data (host-providers/normalize-policy
                            (-> path io/file slurp edn/read-string))}
      (catch Exception e
        {:kotoba.policy/ok? false
         :kotoba.policy/path path
         :kotoba.policy/error (.getMessage e)}))
    {:kotoba.policy/ok? true
     :kotoba.policy/data nil}))

(defn cacao-chain-data
  "Chain vector from `--cacao` EDN: {:cacao/chain [\"b64\" ...]} or a plain
  EDN vector of base64 strings."
  [data]
  (cond
    (map? data) (:cacao/chain data)
    (vector? data) data
    :else nil))

(defn cacao-result
  "Load the `--cacao <file>` delegation chain, when the option is present."
  [argv]
  (if-let [path (option-value argv "--cacao")]
    (try
      {:kotoba.cacao/ok? true
       :kotoba.cacao/path path
       :kotoba.cacao/chain (cacao-chain-data
                            (-> path io/file slurp edn/read-string))}
      (catch Exception e
        {:kotoba.cacao/ok? false
         :kotoba.cacao/path path
         :kotoba.cacao/error (.getMessage e)}))
    {:kotoba.cacao/ok? true
     :kotoba.cacao/chain nil}))

(defn verified-cacao-chain
  "Real crypto boundary: verify the delegation chain (cacao.core/verify-chain,
  signatures + linkage + attenuation + expiry ordering + freshness at the
  current instant) and map the VERIFIED result to capability grants
  (kotoba.lang.capability-cacao/grants-from-chain — crypto-free).
  Returns {:chain <verify-chain result> :grants [..] :skipped [..]
           :problems <nil-or-problems>}."
  [chain]
  (let [verified (cacao-core/verify-chain chain
                                          {:now (str (java.time.Instant/now))})
        mapped (capability-cacao/grants-from-chain verified)]
    {:chain verified
     :grants (:grants mapped)
     :skipped (:skipped mapped)
     :problems (cond
                 ;; grants-from-chain fails closed on an unverified chain and
                 ;; already echoes the chain problems after :chain/not-verified
                 (seq (:problems mapped)) (vec (:problems mapped))
                 (not (:chain/valid? verified)) (vec (:chain/problems verified))
                 :else nil)}))

(defn contract-exports
  "Return common plus target-specific exports from a selfhost contract seed."
  ([seed] (contract-exports seed nil))
  ([seed target]
   (merge (:common-exports seed)
          (when target
            (get-in seed [:target-exports target])))))

(defn safe-analyzer-fact-classification
  "Return the Rust-free source fact classification seed."
  []
  (selfhost-seed "safe_analyzer_facts"))

(defn safe-analyzer-fact-classified?
  "True when `value` is listed under `classification` in safe_analyzer_facts.edn."
  [classification value]
  (boolean
   (some #{value}
         (get (safe-analyzer-fact-classification) classification))))

(defn source-file-readable?
  "True when `plan`'s :kotoba.source/path names an existing, readable file."
  [plan]
  (let [file (io/file (:kotoba.source/path plan))]
    (and (.isFile file)
         (.canRead file))))

(defn runtime-data
  "Assemble the launcher's `:kotoba.cli/data` payload for a `run`/`check`
  result: the source plan, the delegated-authority request metadata, and the
  runtime result itself."
  [original-argv normalized-argv plan runtime-result]
  {:kotoba.launcher/source-plan plan
   :kotoba.launcher/authority-request (authority-request original-argv normalized-argv plan)
   :kotoba.runtime/result runtime-result})

(defn guarded-run-result
  "Capability-guarded `run` (issue #263): the static check admits the policy's
  capabilities, and every host provider invocation is dispatched through
  kotoba.lang.capability-host/guard-call with grants/local policy derived from
  the policy EDN. The ordered receipt journal is attached to the result as
  :kotoba.host/receipts. HANDLERS optionally overrides the provider handler
  registry (kotoba.host-providers/default-handlers).

  The run also installs the S4b capability-passing surface: a per-run
  capability table (kotoba.cap-table) behind `cap-acquire` and the `<op>-with`
  use variants, sharing the same receipt journal and provider handlers.

  RUN-OPTS optionally carries :handlers (overriding
  kotoba.host-providers/default-handlers) and :cacao-grants (verified CACAO
  delegation-chain grants replacing the policy-derived grants; the local
  policy side still comes from POLICY, which therefore narrows the chain)."
  ([safe-facts plan forms policy] (guarded-run-result safe-facts plan forms policy nil))
  ([safe-facts plan forms policy {:keys [handlers cacao-grants]}]
   (let [{:keys [record! entries]} (host-providers/journal)
         now (str (java.time.LocalDate/now))
         opts (cond-> {:record! record! :now now}
                handlers (assoc :handlers handlers)
                cacao-grants (assoc :cacao-grants cacao-grants))
         host-call (host-providers/host-call policy opts)
         cap-table (cap-table/make-table)
         cap-fns (host-providers/capability-passing-fns cap-table policy opts)
         ran (runtime/run safe-facts plan forms
                          {:policy policy
                           :host-call host-call
                           :capability-query (host-providers/capability-query-fn policy)
                           :host-fns cap-fns})]
     (assoc ran :kotoba.host/receipts (entries)))))

(defn runtime-result
  "Run/check an existing source file through the CLJ-owned executable slice.

  When `--policy <path>` accompanies `run`/`check`, the policy EDN drives the
  static capability check, and `run` additionally installs the capability
  guard (see `guarded-run-result`). Without `--policy` the legacy ambient
  behavior is unchanged (host-import ops are rejected as
  :capability-not-granted and no receipts are emitted).

  When `--cacao <file>` accompanies `run`, the file's delegation chain is
  verified (cacao.core/verify-chain) and its grants replace the
  policy-derived grants for the guarded run; an invalid/expired chain aborts
  with :run/cacao-invalid before any execution. Without `--policy`, a policy
  admitting the chain's capability kinds is synthesized
  (kotoba.host-providers/grants->policy) so the local policy defaults to
  allowing whatever the chain grants — an explicit `--policy` narrows it."
  [command authority-result original-argv normalized-argv plan]
  (when (and (source-commands command)
             (source-file-readable? plan)
             (not (:kotoba.source/data? plan)))
    (let [policy-result (policy-result original-argv)
          policy (:kotoba.policy/data policy-result)]
      (if-not (:kotoba.policy/ok? policy-result)
        {:kotoba.cli/ok? false
         :kotoba.cli/code (if (= "run" command)
                            :run/policy-not-readable
                            :check/policy-not-readable)
         :kotoba.cli/data policy-result}
        (let [forms (runtime/read-file (:kotoba.source/path plan)
                                       (:kotoba.source/reader-target plan))
              safe-facts (safe-analyzer-fact-classification)]
          (case command
            "check"
            (let [checked (runtime/check safe-facts plan forms policy)
                  semantic? (= "semantic-code" (option-value original-argv "--kind"))
                  semantic-result
                  (when (and semantic? (:kotoba.runtime/ok? checked))
                    (try
                      (let [source-text (slurp (io/file (:kotoba.source/path plan)))
                            profile-text (slurp (io/resource "lang/profile.edn"))
                            codebase
                            (semantic-code/compile-definitions
                             forms {:source-cid (semantic-code/source-cid source-text)
                                    :profile-cid (semantic-code/source-cid profile-text)})]
                        {:ok? true
                         :codebase codebase
                         :summary
                         {:kotoba.semantic/schema (:schema codebase)
                          :kotoba.semantic/source-cid (:source-cid codebase)
                          :kotoba.semantic/profile-cid (:profile-cid codebase)
                          :kotoba.semantic/hash-contract-cid
                          (:hash-contract-cid codebase)
                          :kotoba.semantic/definitions
                          (into (sorted-map)
                                (map (fn [[name {:keys [cid]}]] [(str name) cid]))
                                (:definitions codebase))}})
                      (catch clojure.lang.ExceptionInfo e
                        {:ok? false
                         :problem (ex-data e)
                         :message (.getMessage e)})))
                  checked (if (:ok? semantic-result)
                            (update checked :kotoba.runtime/ir
                                    semantic-code/attach-to-ir (:codebase semantic-result))
                            checked)
                  ok? (and (:kotoba.runtime/ok? checked)
                           (not (false? (:ok? semantic-result))))]
              {:kotoba.cli/ok? ok?
               :kotoba.cli/code (cond
                                  (and semantic? (not ok?)) :check/semantic-invalid
                                  ok? :check/valid
                                  :else :check/invalid)
               :kotoba.cli/data
               (merge (:kotoba.cli/data authority-result)
                      (runtime-data original-argv normalized-argv plan checked)
                      (when semantic?
                        (if (:ok? semantic-result)
                          (:summary semantic-result)
                          {:kotoba.semantic/problem (:problem semantic-result)
                           :kotoba.semantic/message (:message semantic-result)})))})

            "run"
            (let [cacao-load (cacao-result original-argv)]
              (cond
                (not (:kotoba.cacao/ok? cacao-load))
                {:kotoba.cli/ok? false
                 :kotoba.cli/code :run/cacao-not-readable
                 :kotoba.cli/data cacao-load}

                :else
                (let [cacao (when (:kotoba.cacao/path cacao-load)
                              (verified-cacao-chain
                               (:kotoba.cacao/chain cacao-load)))]
                  (if (:problems cacao)
                    ;; invalid/expired chain: the run does NOT proceed
                    {:kotoba.cli/ok? false
                     :kotoba.cli/code :run/cacao-invalid
                     :kotoba.cli/data {:kotoba.cacao/path (:kotoba.cacao/path cacao-load)
                                       :kotoba.cacao/problems (:problems cacao)}}
                    (let [effective-policy
                          (or policy
                              (when cacao
                                (host-providers/grants->policy (:grants cacao))))
                          ran (if effective-policy
                                (guarded-run-result safe-facts plan forms effective-policy
                                                    (when cacao
                                                      {:cacao-grants (:grants cacao)}))
                                (runtime/run safe-facts plan forms))
                          ok? (:kotoba.runtime/ok? ran)]
                      {:kotoba.cli/ok? ok?
                       :kotoba.cli/code (if ok? :run/completed :run/failed)
                       :kotoba.cli/data (merge (:kotoba.cli/data authority-result)
                                               (runtime-data original-argv normalized-argv plan
                                                             (dissoc ran :kotoba.host/receipts))
                                               (when effective-policy
                                                 {:kotoba.host/receipts (:kotoba.host/receipts ran)})
                                               (when policy
                                                 {:kotoba.policy/path (:kotoba.policy/path policy-result)})
                                               (when cacao
                                                 (let [chain (:chain cacao)]
                                                   {:kotoba.cacao/path (:kotoba.cacao/path cacao-load)
                                                    :kotoba.cacao/root-iss (:chain/root-iss chain)
                                                    :kotoba.cacao/holder (:chain/holder chain)
                                                    :kotoba.cacao/depth (:chain/depth chain)})))})))))

            nil))))))

(defn wasm-emit-result*
  "The unguarded `wasm emit` implementation (no package-admission gate — see
  `wasm-emit-result` for the safe-build entry point that wraps this). Resolves
  the source plan, checks it against policy, and either emits a WebAssembly
  binary module (writing it to `--output`/`-o` when given) or falls back to
  the EDN IR artifact byte-count when compilation isn't supported for the
  source."
  [argv]
  (let [normalized-argv (normalize-source-argv (vec (cons "run" (rest argv))))
        plan (source-argv-plan normalized-argv)
        policy-result (policy-result argv)
        policy (:kotoba.policy/data policy-result)
        output (or (option-value argv "--output")
                   (option-value argv "-o"))]
    (cond
      (not (:kotoba.policy/ok? policy-result))
      {:kotoba.cli/ok? false
       :kotoba.cli/code :wasm/policy-not-readable
       :kotoba.cli/data policy-result}

      (nil? plan)
      {:kotoba.cli/ok? false
       :kotoba.cli/code :wasm/missing-source
       :kotoba.cli/data {:kotoba.wasm/usage "kotoba wasm emit <source> [--reader-target target]"}}

      (not (source-file-readable? plan))
      {:kotoba.cli/ok? false
       :kotoba.cli/code :wasm/source-not-readable
       :kotoba.cli/data {:kotoba.source/path (:kotoba.source/path plan)}}

      :else
      (let [forms (runtime/read-file (:kotoba.source/path plan)
                                     (:kotoba.source/reader-target plan))
            checked (runtime/check (safe-analyzer-fact-classification) plan forms policy)
            ir (:kotoba.runtime/ir checked)
            edn-bytes (when ir (runtime/wasm-artifact ir))
            wasm (when (:kotoba.runtime/ok? checked)
                   (runtime/wasm-binary forms policy))]
        (cond
          (not (:kotoba.runtime/ok? checked))
          {:kotoba.cli/ok? false
           :kotoba.cli/code :wasm/check-failed
           :kotoba.cli/data {:kotoba.launcher/source-plan plan
                             :kotoba.runtime/result checked
                             :kotoba.wasm/artifact-kind :kotoba.runtime/edn-ir
                             :kotoba.wasm/binary? false
                             :kotoba.wasm/byte-count (when edn-bytes (alength edn-bytes))}}

          (:kotoba.wasm/ok? wasm)
          (do
            (when output
              (let [file (io/file output)]
                (io/make-parents file)
                (with-open [out (io/output-stream file)]
                  (.write out ^bytes (:kotoba.wasm/binary wasm)))))
          {:kotoba.cli/ok? true
           :kotoba.cli/code :wasm/binary-emitted
           :kotoba.cli/data {:kotoba.launcher/source-plan plan
                             :kotoba.runtime/result checked
                             :kotoba.wasm/artifact-kind :webassembly/module
                             :kotoba.wasm/binary? true
                             :kotoba.wasm/byte-count (:kotoba.wasm/byte-count wasm)
                             :kotoba.wasm/export (:kotoba.wasm/export wasm)
                             :kotoba.wasm/result-type (:kotoba.wasm/result-type wasm)
                             :kotoba.wasm/function-count (:kotoba.wasm/function-count wasm)
                             :kotoba.wasm/local-count (:kotoba.wasm/local-count wasm)
                             :kotoba.wasm/import-count (:kotoba.wasm/import-count wasm)
                             :kotoba.wasm/imports (:kotoba.wasm/imports wasm)
                             :kotoba.wasm/memory? (:kotoba.wasm/memory? wasm)
                             :kotoba.wasm/memory-min-pages (:kotoba.wasm/memory-min-pages wasm)
                             :kotoba.wasm/heap-base (:kotoba.wasm/heap-base wasm)
                             :kotoba.wasm/data-segment-count (:kotoba.wasm/data-segment-count wasm)
                             :kotoba.wasm/output output
                             :kotoba.wasm/magic [0 97 115 109]}}
            )

          :else
          {:kotoba.cli/ok? false
           :kotoba.cli/code :wasm/binary-unsupported
           :kotoba.cli/data {:kotoba.launcher/source-plan plan
                             :kotoba.runtime/result checked
                             :kotoba.wasm/problems (:kotoba.wasm/problems wasm)
                             :kotoba.wasm/artifact-kind :kotoba.runtime/edn-ir
                             :kotoba.wasm/binary? false
                             :kotoba.wasm/byte-count (when edn-bytes (alength edn-bytes))}})))))

(defn cljs-emit-result*
  "The unguarded `cljs emit` implementation (no package-admission gate -- see
  `cljs-emit-result` for the safe-build entry point that wraps this). Resolves
  the source plan, runs it through the same static safe-kotoba-subset check
  `wasm emit` uses, and -- only if that passes -- compiles the source to
  plain ClojureScript text via `kotoba.runtime/cljs-source` (writing it to
  `--output`/`-o` when given, else returning it inline).

  A passing `runtime/check` does NOT guarantee `cljs-source` itself succeeds:
  the general safe-kotoba-subset analyzer has no notion of this particular
  backend's narrower op support (ADR-2607151500 addendum 6 -- i64/f32/
  bitwise/string/memory/capability ops are valid safe-kotoba but rejected by
  this backend specifically). `cljs-source` throwing on such a program (via
  its own `cljs-reject!`) is caught here and turned into a clean
  `:cljs/emit-unsupported` result instead of an uncaught stack trace,
  mirroring `wasm-run-result*`'s handling of the distinct
  `:kotoba.host/denied` capability-denial ex-data shape for a different kind
  of expected, structured failure."
  [argv]
  (let [normalized-argv (normalize-source-argv (vec (cons "run" (rest argv))))
        plan (source-argv-plan normalized-argv)
        policy-result (policy-result argv)
        policy (:kotoba.policy/data policy-result)
        output (or (option-value argv "--output")
                   (option-value argv "-o"))]
    (cond
      (not (:kotoba.policy/ok? policy-result))
      {:kotoba.cli/ok? false
       :kotoba.cli/code :cljs/policy-not-readable
       :kotoba.cli/data policy-result}

      (nil? plan)
      {:kotoba.cli/ok? false
       :kotoba.cli/code :cljs/missing-source
       :kotoba.cli/data {:kotoba.cljs/usage "kotoba cljs emit <source> [--reader-target target] [--output path]"}}

      (not (source-file-readable? plan))
      {:kotoba.cli/ok? false
       :kotoba.cli/code :cljs/source-not-readable
       :kotoba.cli/data {:kotoba.source/path (:kotoba.source/path plan)}}

      :else
      (let [forms (runtime/read-file (:kotoba.source/path plan)
                                     (:kotoba.source/reader-target plan))
            checked (runtime/check (safe-analyzer-fact-classification) plan forms policy)]
        (cond
          (not (:kotoba.runtime/ok? checked))
          {:kotoba.cli/ok? false
           :kotoba.cli/code :cljs/check-failed
           :kotoba.cli/data {:kotoba.launcher/source-plan plan
                             :kotoba.runtime/result checked}}

          :else
          (try
            (let [src (runtime/cljs-source forms)]
              (when output
                (let [file (io/file output)]
                  (io/make-parents file)
                  (spit file src)))
              {:kotoba.cli/ok? true
               :kotoba.cli/code :cljs/source-emitted
               :kotoba.cli/data {:kotoba.launcher/source-plan plan
                                 :kotoba.runtime/result checked
                                 :kotoba.cljs/artifact-kind :clojurescript/source
                                 :kotoba.cljs/source (when-not output src)
                                 :kotoba.cljs/byte-length (count src)
                                 :kotoba.cljs/output output}})
            (catch clojure.lang.ExceptionInfo e
              {:kotoba.cli/ok? false
               :kotoba.cli/code :cljs/emit-unsupported
               :kotoba.cli/data {:kotoba.launcher/source-plan plan
                                 :kotoba.runtime/result checked
                                 :kotoba.cljs/problem (ex-data e)
                                 :kotoba.cljs/message (ex-message e)}})))))))

(defn- admission-gated
  "Run the package admission gate over ARGV via LOCK-OPTION and, only if the
  lock is admitted, call UNGUARDED-FN with ARGV, attaching the admission
  receipt to its result. A missing or rejected lock short-circuits with the
  receipt/error instead of calling UNGUARDED-FN at all — `--package-lock` is
  mandatory, not opt-in (F-001: a caller could previously skip package
  admission for both `wasm emit` and `wasm run` simply by omitting the
  flag). Shared by `wasm-emit-result`/`wasm-run-result`/`cljs-emit-result` so
  none of the safe-build entry points can drift apart on this gate.
  REJECT-CODE is caller-supplied (rather than a single hardcoded
  `:wasm/package-rejected`) so a non-wasm caller's rejection is reported
  under its own namespace instead of silently borrowing wasm's."
  [argv lock-option reject-code unguarded-fn]
  (let [admission (package-admission/admit (admission-options argv lock-option))]
    (if-not (:kotoba.admission/ok? admission)
      {:kotoba.cli/ok? false
       :kotoba.cli/code reject-code
       :kotoba.cli/data (cond-> {:kotoba.package/admission-code (:kotoba.admission/code admission)}
                          (:kotoba.admission/receipt admission)
                          (assoc :kotoba.package/receipt (:kotoba.admission/receipt admission))

                          (:kotoba.admission/error admission)
                          (assoc :kotoba.package/error (:kotoba.admission/error admission)))}
      (update (unguarded-fn argv)
              :kotoba.cli/data
              (fnil assoc {})
              :kotoba.package/receipt (:kotoba.admission/receipt admission)))))

(defn wasm-emit-result
  "Safe-build entry point for `wasm emit`. `--package-lock <path>` is
  mandatory: the package admission gate always runs first, and a missing or
  rejected lock aborts the build with the admission receipt/error in the
  payload — there is no way to opt out (F-001)."
  [argv]
  (admission-gated argv "--package-lock" :wasm/package-rejected wasm-emit-result*))

(defn cljs-emit-result
  "Safe-build entry point for `cljs emit`. Same `--package-lock`-mandatory
  admission gate as `wasm-emit-result` (F-001) -- a new entry point must not
  reopen the gap that fix closed by giving a caller an ungated path to
  compile a source file just because it targets a different backend."
  [argv]
  (admission-gated argv "--package-lock" :cljs/package-rejected cljs-emit-result*))

(def ^:private kgraph-ops
  #{'kgraph-assert! 'kgraph-retract! 'kgraph-get-objects 'kgraph-query})

(defn wasm-run-result*
  "`wasm run <source>`: check + emit (as `wasm emit` does), then actually
  EXECUTE the module via kotoba.wasm-exec (com.dylibso.chicory) — the piece
  `wasm emit` deliberately stops short of. kgraph-* host imports run for real
  against a fresh per-invocation `kotoba.kgraph` store; the notify/
  clipboard/http/keychain/fs/log/clock/random/topic-bus and actor-host
  (gen-keypair/sign/verify/sha256-hex/http-post/log-read) surface runs for
  real too, against a fresh per-invocation `kotoba.wasm-exec/
  default-host-state` (`kotoba.wasm-exec/real-host-functions`) — real
  in-memory clipboard/keychain/notification-log/append-log/topic queues, a
  sandboxed real filesystem root, real HTTP, real Ed25519/SHA-256. Only the
  device-access quartet (pci-config/dma-map/irq-subscribe/mmio-map) still
  gets a trivial 0-returning stub (kotoba.wasm-exec/stub-host-function) —
  permanently host/hypervisor-only, not a placeholder awaiting a real
  implementation — so a valid program never fails to link here for lack of
  a provider.

  Mirrors `guarded-run-result`/`runtime-result`'s interpreter-path receipt
  surface: a `kotoba.host-providers/journal` is built and threaded into
  `kotoba.wasm-exec/kgraph-host-functions` as :record!/:now, so every guarded
  kgraph-* call — granted or denied — leaves a receipt; the ordered journal
  is attached to the result as :kotoba.host/receipts, but ONLY when a policy
  was actually supplied (same `(when policy ...)` convention as
  `runtime-result`'s `(when effective-policy ...)`), since no policy means no
  meaningful guard was installed.

  A runtime capability denial (`kotoba.wasm-exec/guard-kgraph-call` throwing
  ex-info with :kotoba.host/denied) is caught here and converted into a clean
  `:wasm/run-denied` :kotoba.cli/ok? false result — mirroring
  `kotoba.runtime/run`'s interpreter-path handling of the exact same ex-data
  shape — instead of escaping as an uncaught exception. Any other
  ExceptionInfo (e.g. `kotoba.wasm-exec/fuel-listener`'s fuel-exhausted guard)
  is not a capability denial and is re-thrown unchanged.

  A required op that is NEITHER a kgraph-* op NOR covered by
  `kotoba.wasm-exec/real-op-ids` normally falls through to
  `kotoba.wasm-exec/stub-host-function` — a trivial always-0 stub, harmless
  for e.g. the permanently-stubbed device-access quartet. But for the S4b
  capability-passing surface (`cap-acquire` and every `<op>-with` variant,
  see `kotoba.runtime/cap-passing-ops`) that stub is actively dangerous: it
  silently discards the static affine-capability checker's guarantees and
  returns a fabricated handle/value instead of ever failing, so a program
  using `cap-acquire`/`host-i64-roundtrip-with` would appear to `wasm run`
  successfully while actually running under wrong (always-0) semantics. If
  any required op is a member of `kotoba.runtime/cap-passing-ops` and would
  otherwise be stubbed, this refuses the run entirely
  (`:wasm/cap-passing-unimplemented`) rather than linking the stub — loud
  failure instead of silent wrong behavior. This is a detect-and-refuse
  guard, not a real WASM implementation of capability-affine handles; the
  interpreter path (`kotoba.runtime/run` via `guarded-run-result`) already
  implements `cap-acquire`/`<op>-with` for real (`kotoba.host-providers/
  capability-passing-fns`) and is unaffected."
  [argv]
  (let [normalized-argv (normalize-source-argv (vec (cons "run" (rest argv))))
        plan (source-argv-plan normalized-argv)
        policy-result (policy-result argv)
        policy (:kotoba.policy/data policy-result)]
    (cond
      (not (:kotoba.policy/ok? policy-result))
      {:kotoba.cli/ok? false
       :kotoba.cli/code :wasm/policy-not-readable
       :kotoba.cli/data policy-result}

      (nil? plan)
      {:kotoba.cli/ok? false
       :kotoba.cli/code :wasm/missing-source
       :kotoba.cli/data {:kotoba.wasm/usage "kotoba wasm run <source> [--policy <path>]"}}

      (not (source-file-readable? plan))
      {:kotoba.cli/ok? false
       :kotoba.cli/code :wasm/source-not-readable
       :kotoba.cli/data {:kotoba.source/path (:kotoba.source/path plan)}}

      :else
      (let [forms (runtime/read-file (:kotoba.source/path plan)
                                     (:kotoba.source/reader-target plan))
            checked (runtime/check (safe-analyzer-fact-classification) plan forms policy)
            wasm (when (:kotoba.runtime/ok? checked) (runtime/wasm-binary forms policy))
            ops (when (:kotoba.wasm/ok? wasm) (runtime/required-host-imports forms))
            stubbed-ops (when ops
                         (->> ops (remove kgraph-ops) (remove wasm-exec/real-op-ids)))
            unimplemented-cap-ops (when stubbed-ops
                                    (filterv runtime/cap-passing-ops stubbed-ops))]
        (cond
          (not (:kotoba.runtime/ok? checked))
          {:kotoba.cli/ok? false
           :kotoba.cli/code :wasm/check-failed
           :kotoba.cli/data {:kotoba.launcher/source-plan plan
                             :kotoba.runtime/result checked}}

          (not (:kotoba.wasm/ok? wasm))
          {:kotoba.cli/ok? false
           :kotoba.cli/code :wasm/binary-unsupported
           :kotoba.cli/data {:kotoba.launcher/source-plan plan
                             :kotoba.runtime/result checked
                             :kotoba.wasm/problems (:kotoba.wasm/problems wasm)}}

          (seq unimplemented-cap-ops)
          {:kotoba.cli/ok? false
           :kotoba.cli/code :wasm/cap-passing-unimplemented
           :kotoba.cli/data {:kotoba.launcher/source-plan plan
                             :kotoba.runtime/result checked
                             :kotoba.wasm/ops (mapv str unimplemented-cap-ops)}}

          :else
          (let [stub-fns (->> stubbed-ops
                              (map runtime/host-imports)
                              (map wasm-exec/stub-host-function))
                {:keys [record! entries]} (host-providers/journal)
                now (str (java.time.LocalDate/now))]
            (try
              ;; POLICY (already computed above for the static `check`
              ;; gate) is threaded into `instantiate` too, so `has-capability?`
              ;; and the kgraph-*/real-provider effects are enforced at RUN
              ;; time under the same policy that governed emission — closing
              ;; the gap where the runtime executor previously granted every
              ;; capability unconditionally regardless of `--policy`
              ;; (ADR-2607050500). :record!/:now flow into every
              ;; guard-host-call dispatch so each attempted call (granted or
              ;; denied) leaves a receipt in ENTRIES, exactly as the
              ;; interpreter path's `guarded-run-result` does via
              ;; kotoba.host-providers/host-call. `real-host-functions` now
              ;; covers everything `stub-fns` used to fake except the
              ;; device-access quartet (pci-config/dma-map/irq-subscribe/
              ;; mmio-map, permanently host/hypervisor-only) — a genuine
              ;; clipboard/keychain/filesystem/HTTP/crypto/topic-bus/log,
              ;; not a 0-returning placeholder, backs every other declared
              ;; import a guest calls.
              (let [real-fns (when (some wasm-exec/real-op-ids ops)
                               (wasm-exec/real-host-functions
                                (wasm-exec/default-host-state) policy
                                {:record! record! :now now}))
                    instance (wasm-exec/instantiate (:kotoba.wasm/binary wasm)
                                                    (concat (wasm-exec/kgraph-host-functions
                                                             (atom []) policy
                                                             {:record! record! :now now})
                                                            real-fns
                                                            stub-fns)
                                                    policy)
                    value (wasm-exec/call-main instance (or (:kotoba.wasm/result-type wasm) :i64))]
                {:kotoba.cli/ok? true
                 :kotoba.cli/code :wasm/run-completed
                 :kotoba.cli/data (merge {:kotoba.launcher/source-plan plan
                                          :kotoba.wasm/value value
                                          :kotoba.wasm/result-type (:kotoba.wasm/result-type wasm)
                                          :kotoba.wasm/import-count (:kotoba.wasm/import-count wasm)
                                          :kotoba.wasm/imports (:kotoba.wasm/imports wasm)}
                                         (when policy
                                           {:kotoba.host/receipts (entries)}))})
              ;; A denied kgraph-* call throws all the way up through
              ;; Chicory's `call-main` uncaught (verified: Chicory does not
              ;; wrap host-function exceptions, so `guard-kgraph-call`'s
              ;; ex-info in kotoba.wasm-exec reaches here byte-for-byte).
              ;; Mirror kotoba.runtime/run's interpreter-path handling (the
              ;; `(catch clojure.lang.ExceptionInfo e (if (:kotoba.host/denied
              ;; (ex-data e)) ... (throw e)))` pattern): a capability denial
              ;; becomes a clean :kotoba.cli/ok? false result instead of an
              ;; uncaught stack trace; any OTHER ExceptionInfo (e.g. the
              ;; fuel-exhausted guard in kotoba.wasm-exec/fuel-listener) is
              ;; not a capability denial and must propagate unchanged, not be
              ;; swallowed by this catch.
              (catch clojure.lang.ExceptionInfo e
                (if-let [denied (:kotoba.host/denied (ex-data e))]
                  {:kotoba.cli/ok? false
                   :kotoba.cli/code :wasm/run-denied
                   :kotoba.cli/data (merge {:kotoba.launcher/source-plan plan
                                            :kotoba.host/denied denied
                                            :kotoba.host/call (:kotoba.host/call (ex-data e))}
                                           (when policy
                                             {:kotoba.host/receipts (entries)}))}
                  (throw e))))))))))

(defn wasm-run-result
  "Safe-build entry point for `wasm run`. Same mandatory package-admission
  gate as `wasm-emit-result` (see `admission-gated`) — `wasm run` actually
  executes the compiled module against real host capabilities, so it is at
  least as sensitive to unverified package inputs as `wasm emit`, and must
  not be reachable without admission (F-001: previously `wasm run` did not
  consult package admission at all, regardless of `--package-lock`)."
  [argv]
  (admission-gated argv "--package-lock" :wasm/package-rejected wasm-run-result*))

(defn wasm-result
  "Handle launcher-owned Wasm-facing commands."
  [argv]
  (case (second argv)
    "emit" (wasm-emit-result argv)
    "run" (wasm-run-result argv)
    {:kotoba.cli/ok? false
     :kotoba.cli/code :wasm/unknown-command
     :kotoba.cli/data {:kotoba.wasm/command (second argv)
                       :kotoba.wasm/commands ["emit" "run"]}}))

(defn cljs-result
  "Handle launcher-owned ClojureScript-facing commands. Only `emit` exists --
  unlike `wasm run`, there is no `cljs run` here: this backend emits plain
  ClojureScript source text meant to be required/evaluated by a host cljs
  runtime (nbb, a browser bundle, Node), not executed in-process by this JVM
  launcher (ADR-2607151500 addendum 6 -- no memory-based host ABI, no
  Chicory-style in-process instantiation for this target)."
  [argv]
  (case (second argv)
    "emit" (cljs-emit-result argv)
    {:kotoba.cli/ok? false
     :kotoba.cli/code :cljs/unknown-command
     :kotoba.cli/data {:kotoba.cljs/command (second argv)
                       :kotoba.cljs/commands ["emit"]}}))

(defn safe-dispatch
  "Process-boundary dispatch. Language/runtime rejection is data; Java stack
  traces and host paths never become the CLI protocol. Library callers may
  continue using `dispatch` when they need exceptions for debugging."
  [argv]
  (try
    (let [result (dispatch argv)]
      (if (and (false? (:kotoba.cli/ok? result))
               (= :run/failed (:kotoba.cli/code result)))
        {:kotoba.cli/ok? false
         :kotoba.cli/code :runtime/rejected
         :kotoba.cli/diagnostic
         {:format :kotoba.diagnostic/v1
          :code :kotoba/runtime-rejected
          :severity :error
          :source (some-> (second argv) io/file .getName)}
         :kotoba.cli/data
         {:problems (get-in result [:kotoba.cli/data
                                    :kotoba.runtime/result
                                    :kotoba.runtime/problems])}}
        result))
    (catch clojure.lang.ExceptionInfo error
      {:kotoba.cli/ok? false
       :kotoba.cli/code :runtime/rejected
       :kotoba.cli/diagnostic
       {:format :kotoba.diagnostic/v1
        :code :kotoba/runtime-rejected
        :severity :error
        :exception-class (.getName (class error))}})
    (catch Exception error
      {:kotoba.cli/ok? false
       :kotoba.cli/code :runtime/internal-error
       :kotoba.cli/diagnostic
       {:format :kotoba.diagnostic/v1
        :code :kotoba/internal-error
        :severity :error
        :exception-class (.getName (class error))}})))

(defn -main [& argv]
  (let [result (safe-dispatch argv)]
    (println (render-result result (json-requested? argv)))
    (System/exit (result->exit result))))
