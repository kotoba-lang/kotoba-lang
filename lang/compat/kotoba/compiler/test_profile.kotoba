(ns kotoba.compiler.test-profile
  "One-source Kotoba test runner.

  Exported zero-arity functions whose names start with `test-` are tests and
  pass only when they return i64 1. `test-handler(cap-id,value)` is an optional
  Kotoba-defined deterministic ability handler used by every target. The same
  checked KIR is executed by the JVM oracle, restricted ESM, and Wasm."
  (:require [json.data-json :as json]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [kotoba.artifact.core :as artifact]
            [kotoba.compiler.core :as compiler]
            [kotoba.compiler.effect-row :as effect-row]
            [kotoba.sema :as sema]
            [kotoba.kir :as ir]))

(def targets [:jvm-kir :js :wasm])

(defn- harness-source [source]
  (let [hir (sema/analyze source)]
    (if (some #(= 'main (:name %)) (:functions hir))
      source
      ;; The admitted Wasm browser host requires the conventional `main`
      ;; lifecycle export. It is harness plumbing, never a test expectation.
      (str (str/replace-first source #"\(:export\s+\["
                              "(:export [main ")
           "\n(defn main [] 0)\n"))))

(defn- tests-in [kir]
  (let [functions (into {} (map (juxt :name identity)) (:functions kir))]
    (->> (:exports kir)
         (filter #(and (str/starts-with? (name %) "test-")
                       (not= 'test-handler %)))
         (mapv (fn [test-name]
                 (let [function (get functions test-name)]
                   (when-not (and function (empty? (:params function)))
                     (throw (ex-info "Kotoba tests must be exported zero-arity functions"
                                     {:phase :test :test test-name})))
                   test-name))))))

(defn- test-policy
  "A policy granting exactly the row's capability calls. `:abort` is not a
  grant (see `kotoba.compiler.effect-row`) and has no `first` to compare."
  [checked]
  {:allow (effect-row/grants (get-in checked [:hir :effects]))})

(defn- jvm-results [kir tests]
  (letfn [(handler [cap-id value]
            (ir/execute kir 'test-handler [cap-id value]))]
    (mapv (fn [test-name]
            (try
              ;; same rule as the js/wasm probes above
              {:test test-name
               :ok (contains? #{true 1} (ir/execute kir test-name [] {:cap-call handler}))}
              (catch Exception error
                {:test test-name :ok false :error (or (ex-message error) "test trap")})))
          tests)))

(defn- node-run [target program]
  (let [result (shell/sh "node" "--input-type=module" "-e" program)]
    (when-not (zero? (:exit result))
      (throw (ex-info "Kotoba target test process failed"
                      {:phase :test :target target :stderr (:err result)})))
    (json/read-str (:out result) {:key-fn keyword})))

(defn- encoded-text [text]
  (.encodeToString (java.util.Base64/getEncoder)
                   (.getBytes ^String text "UTF-8")))

(defn- capability-ids
  "The wire ids the js/wasm probes must serve. Only `[:cap/call id]` members
  carry one; a control effect such as `:abort` is a keyword and destructuring
  it as a pair would throw before the first test ran."
  [kir]
  (->> (:effects kir)
       (filter effect-row/grant?)
       (map second)
       distinct
       sort
       vec))

(defn- js-results [source tests cap-ids]
  (let [encoded (encoded-text source)
        names (json/write-str (mapv name tests))
        ids (json/write-str cap-ids)]
    (node-run :javascript
     (str "const names=" names ",ids=" ids ";"
          "const m=await import('data:text/javascript;base64," encoded "');"
          "let x;"
          "const grants=Object.fromEntries(ids.map(id=>[id,value=>"
          "x['test-handler'](BigInt(id),value)]));"
          "x=m.instantiateKotoba(grants);"
          ;; A test IS a predicate. Under language profile 5 a comparison infers
          ;; `:bool`, so a test returns a boolean on every target as soon as
          ;; result inference reaches it; 1n stays accepted for the profile-4
          ;; deprecation window (lang/version-policy.edn). Stating the rule here
          ;; rather than after it breaks -- measured 2026-07-31, `test-pure`
          ;; alone infers `:bool` while the three-function module in
          ;; test_profile_test still infers `:i64`, so which side of this the
          ;; runner lands on today depends on inference reach, not on intent.
          "const pass=v=>v===true||v===1n;"
          "const out=names.map(name=>{try{return {test:name,ok:pass(x[name]())}}"
          "catch(e){return {test:name,ok:false,error:'test trap'}}});"
          "console.log(JSON.stringify(out));"))))

(defn- wasm-results [bytes tests cap-ids]
  (let [encoded (.encodeToString (java.util.Base64/getEncoder) bytes)
        names (json/write-str (mapv name tests))
        ids (json/write-str cap-ids)]
    (node-run
     :wasm
     (str "const names=" names ",ids=" ids ";"
          "const bytes=Uint8Array.from(Buffer.from('" encoded "','base64'));"
          "const host=await import('./runtime/browser-host.mjs');"
          "let instance;"
          "const loaded=await host.instantiateKotoba(bytes,{allowCapabilities:ids,"
          "capCall:(id,value)=>instance.exports['test-handler'](BigInt(id),value)});"
          "instance=loaded.instance;"
          "const pass=v=>v===true||v===1n;"
          "const out=names.map(name=>{try{return {test:name,ok:pass(instance.exports[name]())}}"
          "catch(e){return {test:name,ok:false,error:'test trap'}}});"
          "console.log(JSON.stringify(out));"))))

(defn run-source
  "Run one Kotoba source's exported `test-*` definitions on all semantic
  targets. Returns a deterministic report; target adapters never define test
  expectations."
  [source]
  (let [source (harness-source source)
        checked {:hir (sema/analyze source)}
        policy (test-policy checked)
        js (compiler/compile-source source :js-kotoba-v1 policy)
        wasm (compiler/compile-source source :wasm32-kotoba-v1 policy)
        kir (:kir js)
        tests (tests-in kir)
        cap-ids (capability-ids kir)
        _ (when (empty? tests)
            (throw (ex-info "no exported test-* definitions" {:phase :test})))
        results {:jvm-kir (jvm-results kir tests)
                 :js (js-results (:source js) tests cap-ids)
                 :wasm (wasm-results (:bytes wasm) tests cap-ids)}
        failed (vec (for [[target cases] results
                          case cases
                          :when (not (:ok case))]
                      (assoc case :target target)))]
    {:format :kotoba.test-report/v1
     :ok (empty? failed)
     ;; NOT a CID, and it was called one until 2026-08-10. It is a sha2-256
     ;; over selected KIR keys — the same codec-mislabelling
     ;; `kotoba-lang/lang/code-identity.edn` records as a defect of DefCID
     ;; payload v1, which addressed pr-str output under a dag-cbor label.
     ;;
     ;; A real DefCID seals six inputs, two of which this compiler cannot
     ;; supply: it computes no desugar-contract-version under any name, and
     ;; its profile-version disagrees with the release policy's. Naming this
     ;; field a CID invited exactly the substitution those fields exist to
     ;; prevent. See com-junkawasaki ADR-2608100400.
     :test-kir-sha256 (artifact/sha256
                       (select-keys kir [:format :exports :signature
                                         :effects :functions]))
     :tests tests
     :targets targets
     :results results
     :failed failed}))
