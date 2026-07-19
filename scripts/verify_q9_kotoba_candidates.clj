(ns verify-q9-kotoba-candidates
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [kotoba.compiler.core :as compiler]
            [kotoba.compiler.frontend :as frontend]
            [kotoba.runtime :as runtime]
            [kotoba.wasm-exec :as wasm-exec])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]))

(def workspace
  (.getCanonicalFile
   (io/file (or (System/getenv "Q9_WORKSPACE") "../../.."))))
(def audit-path (io/file workspace "orgs/kotoba-lang/kotoba-lang/lang/q9-kotoba-extension-audit.edn"))
(def evidence-path (io/file workspace "orgs/kotoba-lang/kotoba-lang/lang/q9-kotoba-candidate-verification.edn"))
(def toolchain (System/getenv "Q9_TOOLCHAIN"))

(defn sha256 [^String text]
  (let [bytes (.digest (MessageDigest/getInstance "SHA-256")
                       (.getBytes text StandardCharsets/UTF_8))]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) bytes))))

(defn error-fact [^Throwable error]
  (let [data (ex-data error)]
    (cond-> {:message (ex-message error)}
      (:phase data) (assoc :phase (:phase data))
      (:form data) (assoc :form (:form data))
      (:target data) (assoc :target (:target data)))))

(def kernel-operations
  '#{kernel-load-u8 kernel-load-u8-4k kernel-load-u8-16k
     kernel-store-u8 kernel-store-u8-4k kernel-read-cr2 kernel-boot-info
     kernel-read-cr3 kernel-write-cr3 kernel-invlpg kernel-cli kernel-sti
     kernel-hlt kernel-pause kernel-out-u8 kernel-out-u32})

(defn targets-for [hir]
  (if (some #(and (seq? %) (contains? kernel-operations (first %)))
            (tree-seq coll? seq (:functions hir)))
    [:x86_64-aiueos-kernel-v1]
    [:wasm32-kotoba-v1 :js-kotoba-v1]))

(defn compile-target [source policy target]
  (try
    (let [artifact (compiler/compile-source source target policy)]
      {:status :pass :format (:format artifact)})
    (catch Throwable error
      {:status :fail :error (error-fact error)})))

(defn compiler-verification [source]
  (try
    (let [hir (frontend/analyze source)
          effects (set (:effects hir))
          policy {:allow effects}
          targets (targets-for hir)
          results (into {} (map (fn [target]
                                  [target (compile-target source policy target)])
                                targets))
          passed (set (for [[target result] results
                            :when (= :pass (:status result))] target))]
      {:status (if (seq passed) :pass :fail)
       :effects effects :verified-targets passed :targets results})
    (catch Throwable error
      {:status :fail :error (error-fact error)})))

(defn runtime-verification [source]
  (try
    (let [forms (runtime/read-forms source :kotoba)
          artifact (runtime/wasm-binary forms)]
      (if (:kotoba.wasm/ok? artifact)
        {:status :pass :target :kotoba-component-wasm-v1
         :imports (mapv #(select-keys % [:module :field :capability :params :result])
                        (:kotoba.wasm/imports artifact))
         :exports (:kotoba.wasm/exports artifact)
         :byte-count (:kotoba.wasm/byte-count artifact)}
        {:status :fail :error {:phase :wasm :message "runtime artifact rejected"}}))
    (catch Throwable error
      {:status :fail :error (error-fact error)})))

(def conformance-root
  (io/file workspace "orgs/kotoba-lang/kotoba-lang/lang/conformance"))

(defn conformance-case-proof [{:keys [entry prelude function args expect source-paths]}]
  (when (and (contains? expect :kotoba) (not source-paths) entry
             (.endsWith ^String entry ".kotoba"))
    (let [entry-path (io/file conformance-root entry)
          prelude-path (when prelude
                         (io/file workspace "orgs/kotoba-lang/kotoba-lang/lang" prelude))
          source (str (when prelude-path (str (slurp prelude-path) "\n"))
                      (slurp entry-path))
          entry-symbol (symbol (or function "main"))
          renamed '__kotoba_q9_conformance_entry
          forms (runtime/read-forms source :kotoba)
          renamed-forms (mapv (fn [form]
                                (if (and (seq? form) (= 'defn (first form))
                                         (= entry-symbol (second form)))
                                  (cons 'defn (cons renamed (nnext form)))
                                  form)) forms)
          wrapped (conj renamed-forms
                        (list 'defn 'main [] (apply list renamed (or args []))))
          artifact (runtime/wasm-binary wrapped)
          actual (when (:kotoba.wasm/ok? artifact)
                   (wasm-exec/run-main (:kotoba.wasm/binary artifact) []))
          proof {:status (if (= (:kotoba expect) actual) :pass :fail)
                 :expected (:kotoba expect) :actual actual}
          paths (cond-> [(str "orgs/kotoba-lang/kotoba-lang/lang/conformance/" entry)]
                  prelude (conj (str "orgs/kotoba-lang/kotoba-lang/lang/" prelude)))]
      (zipmap paths (repeat proof)))))

(defn conformance-proofs []
  (let [manifest (edn/read-string (slurp (io/file conformance-root "manifest.edn")))]
    (apply merge (keep conformance-case-proof (:cases manifest)))))

(defn lab-project-proofs []
  (if-let [compile-project (ns-resolve 'kotoba.compiler.core 'compile-project)]
    (let [dependency-path "orgs/kotoba-lang/lab/src/kotoba/lab/verification.kotoba"
          root-path "orgs/kotoba-lang/lab/test/kotoba/lab/verification_conformance.kotoba"
          sources {'kotoba.lab.verification (slurp (io/file workspace dependency-path))
                   'kotoba.lab.verification-conformance (slurp (io/file workspace root-path))}]
      (try
        (let [js (compile-project sources 'kotoba.lab.verification-conformance
                                  :js-kotoba-v1)
              wasm (compile-project sources 'kotoba.lab.verification-conformance
                                    :wasm32-kotoba-v1)
              encoded (.encodeToString (java.util.Base64/getEncoder) ^bytes (:bytes wasm))
              host-uri (str (.toURI (.getCanonicalFile
                                     (io/file (or (System/getenv "Q9_COMPILER_ROOT") ".")
                                              "runtime/browser-host.mjs"))))
              probe (shell/sh
                     "node" "--input-type=module" "-e"
                     (str "import('" host-uri "').then(async m=>{"
                          "const h=await m.instantiateKotoba(Buffer.from(process.argv[1],'base64'));"
                          "const v=h.instance.exports.main();console.log(String(v));"
                          "if(v!==42n)process.exit(2)}).catch(e=>{console.error(e);process.exit(70)})")
                     encoded)
              proof {:status (if (and (= :javascript/v1 (:format js))
                                      (= :wasm/v1 (:format wasm))
                                      (zero? (:exit probe))
                                      (= "42" (str/trim (:out probe)))) :pass :fail)
                     :targets [:js-kotoba-v1 :wasm32-kotoba-v1]
                     :project-digest (:project-digest wasm)
                     :runtime-result (str/trim (:out probe))}]
          (zipmap [dependency-path root-path] (repeat proof)))
        (catch Throwable error
          (zipmap [dependency-path root-path]
                  (repeat {:status :fail :error (error-fact error)})))))
    {}))

(defn fixture-verification [path source]
  (try
    (case path
      "orgs/kotoba-lang/kotoba/src/demo_string_host_sugar.kotoba"
      (let [forms (runtime/read-forms source :kotoba)
            lowered (runtime/lower-language-forms forms)
            body (some #(when (and (seq? %) (= 'defn (first %))
                                   (= 'main (second %))) (last %)) lowered)]
        {:status (if (and (= 'sha256-hex (first body))
                          (= 'str-ptr (first (second body)))
                          (= 'str-len (first (nth body 2)))) :pass :fail)
         :kind :structural-lowering})

      "orgs/kotoba-lang/kotoba/src/mesh_bad_route.kotoba"
      (let [artifact (runtime/wasm-binary (runtime/read-forms source :kotoba))]
        {:status (if (and (not (:kotoba.wasm/ok? artifact))
                          (.contains source "totally-unrecognized-op")) :pass :fail)
         :kind :expected-negative-compilation})

      {:status :not-applicable})
    (catch Throwable error
      {:status :fail :error (error-fact error)})))

(defn proof-passes? [proof]
  (or (= :pass (get-in proof [:compiler :status]))
      (= :pass (get-in proof [:component-runtime :status]))
      (= :pass (get-in proof [:project-conformance :status]))
      (= :pass (get-in proof [:fixture-contract :status]))))

(defn verify-one [previous-by-path project-proofs path]
  (let [source (slurp (io/file workspace path))
        digest (sha256 source)
        compiler-proof (compiler-verification source)
        runtime-proof (if (= :pass (:status compiler-proof))
                        {:status :not-required}
                        (runtime-verification source))
        fixture-proof (fixture-verification path source)
        previous (get previous-by-path path)
        retained (if (= digest (:sha256 previous)) (:proofs previous) {})
        proofs (assoc retained (keyword toolchain)
                      {:compiler compiler-proof :component-runtime runtime-proof
                       :project-conformance (get project-proofs path
                                                 {:status :not-applicable})
                       :fixture-contract fixture-proof})
        fixture-verified? (some #(= :pass (get-in % [:fixture-contract :status]))
                                (vals proofs))
        verified? (some proof-passes? (vals proofs))]
    {:path path :sha256 digest
     :status (cond fixture-verified? :canonical-fixture-verified
                   verified? :canonical-verified
                   :else :canonical-rejected)
     :proofs proofs}))

(defn -main [& _]
  (when (str/blank? toolchain)
    (throw (ex-info "Q9_TOOLCHAIN must identify the exact verifier toolchain" {})))
  (let [audit (edn/read-string (slurp audit-path))
        previous (when (.isFile evidence-path)
                   (edn/read-string (slurp evidence-path)))
        previous-by-path (into {} (map (juxt :path identity) (:entries previous)))
        project-proofs (merge (conformance-proofs) (lab-project-proofs))
        candidates (->> (:entries audit)
                        (filter #(contains? #{:canonical-candidate-unverified
                                             :canonical-verified
                                             :canonical-fixture-verified
                                             :canonical-rejected}
                                           (:classification %)))
                        (map :path)
                        sort)
        entries (mapv (partial verify-one previous-by-path project-proofs) candidates)
        counts (frequencies (map :status entries))
        evidence {:kotoba.lang.q9.candidate-verification/version 3
                  :generator "scripts/verify-q9-kotoba-candidates.clj"
                  :compiler-version compiler/compiler-version
                  :last-toolchain toolchain
                  :target-rule
                  :compiler-kernel-or-wasm-js-then-canonical-component-runtime
                  :candidate-count (count entries)
                  :counts counts
                  :entries entries}]
    (spit evidence-path (with-out-str (pprint/pprint evidence)))
    (println "Q9 KOTOBA CANDIDATE VERIFICATION:" (count entries) "paths" counts)))
