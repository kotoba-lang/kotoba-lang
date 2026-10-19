(ns kotoba.compiler.nbb.evm-cli
  "EVM-only nbb compiler entrypoint -- the JDK-free half of
  `compile --target evm256-kotoba-v1`.

  The EVM backend (`kotoba.compiler.backend.evm`) is portable .cljc and its
  nbb parity was already asserted byte-for-byte against the JVM's pinned
  digest (`test/nbb/run.cljs`'s evm case). What kept this target off the
  JDK-free CLI was routing only: `bin/amu` had no entrypoint that could serve
  it, so every `--jvm-free` EVM compile failed closed while the nbb test suite
  proved the same lowering all along. This entrypoint closes that gap.

  The JVM CLI writes three files for this target: the creation bytecode,
  `.abi.json`, and `.manifest.edn`. This driver writes the same three plus the
  `.provenance.edn` and `.publication.edn` descriptors the other nbb
  entrypoints emit, so an output set verifies identically regardless of which
  runtime produced it.

  Provenance parity note: `kotoba.compiler.provenance/descriptor` is shared,
  so the `:evm/v1` branch in its `outputs` map seals the same fields on both
  runtimes. If the backend's own manifest digest and the shared seal ever
  disagree about the creation bytes, that is a contradiction, not a richer
  description -- hence the cross-check below, which fails the compile."
  (:require ["node:buffer" :refer [Buffer]]
            [clojure.walk :as walk]
            [json.data-json :as json]
            [kotoba.artifact.core :as artifact]
            [kotoba.compiler.backend.evm :as evm]
            [kotoba.compiler.capability-names :as cap-names]
            [kotoba.compiler.effect-row :as effect-row]
            [kotoba.compiler.nbb.cli-support :as support]
            [kotoba.compiler.nbb.io :as io]
            [kotoba.compiler.nbb.output-set :as output-set]
            [kotoba.compiler.nbb.project-source :as project-source]
            [kotoba.compiler.provenance :as provenance]
            [kotoba.kir :as ir]
            [kotoba.kir.compatibility :as compatibility]
            [kotoba.kir.target :as target-profile]
            [kotoba.sema :as sema]))

(def targets
  {"evm" :evm256-kotoba-v1
   "evm256" :evm256-kotoba-v1
   ;; The JVM CLI's parse-target table accepts the full profile name; mirror
   ;; it here so a flag that works on one route works on the other.
   "evm256-kotoba-v1" :evm256-kotoba-v1})

(defn- read-policy! [args]
  (cap-names/wire-policy (support/read-policy args)))

(defn- compile-uncached! [args target output source linked?]
  (let [policy (support/timed "policy-read" #(read-policy! args))
        emit-metadata (support/emit-metadata args)
        hir (support/timed "frontend"
                           #(sema/analyze source
                                          (project-source/analyze-opts policy linked?)))
        admission-result (support/timed
                          "admission"
                          #(effect-row/check hir (support/capability-policy policy)))
        kir (support/timed "kir-lower" #(ir/lower hir))
        profile (target-profile/profile target)
        compat (compatibility/descriptor
                {:hir-format (:format hir) :kir-format (:format kir)
                 :target target :target-profile profile
                 ;; The EVM backend admits only pure zero-arity i64 modules
                 ;; (evm/admitted-main rejects everything else), so the value
                 ;; ABI is direct-i64 by construction. Stated here rather than
                 ;; derived, and re-derived again below from the KIR so a
                 ;; backend that widens admission fails loudly instead of
                 ;; stamping a descriptor that no longer describes it.
                 :value-abi (if (ir/uses-f64? kir) :kotoba.typed/mixed-f64-v2
                                :kotoba.i64/direct-v1)})
        result (support/timed
                "evm-emit"
                (fn []
                  (let [artifact (evm/emit kir)]
                    (evm/verify-artifact! artifact)
                    (assoc artifact
                           :hir hir
                           :admission admission-result
                           :compatibility compat))))
        provenance-result (support/timed "provenance"
                                        #(provenance/attach source policy
                                                            emit-metadata result))
        ;; The backend's manifest digest and the shared provenance seal must
        ;; name the same bytes. Two identities for one artifact is not a
        ;; bigger description, it is a contradiction -- refuse the compile.
        primary (get-in provenance-result [:provenance :outputs :primary])
        _ (when-not (and (map? primary)
                         (= (:sha256 primary) (:creation-sha256 result)))
            (throw (ex-info
                    "EVM creation digest disagrees between the backend manifest and the shared provenance seal"
                    {:phase :provenance
                     :backend-manifest-sha256 (:creation-sha256 result)
                     :provenance-sha256 (:sha256 primary)})))
        bytes (Buffer.from (clj->js (map #(bit-and (int %) 0xff)
                                         (:creation-bytes result))))
        abi-json (json/write-str (:abi result)
                                 {:key-fn (fn [k] (if (keyword? k)
                                                    (subs (str k) 1)
                                                    (str k)))})
        ;; `artifact/edn-safe` prints an i64 as a plain integer token, matching
        ;; the JVM CLI's manifest output (same reason as the native driver's
        ;; comment: text that round-trips is not the same value, and the
        ;; manifest is text).
        manifest-edn (pr-str (artifact/edn-safe
                              (select-keys result [:format :target :target-profile
                                                   :selector :kir-sha256
                                                   :runtime-sha256
                                                   :creation-sha256 :limits])))
        provenance-text (support/timed "provenance-serialize"
                                      #(pr-str (artifact/edn-safe
                                                (:provenance provenance-result))))
        provenance-output (str output ".provenance.edn")
        publication-output (str output ".publication.edn")
        publication-text (output-set/serialize output bytes provenance-text)]
    (support/timed "output-set-write"
                   #(io/write-set! [{:path output :bytes bytes}
                                    {:path (str output ".abi.json") :text abi-json}
                                    {:path (str output ".manifest.edn") :text manifest-edn}
                                    {:path provenance-output :text provenance-text}
                                    {:path publication-output :text publication-text}]))
    {:ok true :target target :output output
     :abi-output (str output ".abi.json")
     :manifest-output (str output ".manifest.edn")
     :provenance-output provenance-output
     :publication-output publication-output}))

(defn- compile! [args]
  (let [target-name (or (support/option args "--target") "evm")
        target (get targets target-name)
        _ (when-not target
            (support/usage-error!
             (str "error: nbb EVM path does not cover target " target-name)))
        _ (when-not (= :evm256-kotoba-v1 (target-profile/backend target))
            (support/usage-error! (str "error: target is not EVM: " target-name)))
        resolved (project-source/resolve-source! args)
        input (:input resolved)
        source (:source resolved)
        linked? (:linked? resolved)
        output (or (support/option args "--output") (str input ".evm"))]
    (merge (compile-uncached! args target output source linked?)
           (project-source/inputs-record resolved))))

(defn- run! [args]
  (case (first args)
    "compile" (compile! args)
    (support/usage-error!
     (str "error: nbb EVM path does not cover command " (first args)))))

;; This namespace is required as a side effect by `test/nbb/run.cljs` (the
;; reachability gate demands every source namespace be reachable from tests),
;; and requiring it with no command-line arguments would otherwise run the
;; CLI against nothing and fail the suite's own run. Execute only when this
;; file was actually named as the entrypoint -- the same guard every other
;; nbb entrypoint draws by checking for a subcommand first.
(when (seq *command-line-args*)
  (support/execute! run!))
