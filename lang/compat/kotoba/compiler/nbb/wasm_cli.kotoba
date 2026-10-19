(ns kotoba.compiler.nbb.wasm-cli
  "Primary JDK-free entrypoint for `check` and Wasm compilation. Native
  emitters and the native verifier stay outside this namespace's dependency
  closure, while Wasm provenance is emitted from the same checked HIR/KIR,
  policy, target profile, compatibility descriptor, and bytes as the JVM
  compiler."
  (:require ["node:path" :as node-path]
            [kotoba.compiler.capability-names :as cap-names]
            [kotoba.compiler.nbb.cli-support :as support]
            [kotoba.compiler.definition-identity :as definition-identity]
            [kotoba.compiler.nbb.package-authoring :as package-authoring]
            [kotoba.compiler.nbb.package-lock :as package-lock]
            [kotoba.compiler.nbb.compile-cache :as compile-cache]
            [kotoba.sema :as sema]
            [kotoba.compiler.nbb.io :as io]
            [kotoba.compiler.nbb.module-lock :as module-lock]
            [kotoba.compiler.nbb.project-files :as project-files]
            [kotoba.compiler.nbb.project-source :as project-source]
            [kotoba.compiler.nbb.output-set :as output-set]
            [kotoba.artifact.core :as artifact]
            [kotoba.compiler.provenance :as provenance]
            [kotoba.kir :as ir]
            [kotoba.compiler.effect-row :as effect-row]
            [kotoba.kir.compatibility :as compatibility]
            [kotoba.kir.target :as target-profile]
            [kotoba.wasm.core :as wasm]
            [kotoba.wasm.typed :as typed]))

(def targets
  {"wasm32" :wasm32-kotoba-v1
   "wasm32-browser" :wasm32-browser-kotoba-v1
   "wasm32-wasi" :wasm32-wasi-kotoba-v1})

(defn- read-policy!
  "`--policy`, with named grants canonicalised to wire ids.

  Applied at every place this entrypoint decodes a capability policy -- before
  admission AND before provenance -- so `[:cap/call :hash/sha256]` and
  `[:cap/call 3]` are the same policy: same `:policy-sha256`, same artifact,
  byte for byte. See `kotoba.compiler.nbb.cli-support/parse-policy-material`
  for why the canonicalisation is here rather than in that shared decoder."
  [args]
  (cap-names/wire-policy (support/read-policy args)))

(defn- decode-policy!
  "The cached-compile counterpart of `read-policy!`, for already-read material."
  [material]
  (cap-names/wire-policy (support/parse-policy-material material)))

(defn- resolve-hir! [source opts stage-cache]
  (support/timed "frontend"
                 #(compile-cache/resolve-stage!
                   stage-cache :hir
                   ;; The whole options map, not just :language-profile: a
                   ;; linked project adds :admit-linked-synthetics?, and a key
                   ;; blind to it would answer a linked question with an
                   ;; unlinked HIR. Tag raised to v3 so older entries miss.
                   (pr-str [:kotoba.hir-cache/v3 source opts])
                   (fn [] (sema/analyze source opts)))))

(defn- resolve-kir! [hir stage-cache]
  (support/timed "kir-lower"
                 #(compile-cache/resolve-stage!
                   ;; Source spelling is deliberately excluded: a frontend-
                   ;; equivalent edit still reruns admission, then reuses KIR.
                   stage-cache :kir (pr-str hir) (fn [] (ir/lower hir)))))

(defn- stage-status [hir-result kir-result emit-result]
  (cond-> {:hir (:cache hir-result) :kir (:cache kir-result)}
    emit-result (assoc :wasm (:cache emit-result))))

(defn- emit-material
  "The cache key for Wasm EMISSION: the module's definition graph plus every
  emitter input that is not part of it.

  This is the ADR 0300 extension of the existing cache, and it is a SECOND
  stage inside `compile-cache`, not a second cache. The artifact entry above
  stays keyed on source text, because it also carries the `.provenance.edn`
  sidecar and provenance seals `:source-sha256` -- serving a rename the old
  provenance would be a wrong answer, not a fast one. Emission has no such
  obligation: it is a function of the code and the target.

  `definition-identity/cache-material` supplies the ordered definition CIDs
  and the export names; both halves were measured against emitted bytes on
  2026-09-02 (see the ADR): renaming a NON-exported function leaves the
  `.wasm` byte-identical, swapping two private functions' declaration order
  does NOT, and an exported name is in the bytes. Returns nil when any
  definition lacks a CID, and a nil material means this stage is skipped
  entirely rather than keyed on a partial identity."
  [report hir target fuel value-abi wasm-features policy-material lock-cid kir-format]
  (when-let [material (definition-identity/cache-material report (:exports hir))]
    {:definition-count (definition-identity/definition-count material)
     :text (pr-str [:kotoba.wasm-emit-cache/v1
                    material
                    (name target)
                    fuel
                    value-abi
                    (vec (sort (map name wasm-features)))
                    kir-format
                    compatibility/compiler-version
                    (:text policy-material)
                    lock-cid])}))

(def ^:private floating-point-policy
  :kotoba.floating-point/ieee-754-f32-f64-v7)

(defn- definitions-recompiled
  "How many definitions this compile actually re-emitted.

  The unit is deliberately DEFINITIONS, not milliseconds: wall clock on this
  machine is a measurement of how many other agents are running, and a cache
  that is reported in seconds cannot be told apart from a machine that got
  quieter. `0` means the emission stage was served from its CID key; `n` means
  the module's n definitions were emitted.

  `:unmeasured` when no cache material could be built -- a module with a
  refused definition has no identity to key on, and reporting `n` for it would
  say the cache had been asked and missed when it was never asked."
  [material emit-result]
  (cond
    (nil? material) :unmeasured
    (= :hit (:cache emit-result)) 0
    :else (:definition-count material)))

(defn- compile-wasm!
  "Keep the primary Node result and provenance identity aligned with
  `kotoba.compiler.core/compile-source*`'s Wasm branch. In particular, the
  CLI build-metadata fuel and policy fuel budget are emitter inputs, not
  admission-only metadata."
  [source target policy emit-metadata hir admission-result kir
   {:keys [stage-cache policy-material lock-cid]}]
  (let [profile (target-profile/profile target)
        typed-values? (= :kotoba.kir/v4 (:format kir))
        value-abi (cond (ir/uses-f32? hir) :kotoba.typed/mixed-f32-f64-v3
                        (ir/uses-f64? hir) :kotoba.typed/mixed-f64-v2
                        typed-values? :kotoba.typed/externref-v1
                        :else :kotoba.i64/direct-v1)
        fuel (or (:fuel emit-metadata) (get-in policy [:budgets :fuel]) 512)
        compat (compatibility/descriptor
                {:hir-format (:format hir) :kir-format (:format kir)
                 :target target :target-profile profile :value-abi value-abi})
        result {:format :wasm/v1 :target target :target-profile profile
                :hir hir :kir kir :admission admission-result
                :compatibility compat
                :floating-point-policy floating-point-policy
                :value-profile (if typed-values?
                                 :kotoba.value/typed-v1 :kotoba.value/i64-v1)
                :value-abi value-abi
                :wasm-features (cond-> #{}
                                 (typed/requires-host-runtime? kir)
                                 (conj :reference-types)
                                 (ir/uses-f32? kir) (conj :ieee-754-f32)
                                 (ir/uses-f64? kir) (conj :ieee-754-f64))
                :limits (cond-> {:fuel fuel :replenishable? false}
                          typed-values?
                          (assoc :parametric-adt-depth 12
                                 :parametric-adt-nodes 64
                                 :variant-cases 32
                                 :heterogeneous-vector-items 32
                                 :typed-set-items 32
                                 :typed-map-entries 31
                                 :record-fields 32
                                 :vector-i64-items 16384
                                 :vector-f64-items 16384
                                 :compact-graph-items 128
                                 :string-index-key-bytes 65536))
                :bytes nil}
        wasm-features (:wasm-features result)
        ;; Computed ONCE and carried on the result, so `provenance/descriptor`
        ;; reuses it rather than hashing every definition a second time.
        report (definition-identity/describe {:hir hir :kir kir})
        result (assoc result :definitions report)
        material (emit-material report hir target fuel value-abi wasm-features
                                policy-material lock-cid (:format kir))
        emit-result (support/timed
                     "wasm-emit"
                     #(compile-cache/resolve-stage!
                       (when material stage-cache) :wasm (:text material)
                       (fn [] (wasm/emit kir target {:fuel fuel}))))
        result (assoc result :bytes (:value emit-result))]
    {:result (support/timed "provenance"
                            #(provenance/attach source policy emit-metadata result))
     :emit emit-result
     :material material}))

(defn- serialized-wasm [result]
  {:bytes (.from js/Buffer (:bytes result))
   :provenance-text (support/timed
                     "provenance-serialize"
                     #(pr-str (artifact/edn-safe (:provenance result))))})

(defn- write-wasm! [output {:keys [bytes provenance-text]}]
  (let [provenance-output (str output ".provenance.edn")
        publication-output (str output ".publication.edn")
        publication-text (output-set/serialize output bytes provenance-text)]
    (support/timed
     "output-set-write"
     #(io/write-set! [{:path output :bytes bytes}
                      {:path provenance-output :text provenance-text}
                      {:path publication-output :text publication-text}]))
    {:provenance-output provenance-output
     :publication-output publication-output}))

(defn- computed-definition-cids
  "The definition CIDs of a built graph, as a flat collection."
  [report]
  (keep :cid (vals (:entries report))))

(defn- package-verdict!
  "Bind a consumed package lock to the code that was actually built.

  Resolution alone proves a directory exists where the lock says one should.
  This proves the definitions in it are the ones the lock names -- and it is
  the reason `--package-lock` is a pin rather than a path search with extra
  ceremony. Throws on a mismatch; the caller emits nothing.

  Returns nil when no package lock was consumed, so a caller `cond->`-ing on
  it prints nothing rather than printing an empty verification."
  [resolved definitions]
  (when-let [packages (:packages resolved)]
    (let [verdict (support/timed
                   "package-definitions"
                   #(package-lock/verify-definitions
                     (:dependencies packages)
                     (computed-definition-cids definitions)))]
      (package-lock/report packages verdict))))

(defn- check! [args context]
  (let [resolved (project-source/resolve-source! args)
        source (:source resolved)
        policy (support/timed "policy-read" #(read-policy! args))
        hir-result (resolve-hir! source
                                 (project-source/analyze-opts policy (:linked? resolved))
                                 (:stages context))
        hir (:value hir-result)
        result (support/timed
                "admission"
                #(effect-row/check hir (support/capability-policy policy)))
        definitions (definition-identity/describe
                     {:hir hir :kir (try (ir/lower hir) (catch :default _ nil))})]
    ;; Same keys as the JVM `check --json` in kotoba.compiler.cli. This path
    ;; used to answer with :ok/:effects/:admission only, so a consumer keying
    ;; on :format -- the versioned output contract -- saw nothing to key on,
    ;; and :exports was simply absent. Same command, same file, two shapes.
    ;;
    ;; `cap-names/name-grants` is applied to everything a person reads here.
    ;; The wire id stays in HIR, in the admission decision, in KIR and in the
    ;; emitted bytes; it stops at this line. `:named-operations` is what the
    ;; frontend already computed during ability elaboration -- it was present
    ;; in HIR and simply never reported.
    ;;
    ;; `:definitions` is ADR 0300: same key, same shape and the same CIDs as
    ;; the JVM `check --json`, which `scripts/test-definition-cid-parity.cljs`
    ;; asserts definition by definition.
    (cond-> {:ok true
             :format :kotoba.check/v1
             :language-profile (:language-profile hir)
             :effects (cap-names/name-grants (:effects hir))
             :named-operations (:named-operations hir)
             :exports (:exports hir)
             :definitions definitions
             :admission (cap-names/name-grants result)}
      ;; A linked answer says so. Without this a caller cannot tell a one-file
      ;; check from a check of a graph that happened to link -- the difference
      ;; between "this module is admitted" and "these N modules are".
      (:project resolved) (assoc :project (:project resolved))
      ;; A check that consumed a package lock says what the lock bought. Absent
      ;; this key the answer is identical whether a lock was verified or never
      ;; supplied, which is the shape that lets a build quietly stop checking.
      (:packages resolved) (assoc :packages (package-verdict! resolved definitions))
      context (assoc :stage-cache {:hir (:cache hir-result)}))))

(defn- definition-cids!
  "ADR 0300. One CID per top-level function, in declaration order.

  Same report, same keys and same CIDs as the JVM twin in
  `kotoba.compiler.cli`; `definition_identity_parity_test` asserts the two
  routes agree definition by definition."
  [args]
  (let [resolved (project-source/resolve-source! args)
        policy (support/timed "policy-read" #(read-policy! args))
        hir (:value (resolve-hir! (:source resolved)
                                  (project-source/analyze-opts policy (:linked? resolved))
                                  nil))
        report (definition-identity/describe
                {:hir hir :kir (try (ir/lower hir) (catch :default _ nil))})]
    (when-not (map? (:entries report))
      (throw (ex-info "no definition identity is available for this module"
                      {:phase :definition-identity :reason (:reason report)})))
    (cond-> (assoc report
                   :ok true
                   :format :kotoba.definition-cids/v1
                   :lines (definition-identity/format-lines report)
                   :scanned (definition-identity/scanned-line report))
      (:packages resolved)
      (assoc :packages (package-verdict! resolved report)))))

(defn- compile-uncached! [args target output source linked? lock-cid]
  (let [policy (support/timed "policy-read" #(read-policy! args))
        emit-metadata (support/emit-metadata args)
        hir (:value (resolve-hir! source (project-source/analyze-opts policy linked?) nil))
        admission-result (support/timed
                          "admission"
                          #(effect-row/check hir (support/capability-policy policy)))
        kir (support/timed "kir-lower" #(ir/lower hir))
        compiled (compile-wasm! source target policy emit-metadata
                                hir admission-result kir
                                {:stage-cache nil :lock-cid lock-cid})
        serialized (serialized-wasm (:result compiled))
        {:keys [provenance-output publication-output]}
        (write-wasm! output serialized)]
    {:ok true :target target :output output
     :provenance-output provenance-output
     :publication-output publication-output
     ;; No worker context means no cache at all, so every definition in the
     ;; module was emitted. Reported anyway: "the cache was not consulted" and
     ;; "the cache missed" are different facts and must not print the same.
     :definitions-recompiled (definitions-recompiled (:material compiled) nil)}))

(defn- compile-cached! [args target output source linked? context lock-cid]
  ;; Policy material is part of artifact identity. Declarative policy controls
  ;; also affect HIR and emission, so decode them before consulting either
  ;; stage cache; otherwise a language-profile change could reuse the wrong HIR.
  (let [policy-attempt (support/timed
                        "policy-read"
                        #(try {:material (support/read-policy-material args)}
                              (catch :default error {:error error})))
        material (:material policy-attempt)
        emit-metadata (support/emit-metadata args)
        key (when material
              (support/timed "cache-key"
                             #(compile-cache/key-for target source material
                                                     emit-metadata linked?)))
        artifact-cache (:artifacts context)
        stage-cache (:stages context)
        cached (when key (support/timed "cache-lookup"
                                        #(compile-cache/lookup! artifact-cache key)))]
    (if cached
      (let [bytes (:bytes cached)
            artifact-valid? (support/timed
                             "cache-artifact-integrity"
                             #(= (:sha256 cached) (compile-cache/sha256 bytes)))
            provenance-valid? (support/timed
                               "cache-provenance-integrity"
                               #(= (:provenance-sha256 cached)
                                   (compile-cache/sha256
                                    (:provenance-text cached))))]
        (when-not (and artifact-valid? provenance-valid?)
          (compile-cache/remove! artifact-cache key)
          (throw (ex-info "compiler cache integrity mismatch" {:cache-key key})))
        (let [{:keys [provenance-output publication-output]}
              (write-wasm! output cached)]
          {:ok true :target target :output output
           :provenance-output provenance-output
           :publication-output publication-output
           :cache :hit :cache-key key
           ;; A whole-artifact hit re-emitted nothing.
           :definitions-recompiled 0}))
      (let [_ (when-let [error (:error policy-attempt)] (throw error))
            policy (support/timed "policy-decode"
                                  #(decode-policy! material))
            hir-result (resolve-hir! source (project-source/analyze-opts policy linked?) stage-cache)
            hir (:value hir-result)
            admission-result (support/timed
                              "admission"
                              #(effect-row/check hir
                                                (support/capability-policy policy)))
            kir-result (resolve-kir! hir stage-cache)
            kir (:value kir-result)
            compiled (compile-wasm! source target policy emit-metadata
                                    hir admission-result kir
                                    {:stage-cache stage-cache
                                     :policy-material material
                                     :lock-cid lock-cid})
            serialized (serialized-wasm (:result compiled))
            bytes (:bytes serialized)
            {:keys [provenance-output publication-output]}
            (write-wasm! output serialized)
            sealed (assoc serialized
                          :sha256 (compile-cache/sha256 bytes)
                          :provenance-sha256
                          (compile-cache/sha256 (:provenance-text serialized)))
            size (+ (.-length bytes)
                    (.byteLength js/Buffer (:provenance-text serialized) "utf8"))]
        (support/timed "cache-store"
                       #(compile-cache/put! artifact-cache key sealed size))
        {:ok true :target target :output output
         :provenance-output provenance-output
         :publication-output publication-output
         :cache :miss :cache-key key
         :definitions-recompiled (definitions-recompiled (:material compiled)
                                                         (:emit compiled))
         ;; The emission stage's key, in the answer. Reported because the
         ;; interesting claim about a cache is not "it remembered" but "the key
         ;; says what the compiler thinks it says": two compiles that must
         ;; share an artifact have to share this string, and two that must not
         ;; have to differ in it. A test that can only observe hit/miss cannot
         ;; tell a correct key from a fresh cache.
         :emit-cache-key (:cache-key (:emit compiled))
         :stage-cache (stage-status hir-result kir-result (:emit compiled))}))))

(defn- compile! [args context]
  (let [target-name (or (support/option args "--target") "wasm32")
        target (get targets target-name)
        _ (when-not target
            (support/usage-error!
             (str "error: nbb Wasm path does not cover target " target-name)))
        _ (when (and context (not= target (:target context)))
            (support/usage-error!
             (str "error: worker is locked to target " (name (:target context)))))
        backend (target-profile/backend target)
        _ (when-not (= :wasm32-kotoba-v1 backend)
            (support/usage-error! (str "error: target is not Wasm: " target-name)))
        resolved (project-source/resolve-source! args)
        input (:input resolved)
        source (:source resolved)
        linked? (:linked? resolved)
        output (or (support/option args "--output") (str input ".wasm"))
        lock-cid (get-in resolved [:lock :lock-cid])
        ;; Before anything is emitted. The compile paths below may answer from
        ;; a stage cache, so the package verdict cannot ride along with
        ;; whichever HIR they happen to compute; when a lock was consumed this
        ;; runs the frontend once for the sole purpose of checking it. Nothing
        ;; is paid when no lock was given.
        packages (when (:packages resolved)
                   (let [policy (support/timed "policy-read" #(read-policy! args))
                         hir (:value (resolve-hir!
                                      source
                                      (project-source/analyze-opts policy linked?)
                                      nil))]
                     (package-verdict!
                      resolved
                      (definition-identity/describe
                       {:hir hir
                        :kir (try (ir/lower hir) (catch :default _ nil))}))))
        result (if context
                 (compile-cached! args target output source linked? context lock-cid)
                 (compile-uncached! args target output source linked? lock-cid))]
    (cond-> (merge result (project-source/inputs-record resolved))
      packages (assoc :packages packages))))

(defn- module-lock!
  "Pin a path-resolved project once so every later compile of it resolves by
  CID instead of by whatever is on disk.

  Producing the lock and consuming it are separate commands on purpose, and
  until now they were on separate RUNTIMES too -- production was JVM-only, so
  a JDK-free consumer still needed a JDK somewhere upstream to get its lock.
  That is the whole of the Q9 objection, moved rather than removed, so both
  halves run here."
  [args]
  (let [input (support/timed "source-admit" #(support/source! (second args)))
        ;; The same roots `check` and `compile` get: a lock pinned without the
        ;; dependencies would name a graph the entry cannot actually link.
        resolved-packages (project-source/resolve-packages! args)
        source-roots (project-source/source-roots args resolved-packages)
        blocks (or (support/option args "--blocks")
                   (support/usage-error! "--blocks <dir> is required"))
        _ (when (empty? source-roots)
            (support/usage-error! "--source-path is required"))
        ;; The JVM twin's default is the bare name `kotoba.modules.edn`,
        ;; resolved against the process CWD -- which, through `bin/amu`, is
        ;; the Amu checkout rather than the caller's directory. Anchoring it
        ;; to the entry module puts the lock beside the sources it pins. Pass
        ;; --output to say otherwise.
        output (or (support/option args "--output")
                   (.join node-path (.dirname node-path input) "kotoba.modules.edn"))
        lock (support/timed
              "module-lock-derive"
              #(module-lock/lock-from-source-paths
                project-files/load-closed-graph input source-roots blocks))]
    (support/timed "module-lock-write"
                   #(io/write-text! output (pr-str (dissoc lock :lock-cid))))
    (cond-> {:ok true :lock output :blocks blocks
             :root (:root lock) :modules (count (:modules lock))
             :lock-cid (:lock-cid lock)}
      resolved-packages
      (assoc :packages (package-lock/report resolved-packages
                                            {:check :definition-cids
                                             :outcome :deferred
                                             :note (str "a module lock pins every "
                                                        "module by CID; definition "
                                                        "identity is checked when it "
                                                        "is compiled")})))))

(defn- describe-definition-cids
  "The definition CIDs of one module file, for the lock producer. Reads the
  file alone rather than as part of a project: definition identity is
  context-independent -- measured 2026-09-06, `double-it` hashes to the same
  CID compiled alone and as a dependency of another module -- which is the
  property that lets a lock pin them at all."
  [path]
  (let [source (io/read-text-file path)
        hir (:value (resolve-hir! source (support/analyze-options nil) nil))
        report (definition-identity/describe
                {:hir hir :kir (try (ir/lower hir) (catch :default _ nil))})]
    (when-not (map? (:entries report))
      (throw (ex-info "no definition identity is available for this module"
                      {:phase :definition-identity :module path
                       :reason (:reason report)})))
    (keep :cid (vals (:entries report)))))

(defn- run! [args context]
  (case (first args)
    "check" (check! args context)
    "definition-cids" (definition-cids! args)
    "compile" (compile! args context)
    ;; `bin/amu` routes every target-less command here, the same way `check`
    ;; arrives. A lock is target-independent, so there is nothing for the
    ;; native entry points to answer differently.
    "module-lock" (module-lock! args)
    ;; Authoring, not building. `package-manifest` needs no resolver at all;
    ;; `package-lock` reads each dependency's own signed manifest and asks
    ;; this compiler for the definition CIDs, so the values it writes are
    ;; measured rather than declared.
    "package-fetch" (package-authoring/package-fetch! args)
    "package-manifest" (package-authoring/package-manifest! args)
    "package-lock" (package-authoring/package-lock! describe-definition-cids args)
    (support/usage-error!
     (str "error: nbb Wasm path does not cover command " (first args)))))

(if (= "worker" (first *command-line-args*))
  (let [target-name (or (support/option *command-line-args* "--target") "wasm32")
        target (get targets target-name)
        _ (when-not target
            (support/usage-error!
             (str "error: nbb Wasm worker does not cover target " target-name)))
        context (assoc (compile-cache/create-context) :target target)]
    (support/serve! #(run! % context) target))
  (support/execute! #(run! % nil)))
