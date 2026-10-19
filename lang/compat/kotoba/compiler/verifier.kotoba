(ns kotoba.compiler.verifier
  (:require [clojure.set :as set]
            [kotoba.compiler.artifact :as artifact]
            [kotoba.compiler.backend.aarch64 :as aarch64]
            [kotoba.compiler.backend.x86-64 :as x86-64]
            [kotoba.compiler.compatibility :as compatibility-profile]
            [kotoba.compiler.ir :as ir]
            [kotoba.compiler.target :as target-profile]))

(defn- reject! [message data]
  (throw (ex-info message (assoc data :phase :verify))))

(def target-contracts
  {:x86_64-kotoba-v1 {:lowering :runtime-sysv-v1 :emit x86-64/emit-program}
   :aarch64-kotoba-v1 {:lowering :runtime-aapcs64-v1 :emit aarch64/emit-program}})

(def ^:private artifact-fields
  #{:format :target :target-profile :value :kir-sha256 :lowering :fuel-abi :context-abi
    :effects :limits :code :program :exports :compatibility :sha256})

(def max-functions 1024)
(def max-expression-nodes 50000)
(def max-lowered-nodes 100000)
(def ^:private max-depth 256)
(def ^:private max-bindings 4096)
(def ^:private max-parameters 5)
(def ^:private max-symbol-chars 128)
(def ^:private arithmetic '#{+ - * quot bit-xor bit-and})
(def ^:private comparisons '#{= < > <= >=})
(def ^:private heap-operations '{pair 2 pair-first 1 pair-second 1})

(defn- valid-name? [value]
  (and (simple-symbol? value) (<= (count (name value)) max-symbol-chars)))

(defn- valid-effect? [effect]
  (and (vector? effect) (= 2 (count effect)) (= :cap/call (first effect))
       (integer? (second effect)) (<= 0 (second effect) 255)))

(defn- bounded-sum [values]
  (reduce (fn [total value] (min (inc max-lowered-nodes) (+ total value)))
          0 values))

(defn- lowered-cost [form env]
  (cond
    (integer? form) 1
    (symbol? form) (get env form 1)
    :else
    (let [[op & args] form]
      (if (= op 'let)
        (let [[bindings body] args
              env' (reduce (fn [current [name value]]
                             (assoc current name (lowered-cost value current)))
                           env (partition 2 bindings))]
          (lowered-cost body env'))
        (bounded-sum (cons 1 (map #(lowered-cost % env) args)))))))

(declare verify-expr!)

(defn- verify-bindings! [bindings locals signatures depth nodes facts]
  (when-not (and (vector? bindings) (even? (count bindings))
                 (<= (quot (count bindings) 2) max-bindings))
    (reject! "runtime KIR let bindings rejected" {}))
  (let [names (take-nth 2 bindings)]
    (when-not (= (count names) (count (distinct names)))
      (reject! "runtime KIR duplicate binding rejected" {})))
  (loop [pairs (partition 2 bindings) env locals]
    (if-let [[name value] (first pairs)]
      (do
        (when-not (valid-name? name)
          (reject! "runtime KIR local name rejected" {:name name}))
        (verify-expr! value env signatures (inc depth) nodes facts)
        (recur (next pairs) (conj env name)))
      env)))

(defn- verify-expr! [form locals signatures depth nodes facts]
  (when (> (vswap! nodes inc) max-expression-nodes)
    (reject! "runtime KIR expression budget exhausted" {}))
  (when (> depth max-depth)
    (reject! "runtime KIR expression depth rejected" {:depth depth}))
  (cond
    (integer? form)
    (when-not (<= Long/MIN_VALUE form Long/MAX_VALUE)
      (reject! "runtime KIR integer is outside i64" {:value form}))

    (symbol? form)
    (when-not (contains? locals form)
      (reject! "runtime KIR contains an unbound symbol" {:symbol form}))

    (seq? form)
    (let [[op & args] form]
      (when-not (simple-symbol? op)
        (reject! "runtime KIR computed call rejected" {:operation op}))
      (cond
        (= op 'let)
        (let [[bindings & body] args]
          (when-not (= 1 (count body))
            (reject! "runtime KIR let arity rejected" {}))
          (verify-expr! (first body)
                        (verify-bindings! bindings locals signatures depth nodes facts)
                        signatures (inc depth) nodes facts))

        (= op 'if)
        (do
          (when-not (= 3 (count args)) (reject! "runtime KIR if arity rejected" {}))
          (doseq [arg args] (verify-expr! arg locals signatures (inc depth) nodes facts)))

        (= op 'do)
        (do
          (when (empty? args) (reject! "runtime KIR do arity rejected" {}))
          (doseq [arg args] (verify-expr! arg locals signatures (inc depth) nodes facts)))

        (= op 'cap-call)
        (let [[cap-id value :as call-args] args]
          (when-not (and (= 2 (count call-args)) (integer? cap-id) (<= 0 cap-id 255))
            (reject! "runtime KIR capability call rejected" {}))
          (vswap! facts update :effects conj [:cap/call cap-id])
          (verify-expr! value locals signatures (inc depth) nodes facts))

        (contains? arithmetic op)
        (do
          (when (or (empty? args) (and (contains? '#{quot bit-xor bit-and} op) (not= 2 (count args))))
            (reject! "runtime KIR arithmetic arity rejected" {:operation op}))
          (doseq [arg args] (verify-expr! arg locals signatures (inc depth) nodes facts)))

        (contains? comparisons op)
        (do
          (when-not (= 2 (count args))
            (reject! "runtime KIR comparison arity rejected" {:operation op}))
          (doseq [arg args] (verify-expr! arg locals signatures (inc depth) nodes facts)))

        (contains? heap-operations op)
        (do
          (when-not (= (get heap-operations op) (count args))
            (reject! "runtime KIR heap operation arity rejected" {:operation op}))
          (doseq [arg args] (verify-expr! arg locals signatures (inc depth) nodes facts)))

        (contains? '#{kernel-load-u8 kernel-load-u8-4k kernel-load-u8-16k
                      kernel-store-u8 kernel-store-u8-4k
                      kernel-load-u32 kernel-store-u32} op)
        (do
          (when-not (= ({'kernel-load-u8 3 'kernel-load-u8-4k 3
                         'kernel-load-u8-16k 3 'kernel-store-u8 4
                         'kernel-store-u8-4k 4
                         'kernel-load-u32 3 'kernel-store-u32 4} op) (count args))
            (reject! "runtime KIR kernel memory operation arity rejected" {:operation op}))
          (doseq [arg args] (verify-expr! arg locals signatures (inc depth) nodes facts)))

        (contains? '#{kernel-boot-info kernel-read-cr2 kernel-read-cr3 kernel-write-cr3 kernel-invlpg
                      kernel-cli kernel-sti kernel-hlt kernel-pause
                      kernel-out-u8 kernel-out-u32} op)
        (do
          (when-not (= ({'kernel-boot-info 0 'kernel-read-cr2 0 'kernel-read-cr3 0 'kernel-write-cr3 1
                         'kernel-invlpg 1 'kernel-cli 0 'kernel-sti 0 'kernel-hlt 0
                         'kernel-pause 0 'kernel-out-u8 2 'kernel-out-u32 2} op)
                       (count args))
            (reject! "runtime KIR kernel privileged operation arity rejected"
                     {:operation op}))
          (doseq [arg args] (verify-expr! arg locals signatures (inc depth) nodes facts)))

        (contains? signatures op)
        (do
          (when-not (= (count (get signatures op)) (count args))
            (reject! "runtime KIR call arity rejected" {:function op}))
          (vswap! facts update :calls conj op)
          (doseq [arg args] (verify-expr! arg locals signatures (inc depth) nodes facts)))

        :else (reject! "runtime KIR operation rejected" {:operation op})))

    :else (reject! "runtime KIR value type rejected" {:value form})))

(defn- infer-effects [direct]
  (loop [inferred (into {} (map (fn [[name facts]] [name (:effects facts)]) direct))]
    (let [next-effects
          (into {} (map (fn [[name {:keys [effects calls]}]]
                          [name (reduce set/union effects (map #(get inferred % #{}) calls))])
                        direct))]
      (if (= inferred next-effects) inferred (recur next-effects)))))

(defn- verify-program! [program]
  (when-not (and (map? program)
                 (= #{:format :entry :exports :signature :effects :functions} (set (keys program)))
                 (= :kotoba.kir/v3 (:format program))
                 (= 'main (:entry program))
                 (= {:params [] :result :i64} (:signature program))
                 (set? (:effects program))
                 (every? valid-effect? (:effects program))
                 (vector? (:functions program))
                 (vector? (:exports program))
                 (<= 1 (count (:functions program)) max-functions))
    (reject! "runtime KIR module shape rejected" {}))
  (let [functions (:functions program)
        signatures
        (into {}
              (map (fn [function]
                     (when-not (and (map? function)
                                    (= #{:name :params :result :effects :body}
                                       (set (keys function)))
                                    (valid-name? (:name function))
                                    (vector? (:params function))
                                    (<= (count (:params function)) max-parameters)
                                    (every? valid-name? (:params function))
                                    (= (count (:params function))
                                       (count (distinct (:params function))))
                                    (= :i64 (:result function))
                                    (set? (:effects function))
                                    (every? valid-effect? (:effects function)))
                       (reject! "runtime KIR function shape rejected" {:function (:name function)}))
                     [(:name function) (:params function)]))
              functions)]
    (when-not (and (= (count functions) (count signatures)) (contains? signatures 'main)
                   (empty? (get signatures 'main))
                   (= (count (:exports program)) (count (distinct (:exports program))))
                   (every? #(contains? signatures %) (:exports program))
                   (some #{'main} (:exports program)))
      (reject! "runtime KIR entry or function identity rejected" {}))
    (let [nodes (volatile! 0)
          direct
          (into {}
                (map (fn [function]
                       (let [facts (volatile! {:effects #{} :calls #{}})]
                         (verify-expr! (:body function) (set (:params function))
                                       signatures 0 nodes facts)
                         [(:name function) @facts])))
                functions)
          inferred (infer-effects direct)
          declared (into {} (map (juxt :name :effects) functions))
          total (reduce set/union #{} (vals inferred))
          cost (bounded-sum (map #(lowered-cost (:body %) {}) functions))]
      (when-not (= inferred declared)
        (reject! "runtime KIR function effects rejected" {}))
      (when-not (= total (:effects program))
        (reject! "runtime KIR module effects rejected" {}))
      (when (> cost max-lowered-nodes)
        (reject! "runtime KIR lowering budget exhausted" {:cost cost}))))
  program)

(defn- verify-runtime! [{:keys [target program code exports lowering limits fuel-abi context-abi]
                         profile-value :target-profile}]
  (let [backend (target-profile/backend target)
        expected-profile (target-profile/profile target)
        {expected-lowering :lowering emit :emit} (get target-contracts backend)]
    (when (and (not (contains? #{:x86_64-aiueos-kernel-v1 :aarch64-aiueos-kernel-v1} target))
               (some #(and (seq? %) (contains? '#{kernel-load-u8 kernel-load-u8-4k
                                                  kernel-load-u8-16k kernel-store-u8
                                                  kernel-store-u8-4k kernel-load-u32 kernel-store-u32
                                                  kernel-boot-info kernel-read-cr2
                                                  kernel-read-cr3 kernel-write-cr3 kernel-invlpg
                                                  kernel-cli kernel-sti kernel-hlt kernel-pause
                                                  kernel-out-u8 kernel-out-u32} (first %)))
                     (tree-seq coll? seq (:functions program))))
      (reject! "bounded kernel memory operation requires the aiueos kernel target"
               {:target target}))
    (when-not (= expected-profile profile-value)
      (reject! "native target profile does not match target identity" {:target target}))
    (when-not emit (reject! "not a native verifier target" {:target target}))
    (when-not (= expected-lowering lowering)
      (reject! "native runtime lowering mode is not admitted"
               {:target target :lowering lowering}))
    (when-not (= :kotoba.kir/v3 (:format program))
      (reject! "native artifact requires runtime KIR v3"
               {:target target :program-format (:format program)}))
    (verify-program! program)
    (let [expected (try (emit program)
                        (catch Exception e
                          (reject! "runtime KIR cannot be safely lowered"
                                   {:target target :cause (.getMessage e)})))]
      (when-not (= (:exports expected) exports)
        (reject! "native export table rejected" {:target target}))
      (when-not (= (:code expected) code)
        (reject! "native instruction stream rejected" {:target target})))
    (let [expected-fuel-abi (case backend
                              :x86_64-kotoba-v1 {:mode :hidden-context-r9 :initial 256}
                              :aarch64-kotoba-v1 {:mode :hidden-context-x7 :initial 256})
          expected-limits {:memory-bytes 65536
                           :fuel 256
                           :stack-bytes 4096}
          expected-context {:version 2 :fuel-offset 8 :allow-bitmap-offset 16
                            :allow-bitmap-bytes 32 :cap-call-offset 48
                            :pair-new-offset 56 :pair-first-offset 64
                            :pair-second-offset 72 :pair-capacity 4096}]
      (when-not (= expected-fuel-abi fuel-abi)
        (reject! "fuel ABI is not admitted" {:target target :fuel-abi fuel-abi}))
      (when-not (= expected-limits limits)
        (reject! "resource limits are not admitted" {:target target :limits limits}))
      (when-not (= expected-context context-abi)
        (reject! "execution context ABI is not admitted"
                 {:target target :context-abi context-abi})))))

(defn verify-artifact! [{:keys [format target target-profile code effects kir-sha256 compatibility] :as kexe}]
  (when-not (and (map? kexe) (= artifact-fields (set (keys kexe))))
    (reject! "native artifact schema rejected" {}))
  (when-not (= :kotoba.kexe/v1 format) (reject! "unknown artifact format" {}))
  (when-not (and (string? kir-sha256) (re-matches #"[0-9a-f]{64}" kir-sha256))
    (reject! "missing or malformed KIR identity" {}))
  (when-not (= effects (get-in kexe [:program :effects]))
    (reject! "artifact effects do not match runtime KIR" {}))
  (when-not (every? #(and (vector? %) (= :cap/call (first %))
                          (= 2 (count %)) (integer? (second %))
                          (<= 0 (second %) 255)) effects)
    (reject! "native artifact contains an unsupported effect" {:effects effects}))
  (when-not (and (vector? code) (<= 1 (count code) (* 1024 1024))
                 (every? #(and (integer? %) (<= 0 % 255)) code))
    (reject! "malformed code bytes" {}))
  (when-not (artifact/valid-seal? kexe) (reject! "artifact integrity mismatch" {}))
  (when-not (= kir-sha256 (artifact/sha256 (:program kexe)))
    (reject! "runtime KIR identity mismatch" {}))
  (verify-runtime! kexe)
  (let [expected (compatibility-profile/descriptor
                  {:hir-format :kotoba.hir/v2
                   :kir-format (get-in kexe [:program :format])
                   :target target :target-profile target-profile
                   :value-abi :kotoba.i64/direct-v1})]
    (when-not (= expected compatibility)
      (reject! "native compatibility metadata rejected" {:target target})))
  (let [kernel-operations '#{kernel-load-u8 kernel-load-u8-4k kernel-load-u8-16k
                             kernel-store-u8 kernel-store-u8-4k kernel-read-cr2
                             kernel-load-u32 kernel-store-u32
                             kernel-boot-info kernel-read-cr3 kernel-write-cr3 kernel-invlpg
                             kernel-cli kernel-sti kernel-hlt kernel-pause
                             kernel-out-u8 kernel-out-u32}
        kernel-native? (some #(and (seq? %) (contains? kernel-operations (first %)))
                             (tree-seq coll? seq (get-in kexe [:program :functions])))
        expected-value
        (when (and (empty? effects) (not kernel-native?))
          (try
            (ir/execute (:program kexe) (get-in kexe [:program :entry]) [])
            (catch Exception error
              (reject! "native artifact oracle evaluation rejected"
                       {:cause (.getMessage error)}))))]
    (when-not (= expected-value (:value kexe))
      (reject! "native artifact oracle value rejected" {})))
  kexe)
