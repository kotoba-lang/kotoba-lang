(ns kotoba.compiler.nbb.cli
  "Shared ordinary-native CLI implementation. ISA-specific executable
  entrypoints inject exactly one emitter, so compiling AArch64 never loads
  x86-64 code and vice versa."
  (:require [cljs.reader :as reader]
            [clojure.walk :as walk]
            [kotoba.compiler.capability-names :as cap-names]
            [kotoba.compiler.nbb.cli-support :as support]
            [kotoba.compiler.nbb.compile-cache :as compile-cache]
            [kotoba.sema :as sema]
            [kotoba.compiler.nbb.io :as io]
            [kotoba.compiler.nbb.output-set :as output-set]
            [kotoba.compiler.nbb.project-source :as project-source]
            [kotoba.compiler.effect-row :as effect-row]
            [kotoba.artifact.core :as artifact]
            [kotoba.kir.compatibility :as compatibility]
            [kotoba.kir.cljs-i64 :as i64]
            [kotoba.kir :as ir]
            [kotoba.compiler.provenance :as provenance]
            [kotoba.compiler.uefi-operations :as uefi]
            [kotoba.kir.target :as target-profile]
            [kotoba.verifier :as verifier]))

;; Mirrors `kotoba.compiler.cli`'s `parse-target`, restricted to ORDINARY
;; (non-aiueos) native target names -- everything this fast
;; path claims. The `x86_64-aiueos-*`/`aarch64-aiueos-*` firmware/kernel
;; sub-targets (ELF64/PE32+ packaging via `kotoba.compiler.packaging.*`,
;; only reachable through `clojure -M:run compile` directly, not this
;; usage-documented list) stay JVM-only -- out of scope here, same
;; boundary `kotoba.compiler.core/compile-source*`'s own `cond->` draws
;; between the sealed `:kexe/v1` artifact (every ordinary native target)
;; and the extra `:binary`/`:object` packaging (aiueos targets only).
;; Every other name in the JVM `parse-target` table falls through to the
;; JVM path in `bin/kotoba` before this script is ever spawned.
(def targets
  {"x86_64" :x86_64-kotoba-v1
   "x86_64-linux" :x86_64-linux-kotoba-v1
   "x86_64-macos" :x86_64-macos-kotoba-v1
   "x86_64-windows" :x86_64-windows-kotoba-v1
   "aarch64" :aarch64-kotoba-v1
   "aarch64-linux" :aarch64-linux-kotoba-v1
   "aarch64-macos" :aarch64-macos-kotoba-v1
   "aarch64-windows" :aarch64-windows-kotoba-v1
   "aarch64-android" :aarch64-android-kotoba-v1
   "aarch64-ios" :aarch64-ios-kotoba-v1
   ;; The aiueos profiles reach the same two ISA emitters as the entries above
   ;; -- `target/backend` maps every one of them to :x86_64-kotoba-v1 or
   ;; :aarch64-kotoba-v1 -- so nothing about code generation kept them off this
   ;; route. What kept them off was that this driver had no packaging step, and
   ;; admitting a kernel target without one would have written artifact EDN to
   ;; a path named `.o`: a silent wrong answer rather than a refusal. With
   ;; `kotoba.compiler.nbb.native-package` supplying the same ELF64/PE32+
   ;; packagers the JVM CLI calls, the object build no longer needs a JDK.
   "x86_64-aiueos-kernel-v1" :x86_64-aiueos-kernel-v1
   "x86_64-aiueos-user-v1" :x86_64-aiueos-user-v1
   "x86_64-aiueos-uefi-v1" :x86_64-aiueos-uefi-v1
   "aarch64-aiueos-kernel-v1" :aarch64-aiueos-kernel-v1})

;; fuel64: the same ceiling `kotoba.verifier` enforces, which is
;; `kotoba.kir/max-fuel` -- read rather than copied, because a JVM-free route
;; that admitted a different budget from the verifier would produce artifacts
;; the verifier then refused, and the diagnostic would name the artifact rather
;; than the disagreement. 2^20 until 2026-09-03; see the ADR.
(def ^:private max-native-fuel ir/max-fuel)


(defn native-value-abi
  "The value ABI stamped into a native artifact's compatibility descriptor.

  This mirrored only the last two branches. `kotoba.compiler.core` (the JVM
  compiler), `kotoba.compiler.nbb.wasm-cli` and `kotoba.verifier` all select
  from FOUR, so any program carrying floating point compiled through this
  JDK-free driver stamped `:kotoba.typed/externref-v1` while the verifier
  re-derived `:kotoba.typed/mixed-f64-v2` and refused the artifact with
  `native compatibility metadata rejected` -- a diagnostic that names the
  metadata rather than the missing branch, so it reads as an unsupported
  program rather than a driver that cannot describe it.

  Measured 2026-08-30: a `vector-f64` module reached this path and was refused
  exactly so. It is not vector-specific; `uses-f64?` is true for any f64
  literal, since the frontend desugars one into `f64-from-bits`.

  Derived from the KIR rather than the HIR because the KIR is what the
  verifier reads back out of the sealed artifact. `kotoba.compiler.core`
  derives the same answer from the HIR; deriving it here from the value the
  checker actually inspects removes the possibility of the two disagreeing."
  [kir typed-values?]
  (cond
    (ir/uses-f32? kir) :kotoba.typed/mixed-f32-f64-v3
    (ir/uses-f64? kir) :kotoba.typed/mixed-f64-v2
    typed-values? :kotoba.typed/externref-v1
    :else :kotoba.i64/direct-v1))

(defn- native-fuel! [policy]
  (let [declared (or (get-in policy [:budgets :fuel]) 512)
        ;; Bounded EDN preserves integer literals as BigInt on the Node path.
        ;; The native ABI and KIR oracle use plain host integers. Converting
        ;; only after this bound check is exact in JavaScript and preserves the
        ;; original policy value for provenance hashing.
        ;;
        ;; fuel64: `Number.isSafeInteger` was ALREADY enforcing the ceiling
        ;; this stream went on to choose. It is `<= 2^53-1` by definition, so
        ;; the JVM-free route has refused anything the KIR counter could not
        ;; decrement since the day it was written -- the `max-native-fuel` test
        ;; beside it (2^20) was simply four hundred million times tighter and
        ;; hid it. The ceiling is not an invention; it is the number this line
        ;; already implemented, now written down where the other routes can
        ;; read it.
        fuel (if (i64/bigint-value? declared) (js/Number declared) declared)]
    (when-not (and (js/Number.isSafeInteger fuel)
                   (<= 1 fuel max-native-fuel))
      (throw (ex-info "native fuel budget is not admitted"
                      {:phase :verify :fuel declared
                       :maximum max-native-fuel})))
    fuel))

(defn- fuel-policy!
  "The policy with `--fuel` folded into `[:budgets :fuel]` -- after refusing
  the case where both were given and disagree. Mirrors
  `kotoba.compiler.core/refuse-fuel-declared-twice!` (amu-h6): the flag used
  to overwrite the policy's number here silently, so the policy's author never
  learned theirs was not the one sealed.

  Compared as decimal text: the flag is a BigInt (`support/emit-metadata`) and
  the policy value is a BigInt or a plain integer depending on how it was
  read, and `=` does not cross that boundary."
  [policy emit-metadata]
  (let [flag (:fuel emit-metadata)
        from-policy (get-in policy [:budgets :fuel])]
    (when (and (some? flag) (some? from-policy) (not= (str flag) (str from-policy)))
      (throw (ex-info "fuel declared twice with different values: --fuel and the policy's :budgets :fuel must agree"
                      {:phase :usage :reason :fuel-declared-twice
                       :flag flag :policy from-policy})))
    (cond-> policy
      (some? flag) (assoc-in [:budgets :fuel] flag))))

(defn- read-policy!
  "`--policy`, with named grants canonicalised to wire ids. Same seam and same
  reason as `kotoba.compiler.nbb.wasm-cli/read-policy!`."
  [args]
  (cap-names/wire-policy (support/read-policy args)))

(defn- decode-policy!
  "The cached-compile counterpart of `read-policy!`, for already-read material."
  [material]
  (cap-names/wire-policy (support/parse-policy-material material)))

;; Mirrors `kotoba.compiler.core/compile-source*`'s `:else` (native) branch
;; byte-for-byte -- same sealed `:kotoba.kexe/v1` shape, same
;; `fuel-abi`/`context-abi`/`limits` constants, same pre-checks -- so a
;; `.kexe` produced here and one produced by `clojure -M:run compile` for
;; the identical source/target/policy verify identically against either
;; artifact's own `:sha256` seal and against `verifier/verify-artifact!`.
;; Deliberately does NOT replicate the `x86_64-aiueos-*`/`aarch64-aiueos-*`
;; `:binary`/`:object` packaging step (see `targets`' own comment above for
;; why that stays out of scope).
(defn- resolve-hir! [source opts stage-cache]
  (support/timed "frontend"
                 #(compile-cache/resolve-stage!
                   stage-cache :hir
                   ;; The whole options map, not just :language-profile. A
                   ;; linked project carries :admit-linked-synthetics?, and a
                   ;; key blind to it would answer a linked question with an
                   ;; unlinked HIR -- inside one worker process, where the two
                   ;; kinds of compile share a stage cache. Tag raised to v3 so
                   ;; entries written under the old key miss rather than being
                   ;; read under the new meaning.
                   (pr-str [:kotoba.hir-cache/v3 source opts])
                   (fn [] (sema/analyze source opts)))))

(defn- resolve-kir! [hir stage-cache]
  (support/timed "kir-lower"
                 #(compile-cache/resolve-stage!
                   ;; Native oracle evaluation is reusable only when the
                   ;; admitted HIR is byte-for-byte semantically identical.
                   stage-cache :kir (pr-str hir) (fn [] (ir/lower hir)))))

(defn- compile-native! [hir target backend policy emit-metadata emit-program stage-cache]
  (when (and (= :kotoba.hir/v3 (:format hir))
             (not (and (contains? #{:x86_64-kotoba-v1 :aarch64-kotoba-v1} backend)
                       (ir/only-native-word-typed-features? hir))))
    (throw (ex-info "typed values currently require the kotoba-script web target, typed Wasm target, or qualified native string/scalar-record/option-i64/result-i64 features"
                    {:phase :target :target target :backend backend
                     :value-profile :kotoba.value/typed-v1})))
  ;; Keep the JDK-free self-hosted driver aligned with the JVM compiler:
  ;; ordinary native artifacts are exportable libraries when their target
  ;; profile does not require an entry symbol. Aiueos firmware/kernel/process
  ;; profiles still fail closed because their declared entry must exist.
  (when (and (nil? (:entry hir))
             (not (and (contains? #{:x86_64-kotoba-v1 :aarch64-kotoba-v1} backend)
                       (nil? (:entry (target-profile/profile target))))))
    (throw (ex-info "entryless libraries currently require the kotoba-script web target, the Wasm target, or an entryless native target"
                    {:phase :target :target target :backend backend})))
  ;; boot: the same target gate the JVM route applies. A gate on one route is
  ;; not a gate -- this driver serves `--target x86_64-aiueos-uefi-v1
  ;; --artifact image` and would otherwise be the way around it.
  (uefi/reject-outside-uefi-target! target hir)
  ;; boot-lit: and the literal pool. Gated on both routes, for the reason the
  ;; line above is gated on both.
  (uefi/reject-rodata-literals-outside-native-targets! target hir)
  (let [admission (support/timed
                   "admission"
                   #(effect-row/check hir (support/capability-policy policy)))
        kir-result (resolve-kir! hir stage-cache)
        kir (:value kir-result)
        value (:oracle-value kir)
        profile (target-profile/profile target)
        typed-values? (= :kotoba.kir/v4 (:format kir))
        value-abi (native-value-abi kir typed-values?)
        compat (compatibility/descriptor
                {:hir-format (:format hir) :kir-format (:format kir)
                 :target target :target-profile profile :value-abi value-abi})
        program (select-keys kir [:format :entry :exports :signature :effects :functions])
        declared-fuel (native-fuel! (fuel-policy! policy emit-metadata))
        ;; Verification re-emits from this closed program. Do not let
        ;; compiler-private KIR metadata influence the bytes being sealed.
        emitted (support/timed "native-emit" #(emit-program program))
        code (:code emitted)
        artifact-map
        (support/timed
         "artifact-seal"
         (fn []
           (artifact/seal
            {:format :kotoba.kexe/v1 :target target :target-profile profile :value value
             :kir-sha256 (artifact/sha256 program)
             :lowering (case backend
                         :x86_64-kotoba-v1 :runtime-sysv-v1
                         :aarch64-kotoba-v1 :runtime-aapcs64-v1)
             :fuel-abi (case backend
                         :x86_64-kotoba-v1
                         {:mode :hidden-context-r9 :initial declared-fuel}
                         :aarch64-kotoba-v1
                         {:mode :hidden-context-x7 :initial declared-fuel})
             :context-abi {:version 4 :fuel-offset 8 :allow-bitmap-offset 16
                           :allow-bitmap-bytes 32 :cap-call-offset 48
                           :pair-new-offset 56 :pair-first-offset 64
                           :pair-second-offset 72 :pair-capacity 4096
                           :kgraph-assert-offset 80 :kgraph-get-offset 88
                           :kgraph-count-offset 96 :kgraph-entity-at-offset 104
                           :kgraph-capacity 4096
                           :string-equal-offset 112 :string-concat-offset 120
                           :typed-cap-call-offset 128
                           :string-substring-offset 136
                           :string-code-point-at-offset 144
                           :string-pool-capacity 65536
                           :vector-new-empty-offset 152
                           :vector-conj-offset 160
                           :vector-count-offset 168
                           :vector-at-offset 176
                           :vector-assoc-offset 184
                           :vector-drop-offset 192
                           :vector-alloc-offset 200
                           :vector-assoc-in-place-offset 208
                           :vector-capacity 4096
                           :vector-item-capacity 65536}
             :effects (:effects hir)
             :compatibility compat
             :limits {:memory-bytes 65536 :fuel declared-fuel :stack-bytes 4096}
             :code (mapv #(bit-and (int %) 0xff) code)
             :program program :exports (:exports emitted)})))]
    (support/timed "native-verify" #(verifier/verify-artifact! artifact-map))
    {:format :kexe/v1 :target target :hir hir :kir kir
     :admission admission :artifact artifact-map :compatibility compat
     :stage-cache {:kir (:cache kir-result)}}))

(defn- serialized-native [source policy emit-metadata result package artifact-kind]
  (let [provenance-result (support/timed "provenance"
                                         #(provenance/attach source policy emit-metadata result))]
    (cond-> {:artifact-text (support/timed
                             "artifact-serialize"
                             #(pr-str (artifact/edn-safe (:artifact provenance-result))))
             :provenance-text (support/timed
                               "provenance-serialize"
                               #(pr-str (artifact/edn-safe (:provenance provenance-result))))}
      ;; Packaged from the sealed artifact map, never from the EDN text above.
      ;; `artifact/edn-safe` prints an i64 as a plain integer token, so text
      ;; that round-trips is not the same value -- packaging the reparse would
      ;; be packaging a lossy copy of what was verified.
      package (assoc :binary (support/timed
                              "native-package"
                              #(package (:target result) (:artifact result)
                                        artifact-kind))))))

(defn- write-native! [output {:keys [artifact-text provenance-text binary]}]
  (let [provenance-output (str output ".provenance.edn")
        publication-output (str output ".publication.edn")
        ;; When a target packages, `--output` holds the ELF64/PE32+ bytes and
        ;; the artifact EDN is not published at all -- the JVM CLI's behaviour
        ;; exactly. The output set therefore identifies what was written, not
        ;; what it was derived from; `output-set/descriptor` hashes a Buffer
        ;; and a string the same way, and `verify-output-set` already reads
        ;; every member as bytes, so neither end needed a binary special case.
        published (or binary artifact-text)
        publication-text (output-set/serialize output published provenance-text)]
    (support/timed
     "output-set-write"
     #(io/write-set! [(if binary
                        {:path output :bytes binary}
                        {:path output :text artifact-text})
                      {:path provenance-output :text provenance-text}
                      {:path publication-output :text publication-text}]))
    (cond-> {:provenance-output provenance-output
             :publication-output publication-output}
      binary (assoc :artifact-bytes (.-length binary)))))

(defn- compile-uncached! [args source linked? target backend output emit-program package]
  (let [policy (support/timed "policy-read" #(read-policy! args))
        emit-metadata (support/emit-metadata args)
        artifact-kind (support/option args "--artifact")
        hir (:value (resolve-hir! source
                                  (project-source/analyze-opts policy linked?) nil))
        result (compile-native! hir target backend policy emit-metadata emit-program nil)
        serialized (serialized-native source policy emit-metadata result
                                      package artifact-kind)
        {:keys [provenance-output publication-output artifact-bytes]}
        (write-native! output serialized)]
    (cond-> {:ok true :target target :output output
             :provenance-output provenance-output
             :publication-output publication-output}
      artifact-bytes (assoc :artifact-bytes artifact-bytes))))

(defn- compile-cached! [args source linked? target backend output emit-program package context]
  (let [policy-attempt (support/timed
                        "policy-read"
                        #(try {:material (support/read-policy-material args)}
                              (catch :default error {:error error})))
        material (:material policy-attempt)
        emit-metadata (support/emit-metadata args)
        artifact-kind (support/option args "--artifact")
        key (when material
              (support/timed "cache-key"
                             #(compile-cache/key-for target source material
                                                     emit-metadata linked?
                                                     artifact-kind)))
        artifact-cache (:artifacts context)
        stage-cache (:stages context)
        cached (when key (support/timed "cache-lookup"
                                        #(compile-cache/lookup! artifact-cache key)))]
    (if cached
      (let [artifact-valid? (support/timed
                             "cache-artifact-integrity"
                             #(= (:artifact-sha256 cached)
                                 (compile-cache/sha256 (:artifact-text cached))))
            provenance-valid? (support/timed
                               "cache-provenance-integrity"
                               #(= (:provenance-sha256 cached)
                                   (compile-cache/sha256 (:provenance-text cached))))
            ;; A packaged target's `--output` is the ELF64/PE32+ bytes, so a
            ;; hit that restored only the two EDN texts would republish the
            ;; artifact EDN under a path named `.o`. Cached base64 is checked
            ;; on the same footing as the texts: a cache that cannot prove
            ;; what it holds is evicted, never served short.
            binary-valid? (support/timed
                           "cache-binary-integrity"
                           #(or (nil? (:binary-base64 cached))
                                (= (:binary-sha256 cached)
                                   (compile-cache/sha256 (:binary-base64 cached)))))]
        (when-not (and artifact-valid? provenance-valid? binary-valid?)
          (compile-cache/remove! artifact-cache key)
          (throw (ex-info "compiler cache integrity mismatch" {:cache-key key})))
        (let [restored (cond-> cached
                         (:binary-base64 cached)
                         (assoc :binary (js/Buffer.from (:binary-base64 cached) "base64")))
              {:keys [provenance-output publication-output artifact-bytes]}
              (write-native! output restored)]
          (cond-> {:ok true :target target :output output
                   :provenance-output provenance-output
                   :publication-output publication-output
                   :cache :hit :cache-key key}
            artifact-bytes (assoc :artifact-bytes artifact-bytes))))
      (let [_ (when-let [error (:error policy-attempt)] (throw error))
            policy (support/timed "policy-decode"
                                  #(decode-policy! material))
            hir-result (resolve-hir! source
                                     (project-source/analyze-opts policy linked?)
                                     stage-cache)
            hir (:value hir-result)
            result (compile-native! hir target backend policy emit-metadata
                                    emit-program stage-cache)
            serialized (serialized-native source policy emit-metadata result
                                          package artifact-kind)
            {:keys [provenance-output publication-output artifact-bytes]}
            (write-native! output serialized)
            binary-base64 (some-> (:binary serialized) (.toString "base64"))
            sealed (cond-> (assoc (dissoc serialized :binary)
                                  :artifact-sha256 (compile-cache/sha256 (:artifact-text serialized))
                                  :provenance-sha256 (compile-cache/sha256 (:provenance-text serialized)))
                     binary-base64 (assoc :binary-base64 binary-base64
                                          :binary-sha256 (compile-cache/sha256 binary-base64)))
            size (+ (.byteLength js/Buffer (:artifact-text serialized) "utf8")
                    (.byteLength js/Buffer (:provenance-text serialized) "utf8")
                    (if binary-base64
                      (.byteLength js/Buffer binary-base64 "utf8")
                      0))]
        (support/timed "cache-store" #(compile-cache/put! artifact-cache key sealed size))
        (cond-> {:ok true :target target :output output
                 :provenance-output provenance-output
                 :publication-output publication-output
                 :cache :miss :cache-key key
                 :stage-cache {:hir (:cache hir-result)
                               :kir (get-in result [:stage-cache :kir])}}
          artifact-bytes (assoc :artifact-bytes artifact-bytes))))))

(defn run!
  "`package` is the ISA entrypoint's artifact packager (or nil for a driver
  that has none). Supplied rather than required here so the Wasm driver, which
  shares this namespace, does not load two native packagers it never calls."
  [args expected-backend emit-program package context]
  (case (first args)
    "compile"
    (let [target-name (support/option args "--target")
            target (get targets target-name)
            _ (when-not target
                (support/usage-error!
                 (str "error: nbb native path does not cover target " target-name)))
            _ (when (and context (not= target (:target context)))
                (support/usage-error!
                 (str "error: worker is locked to target " (name (:target context)))))
            backend (target-profile/backend target)
            _ (when-not (= expected-backend backend)
                (support/usage-error!
                 (str "error: target " target-name " does not match loaded native ISA")))
            ;; Rejected before any compilation, matching the JVM CLI: an
            ;; unknown `--artifact` must not be discovered after the work.
            _ (when-not (contains? #{nil "object" "image"} (support/option args "--artifact"))
                (throw (ex-info "unknown native artifact kind"
                                {:phase :artifact-target
                                 :artifact (support/option args "--artifact")})))
            ;; A module declaring `(:require ...)` is resolved and linked the
            ;; same way the Wasm driver resolves one, through the same shared
            ;; `project-source/resolve-source!`. This used to read the entry
            ;; file alone: `--source-path` and `--module-lock` were accepted on
            ;; the command line and never looked at, so a kernel object could
            ;; not import anything and every helper had to be copied into every
            ;; source that needed it.
            resolved (project-source/resolve-source! args)
            input (:input resolved)
            source (:source resolved)
            linked? (:linked? resolved)
            output (or (support/option args "--output") (str input ".kexe"))
            result (try
                     (if context
                       (compile-cached! args source linked? target backend output
                                        emit-program package context)
                       (compile-uncached! args source linked? target backend output
                                          emit-program package))
                     ;; A refusal against the linked unit names the module
                     ;; and line that wrote the form (amu-h10), as on the
                     ;; JVM route's `compile-project`.
                     (catch :default error
                       (throw (project-source/attribute-error error resolved))))]
        (merge result (project-source/inputs-record resolved)))

    "extract-native"
    (let [input (second args)
          serialized (reader/read-string (io/read-text-file input))
          ;; EDN has no i64 type marker: the writer deliberately prints an
          ;; nbb bigint as the same plain integer token the JVM writes. Restore
          ;; the oracle value boundary before the CLJS verifier re-executes the
          ;; entry; structural metadata and machine bytes remain JS numbers.
          artifact-map (update serialized :value
                               #(walk/postwalk (fn [x]
                                                 (if (integer? x) (i64/->bigint x) x))
                                               %))
          symbol (symbol (or (support/option args "--symbol") "main"))
          output (or (support/option args "--output") "program.bin")
          _ (verifier/verify-artifact! artifact-map)
          export (get (:exports artifact-map) symbol)]
      (when-not export
        (throw (ex-info "unknown native export" {:phase :verify :entry symbol})))
      (io/write-bytes! output (js/Buffer.from (clj->js (:code artifact-map))))
      (merge {:ok true :output output :symbol symbol} export))

    (support/usage-error!
     (str "error: nbb native path does not cover command " (first args)))))
