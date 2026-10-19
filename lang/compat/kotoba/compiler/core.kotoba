(ns kotoba.compiler.core
  (:require [clojure.walk :as walk]
            [kotoba.compiler.capability-names :as cap-names]
            [kotoba.sema :as sema]
            [kotoba.kir.compatibility :as compatibility]
            [kotoba.compiler.provenance :as provenance]
            [kotoba.compiler.uefi-operations :as uefi]
            [kotoba.compiler.ipld-adl-source :as ipld-adl-source]
            [kotoba.compiler.cache :as cache]
            [kotoba.compiler.project :as project]
            [kotoba.kir :as ir]
            [kotoba.compiler.effect-row :as effect-row]
            [kotoba.wasm.core :as wasm]
            [kotoba.wasm.typed :as typed]
            [kotoba.component.wit :as component-wit]
            [kotoba.component.artifact :as component-artifact]
            [kotoba.component.core :as component-core]
            [kotoba.component.admission :as component-admission]
            [kotoba.abi.contract :as abi]
            [kotoba.compiler.backend.cljs :as cljs]
            [kotoba.compiler.backend.evm :as evm]
            [kotoba.script :as script]
            [kotoba.native.x86-64 :as x86-64]
            [kotoba.native.aarch64 :as aarch64]
            [kotoba.native.aggregate-abi :as aggregate-abi]
            [kotoba.native.elf64 :as elf64]
            [kotoba.compiler.packaging.pe32plus :as pe32plus]
            [kotoba.artifact.core :as artifact]
            [kotoba.kir.target :as target-profile]
            [kotoba.verifier :as verifier])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]))

(def compiler-version compatibility/compiler-version)
(def floating-point-policy :kotoba.floating-point/ieee-754-f32-f64-v7)

(defn- text-sha256 [text]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes ^String text StandardCharsets/UTF_8))]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) digest))))

(def targets target-profile/compatibility-targets)
(def supported-targets (set (keys target-profile/profiles)))

(def source-compilable-targets
  "Targets `compile-source` emits directly.

  A component target is deliberately excluded: a component is a core module
  lifted through the Canonical ABI by `compile-component`, not a backend
  output. Callers that fan out over every target (conformance sweeps, policy
  seal checks) want this set; `supported-targets` remains every declared
  target, so `--target component` still validates as a real target name."
  (into #{} (remove #(= :component (:execution (target-profile/profile %))))
        supported-targets))

;; Native (x86_64/aarch64) targets admit the string slice of "typed values"
;; (string literals + string-byte-length/string=?/string-concat,
;; ADR-2607198300 follow-up) PLUS -- as of the first native record increment
;; (ADR 0062) -- a sealed, all-scalar (`:i64`/`:bool` fields only)
;; `record-new`/`record-get` pair, used only in the one exact nested
;; construction+projection shape `backend/x86-64.cljc`/`backend/aarch64.cljc`
;; implement, PLUS -- as of the second native increment (ADR 0063) -- a
;; sealed, all-scalar-cased `variant-new`/`variant-match` pair, used only in
;; the analogous one exact nested construction+dispatch shape those same two
;; files implement, PLUS sealed typed capability boundaries for i64, bounded
;; UTF-8 strings, `:option-i64`, and `:result-i64`. The structured callback
;; validates pair-backed string pointer/length handles or canonical tagged
;; `(tag,payload)` handles on both sides. Every other typed feature
;; (generic options/results, general/nested/escaping records or variants,
;; typed maps/vectors/sets) has zero native backend codegen and
;; must keep producing the same "requires kotoba-script web target"
;; rejection it always has. This is a content check, not a blanket
;; per-backend allowance:
;; :kotoba.hir/v3 covers ALL typed features uniformly, so admitting the
;; format for native without inspecting which features are actually used
;; would silently let unsupported ops reach the backend and crash confusingly
;; instead of rejecting cleanly. Shared with `kotoba.compiler.nbb.cli` (the
;; nbb-native fast path) via
;; `kotoba.kir/only-native-word-typed-features?` so both
;; compile paths admit the exact same native-typed-feature subset.

(def ^:private cljs-bounded-vector-ops
  '#{vector-new vector-count vector-get vector-at vector-drop vector-take vector-assoc vector-conj
     hetero-vector-new hetero-vector-count hetero-vector-at
     hetero-vector-assoc hetero-vector-equal})

(defn- cljs-bounded-vector-type?
  "The closed typed-value universe implemented by backend.cljs for pure
  collection code.  This intentionally excludes every provider/document/
  record/map/set family; widening the CLJS target is permitted only when both
  its boundary validator and expression lowerer own the representation."
  ([type] (cljs-bounded-vector-type? type 0))
  ([type depth]
   (and (<= depth 8)
        (or (contains? #{:i64 :bool :vector-i64} type)
            (and (vector? type)
                 (= :vector (first type))
                 (= 2 (count type))
                 (vector? (second type))
                 (<= (count (second type)) 32)
                 (every? #(cljs-bounded-vector-type? % (inc depth))
                         (second type)))))))

(defn- only-cljs-bounded-vector-features?
  "Admit only the typed vector slice for which backend.cljs has exact
  lowering.  Generic scalar control flow and named calls remain available;
  any other typed operation stays fail-closed through ir/non-string-typed-ops."
  [hir]
  (letfn [(walk [form]
            (cond
              (or (integer? form) (symbol? form) (boolean? form)) true
              (or (nil? form) (string? form) (keyword? form)) false
              (seq? form)
              (let [[op & args] form]
                (cond
                  (= op 'let)
                  (let [[bindings body] args]
                    (and (= 2 (count args))
                         (vector? bindings)
                         (even? (count bindings))
                         (every? symbol? (take-nth 2 bindings))
                         (every? walk (take-nth 2 (rest bindings)))
                         (walk body)))

                  (= op 'hetero-vector-new)
                  (let [[type & items] args]
                    (and (cljs-bounded-vector-type? type)
                         (= :vector (first type))
                         (= (count (second type)) (count items))
                         (every? walk items)))

                  (= op 'hetero-vector-count)
                  (let [[type value] args]
                    (and (= 2 (count args))
                         (cljs-bounded-vector-type? type)
                         (= :vector (first type))
                         (walk value)))

                  (= op 'hetero-vector-at)
                  (let [[type value index] args]
                    (and (= 3 (count args))
                         (cljs-bounded-vector-type? type)
                         (= :vector (first type))
                         (integer? index)
                         (<= 0 index)
                         (< index (count (second type)))
                         (walk value)))

                  (= op 'hetero-vector-assoc)
                  (let [[type value index item] args]
                    (and (= 4 (count args))
                         (cljs-bounded-vector-type? type)
                         (= :vector (first type))
                         (integer? index)
                         (<= 0 index)
                         (< index (count (second type)))
                         (walk value)
                         (walk item)))

                  (= op 'hetero-vector-equal)
                  (let [[type left right] args]
                    (and (= 3 (count args))
                         (cljs-bounded-vector-type? type)
                         (= :vector (first type))
                         (walk left)
                         (walk right)))

                  (contains? cljs-bounded-vector-ops op)
                  (every? walk args)

                  :else
                  (and (not (contains? ir/non-string-typed-ops op))
                       (every? walk args))))
              :else false))]
    (every? (fn [{:keys [param-types result body]}]
              (and (every? cljs-bounded-vector-type? param-types)
                   (cljs-bounded-vector-type? result)
                   (walk body)))
            (:functions hir))))

(defn- only-cljs-implemented-typed-features?
  "Compose the two CLJS-owned typed slices per function.  Checking per
  function matters: a real application module may expose provider-boundary
  functions and pure vector-processing functions together even though no
  individual function needs to mix their representations."
  [hir]
  (every? (fn [function]
            (let [single-function-hir (assoc hir :functions [function])]
              (or (ir/only-cljs-provider-typed-features? single-function-hir)
                  (only-cljs-bounded-vector-features? single-function-hir))))
          (:functions hir)))


(defn- capability-deny-message
  "T3.2: name missing grant / effect / policy in admission denials.

  The grants are spelled with their catalog NAMES here, not their wire ids.
  This message is the one place a caller is told what to put in `--policy`, so
  it is the most user-facing text the compiler emits about capabilities, and
  `lang/capability-catalog.edn` says `:numeric-id :not-user-facing`. Measured
  2026-08-31 it read `missing grants #{[:cap/call 3]}` -- a number the caller
  then had to look up in order to act on the very sentence telling them to act.

  `ex-data` is deliberately NOT renamed: `:missing`/`:required`/`:allowed` stay
  in the wire form a machine consumer already keys on. Prose gets names, data
  keeps ids."
  [error]
  (let [d (ex-data error)
        missing (cap-names/name-grants (:missing d))
        required (cap-names/name-grants (:required d))
        allowed (cap-names/name-grants (:allowed d))
        base (or (ex-message error) "capability denied")]
    (cond
      (seq missing)
      (str base
           "; missing grants " (pr-str missing)
           " (required " (pr-str required)
           ", allowed " (pr-str (or allowed #{})) ")")
      (seq (:abac/violations d))
      (str base "; ABAC violations " (pr-str (:abac/violations d))
           (when-let [pid (:abac/policy-id d)] (str " policy-id=" pid)))
      (:crypto d)
      (str base "; crypto policy " (pr-str (:crypto d)))
      (:hardware-signing d)
      (str base "; hardware-signing " (pr-str (:hardware-signing d)))
      (seq (:information-flow/violations d))
      (str base "; information-flow violations "
           (pr-str (:information-flow/violations d)))
      :else base)))

(defn- rethrow-admission!
  "Rethrow admission ExceptionInfo with T3.2 message + stable error code."
  [error]
  (let [d (ex-data error)
        code (cond
               (seq (:missing d)) :kotoba.error/capability-missing-grant
               (seq (:abac/violations d)) :kotoba.error/capability-abac-deny
               (:crypto d) :kotoba.error/capability-crypto-deny
               (:hardware-signing d) :kotoba.error/capability-hardware-deny
               (seq (:information-flow/violations d)) :kotoba.error/capability-flow-deny
               :else :kotoba.error/capability-denied)]
    (throw (ex-info (capability-deny-message error)
                    (assoc d
                           :phase (or (:phase d) :admission)
                           :kotoba.error/code code)))))

(defn- admit!
  [hir policy]
  (try
    (effect-row/check hir policy)
    (catch clojure.lang.ExceptionInfo e
      (if (= :admission (:phase (ex-data e)))
        (rethrow-admission! e)
        (throw e)))))

(defn- emit-native-ir!
  "Keep extracted native-layer detail while presenting one stable compiler IR
  phase to callers. GMIR, MIR, MC, layout, and KIR-to-GMIR are implementation
  boundaries, not new public diagnostic phases."
  [backend kir]
  (try
    ((case backend
       :x86_64-kotoba-v1 x86-64/emit-program
       :aarch64-kotoba-v1 aarch64/emit-program) kir)
    (catch clojure.lang.ExceptionInfo error
      (let [data (ex-data error)
            phase (:phase data)]
        (if (contains? #{:kir-to-gmir :gmir :mir :mc :mc-encode :aggregate-abi
                         :record-boundary :variant-boundary}
                       phase)
          (throw (ex-info (ex-message error)
                          (assoc data :phase :ir :ir/phase phase)
                          error))
          (throw error))))))

(def ^:private non-capability-policy-keys
  "Policy keys that are NOT capability grants.

  Admission is deny-by-default AND closed over its key set: an unrecognised key
  is a `malformed capability policy` error rather than something ignored, which
  is the right default — a grant nobody understands must not be waved through.
  The cost is that every non-grant key has to be named here, and until
  2026-08-11 `:budgets` was not, so a policy declaring `{:budgets {:fuel n}}`
  was rejected outright even though `compile-source` reads exactly that path to
  size the artifact's fuel global."
  #{:language-profile :budgets})

(defn- capability-policy
  "The capability half of a policy, with the declarative keys removed."
  [policy]
  (apply dissoc policy non-capability-policy-keys))

(defn check-source
  "Frontend admit + optional language-profile (T9.2).

  `policy` may include `:language-profile :pure-product` (or pass via
  3-arity `opts`). Returns {:hir :admission :language-profile}.

  `opts` may carry `:admit-linked-synthetics?`, the same seam `compile-source`
  reads from its build metadata: `project/link-source` renames cross-module
  symbols with a reserved `__kotoba_` prefix that authored source may not use,
  so a linked source is only admissible with the flag the linker's own output
  earns. Callers pass it by going through `check-project`, never by hand."
  ([source] (check-source source {} {}))
  ([source policy] (check-source source policy {}))
  ([source policy opts]
   (let [language-profile (or (:language-profile opts)
                              (:language-profile policy))
         admission-policy (capability-policy policy)
         analyze-opts (cond-> {}
                        language-profile (assoc :language-profile language-profile)
                        (:admit-linked-synthetics? opts)
                        (assoc :admit-linked-synthetics? true))
         hir (sema/analyze source analyze-opts)]
     (try
       {:hir hir
        :admission (admit! hir admission-policy)
        :language-profile language-profile}
       (catch clojure.lang.ExceptionInfo e
         ;; W1: rethrow admission denials with semantic operation names from
         ;; elaboration, not only numeric [:cap/call id] effect rows.
         (let [data (ex-data e)
               named (:named-operations hir)
               ops (when (seq named) (set named))]
           (throw (ex-info (ex-message e)
                           (cond-> (or data {})
                             (seq ops) (assoc :operations ops
                                              :named-operations ops)
                             true (assoc :phase (or (:phase data) :admission))
                             true (assoc :hir-effects (:effects hir))
                             language-profile (assoc :language-profile language-profile))))))))))

(defn- component-kir
  "Make the source language's legacy scalar `cap-call` explicit at the
  Component boundary. In KIR, untyped cap-call has always been an i64 -> i64
  operation; Component WIT/core lowering intentionally accepts only
  `typed-cap-call`. Keeping this normalization here preserves all ordinary
  backend KIR identities while preventing an effectful Component from falling
  through to the unbindable generic `kotoba:cap::call` core import."
  [kir]
  (-> kir
      (update :functions
              (fn [functions]
                (mapv (fn [function]
                        (-> function
                            (update :param-types
                                    #(or % (vec (repeat (count (:params function)) :i64))))
                            (update :result #(or % :i64))
                            (update :body
                                    #(walk/postwalk
                                      (fn [form]
                                        (if (and (seq? form)
                                                 (= 'cap-call (first form))
                                                 (= 3 (count form)))
                                          (let [[_ id request] form]
                                            (list 'typed-cap-call id :i64 :i64 request))
                                          form))
                                      %))))
                      functions)))))

(defn compile-component-wit
  "Compile source through checked HIR/KIR and emit its deterministic closed WIT
  package. This does not claim to emit a Component binary."
  ([source] (compile-component-wit source {}))
  ([source policy]
   (let [hir (sema/analyze source)
         checked (admit! hir policy)
         kir (component-kir (ir/lower hir))]
     (assoc (component-wit/emit kir) :admission checked))))

(declare compile-source)

(def default-component-budgets
  "Declared resource bounds used when a caller supplies none.

  `:fuel` keeps the historical 512-call budget so an unparameterized component
  behaves exactly like every core-wasm module. `:memory-pages` is a DECLARED
  host bound, not a property read back out of the module -- kototama's
  `component-platform.edn` requires the key to be a positive integer for the
  `:sync` profile, and 16 pages (1 MiB) is a default, not a measurement."
  {:fuel wasm/default-fuel :memory-pages 16})

(defn- component-capability-ids [kir]
  (->> (:functions kir)
       (mapcat #(tree-seq coll? seq (:body %)))
       (keep (fn [form]
               (when (and (seq? form) (= 'typed-cap-call (first form)))
                 (second form))))
       set))

(defn compile-component
  "Compile the currently qualified slice to a validated Component Model binary
  plus its admission request. Structured/provider Canonical ABI lowering
  remains fail-closed.

  `opts` takes `:profile` (`:sync`/`:async`), `:budgets`, `:package-lock-cid`.
  The declared `:fuel` budget is compiled into the core module's fuel global
  where the shape allows it; `:fuel-enforcement` in the result reports whether
  that happened or the budget is host-enforced only."
  ([source] (compile-component source {} {}))
  ([source policy] (compile-component source policy {}))
  ([source policy {:keys [profile budgets component-abilities capability-mode target
                          admit-linked-synthetics?]
                   :or {profile :sync target :wasm-component-kotoba-v1} :as opts}]
   (let [budgets (merge default-component-budgets budgets)
         typed-v3? (= target abi/component-target-v2)
         _ (when-not (contains? #{nil :function :linear-resource} capability-mode)
             (throw (ex-info "Component capability mode is unsupported"
                             {:phase :component-capability-mode
                              :capability-mode capability-mode})))
         hir (sema/analyze source
                               (cond-> {}
                                 admit-linked-synthetics?
                                 (assoc :admit-linked-synthetics? true)))
         checked-admission (admit! hir policy)
         kir (component-kir (ir/lower hir))
         target-profile (target-profile/profile target)
         typed-values? (= :kotoba.kir/v4 (:format kir))
         value-abi (cond (ir/uses-f32? hir) :kotoba.typed/mixed-f32-f64-v3
                         (ir/uses-f64? hir) :kotoba.typed/mixed-f64-v2
                         typed-values? :kotoba.typed/externref-v1
                         :else :kotoba.i64/direct-v1)
         compatibility (compatibility/descriptor
                        {:hir-format (:format hir) :kir-format (:format kir)
                         :target target :target-profile target-profile
                         :value-abi value-abi})
         capability-ids (component-capability-ids kir)
         _ (when (and typed-v3?
                      (not (contains? #{#{1} #{2} #{3} #{4} #{5} #{6} #{7}
                                        #{13} #{14} #{15} #{16}}
                                      capability-ids)))
             (throw (ex-info
                     "typed v0.3 lowering currently requires one implemented vertical slice"
                     {:phase :component-abi-v3
                      :target target
                              :implemented [#{1} #{2} #{3} #{4} #{5} #{6} #{7}
                                            #{13} #{14} #{15} #{16}]
                      :supplied capability-ids})))
         _ (when (and component-abilities
                      (not= capability-ids (set (keys component-abilities))))
             (throw (ex-info "Component abilities must exactly match capability calls"
                             {:phase :component-abilities
                              :required capability-ids
                              :supplied (set (keys component-abilities))})))
         _ (when (and component-abilities
                      (not-every? abi/valid-ability? (vals component-abilities)))
             (throw (ex-info "Component ability descriptor is invalid"
                             {:phase :component-abilities})))
         _ (when (and (= capability-mode :linear-resource)
                      (not (contains? #{:scalar-capability-call
                                       :scalar-literal-capability-call}
                                     (component-core/assert-supported! kir))))
             (throw (ex-info "Linear resource mode requires one direct scalar capability call"
                             {:phase :component-capability-mode
                              :capability-mode capability-mode})))
         component-opts {:capability-mode (or capability-mode :function)
                         :typed-capability-v3? typed-v3?}
         wit (component-wit/emit kir component-opts)
         enforcement (component-core/fuel-enforcement kir)
         component-bytes (component-core/emit kir :wasm32-wasi-kotoba-v1
                                              {:fuel (:fuel budgets)
                                               :memory-pages (:memory-pages budgets)
                                               :capability-mode capability-mode
                                               :typed-capability-v3? typed-v3?})
         packaged (component-artifact/package component-bytes kir wit)
         result
         (assoc packaged
            :hir hir
            :kir kir
            :target-profile target-profile
            :capabilities (into #{} (map abi/component-import-key) capability-ids)
            :component-imports
            (into (sorted-map)
                  (map (fn [id]
                         [(abi/component-import-key id)
                          (get component-abilities id)]))
                  capability-ids)
            :wit wit
            :admission checked-admission
            :floating-point-policy floating-point-policy
            :compatibility compatibility
            :budgets budgets
            :capability-mode (or capability-mode :function)
            :fuel-enforcement enforcement
            :admission-request (component-admission/request
                                packaged wit
                                (assoc opts :profile profile :budgets budgets)))]
     (provenance/attach source policy opts result))))

(defn refuse-fuel-declared-twice!
  "`--fuel N` (emit-metadata `:fuel`) and a policy `{:budgets {:fuel M}}` are
  two spellings of ONE budget. `declared-fuel` reads them in that order, so
  when they disagreed the flag won and the policy's author never learned that
  the number in their file was not the one sealed -- a build that reported
  success with a budget nobody had checked. Equal values are one declaration
  and compile; different values are refused before anything is lowered.

  Mirrored by the nbb native driver's `fuel-policy!` so both routes refuse
  with the same data (amu-h6)."
  [policy emit-metadata]
  (let [flag (:fuel emit-metadata)
        from-policy (get-in policy [:budgets :fuel])]
    (when (and (some? flag) (some? from-policy) (not= flag from-policy))
      (throw (ex-info "fuel declared twice with different values: --fuel and the policy's :budgets :fuel must agree"
                      {:phase :usage :reason :fuel-declared-twice
                       :flag flag :policy from-policy})))))

(defn- compile-source*
  ([source target] (compile-source* source target {}))
  ([source target policy] (compile-source* source target policy {}))
  ([source target policy emit-metadata]
   (when-not (contains? supported-targets target)
     (throw (ex-info "unsupported target" {:target target :supported supported-targets})))
   ;; A Component is not a backend output: it is a core module lifted through
   ;; the Canonical ABI and packaged by the pinned wasm-tools toolchain. Route
   ;; it explicitly rather than letting it fall through to the wasm32 backend,
   ;; which would silently emit a bare core module under a component target
   ;; name.
   (when (= :component (:execution (target-profile/profile target)))
     (throw (ex-info "component targets compile through compile-component"
                     {:phase :target-routing :target target
                      :entry-point 'kotoba.compiler.core/compile-component})))
   (let [profile (target-profile/profile target)
        backend (target-profile/backend target)
        language-profile (or (:language-profile emit-metadata)
                             (:language-profile policy))
        hir (sema/analyze source (cond-> {}
                                       language-profile (assoc :language-profile language-profile)
                                       (:admit-linked-synthetics? emit-metadata)
                                       (assoc :admit-linked-synthetics? true)))
        ;; f32 and f64 were gated separately because kotoba-native implemented
        ;; f64 scalar arithmetic (ADR-2608030300) and no f32 at all, so
        ;; admitting them together would have routed f32 to a backend that
        ;; could not emit it. Both widths now reach both native ISAs
        ;; (kotoba-lang docs/adr/ADR-kotoba-floating-point-on-native.md, landed
        ;; through kotoba-gmir / kotoba-mir / kotoba-codegen / kotoba-native),
        ;; so the two sets are the same set and the gates are kept separate only
        ;; because the two REFUSALS still have to name their own width.
        ;;
        ;; What is admitted on native is narrower than "f32", and the narrowing
        ;; lives one layer down rather than here: `ir/only-native-word-typed-
        ;; features?` refuses f32-min/f32-max (x86 MINSS/MAXSS return the second
        ;; operand on NaN where AArch64 and the oracle return the NaN), the
        ;; `-checked` conversions (they trap in the oracle and no backend emits
        ;; the check), and the truncating float-to-int conversions (three
        ;; answers out of domain). A gate here would have to duplicate that list
        ;; and could then disagree with it.
        _ (when (and (ir/uses-f32? hir)
                     (not (contains? #{:js-kotoba-v1 :wasm32-kotoba-v1
                                       :x86_64-kotoba-v1 :aarch64-kotoba-v1} backend)))
            (throw (ex-info "f32 values require the kotoba-script, Wasm or native target"
                            {:phase :target :target target :backend backend
                             :floating-point-policy floating-point-policy})))
        _ (when (and (ir/uses-f64? hir)
                     (not (contains? #{:js-kotoba-v1 :wasm32-kotoba-v1
                                       :x86_64-kotoba-v1 :aarch64-kotoba-v1} backend)))
            (throw (ex-info "f64 values require the kotoba-script, Wasm or native target"
                            {:phase :target :target target :backend backend
                             :floating-point-policy floating-point-policy})))
        _ (when (and (= :kotoba.hir/v3 (:format hir))
                     (not (contains? #{:js-kotoba-v1 :wasm32-kotoba-v1} backend))
                     (not (and (= :cljs-kotoba-v1 backend)
                               (only-cljs-implemented-typed-features? hir)))
                     (not (and (contains? #{:x86_64-kotoba-v1 :aarch64-kotoba-v1} backend)
                               (ir/only-native-word-typed-features? hir))))
            (throw (ex-info "typed values currently require the kotoba-script web target, typed Wasm/CLJS target, or the qualified native one-word string/record/variant/option/result slice"
                            {:phase :target :target target :backend backend
                             :value-profile :kotoba.value/typed-v1})))
        ;; A library with no entry is admitted on the NATIVE backends too, but
        ;; only where the target's artifact does not need an entry point to
        ;; exist. `:x86_64-kotoba-v1`/`:aarch64-kotoba-v1` emit a code image
        ;; plus an export table, which is exactly what a library is. The aiueos
        ;; profiles are excluded: `:firmware`/`:kernel`/`:process` artifacts each
        ;; name a mandatory entry symbol in their target profile (`:efi_main`,
        ;; `:aiueos_kernel_entry`, `:aiueos_process_entry`), so an entryless
        ;; module would package into an image whose declared entry does not
        ;; exist -- rejected here rather than at link time, where the failure
        ;; would name a missing symbol instead of a missing entry.
        _ (when (and (nil? (:entry hir))
                     (not (contains? #{:js-kotoba-v1 :wasm32-kotoba-v1
                                      :cljs-kotoba-v1} backend))
                     (not (and (contains? #{:x86_64-kotoba-v1 :aarch64-kotoba-v1} backend)
                               (nil? (:entry (target-profile/profile target))))))
            (throw (ex-info "entryless libraries currently require the kotoba-script web target, the Wasm target, or an entryless native target"
                            {:phase :target :target target :backend backend})))
        ;; boot: the UEFI firmware boundary is admitted by the frontend for
        ;; every target, because the frontend has never seen a target keyword.
        ;; This is the layer that has.
        _ (uefi/reject-outside-uefi-target! target hir)
        ;; boot-lit: and the literal pool, which is gated to a WIDER set of
        ;; targets for a different reason -- see the two docstrings.
        _ (uefi/reject-rodata-literals-outside-native-targets! target hir)
        admission (admit! hir (capability-policy policy))
        kir (ir/lower hir)
        value (:oracle-value kir)
        typed-values? (= :kotoba.kir/v4 (:format kir))
        value-abi (cond (ir/uses-f32? hir) :kotoba.typed/mixed-f32-f64-v3
                        (ir/uses-f64? hir) :kotoba.typed/mixed-f64-v2
                        typed-values? :kotoba.typed/externref-v1
                        :else :kotoba.i64/direct-v1)
        declared-fuel (do (refuse-fuel-declared-twice! policy emit-metadata)
                          (or (:fuel emit-metadata)
                              (get-in emit-metadata [:budgets :fuel])
                              (get-in policy [:budgets :fuel])
                              512))
        compatibility (compatibility/descriptor
                       {:hir-format (:format hir) :kir-format (:format kir)
                        :target target :target-profile profile :value-abi value-abi})]
    (cond
      (= backend :wasm32-kotoba-v1)
      (let [typed-values? (= :kotoba.kir/v4 (:format kir))
            ;; T7.4: optional `:fuel` in emit-metadata (or policy `:budgets`)
            ;; bakes into the module-private fuel global; default remains 512.
            fuel declared-fuel
            emit-opts (cond-> {}
                        fuel (assoc :fuel fuel))]
        {:format :wasm/v1 :target target :target-profile profile
         :hir hir :kir kir :admission admission
         :compatibility compatibility
         :floating-point-policy floating-point-policy
         :value-profile (if typed-values? :kotoba.value/typed-v1 :kotoba.value/i64-v1)
         :value-abi value-abi
         :wasm-features (cond-> #{}
                          (typed/requires-host-runtime? kir) (conj :reference-types)
                          (ir/uses-f32? kir) (conj :ieee-754-f32)
                          (ir/uses-f64? kir) (conj :ieee-754-f64))
         :limits (cond-> {:fuel fuel :replenishable? false}
                   typed-values? (assoc :parametric-adt-depth 12
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
         :bytes (wasm/emit kir target emit-opts)})

      ;; ADR-2607151500: cljs backend emits SOURCE TEXT, not bytes -- no
      ;; kexe sealing (that artifact shape is native-code-specific: raw
      ;; :code bytes + a fuel/context ABI for a machine-code caller). A
      ;; cljs host just requires the returned source's namespace directly.
      (= backend :cljs-kotoba-v1)
      {:format :cljs/v1 :target target :target-profile profile
       :hir hir :kir kir :admission admission
       :compatibility compatibility
       :floating-point-policy floating-point-policy
       ;; declared-fuel, not 512: the budget the caller declared was already
       ;; resolved above and honoured by the wasm32 branch; this one used to
       ;; drop it.
       :limits {:fuel declared-fuel :replenishable? false}
       :source (cljs/emit kir declared-fuel)}

      (= backend :js-kotoba-v1)
      (let [source-digest (text-sha256 source)
            kir-digest (artifact/sha256 kir)
            typed-values? (= :kotoba.kir/v4 (:format kir))
            value-profile (if typed-values? :kotoba.value/typed-v1 :kotoba.value/i64-v1)
            limits (cond-> {:fuel declared-fuel :replenishable? false}
                     typed-values? (assoc :string-literal-bytes 4096
                                          :string-module-literal-bytes 65536
                                          :string-value-bytes 65536
                                          :keyword-value-bytes 512
                                          :map-entries 128
                                          :option-i64-slots 2
                                          :result-i64-slots 2
                                          :parametric-adt-depth 12
                                          :parametric-adt-nodes 64
                                          :variant-cases 32
                                          :generic-option-max-slots 3
                                          :heterogeneous-vector-items 32
                                          :typed-set-items 32
                                          :typed-map-entries 31
                                          :record-fields 32
                                          :vector-i64-items 16384
                                          :vector-f64-items 16384
                                          :compact-graph-items 128
                                          :string-index-key-bytes 65536))
            js-source (script/emit kir (merge {:source-digest source-digest
                                               :kir-digest kir-digest
                                               :compiler-version compiler-version}
                                              emit-metadata
                                              ;; last, so the resolved budget
                                              ;; wins: `declared-fuel` already
                                              ;; folded in emit-metadata and the
                                              ;; policy. Before 2026-08-11 this
                                              ;; map carried no fuel at all and
                                              ;; the emitter always wrote 512,
                                              ;; so `--fuel` was accepted and
                                              ;; silently ignored on this target.
                                              {:fuel declared-fuel}))
            output-digest (text-sha256 js-source)]
        {:format :javascript/v1 :target target :target-profile profile
         :hir hir :kir kir :admission admission
         :compatibility compatibility
         :floating-point-policy floating-point-policy
         :value-profile value-profile :limits limits :source js-source
         :manifest {:kotoba.artifact/schema "kotoba-js-artifact/v1"
                    :kotoba.artifact/source-digest source-digest
                    :kotoba.artifact/kir-digest kir-digest
                    :kotoba.artifact/output-digest output-digest
                    :kotoba.artifact/compiler-version compiler-version
                    :kotoba.artifact/compatibility compatibility
                    :kotoba.artifact/floating-point-policy floating-point-policy
                    :kotoba.artifact/value-profile value-profile
                    :kotoba.artifact/limits limits
                    :kotoba.artifact/target target
                    :kotoba.artifact/target-profile profile
                    :kotoba.artifact/effects (:effects kir)}})

      (= backend :evm256-kotoba-v1)
      (let [artifact (evm/emit kir)]
        (evm/verify-artifact! artifact)
        (assoc artifact
               :hir hir
               :admission admission
               :compatibility compatibility
               :floating-point-policy floating-point-policy))

      :else
      (let [program (select-keys kir [:format :entry :exports :signature :effects :functions])
            ;; The verifier only receives this closed program from the sealed
            ;; artifact. Native bytes therefore must be a pure function of the
            ;; same value, never of compiler-private KIR metadata.
            emitted (emit-native-ir! backend program)
            code (:code emitted)
            artifact (artifact/seal
                      {:format :kotoba.kexe/v1 :target target :target-profile profile :value value
                       :kir-sha256 (artifact/sha256 program)
                       :lowering (case backend
                                   :x86_64-kotoba-v1 :runtime-sysv-v1
                                   :aarch64-kotoba-v1 :runtime-aapcs64-v1)
                       :fuel-abi (case backend
                                   :x86_64-kotoba-v1 {:mode :hidden-context-r9 :initial declared-fuel}
                                   :aarch64-kotoba-v1 {:mode :hidden-context-x7 :initial declared-fuel})
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
                       :compatibility compatibility
                       :limits {:memory-bytes 65536
                                :fuel declared-fuel
                                :stack-bytes 4096}
                       :code (mapv #(bit-and (int %) 0xff) code)
                       :program program :exports (:exports emitted)})]
        (verifier/verify-artifact! artifact)
        (cond-> {:format :kexe/v1 :target target :hir hir :kir kir
                 :admission admission :artifact artifact
                 :compatibility compatibility
                 :floating-point-policy floating-point-policy}
          (= target :x86_64-aiueos-uefi-v1)
          (assoc :binary (pe32plus/package-efi artifact))

          (= target :x86_64-aiueos-kernel-v1)
          (assoc :binary (elf64/package-kernel artifact)
                 :object (elf64/package-kernel-object artifact))

          (= target :aarch64-aiueos-kernel-v1)
          (assoc :binary (elf64/package-kernel-aarch64 artifact))

          (= target :x86_64-aiueos-user-v1)
          (assoc :binary (elf64/package-user artifact))))))))

(defn compile-source
  ([source target] (compile-source source target {}))
  ([source target policy] (compile-source source target policy {}))
  ([source target policy emit-metadata]
   (provenance/attach source policy emit-metadata
                      (compile-source* source target policy emit-metadata))))

(defn compile-ipld-adl-source
  "Compile the fail-closed Kotoba ADL authoring profile to ipld-adl-wasm-v1."
  ([source] (compile-ipld-adl-source source {}))
  ([source options]
   (provenance/attach source {} options
                      (ipld-adl-source/compile-source source options))))

(defn compile-source-cached
  [source target policy build-metadata cache-entry trust now]
  (if cache-entry
    (cache/admit! source target policy build-metadata cache-entry trust now)
    {:hit? false
     :result (compile-source source target policy build-metadata)}))

(defn check-project
  "Frontend admit for a closed namespace-symbol -> source-text map: exactly the
  link `compile-project` performs, stopped before lowering.

  This exists because a module that declares `(:require [ns :as alias])` cannot
  be checked one file at a time, and until 2026-08-30 there was no other way to
  ask -- `compile` took `--source-path`/`--module-lock` and `check` took
  neither, so a multi-file guest could be built but never checked. Admits
  nothing `compile-project` does not already admit through the same
  `project/link-source`; it emits no artifact, so it makes no provenance claim.

  Returns `check-source`'s map plus `:root` and `:module-order`."
  ([sources root] (check-project sources root {}))
  ([sources root policy]
   (let [linked (project/link-source sources root)]
     (assoc (try
              (check-source (:source linked) policy {:admit-linked-synthetics? true})
              (catch clojure.lang.ExceptionInfo error
                (throw (project/attribute-linked-error error linked))))
            :root root
            :module-order (:module-order linked)))))

(defn compile-project
  "Compile a closed namespace-symbol -> source-text map without ambient lookup.

  Component targets (T8.3 multi-file project kit body): link the closed graph
  then lift through `compile-component` (not `compile-source`, which rejects
  component execution profiles). Linked source is monomorphic admission
  skeleton material; Canonical lowering still owns component body. Does not
  admit ambient classpath lookup. `:schemas` project-mode restrictions of
  `project/link-source` still apply."
  ([sources root target] (compile-project sources root target {}))
  ([sources root target policy] (compile-project sources root target policy {}))
  ([sources root target policy supply-chain]
   (compile-project sources root target policy supply-chain {}))
  ;; `emit-metadata` is the same map `compile-source` takes -- `:fuel`,
  ;; `:language-profile` -- carried here so a project build seals the budget
  ;; the caller declared. Before this arity the CLI built `source-opts` from
  ;; `--fuel` and handed it only to the single-file branch: on
  ;; `--source-path` / `--module-lock` the flag was accepted, never reached
  ;; `linked-meta`, and `declared-fuel` fell back to the policy or 512 while
  ;; the compile reported success (amu-h5, measured 2026-09-07 on
  ;; `x86_64-aiueos-kernel-v1 --artifact image --fuel 4096`: the sealed RW
  ;; context qword read 512).
  ([sources root target policy supply-chain emit-metadata]
   (let [allowed-keys #{:package-lock-digest :trust-policy-digest
                        :package-receipt-digest}
         values (when (map? supply-chain) (vals supply-chain))
         supplied (count (filter some? values))]
     (when-not (and (map? supply-chain)
                    (every? allowed-keys (keys supply-chain))
                    (or (zero? supplied)
                        (and (= allowed-keys (set (keys supply-chain)))
                             (= 3 supplied)
                             (every? #(and (string? %)
                                           (re-matches #"[0-9a-f]{64}" %))
                                     values))))
       (throw (ex-info "invalid verified supply-chain identity"
                       {:phase :project-link
                        :reason :invalid-supply-chain-identity}))))
   (let [linked (project/link-source sources root)
         module-digests (into (sorted-map)
                              (map (fn [[namespace source]]
                                     [namespace (text-sha256 source)]))
                              (select-keys sources (:module-order linked)))
         graph {:kotoba.module/schema :kotoba.module-graph/v1
                :kotoba.module/root root
                :kotoba.module/order (:module-order linked)
                :kotoba.module/source-digests module-digests}
         graph-digest (artifact/sha256 graph)
         linkage-evidence {:mode :closed-module-graph
                           :module-graph-digest graph-digest
                           :unresolved-symbols #{}
                           :ambient-symbols false}
         _ (when (= :native (:execution (target-profile/profile target)))
             (aggregate-abi/admit-closed-linkage! linkage-evidence))
         project-meta (merge {:module-graph-digest graph-digest
                              :module-source-digests module-digests}
                             supply-chain)
         component-target? (= :component (:execution (target-profile/profile target)))
         ;; Project digests win over anything the caller put under the same
         ;; keys; the caller's build metadata is otherwise carried unchanged.
         linked-meta (merge emit-metadata project-meta
                            {:admit-linked-synthetics? true})
         compiled (try
                    (if component-target?
                      ;; Component opts are target + project digests only here;
                      ;; CLI attaches fuel/profile via direct compile-component.
                      (compile-component (:source linked) policy
                                         (merge {:target target} linked-meta))
                      (compile-source (:source linked) target policy linked-meta))
                    ;; A refusal here was raised against the linked unit. Name
                    ;; the module and line that wrote the form instead
                    ;; (amu-h10; `project/attribute-linked-error`).
                    (catch clojure.lang.ExceptionInfo error
                      (throw (project/attribute-linked-error error linked))))]
     (cond-> (assoc compiled :project graph :project-digest graph-digest
                    :project-linkage linkage-evidence)
       (:manifest compiled)
       (update :manifest merge
               (merge {:kotoba.artifact/module-graph-digest graph-digest
                       :kotoba.artifact/module-source-digests module-digests}
                      (when (seq supply-chain)
                        {:kotoba.artifact/package-lock-digest
                         (:package-lock-digest supply-chain)
                         :kotoba.artifact/trust-policy-digest
                         (:trust-policy-digest supply-chain)
                         :kotoba.artifact/package-receipt-digest
                         (:package-receipt-digest supply-chain)})))))))
