(ns kotoba.compiler.nbb.js-cli
  "JDK-free entrypoint for the restricted-ESM targets (`--target js` /
  `js-browser`): `.kotoba` -> HIR -> checked KIR -> `kotoba.script/emit`.

  Until 2026-09-06 `bin/amu` sent these targets to `clojure -M`, not because
  code generation needed a JVM but because the emitter (`kotoba-lang/
  kotoba-script`) was a `.clj` file. It is `.cljc` now and emits the same
  bytes on both hosts (measured by that repo's `test/nbb/parity.cljs` and by
  `test/nbb/js_parity.cljs` here against the JVM-compiled
  `runtime/http/route-decide.mjs`), so this driver mirrors the
  `:js-kotoba-v1` branch of `kotoba.compiler.core/compile-source*`: same
  limits, same manifest keys, same three output files (`.mjs`,
  `.manifest.edn`, `.manifest.json`).

  What is NOT mirrored, deliberately: the JVM writes the manifest and
  provenance maps in Clojure's hash-map iteration order and this writes them
  in cljs's, and the JVM's `.inputs.edn` sidecar is folded into the printed
  result here (as the Wasm route does). Manifest and provenance are VALUES;
  consumers read them, they do not diff their text -- `test/nbb/js_parity.cljs`
  compares them as EDN. The `.mjs` bytes are the byte-level parity claim."
  (:require ["node:crypto" :as crypto]
            [kotoba.artifact.core :as artifact]
            [kotoba.compiler.capability-names :as cap-names]
            [kotoba.compiler.effect-row :as effect-row]
            [kotoba.compiler.nbb.cli-support :as support]
            [kotoba.compiler.nbb.io :as io]
            [kotoba.compiler.nbb.project-source :as project-source]
            [kotoba.compiler.provenance :as provenance]
            [kotoba.kir :as ir]
            [kotoba.kir.compatibility :as compatibility]
            [kotoba.kir.target :as target-profile]
            [kotoba.script :as script]
            [kotoba.sema :as sema]))

;; The JVM `parse-target` spellings for the two restricted-ESM profiles.
;; `cljs-browser` is NOT here: that backend emits ClojureScript source text
;; and still lives on the JVM route.
(def targets
  {"js" :js-kotoba-v1
   "javascript" :js-kotoba-v1
   "web" :js-kotoba-v1
   "js-browser" :js-browser-kotoba-v1
   "js-kotoba-v1" :js-kotoba-v1
   "js-browser-kotoba-v1" :js-browser-kotoba-v1})

(def ^:private floating-point-policy
  :kotoba.floating-point/ieee-754-f32-f64-v7)

(defn- text-sha256 [text]
  (-> (.createHash crypto "sha256") (.update text "utf8") (.digest "hex")))

(defn- read-policy! [args]
  (cap-names/wire-policy (support/read-policy args)))

(defn- limits
  "The `:limits` map `compile-source*` attaches to a `:javascript/v1` result,
  key for key."
  [fuel typed-values?]
  (cond-> {:fuel fuel :replenishable? false}
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
                         :string-index-key-bytes 65536)))

(defn- host-number
  "`--fuel` arrives as a BigInt (exactness for the Wasm i64 path); the JS
  emitter and the manifest want a host number, as the JVM route has."
  [n]
  (if (and (some? n) (identical? js/BigInt (.-constructor n))) (js/Number n) n))

(defn- json-manifest
  "`clojure.data.json`-shaped text: keyword keys without the colon, compact."
  [manifest]
  (.stringify js/JSON
              (clj->js (artifact/edn-safe manifest)
                       :keyword-fn (fn [k] (subs (str k) 1)))))

(defn- compile-js! [args]
  (let [target-name (or (support/option args "--target") "js")
        target (get targets target-name)
        _ (when-not target
            (support/usage-error!
             (str "error: nbb JS path does not cover target " target-name)))
        backend (target-profile/backend target)
        _ (when-not (contains? #{:js-kotoba-v1 :js-browser-kotoba-v1} backend)
            (support/usage-error! (str "error: target is not restricted ESM: " target-name)))
        profile (target-profile/profile target)
        resolved (project-source/resolve-source! args)
        input (:input resolved)
        source (:source resolved)
        linked? (:linked? resolved)
        output (or (support/option args "--output") (str input ".mjs"))
        policy (support/timed "policy-read" #(read-policy! args))
        module-graph (project-source/module-graph resolved text-sha256)
        ;; What `provenance/attach` seals as `:build-metadata-sha256` and what
        ;; the emitter reads. For a single file it is the CLI's metadata
        ;; (`--fuel` etc.), as `compile-source` receives it. For a linked
        ;; project the JVM CLI does not thread that map at all: `cli.clj` hands
        ;; `compile-project` sources + policy, and `compile-project` builds the
        ;; metadata itself -- the module-graph digest, the per-module source
        ;; digests and `:admit-linked-synthetics? true`, nothing else (the
        ;; comment at its `build-metadata` remark names this as still open).
        ;; Mirrored here key for key so the two routes seal the same value;
        ;; before this (2026-09-06) the nbb route hashed `{}` for a project and
        ;; emitted a header without the graph digest, 281 bytes short.
        emit-metadata (if module-graph
                        {:module-graph-digest (:digest module-graph)
                         :module-source-digests (:module-digests module-graph)
                         :admit-linked-synthetics? true}
                        (support/emit-metadata args))
        hir (support/timed "frontend"
                           #(sema/analyze source (project-source/analyze-opts policy linked?)))
        admission (support/timed "admission"
                                 #(effect-row/check hir (support/capability-policy policy)))
        kir (support/timed "kir-lower" #(ir/lower hir))
        typed-values? (= :kotoba.kir/v4 (:format kir))
        value-profile (if typed-values? :kotoba.value/typed-v1 :kotoba.value/i64-v1)
        ;; Same resolution order as `compile-source*`: --fuel, then the
        ;; policy's :budgets, then the emitter default.
        fuel (host-number (or (:fuel emit-metadata)
                              (get-in emit-metadata [:budgets :fuel])
                              (get-in policy [:budgets :fuel])
                              512))
        value-abi (if typed-values? :kotoba.typed/externref-v1 :kotoba.i64/direct-v1)
        compat (compatibility/descriptor
                {:hir-format (:format hir) :kir-format (:format kir)
                 :target target :target-profile profile :value-abi value-abi})
        source-digest (text-sha256 source)
        kir-digest (artifact/sha256 kir)
        js-source (support/timed
                   "js-emit"
                   #(script/emit kir (merge {:source-digest source-digest
                                             :kir-digest kir-digest
                                             :compiler-version compatibility/compiler-version}
                                            (dissoc emit-metadata :fuel)
                                            {:fuel fuel})))
        manifest {:kotoba.artifact/schema "kotoba-js-artifact/v1"
                  :kotoba.artifact/source-digest source-digest
                  :kotoba.artifact/kir-digest kir-digest
                  :kotoba.artifact/output-digest (text-sha256 js-source)
                  :kotoba.artifact/compiler-version compatibility/compiler-version
                  :kotoba.artifact/compatibility compat
                  :kotoba.artifact/floating-point-policy floating-point-policy
                  :kotoba.artifact/value-profile value-profile
                  :kotoba.artifact/limits (limits fuel typed-values?)
                  :kotoba.artifact/target target
                  :kotoba.artifact/target-profile profile
                  :kotoba.artifact/effects (:effects kir)}
        ;; The same result map `compile-source*` builds, so `provenance/attach`
        ;; seals the same identity the JVM route seals (and folds it into the
        ;; manifest under :kotoba.artifact/provenance, as there).
        result (support/timed
                "provenance"
                #(provenance/attach source policy emit-metadata
                                    {:format :javascript/v1 :target target :target-profile profile
                                     :hir hir :kir kir :admission admission
                                     :compatibility compat
                                     :floating-point-policy floating-point-policy
                                     :value-profile value-profile
                                     :limits (limits fuel typed-values?)
                                     :source js-source
                                     :manifest manifest}))
        ;; After `attach`, as `compile-project` does it: the provenance seal
        ;; does not cover these two keys, the manifest carries them.
        manifest (cond-> (:manifest result)
                   module-graph
                   (merge {:kotoba.artifact/module-graph-digest (:digest module-graph)
                           :kotoba.artifact/module-source-digests (:module-digests module-graph)}))
        manifest-edn (str output ".manifest.edn")
        manifest-json (str output ".manifest.json")
        provenance-output (str output ".provenance.edn")]
    (support/timed
     "output-set-write"
     #(io/write-set! [{:path output :text js-source}
                      {:path manifest-edn :text (pr-str (artifact/edn-safe manifest))}
                      {:path manifest-json :text (json-manifest manifest)}
                      {:path provenance-output :text (pr-str (artifact/edn-safe (:provenance result)))}]))
    (merge {:ok true :target target :output output
            :provenance-output provenance-output
            :manifest-output manifest-edn
            :manifest-json-output manifest-json
            :admission (cap-names/name-grants admission)
            :effects (cap-names/name-grants (:effects hir))
            :bytes (.byteLength js/Buffer js-source "utf8")}
           (when module-graph
             {:project (:graph module-graph)
              :project-digest (:digest module-graph)})
           (project-source/inputs-record resolved))))

(defn- run! [args]
  (case (first args)
    "compile" (compile-js! args)
    (support/usage-error!
     (str "error: nbb JS path does not cover command " (first args)))))

(support/execute! #(run! % ))
