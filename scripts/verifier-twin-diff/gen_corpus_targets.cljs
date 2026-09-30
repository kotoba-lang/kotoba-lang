(ns gencorpus2
  (:require ["node:fs" :as fs] [clojure.string :as str]
            [kotoba.sema :as sema] [kotoba.kir :as kir]
            [kotoba.artifact.core :as artifact]
            [kotoba.kir.compatibility :as compatibility]
            [kotoba.kir.target :as target]
            [kotoba.native.x86-64 :as x86-64]
            [kotoba.native.aarch64 :as aarch64]))
(def context-abi
  {:version 11 :fuel-offset 8 :allow-bitmap-offset 16 :allow-bitmap-bytes 32 :cap-call-offset 48
   :pair-new-offset 56 :pair-first-offset 64 :pair-second-offset 72 :pair-capacity 4096
   :kgraph-assert-offset 80 :kgraph-get-offset 88 :kgraph-count-offset 96 :kgraph-entity-at-offset 104
   :kgraph-capacity 4096 :string-equal-offset 112 :string-concat-offset 120 :typed-cap-call-offset 128
   :string-substring-offset 136 :string-code-point-at-offset 144 :string-pool-capacity 65536
   :vector-new-empty-offset 152 :vector-conj-offset 160 :vector-count-offset 168 :vector-at-offset 176
   :vector-assoc-offset 184 :vector-drop-offset 192 :vector-alloc-offset 200 :vector-assoc-in-place-offset 208
   :string-index-of-offset 216 :arena-enter-offset 224 :arena-leave-offset 232 :string-compare-offset 240
   :string-fold-ascii-offset 248 :string-find-blank-offset 256 :string-skip-blank-offset 264
   :string-index-of-from-offset 272 :string-compare-lines-offset 280 :pair-used-pointer-offset 288
   :pairs-base-offset 296 :pair-validated-base-offset 304 :vector-used-pointer-offset 312
   :vectors-base-offset 320 :vector-items-base-offset 328 :string-find-byte-offset 336
   :string-append-range-offset 344 :string-from-utf8-offset 352 :bytes-from-vector-offset 360
   :bytes-slice-offset 368 :bytes-concat-offset 376 :vector-capacity 4096 :vector-item-capacity 65536})
(def max-fuel 9007199254740991)
(defn build [program tgt backend {:keys [fuel unmetered marker value?]}]
  (let [emit (if (= backend :x86_64) x86-64/emit-program aarch64/emit-program)
        emitted (emit program)
        profile (target/profile tgt)
        typed? (= :kotoba.kir/v4 (:format program))
        initial (if unmetered max-fuel fuel)
        base {:format :kotoba.kexe/v1 :target tgt :target-profile profile
              :value (when (and value? (empty? (:effects program)) (some? (:entry program)))
                       (kir/execute program (:entry program) [] {:fuel fuel}))
              :kir-sha256 (artifact/sha256 program)
              :lowering (if (= backend :x86_64) :runtime-sysv-v1 :runtime-aapcs64-v1)
              :fuel-abi {:mode (if (= backend :x86_64) :hidden-context-r9 :hidden-context-x7) :initial initial}
              :context-abi context-abi :effects (:effects program)
              :compatibility (compatibility/descriptor
                              {:hir-format (if typed? :kotoba.hir/v3 :kotoba.hir/v2) :kir-format (:format program)
                               :target tgt :target-profile profile
                               :value-abi (cond (kotoba.kir/uses-f32? program) :kotoba.typed/mixed-f32-f64-v3
                                                (kotoba.kir/uses-f64? program) :kotoba.typed/mixed-f64-v2
                                                typed? :kotoba.typed/externref-v1 :else :kotoba.i64/direct-v1)})
              :limits (if unmetered
                        {:memory-bytes 65536 :fuel fuel :stack-bytes 4096 :fuel-metering :off}
                        {:memory-bytes 65536 :fuel fuel :stack-bytes 4096})
              :code (mapv #(bit-and (int %) 0xff) (:code emitted))
              :program program :exports (:exports emitted)}]
    (artifact/seal (if marker
                     (assoc base :oracle {:status :inconclusive :trap :budget/fuel :fuel fuel})
                     base))))
(defn out! [name a] (.writeFileSync fs (str "/private/tmp/corpus2/" name ".kexe.edn") (binding [*print-namespace-maps* false] (pr-str (artifact/edn-safe a)))) (println "wrote" name))
(defn try! [name thunk]
  (try
    (out! name (thunk))
    (catch :default e
      (let [m (str (ex-message e))]
        (println "no" name (subs m 0 (min 70 (count m))))))))
(defn compile-example [f]
  (let [src (.readFileSync fs (str "/private/tmp/wt-A-amu-measure/examples/" f) "utf8")
        k (kir/lower (sema/analyze src {}))
        p (select-keys k [:format :entry :exports :signature :effects :functions :schemas])]
    (if (nil? (:schemas p)) (dissoc p :schemas) p)))
;; unmetered, several small programs
(doseq [f ["w1-pure.kotoba" "fuel.kotoba" "heap.kotoba" "structured.kotoba"]]
  (let [p (compile-example f)
        n (str/replace f ".kotoba" "")]
    (try! (str n ".unmetered.x86") #(build p :x86_64-kotoba-v1 :x86_64 {:fuel 512 :unmetered true :value? true}))
    (try! (str n ".unmetered.a64") #(build p :aarch64-macos-kotoba-v1 :aarch64 {:fuel 512 :unmetered true :value? true}))
    (try! (str n ".linux-static.a64") #(build p :aarch64-linux-static-kotoba-v1 :aarch64 {:fuel 512 :value? true}))
    (try! (str n ".linux-static.x86") #(build p :x86_64-linux-static-kotoba-v1 :x86_64 {:fuel 512 :value? true}))
    (try! (str n ".windows.x86") #(build p :x86_64-windows-kotoba-v1 :x86_64 {:fuel 512 :value? true}))))
;; inconclusive oracle marker
(doseq [f ["oracle-fuel-budget.kotoba" "recursive-list.kotoba"]]
  (let [p (compile-example f) n (str/replace f ".kotoba" "")]
    (try! (str n ".inconclusive.x86") #(build p :x86_64-kotoba-v1 :x86_64 {:fuel 512 :marker true :value? false}))
    (try! (str n ".inconclusive.a64") #(build p :aarch64-macos-kotoba-v1 :aarch64 {:fuel 512 :marker true :value? false}))))
;; kernel / uefi
(doseq [f ["aiueos-probe.kotoba" "aiueos-uefi-console.kotoba" "aiueos-uefi-literals.kotoba" "aiueos-uefi-scratch.kotoba"]]
  (let [p (compile-example f) n (str/replace f ".kotoba" "")]
    (try! (str n ".kernel.x86") #(build p :x86_64-aiueos-kernel-v1 :x86_64 {:fuel 512 :value? true}))
    (try! (str n ".uefi.x86") #(build p :x86_64-aiueos-uefi-v1 :x86_64 {:fuel 512 :value? true}))
    (try! (str n ".hosted.x86") #(build p :x86_64-kotoba-v1 :x86_64 {:fuel 512 :value? true}))))
