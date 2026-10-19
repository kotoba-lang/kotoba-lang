(ns kotoba.compiler.cli
  (:require [kotoba.compiler.atomic-output :as atomic-output]
            [kotoba.artifact.core :as artifact]
            [kotoba.compiler.bounded-edn :as bounded-edn]
            [kotoba.compiler.capability-names :as cap-names]
            [kotoba.compiler.core :as compiler]
            [kotoba.compiler.coverage :as coverage]
            [kotoba.compiler.coverage-evidence :as coverage-evidence]
            [kotoba.compiler.definition-identity :as definition-identity]
            [kotoba.compiler.diagnostic :as diagnostic]
            [kotoba.compiler.ios-aot :as ios-aot]
            [kotoba.compiler.interface :as interface]
            [kotoba.compiler.module-lock :as module-lock]
            [kotoba.compiler.project :as project]
            [kotoba.compiler.project-files :as project-files]
            [kotoba.compiler.packaging.pe32plus :as pe32plus]
            [kotoba.compiler.receipt :as receipt]
            [kotoba.compiler.test-profile :as test-profile]
            [kotoba.compiler.release :as release]
            [kotoba.artifact.runtime-identity :as runtime-identity]
            [kotoba.verifier.signing :as signing]
            [kotoba.compiler.source-path :as source-path]
            [kotoba.kir :as ir]
            [kotoba.kir.target :as target-profile]
            [kotoba.sema :as sema]
            [kotoba.verifier :as verifier]
            [json.data-json :as json]
            [clojure.string :as str])
  (:gen-class))

(defn- native-executor-fn [operation]
  (or (try
        (requiring-resolve (symbol "kototama.native.executor" (name operation)))
        (catch java.io.FileNotFoundException _ nil))
      (throw (ex-info
              "native execution requires the kotoba-lang/kototama-native plugin"
              {:phase :native-executor :operation operation
               :dependency 'io.github.kotoba-lang/kototama-native}))))

(defn- parse-target [s]
  (case s "wasm32" :wasm32-kotoba-v1 "x86_64" :x86_64-kotoba-v1
        "aarch64" :aarch64-kotoba-v1
        ;; ADR-2607252500: the primary application artifact.
        "component" :wasm-component-kotoba-v1
        "wasm-component" :wasm-component-kotoba-v1
        "js" :js-kotoba-v1
        "javascript" :js-kotoba-v1
        ;; The `kotoba` CLI's own spelling for this target. Without it the
        ;; alias table dropped `web` through (keyword s) to the bare `:web`,
        ;; rejected as :unsupported-target -- while :js-kotoba-v1 behind it
        ;; was fully implemented (measured 2026-09-05: compile-source green,
        ;; the emitted .mjs answers (keys {10 1 20 2}) = 2n under Node).
        "web" :js-kotoba-v1
        "js-browser" :js-browser-kotoba-v1
        ;; ADR-2607151500's cljs target was reachable only by its full profile
        ;; name -- `--target cljs-kotoba-v1` -- while `js`/`javascript` mapped
        ;; by alias. `--target cljs` fell through `(keyword s)` to the bare
        ;; `:cljs`, which dispatch! then rejected as `:unsupported-target`
        ;; even though the backend behind it (backend.cljs, the KIR -> cljs
        ;; source-text emitter) is implemented and its compile-source path is
        ;; green (measured 2026-09-04). Aliased here alongside its siblings.
        "cljs" :cljs-kotoba-v1
        "cljs-browser" :cljs-browser-kotoba-v1
        "cljs-node" :cljs-node-kotoba-v1
        "evm" :evm256-kotoba-v1
        "evm256" :evm256-kotoba-v1
        "wasm32-browser" :wasm32-browser-kotoba-v1
        "wasm32-wasi" :wasm32-wasi-kotoba-v1
        "x86_64-linux" :x86_64-linux-kotoba-v1
        "x86_64-macos" :x86_64-macos-kotoba-v1
        "x86_64-windows" :x86_64-windows-kotoba-v1
        "aarch64-linux" :aarch64-linux-kotoba-v1
        "aarch64-macos" :aarch64-macos-kotoba-v1
        "aarch64-windows" :aarch64-windows-kotoba-v1
        "aarch64-android" :aarch64-android-kotoba-v1
        "aarch64-ios" :aarch64-ios-kotoba-v1
        (keyword s)))

(defn- option [args flag] (second (drop-while #(not= flag %) args)))
(defn- options [args flag]
  (->> (partition 2 1 args)
       (keep (fn [[candidate value]] (when (= candidate flag) value)))
       vec))

(def ^:dynamic *exit* (fn [status] (System/exit status)))

(defn- kotoba-source! [path] (source-path/admit! path))

(defn- check-definitions
  "Per-definition CIDs for a checked module (ADR 0300).

  `check` does not emit, but a definition CID is defined on typed KIR, so
  answering the question means lowering. That lowering is discarded; the only
  thing kept is the identity report. A lowering failure downgrades the answer
  to a named `:unavailable` reason rather than failing the check -- `check`
  admits the frontend, and a KIR defect is not an admission verdict -- but it
  is never reported as an empty definition set."
  [hir]
  (definition-identity/describe
   {:hir hir
    :kir (try (ir/lower hir) (catch Exception _ nil))}))

(def ^:private detail-keys
  #{:phase :target :artifact-target :host-target :entry :arity :limit :status
    :reason :runtime-sha256 :not-before :expires :now
    ;; A refusal that cannot say what to do instead is a wall. These four are
    ;; the remedy for `:compile/unpinned-inputs` -- a stable problem keyword and
    ;; three fixed command strings. Nothing here is derived from user input, so
    ;; admitting them does not widen what an error can leak, which is what this
    ;; allowlist is for. `:check` joined them for the same reason on 2026-08-30:
    ;; a module rejected for declaring `(:require ...)` needs to be told which
    ;; invocation links its project.
    :problem :pin :then :override :check
    ;; `:fuel-declared-twice` names the two numbers that disagreed. They are
    ;; the caller's own integers (a flag and a policy field), already parsed
    ;; as such, so admitting them widens nothing an error can leak.
    :flag :policy})

(defn error-report
  ([error] (error-report error nil))
  ([error source-name]
  (let [data (ex-data error)
        phase (or (:phase data) :internal)
        details (merge (select-keys data detail-keys)
                       ;; A refusal that names no working path is a wall
                       ;; (same reason `:compile/unpinned-inputs` carries its
                       ;; remedy above). Fixed strings, so redaction holds.
                       (when (not= phase :internal)
                         (:details (diagnostic/refine error))))]
    (cond-> {:format :kotoba.cli-error/v1
             :ok false
             :error phase
             :diagnostic (diagnostic/from-error error source-name)
             :message (if (= phase :internal)
                        "internal compiler error"
                        (diagnostic/refined-message error (ex-message error)))}
      (seq details) (assoc :details details)))))

(defn exit-code [phase]
  (case phase
    :usage 64
    (:decode :read :subset :admission :ir :verify :coverage :project-link
     :module-lock) 65
    ;; Neither 0 nor 65. A refused definition is an ANSWER -- the report lists
    ;; it with its marker and exits 0 -- so this code is reserved for the
    ;; other thing: no identity could be computed at all. Without a distinct
    ;; code the two are indistinguishable to a caller, which is the failure
    ;; where a check that could not measure returns what a passing check does.
    :definition-identity 66
    (:signature :trust :runtime-identity) 77
    :output 74
    :execute 69
    :receipt 76
    70))

(defn- dispatch! [& args]
  (case (first args)
    "keygen"
    (let [output (or (option args "--output") "kotoba-signing-key.edn")
          key (signing/generate-keypair)]
      (atomic-output/write-edn! output key {:private? true})
      (println (pr-str {:ok true :output output :signer (:signer key)})))
    "public-key"
    (let [key (bounded-edn/read-file (second args))
          public (signing/verification-key key)
          output (or (option args "--output") "kotoba-verification-key.edn")]
      (atomic-output/write-edn! output public)
      (println (pr-str {:ok true :output output :signer (:signer public)})))
    "inspect"
    (let [input (kotoba-source! (second args))
          inspected (interface/inspect-source (bounded-edn/read-text-file input))]
      (if-let [output (option args "--output")]
        (do (atomic-output/write-edn! output inspected)
            (println (pr-str {:ok true :output output :sha256 (:sha256 inspected)})))
        (println (pr-str inspected))))
    "trust-key"
    (let [key (bounded-edn/read-file (second args))
          signer (signing/trusted-signer-id! key)
          output (or (option args "--output") "kotoba-trust.edn")
          trust {:format :kotoba.trust/v1 :trusted-signers #{signer}
                 :revoked-signers #{} :revoked-artifacts #{}
                 :trusted-runtime-sha256 #{}
                 :revoked-runtime-sha256 #{}}]
      (atomic-output/write-edn! output trust)
      (println (pr-str {:ok true :output output :signer signer})))
    "trust-runtime"
    (let [evidence (bounded-edn/read-file (second args))
          _ (runtime-identity/validate-measurement! evidence)
          trust-path (option args "--trust")
          trust (signing/validate-trust! (bounded-edn/read-file trust-path))
          runtime (:runtime evidence)
          runtime-sha (runtime-identity/identity-sha256 runtime)
          output (or (option args "--output") trust-path)
          updated (update trust :trusted-runtime-sha256 (fnil conj #{}) runtime-sha)]
      (atomic-output/write-edn! output updated)
      (println (pr-str {:ok true :output output :runtime-sha256 runtime-sha})))
    "measure-runtime"
    (let [{:keys [runtime loader-bytes]} ((native-executor-fn 'measure-runtime))
          output (or (option args "--output") "kotoba-runtime.edn")
          loader-output (or (option args "--loader-output") "kotoba-loader")]
      (atomic-output/write-bytes! loader-output loader-bytes {:executable? true})
      (atomic-output/write-edn! output {:format :kotoba.runtime-measurement/v1
                                        :runtime runtime})
      (println (pr-str {:ok true :output output :loader-output loader-output
                        :runtime-sha256 (runtime-identity/identity-sha256 runtime)})))
    "sign"
    (let [artifact (bounded-edn/read-file (second args))
          key (bounded-edn/read-file (option args "--key"))
          output (or (option args "--output") "program.signed.kexe")
          not-before (Long/parseLong (or (option args "--not-before") "0"))
          expires (Long/parseLong (or (option args "--expires")
                                      (str (+ (quot (System/currentTimeMillis) 1000) 86400))))
          envelope (signing/sign artifact key {:not-before not-before :expires expires})]
      (atomic-output/write-edn! output envelope)
      (println (pr-str {:ok true :output output :signer (get-in envelope [:statement :signer])})))
    "verify-signed"
    (let [envelope (bounded-edn/read-file (second args))
          trust (bounded-edn/read-file (option args "--trust"))
          now (Long/parseLong (or (option args "--now")
                                  (str (quot (System/currentTimeMillis) 1000))))
          result (signing/verify envelope trust now)]
      (println (pr-str (dissoc result :artifact))))
    "run"
    (let [envelope (bounded-edn/read-file (second args))
          trust (bounded-edn/read-file (option args "--trust"))
          policy (bounded-edn/read-file (option args "--policy"))
          input (bounded-edn/read-file (option args "--input"))
          executor-key (bounded-edn/read-file (option args "--executor-key"))
          now (Long/parseLong (or (option args "--now")
                                  (str (quot (System/currentTimeMillis) 1000))))
          parent-path (option args "--parent")
          parent (when parent-path (bounded-edn/read-file parent-path))
          entry (symbol (or (option args "--entry") "main"))
          runtime-measurement (bounded-edn/read-file (option args "--runtime"))
          _ (runtime-identity/validate-measurement! runtime-measurement)
          execution ((native-executor-fn 'execute)
                     envelope trust policy input
                     {:now now :entry entry
                      :runtime (:runtime runtime-measurement)
                      :loader-path (option args "--loader")})
          report (:report execution)
          evidence (:evidence execution)
          value (receipt/create
                 envelope trust policy input evidence
                 {:now now :started-at (:started-at execution)
                  :finished-at (:finished-at execution)
                  :status (:status evidence) :target (:target execution)
                  :entry entry :fuel-initial (get-in report [:fuel :initial])
                  :fuel-remaining (get-in report [:fuel :remaining])
                  :parent parent :executor-key executor-key})
          result-path (or (option args "--result-output") "run.result.edn")
          receipt-path (or (option args "--output") "run.receipt.edn")]
      (atomic-output/write-edn! result-path evidence)
      (atomic-output/write-edn! receipt-path value)
      (println (pr-str {:ok (= :ok (:status evidence))
                        :status (:status evidence) :result evidence
                        :result-output result-path :output receipt-path
                        :receipt-sha256 (:receipt-sha256 value)}))
      (when-not (= :ok (:status evidence)) (*exit* 120)))
    "receipt"
    (let [envelope (bounded-edn/read-file (option args "--signed"))
          trust (bounded-edn/read-file (option args "--trust"))
          policy (bounded-edn/read-file (option args "--policy"))
          input (bounded-edn/read-file (option args "--input"))
          output-value (bounded-edn/read-file (option args "--result"))
          parent-path (option args "--parent")
          parent (when parent-path (bounded-edn/read-file parent-path))
          executor-key (bounded-edn/read-file (option args "--executor-key"))
          output-path (or (option args "--output") "run.receipt.edn")
          value (receipt/create
                 envelope trust policy input output-value
                 {:now (Long/parseLong (option args "--now"))
                  :started-at (Long/parseLong (option args "--started-at"))
                  :finished-at (Long/parseLong (option args "--finished-at"))
                  :status (keyword (option args "--status"))
                  :target (parse-target (option args "--target"))
                  :entry (symbol (or (option args "--entry") "main"))
                  :fuel-initial (Long/parseLong (or (option args "--fuel-initial") "512"))
                  :fuel-remaining (Long/parseLong (option args "--fuel-remaining"))
                  :parent parent :executor-key executor-key})]
      (atomic-output/write-edn! output-path value)
      (println (pr-str {:ok true :output output-path :receipt-sha256 (:receipt-sha256 value)})))
    "verify-receipt"
    (let [value (bounded-edn/read-file (second args))
          envelope (bounded-edn/read-file (option args "--signed"))
          trust (bounded-edn/read-file (option args "--trust"))
          policy (bounded-edn/read-file (option args "--policy"))
          input (bounded-edn/read-file (option args "--input"))
          output-value (bounded-edn/read-file (option args "--result"))
          parent-path (option args "--parent")
          parent (when parent-path (bounded-edn/read-file parent-path))]
      (println (pr-str (receipt/verify value envelope trust policy input output-value
                                       {:now (Long/parseLong (option args "--now"))
                                        :parent parent}))))
    "verify-chain"
    (let [receipts (bounded-edn/read-file (second args))
          trust (bounded-edn/read-file (option args "--trust"))]
      (println (pr-str (receipt/verify-chain receipts trust))))
    "coverage"
    (let [manifest (bounded-edn/read-file (second args))
          _ (coverage/verify-dataset! manifest (option args "--dataset"))
          evidence-path (option args "--evidence")
          evidence (if evidence-path
                     (coverage-evidence/verify-bundle
                      (bounded-edn/read-file evidence-path)
                      (bounded-edn/read-file (option args "--trust"))
                      (Long/parseLong (or (option args "--now")
                                          (str (quot (System/currentTimeMillis) 1000)))))
                     [])]
      (println (pr-str (coverage/report manifest evidence))))
    "sign-coverage-evidence"
    (let [claim (bounded-edn/read-file (second args))
          key (bounded-edn/read-file (option args "--key"))
          output (or (option args "--output") "kotoba-coverage-evidence.edn")
          envelope (coverage-evidence/sign claim key)]
      (atomic-output/write-edn! output [envelope])
      (println (pr-str {:ok true :output output
                        :evidence-sha256 (artifact/sha256 (:statement envelope))})))
    "definition-cids"
    ;; ADR 0300. One CID per top-level function, in declaration order.
    ;;
    ;; No admission runs here, on either route. A definition CID is IDENTITY,
    ;; never authority -- kotoba-lang lang/code-identity.edn is explicit that
    ;; evaluating an effectful definition by CID needs a receipt the identity
    ;; does not grant -- so asking what a module's definitions ARE cannot
    ;; require a grant to run them. The first version of this command went
    ;; through `check-source` and therefore refused a module that names a
    ;; capability without a `--policy`, while the nbb twin answered; the two
    ;; routes gave different answers to the same question.
    ;;
    ;; `--policy` is still read, for the DECLARATIVE half only: a language
    ;; profile changes the HIR, and therefore the KIR, and therefore the CIDs.
    (let [input (kotoba-source! (second args))
          source-roots (options args "--source-path")
          profile-s (option args "--profile")
          policy (if-let [pp (option args "--policy")] (bounded-edn/read-file pp) {})
          graph (when (seq source-roots)
                  (project-files/load-closed-graph input source-roots))
          linked (when graph (project/link-source (:sources graph) (:root graph)))
          text (if linked (:source linked) (bounded-edn/read-text-file input))
          opts (cond-> {}
                 (:language-profile policy)
                 (assoc :language-profile (:language-profile policy))
                 profile-s (assoc :language-profile (keyword profile-s))
                 linked (assoc :admit-linked-synthetics? true))
          report (check-definitions (sema/analyze text opts))]
      (when-not (map? (:entries report))
        (throw (ex-info "no definition identity is available for this module"
                        {:phase :definition-identity :reason (:reason report)})))
      (println (pr-str (assoc report
                              :ok true
                              :format :kotoba.definition-cids/v1
                              :lines (definition-identity/format-lines report)
                              :scanned (definition-identity/scanned-line report)))))

    "check"
    ;; T9.2 / T3.4: frontend admit + optional --profile pure-product.
    ;; Default human pretty errors; --json for machine envelope.
    ;;
    ;; A module that declares `(:require ...)` is not checkable one file at a
    ;; time -- and before 2026-08-30 there was no other way to ask: `compile`
    ;; took `--source-path` / `--module-lock`, `check` took neither, so a
    ;; multi-file guest could be built but never checked, and the entry module
    ;; came back reported as a malformed `:export`. `check` now links the same
    ;; closed graph `compile` links, through the same `project/link-source`,
    ;; and admits the linked source. Nothing new is admitted: this is strictly
    ;; the frontend half of a compile that already works.
    ;;
    ;; `--source-path` needs no `--unpinned` here, unlike `compile`. That flag
    ;; exists (ADR-2608580000 D5) because a path-resolved ARTIFACT cannot say
    ;; which inputs produced it. `check` writes no artifact, so there is no
    ;; provenance record for an unpinned read to falsify.
    (let [input (when-not (option args "--module-lock") (kotoba-source! (second args)))
          source-roots (options args "--source-path")
          module-lock-path (option args "--module-lock")
          policy-path (option args "--policy")
          profile-s (option args "--profile")
          json? (some #{"--json"} args)
          policy (cond-> (cap-names/wire-policy
                          (if policy-path (bounded-edn/read-file policy-path) {}))
                   profile-s (assoc :language-profile (keyword profile-s)))
          locked (when module-lock-path
                   (module-lock/load-locked-graph
                    module-lock-path
                    (or (option args "--blocks")
                        (throw (ex-info "--module-lock requires --blocks <dir>"
                                        {:phase :usage})))))
          graph (cond
                  locked {:sources (:sources locked) :root (:root locked)}
                  (seq source-roots) (project-files/load-closed-graph input source-roots)
                  :else nil)
          modules (when graph (vec (sort (map str (keys (:sources graph))))))]
      (try
        (let [result (if graph
                       (try
                         (compiler/check-project (:sources graph) (:root graph) policy)
                         (catch clojure.lang.ExceptionInfo error
                           (throw (project/with-module-file error (:paths graph)))))
                       (compiler/check-source (bounded-edn/read-text-file input) policy))
              ;; The reporting boundary. The wire id stays in HIR, in the
              ;; admission decision and in any artifact a later `compile`
              ;; emits, and stops on these three lines.
              ;; `lang/capability-catalog.edn` says
              ;; `:numeric-id :not-user-facing`; before 2026-09-01 both this
              ;; envelope and the human line below handed it out anyway.
              ;; `:named-operations` is what ability elaboration already
              ;; computed -- it was in HIR and simply never reported.
              effects (cap-names/name-grants (get-in result [:hir :effects] #{}))
              operations (get-in result [:hir :named-operations])
              admission (cap-names/name-grants (:admission result))
              exports (get-in result [:hir :exports])
              ;; ADR 0300: one CID per top-level function, the same map the
              ;; nbb `check --json` emits under the same key.
              definitions (check-definitions (:hir result))]
          (if json?
            (println (pr-str (cond-> {:ok true
                                      :format :kotoba.check/v1
                                      :language-profile (:language-profile result)
                                      :effects effects
                                      :named-operations operations
                                      :exports exports
                                      :definitions definitions
                                      :admission admission}
                               graph (assoc :root (str (:root graph)) :modules modules))))
            (do (apply println
                       (cond-> ["ok"
                                (str "profile=" (or (some-> (:language-profile result) name) "default"))
                                (str "effects=" (pr-str effects))
                                (str "exports=" (pr-str (or exports [])))]
                         graph (conj (str "modules=" (pr-str modules)))))
                (flush))))
        (catch Exception e
          (if json?
            (do (println (pr-str (error-report e input)))
                (*exit* (exit-code (or (:phase (ex-data e)) :subset))))
            (do (binding [*out* *err*]
                  (println (diagnostic/format-human e input)))
                (*exit* (exit-code (or (:phase (ex-data e)) :subset))))))))
    "test"
    ;; T9.3: official .kotoba test harness (test-profile). Human by default; --json machine.
    (let [input (kotoba-source! (second args))
          json? (some #{"--json"} args)
          report (test-profile/run-source (bounded-edn/read-text-file input))]
      (if json?
        (println (pr-str report))
        (let [by-target (or (:results report) {})
              flat (mapcat (fn [[tgt rows]]
                             (map #(assoc % :target tgt) rows))
                           by-target)
              passed (count (filter :ok flat))
              total (count flat)
              names (or (:tests report) [])]
          (println (str "kotoba test: " passed "/" total
                        (if (:ok report) " passed" " FAILED")
                        " tests=" (pr-str names)
                        " targets=" (pr-str (or (:targets report) []))))
          (doseq [r flat]
            (when-not (:ok r)
              (binding [*out* *err*]
                (println " FAIL" (:test r)
                         (str "target=" (:target r))
                         (or (:error r) "")))))))
      (when-not (:ok report) (*exit* 1)))
    "package-aiueos-boot"
    (let [input (second args)
          output (or (option args "--output") "BOOTX64.EFI")
          payload-path (option args "--payload")
          raw (java.nio.file.Files/readAllBytes
               (java.nio.file.Paths/get input (make-array String 0)))
          kernel (mapv #(bit-and (int %) 0xff) raw)
          payload (if payload-path
                    (mapv #(bit-and (int %) 0xff)
                          (java.nio.file.Files/readAllBytes
                           (java.nio.file.Paths/get payload-path
                                                    (make-array String 0))))
                    [])
          packaged (pe32plus/package-embedded-kernel kernel payload)]
      (atomic-output/write-bytes! output
        (byte-array (map unchecked-byte (:bytes packaged))))
      (println (pr-str {:ok true :target :x86_64-aiueos-uefi-v1
                        :kernel input :output output
                        :kernel-sha256 (:embedded-kernel-sha256 packaged)
                        :payload payload-path
                        :payload-sha256 (:embedded-payload-sha256 packaged)})))
    "module-lock"
    ;; Pin a path-resolved project once so every later compile of it resolves
    ;; by CID instead of by whatever is on disk.
    (let [input (kotoba-source! (second args))
          source-roots (options args "--source-path")
          blocks (or (option args "--blocks")
                     (throw (ex-info "--blocks <dir> is required" {:phase :usage})))
          output (or (option args "--output") "kotoba.modules.edn")]
      (when (empty? source-roots)
        (throw (ex-info "--source-path is required" {:phase :usage})))
      (let [lock (module-lock/lock-from-source-paths input source-roots blocks)]
        (atomic-output/write-edn! output (dissoc lock :lock-cid))
        (println (pr-str {:ok true :lock output :blocks blocks
                          :root (:root lock) :modules (count (:modules lock))
                          :lock-cid (:lock-cid lock)}))))

    "compile"
    (let [input (when-not (option args "--module-lock") (kotoba-source! (second args)))
          source-roots (options args "--source-path")
          module-lock-path (option args "--module-lock")
          unpinned? (boolean (some #{"--unpinned"} args))
          _ (when (and (seq source-roots) (nil? module-lock-path) (not unpinned?))
              ;; `project-files` resolves `(:require [app.util])` by turning a
              ;; namespace into a PATH, so what gets compiled depends on what
              ;; happens to be on disk and the build cannot say which inputs it
              ;; actually used. That stays available -- it is how a source tree
              ;; becomes a lock in the first place -- but it stops being the
              ;; DEFAULT (ADR-2608580000 D5). Saying so costs one flag; not
              ;; being able to tell afterwards costs the property.
              (throw (ex-info "a multi-module compile needs pinned inputs"
                              {:phase :usage
                               :problem :compile/unpinned-inputs
                               :source-paths (vec source-roots)
                               :pin "module-lock <entry> --source-path <dir> --blocks <dir>"
                               :then "compile --module-lock <lock> --blocks <dir>"
                               :override "--unpinned"})))
          locked (when module-lock-path
                   (module-lock/load-locked-graph
                    module-lock-path
                    (or (option args "--blocks")
                        (throw (ex-info "--module-lock requires --blocks <dir>"
                                        {:phase :usage})))))
          target (parse-target (or (option args "--target") "wasm32"))
          output (or (option args "--output")
                     ;; A locked compile has no input PATH to derive a name
                     ;; from -- that is the point -- so the root namespace
                     ;; names the artifact instead.
                     (str (or input (str (:root locked)))
                          (case (:execution (target-profile/profile target))
                            :wasm ".wasm"
                            :component ".component.wasm"
                            :cljs ".cljs"
                            :javascript ".mjs"
                            :evm ".evm"
                            :kernel ".o"
                            :process ".elf"
                            ".kexe")))
          component-target? (= :component (:execution (target-profile/profile target)))
          policy-path (option args "--policy")
          ;; Canonicalised at decode so admission, the emitters and provenance
          ;; all see the wire form whichever spelling the file used: two
          ;; spellings of one policy hash to the same `:policy-sha256` and
          ;; build the same artifact, rather than forking artifact identity on
          ;; notation.
          policy (cap-names/wire-policy
                  (if policy-path (bounded-edn/read-file policy-path) {}))
          artifact-kind (option args "--artifact")
          _ (when-not (contains? #{nil "object" "image"} artifact-kind)
              (throw (ex-info "unknown native artifact kind"
                              {:phase :artifact-target :artifact artifact-kind})))
          component-opts
          (cond-> {:target target}
            (option args "--profile")
            (assoc :profile (keyword (option args "--profile")))
            (option args "--fuel")
            (assoc-in [:budgets :fuel] (Long/parseLong (option args "--fuel")))
            (option args "--memory-pages")
            (assoc-in [:budgets :memory-pages]
                      (Long/parseLong (option args "--memory-pages")))
            (option args "--package-lock-cid")
            (assoc :package-lock-cid (option args "--package-lock-cid"))
            (option args "--capability-mode")
            (assoc :capability-mode
                   (keyword (option args "--capability-mode"))))
          ;; compile-source takes build metadata separately from policy.
          ;; Previously --fuel was threaded only through compile-component,
          ;; so the CLI accepted it for a native/core artifact while silently
          ;; sealing the historical 512 instead.
          source-opts
          (cond-> {}
            (option args "--fuel")
            (assoc :fuel (Long/parseLong (option args "--fuel"))))
          result (cond
                   ;; CID-pinned graph. Identical downstream to the path-
                   ;; resolved cases below: only how the sources were found,
                   ;; and whether that finding was verified, differs.
                   (and locked component-target?)
                   (let [linked (project/link-source (:sources locked) (:root locked))]
                     (compiler/compile-component (:source linked) policy
                                                 (assoc component-opts
                                                        :admit-linked-synthetics? true)))

                   locked
                   (compiler/compile-project (:sources locked) (:root locked) target policy
                                             {} source-opts)

                   ;; Multi-file closed graph → link → compile-component (T8.3
                   ;; multi-file project kit body first slice). Same Canonical
                   ;; lowering path as single-file component compile.
                   (and component-target? (seq source-roots))
                   (let [{:keys [sources root]}
                         (project-files/load-closed-graph input source-roots)
                         linked (project/link-source sources root)]
                     (compiler/compile-component (:source linked) policy
                                                 (assoc component-opts
                                                        :admit-linked-synthetics? true)))

                   ;; A component is lifted from a core module through the
                   ;; Canonical ABI, so it has its own entry point rather than
                   ;; being one more backend behind compile-source.
                   component-target?
                   (compiler/compile-component
                    (bounded-edn/read-text-file input) policy component-opts)

                   ;; `source-opts` rides along exactly as on the single-file
                   ;; branch below. It used to be dropped here, so `--fuel`
                   ;; on a project build was accepted and silently ignored.
                   (seq source-roots)
                   (let [{:keys [sources root paths]} (project-files/load-closed-graph input source-roots)]
                     (try
                       (compiler/compile-project sources root target policy {} source-opts)
                       ;; The graph came from paths, so a refusal attributed
                       ;; to a module can name the file it was loaded from.
                       (catch clojure.lang.ExceptionInfo error
                         (throw (project/with-module-file error paths)))))

                   :else
                   (compiler/compile-source (bounded-edn/read-text-file input)
                                            target policy source-opts))]
      (case (:format result)
        :wasm/v1 (atomic-output/write-bytes! output (:bytes result))
        ;; The component artifact is three files: the binary, the WIT world it
        ;; was lifted against, and the admission request kototama needs. They
        ;; are written together so an artifact can never circulate without the
        ;; interface and bounds it claims.
        :wasm-component/v1
        (do (atomic-output/write-bytes! output (:bytes result))
            (atomic-output/write-text! (str output ".wit") (get-in result [:wit :source]))
            (atomic-output/write-edn! (str output ".admission.edn")
                                      (:admission-request result)))
        ;; ADR-2607151500: the cljs backend emits SOURCE TEXT, not an
        ;; artifact map -- write-edn! would pr-str this into a quoted/
        ;; escaped EDN string literal instead of directly readable cljs
        ;; source (a real, previously-silent bug: `compile --target
        ;; cljs-kotoba-v1` reported :ok true while writing the literal
        ;; text "nil" to --output, since :artifact is absent from a
        ;; :cljs/v1 result).
        :cljs/v1 (atomic-output/write-text! output (:source result))
        :javascript/v1 (do
                         (atomic-output/write-text! output (:source result))
                         (atomic-output/write-edn! (str output ".manifest.edn")
                                                   (:manifest result))
                         (atomic-output/write-text!
                          (str output ".manifest.json")
                          ;; `json.data-json/write-str` takes ONE options MAP,
                          ;; unlike `clojure.data.json/write-str`'s variadic
                          ;; kwargs, despite the shim's docstring claiming the
                          ;; same contract. Called the old way this is 3 args
                          ;; against arities 1 and 2, so it throws an
                          ;; ArityException -- not an ExceptionInfo, so the
                          ;; CLI's `catch Throwable` reported "internal
                          ;; compiler error" and exit 70 AFTER the artifact
                          ;; and the .manifest.edn had already been written.
                          ;; Measured 2026-08-29: every `compile --target js`
                          ;; exited 70 while emitting a perfectly good module,
                          ;; which also broke scripts/http-service-e2e.cljs.
                          (json/write-str (:manifest result)
                                          {:key-fn (fn [k] (if (keyword? k)
                                                             (subs (str k) 1)
                                                             (str k)))})))
        :evm/v1 (do
                  (atomic-output/write-bytes!
                   output (byte-array (map unchecked-byte (:creation-bytes result))))
                  (atomic-output/write-text!
                   (str output ".abi.json")
                   ;; Same shim signature as the :javascript/v1 branch above.
                   (json/write-str (:abi result)
                                   {:key-fn (fn [k] (if (keyword? k)
                                                      (subs (str k) 1)
                                                      (str k)))}))
                  (atomic-output/write-edn!
                   (str output ".manifest.edn")
                   (select-keys result [:format :target :target-profile :selector
                                        :kir-sha256 :runtime-sha256
                                        :creation-sha256 :limits])))
        :kexe/v1 (if-let [packaged (case artifact-kind
                                    "image" (:binary result)
                                    "object" (:object result)
                                    (or (:object result)
                                        (when (= :process (get-in result [:artifact :target-profile :execution]))
                                          (:binary result))))]
                   (atomic-output/write-bytes!
                    output (byte-array (map unchecked-byte (:bytes packaged))))
                   (atomic-output/write-edn! output (:artifact result)))
        (atomic-output/write-edn! output (:artifact result)))
      (let [provenance-output (str output ".provenance.edn")
            inputs (cond-> {:kotoba.compile/inputs
                            (cond locked :module-lock
                                  (seq source-roots) :unpinned-source-path
                                  :else :single-file)}
                     locked (assoc :module-lock module-lock-path
                                   :lock-cid (:lock-cid locked))
                     (seq source-roots) (assoc :source-paths (vec source-roots)))
            inputs-output (str output ".inputs.edn")]
        (atomic-output/write-edn! provenance-output (:provenance result))
        ;; Written BESIDE the provenance rather than into it. `provenance/
        ;; verify!` requires the descriptor's key set to match exactly, so an
        ;; extra key would make every artifact fail its own identity check.
        ;; That means this file is a RECORD, not a seal: it says which way the
        ;; inputs were found, and it is not evidence the way a hash is. Putting
        ;; the mode inside the seal needs `build-metadata` threaded through
        ;; `compile-project`/`compile-source`/`compile-component`, at which
        ;; point a pinned and an unpinned build of identical source would get
        ;; different provenance hashes -- which is the correct end state,
        ;; because they are not the same build.
        (atomic-output/write-edn! inputs-output inputs)
        (println (pr-str (merge {:ok true :target target :output output
                                 :provenance-output provenance-output
                                 :inputs-output inputs-output}
                                (select-keys inputs [:kotoba.compile/inputs]))))))
    "package-ios"
    (let [input (bounded-edn/read-file (second args))
          entry (symbol (or (option args "--entry") "main"))
          platform (keyword (or (option args "--platform") "ios"))
          output (or (option args "--output") "kotoba-ios-program.o")
          manifest-output (or (option args "--manifest-output") (str output ".edn"))
          packaged (ios-aot/package input entry {:platform platform})]
      (atomic-output/write-bytes! output (:object packaged))
      (atomic-output/write-edn! manifest-output (:manifest packaged))
      (println (pr-str {:ok true :target (:target input) :entry entry
                        :platform platform :output output
                        :manifest-output manifest-output})))
    "sbom"
    (let [input (second args)
          output (or (option args "--output") (str input ".spdx"))]
      (atomic-output/write-bytes! output (release/sbom-bytes input))
      (println (pr-str {:ok true :format :spdx/v2.3 :output output})))
    "attest-release"
    (let [input (second args)
          sbom (option args "--sbom")
          key (bounded-edn/read-file (option args "--key"))
          target (parse-target (option args "--target"))
          not-before (Long/parseLong (option args "--not-before"))
          expires (Long/parseLong (option args "--expires"))
          output (or (option args "--output") (str input ".attestation.edn"))
          envelope (release/attest input sbom target key not-before expires)]
      (atomic-output/write-edn! output envelope)
      (println (pr-str {:ok true :target target :output output
                        :subject-sha256 (get-in envelope [:statement :subject :sha256])})))
    "verify-release"
    (let [envelope (bounded-edn/read-file (second args))
          input (option args "--artifact")
          sbom (option args "--sbom")
          trust (bounded-edn/read-file (option args "--trust"))
          now (Long/parseLong (option args "--now"))
          result (release/verify! envelope input sbom trust now)]
      (println (pr-str (assoc result :ok true))))
    "verify"
    (let [artifact (bounded-edn/read-file (second args))]
      (verifier/verify-artifact! artifact)
      (println (pr-str {:ok true :verified true :target (:target artifact)})))
    "extract-native"
    (let [artifact (bounded-edn/read-file (second args))
          symbol (symbol (or (option args "--symbol") "main"))
          output (or (option args "--output") "program.bin")
          _ (verifier/verify-artifact! artifact)
          export (get (:exports artifact) symbol)]
      (when-not export
        (throw (ex-info "unknown native export" {:phase :verify :entry symbol})))
      (atomic-output/write-bytes!
       output (byte-array (map unchecked-byte (:code artifact))))
      (println (pr-str (merge {:ok true :output output :symbol symbol} export))))
    (throw (ex-info "unknown or missing kotoba command" {:phase :usage}))))

(defn -main [& args]
  (try
    (apply dispatch! args)
    (catch clojure.lang.ExceptionInfo error
      (let [source (second args)
            source-name (when (source-path/source-kind source)
                          (.getName (java.io.File. source)))
            report (error-report error source-name)]
        (binding [*out* *err*] (println (pr-str report)))
        (*exit* (exit-code (:error report)))))
    (catch Throwable _
      (binding [*out* *err*]
        (println (pr-str {:format :kotoba.cli-error/v1 :ok false
                          :error :internal :message "internal compiler error"})))
      (*exit* 70))))
