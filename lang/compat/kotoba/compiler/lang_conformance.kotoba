(ns kotoba.compiler.lang-conformance
  "T1.3: dual-backend runner for language pure-product conformance.

  Required backends (from kotoba-lang T1.2 matrix): `:kir` + `:wasm32-kotoba-v1`.
  Pilot fixtures ship under `resources/kotoba/lang-conformance/`. Full matrix
  expansion is progressive as compiler admission covers more fixtures."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [kotoba.artifact.core :as artifact]
            [kotoba.compiler.core :as compiler]
            [kotoba.kir :as ir]
            [clojure.pprint])
  (:import [java.security MessageDigest]
           [java.nio.charset StandardCharsets]))

(def pilot-manifest-resource "kotoba/lang-conformance/pilot-manifest.edn")
(def pilot-root-resource "kotoba/lang-conformance/")
(def pure-product-required #{:kir :wasm32-kotoba-v1})

;; Every backend `run-case` knows how to drive. A `:required-backends` entry
;; outside this set cannot be honoured, and silence about that is what makes it
;; dangerous -- see `run-case`'s own note.
(def known-backends #{:kir :wasm32-kotoba-v1})

(defn load-manifest
  "Load pilot or caller-supplied manifest EDN text/map."
  ([]
   (if-let [url (io/resource pilot-manifest-resource)]
     (load-manifest (slurp url))
     (throw (ex-info "pilot conformance manifest missing"
                     {:resource pilot-manifest-resource}))))
  ([edn-or-map]
   (if (map? edn-or-map)
     edn-or-map
     (edn/read-string edn-or-map))))

(defn pure-product-cases
  [manifest]
  (filterv #(and (= :run (:kind %))
                 (or (= :pure-product-run (:class %))
                     (= pure-product-required (:required-backends %))))
           (:cases manifest)))

(defn case-language-profile
  "T2.1 profile a case is written against. Defaults to `:pure-product`; a case
  that exercises portable-but-not-pure-product surface (dotimes, condp,
  defmethod, threading sugar) must declare `:language-profile :portable`."
  [case]
  (get case :language-profile :pure-product))

(defn- admit-language-profile!
  "T2.3: a case labelled `:pure-product` must pass the T2.1 admission check.
  Executing on both backends is not enough — the runner previously compiled
  with policy {}, so `:language-profile :pure-product` was never applied and
  the headline count silently included cases the profile rejects.

  Runs `check-source` only; artifact bytes and T1.5 goldens are unaffected."
  [source case]
  (when (= :pure-product (case-language-profile case))
    (compiler/check-source source {:language-profile :pure-product} {}))
  nil)

(defn- resolve-source
  "Load case source from classpath under pilot root, or absolute path."
  [entry]
  (let [rel (str pilot-root-resource entry)]
    (if-let [url (io/resource rel)]
      (slurp url)
      (let [f (io/file entry)]
        (when (.isFile f)
          (slurp f))))))

(defn- symbolize-function [function]
  (cond
    (symbol? function) function
    (string? function) (symbol function)
    (keyword? function) (symbol (name function))
    :else 'main))

(defn- case-fuel
  "Optional per-case fuel budget (T7.4 deep loop). Nil → backend default 512."
  [case]
  (when-let [f (:fuel case)]
    (when-not (and (integer? f) (pos? f))
      (throw (ex-info "case :fuel must be a positive integer"
                      {:phase :lang-conformance :fuel f :id (:id case)})))
    f))

(defn- compile-opts
  "emit-metadata map for compile-source when case declares :fuel."
  [case]
  (if-let [f (case-fuel case)]
    {:fuel f}
    {}))

(defn run-kir
  "Compile source → wasm32 path (produces KIR) and execute on KIR oracle.
  Optional 4th arg is a case map (or {:fuel n}) for T7.4 fuel budgets."
  ([source function args] (run-kir source function args {}))
  ([source function args case-or-opts]
   (let [opts (if (map? case-or-opts) (compile-opts case-or-opts) {})
         fuel (or (:fuel opts) (:fuel case-or-opts))
         compiled (compiler/compile-source source :wasm32-kotoba-v1 {} opts)
         fn-sym (symbolize-function function)
         exec-opts (cond-> {} fuel (assoc :fuel fuel))
         result (ir/execute (:kir compiled) fn-sym (vec (or args [])) exec-opts)]
     {:backend :kir
      :ok? true
      :result result
      :fuel fuel
      :wasm-bytes (:bytes compiled)})))

(defn- project-root
  "Best-effort compiler repo root (dir containing runtime/browser-host.mjs)."
  []
  (loop [dir (.getAbsoluteFile (io/file "."))]
    (cond
      (.isFile (io/file dir "runtime/browser-host.mjs")) dir
      (nil? (.getParentFile dir)) (io/file ".")
      :else (recur (.getParentFile dir)))))

(defn run-wasm32
  "Compile to wasm32-kotoba-v1 and execute `main` via Node browser-host runtime.
  Returns {:backend :wasm32-kotoba-v1 :ok? bool :result scalar-or-nil :error ...}.
  Optional 2nd arg is a case map (or {:fuel n}) — fuel is baked into wasm."
  ([source] (run-wasm32 source {}))
  ([source case-or-opts]
   (let [opts (if (map? case-or-opts) (compile-opts case-or-opts) {})
         compiled (compiler/compile-source source :wasm32-kotoba-v1 {} opts)
         bytes (:bytes compiled)]
     (when-not (bytes? bytes)
       (throw (ex-info "wasm compile produced no bytes" {:phase :lang-conformance})))
     (let [encoded (.encodeToString (java.util.Base64/getEncoder) ^bytes bytes)
           root (project-root)
           ;; Same pattern as typed_value_conformance_test (relative import).
           probe (str "import('./runtime/browser-host.mjs').then(async m=>{"
                      "const h=await m.instantiateKotoba(Buffer.from(process.argv[1],'base64'));"
                      "const value=h.instance.exports.main();"
                      "if(typeof value!=='bigint' && typeof value!=='number' && typeof value!=='boolean')process.exit(2);"
                      "console.log(String(value))})")
           res (shell/sh "node" "--input-type=module" "-e" probe encoded
                         :dir (.getAbsolutePath root))]
       (if (zero? (:exit res))
         (let [out (str/trim (:out res))
               parsed (case out
                        "true" true
                        "false" false
                        (try (Long/parseLong out) (catch Exception _ out)))]
           {:backend :wasm32-kotoba-v1
            :ok? true
            :result parsed
            :fuel (:fuel opts)
            :raw-out out})
         {:backend :wasm32-kotoba-v1
          :ok? false
          :error (str "wasm exit " (:exit res) ": " (:err res) (:out res))})))))

(defn run-case
  "Run one pure-product case on required backends. Returns result map.
  Cases may declare `:fuel` (positive int) for deep loop / T7.4 envelopes."
  [case]
  (let [id (:id case)
        entry (:entry case)
        expect (get-in case [:expect :kotoba])
        source (resolve-source entry)
        required (or (:required-backends case) pure-product-required)]
    (if-not source
      {:id id :ok? false :status :missing-source :entry entry}
      (try
        (admit-language-profile! source case)
        (let [kir (when (contains? required :kir)
                    (run-kir source (:function case) (:args case) case))
              wasm (when (contains? required :wasm32-kotoba-v1)
                     (run-wasm32 source case))
              ;; `nil` means "this case does not require that backend", which is a
              ;; legitimate reason not to fail. What is NOT legitimate is every
              ;; backend being nil: the case then asserts nothing and reported
              ;; :passed.
              ;;
              ;; Reachable by a plain typo, and measured 2026-08-19 rather than
              ;; reasoned about: giving a real case `:required-backends #{:typo}`
              ;; yields `{:ok? true :status :passed :kir nil :wasm32-kotoba-v1 nil}`.
              ;; The case stays in the suite -- `pure-product-cases` also selects
              ;; on `:class :pure-product-run`, which all 61 cases carry -- so the
              ;; total does not move either. A renamed backend would convert
              ;; cases into green no-ops without changing a single count.
              ;;
              ;; Unknown names are named rather than merely counted, because
              ;; "nothing ran" and "you asked for a backend that does not exist"
              ;; want different repairs. ADR 0260; same class as ADR 0259.
              unknown (into (sorted-set) (remove known-backends required))
              ran (cond-> #{} kir (conj :kir) wasm (conj :wasm32-kotoba-v1))
              kir-ok? (or (nil? kir) (and (:ok? kir) (= expect (:result kir))))
              wasm-ok? (or (nil? wasm) (and (:ok? wasm) (= expect (:result wasm))))
              ok? (boolean (and kir-ok? wasm-ok? (seq ran) (empty? unknown)))]
          {:id id
           :ok? ok?
           :status (cond (seq unknown) :unknown-backend
                         (empty? ran) :no-backend-ran
                         ok? :passed
                         :else :failed)
           :required required
           :ran ran
           :unknown-backends unknown
           :language-profile (case-language-profile case)
           :expect expect
           :fuel (case-fuel case)
           :kir kir
           :wasm32-kotoba-v1 wasm
           :kir-ok? kir-ok?
           :wasm-ok? wasm-ok?})
        (catch Exception e
          (let [code (:kotoba.error/code (ex-data e))]
            {:id id
             :ok? false
             :status (if (contains? #{:kotoba.error/pure-product-forbidden
                                      :kotoba.error/pure-product-capabilities}
                                    code)
                       :profile-rejected
                       :error)
             :language-profile (case-language-profile case)
             :error (.getMessage e)
             :ex-data (ex-data e)}))))))

(defn run-suite
  "Run all pure-product cases in manifest. Returns aggregate report."
  ([] (run-suite (load-manifest)))
  ([manifest]
   (let [cases (pure-product-cases manifest)
         results (mapv run-case cases)
         passed (count (filter :ok? results))
         failed (filterv (complement :ok?) results)]
     {;; An empty case list used to satisfy `(empty? failed)` and report a pass
      ;; over nothing, the same way ADR 0259's sibling did. Measured:
      ;; `(run-suite {:cases []})` -> `{:ok? true :total 0 :passed 0}`.
      :ok? (boolean (and (seq cases) (empty? failed)))
      :status (if (empty? cases) :no-cases :measured)
      :total (count results)
      :passed passed
      :failed-count (count failed)
      :failed failed
      :results results
      ;; T2.3: how many of the passing cases are actually written against the
      ;; pure-product profile. Reporting one merged number let 5 portable-only
      ;; cases be counted as pure-product coverage.
      :pure-product-passed (count (filter #(and (:ok? %)
                                                (= :pure-product (:language-profile %)))
                                          results))
      :portable-passed (count (filter #(and (:ok? %)
                                            (= :portable (:language-profile %)))
                                      results))
      :required-backends pure-product-required
      :wbs "T1.3"})))


;; --- T1.5 golden digests -------------------------------------------------

(def golden-resource "kotoba/lang-conformance/pilot-golden.edn")

(defn- bytes-sha256-hex
  "SHA-256 of raw bytes as lowercase hex (wasm artifact fingerprint)."
  [^bytes b]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") b)]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) digest))))

(defn- kir-digest-body
  "KIR projection for hashing — select-keys only; artifact/sha256 canonicalizes
  map key order.

  This used to normalize gensym-like symbols to `foo__G0`, `foo__G1` … so that
  digests were reproducible across process runs. They are reproducible now
  because the compiler emits no gensyms: every synthesized name is a function of
  the source (compiler#453 by chain position, #454 on a per-compilation counter,
  and `binding-some` folded in here). Measured across all 52 conformance cases:
  zero symbols matching the `.+__\\d+` pattern the normalization looked for.

  Removing it is not just cleanup. The normalization rewrote by FIRST-SEEN
  ORDER, so a change that altered which synthesized binding appeared where --
  the identity of a temp, not merely its number -- produced the same digest. The
  golden gate could not see it. Now the digest is of the KIR itself."
  [kir]
  (select-keys kir [:format :signature :functions :exports :effects
                    :schemas :schema-identities :blocks :entry :oracle-value]))

(defn digest-case
  "Compile case once; return {:id :kir-sha256 :wasm-sha256 :expect :result}.
  Honors case `:fuel` (T7.4) so digests match dual-backend execution."
  [case]
  (let [source (resolve-source (:entry case))
        expect (get-in case [:expect :kotoba])
        opts (compile-opts case)
        fuel (:fuel opts)]
    (when-not source
      (throw (ex-info "missing source for golden digest" {:id (:id case)
                                                          :entry (:entry case)})))
    (let [compiled (compiler/compile-source source :wasm32-kotoba-v1 {} opts)
          kir (:kir compiled)
          bytes (:bytes compiled)
          exec-opts (cond-> {} fuel (assoc :fuel fuel))
          result (ir/execute kir (symbolize-function (:function case))
                             (vec (or (:args case) [])) exec-opts)]
      {:id (:id case)
       :entry (:entry case)
       :expect expect
       :result result
       :result-ok? (= expect result)
       :fuel fuel
       :kir-sha256 (artifact/sha256 (kir-digest-body kir))
       :wasm-sha256 (bytes-sha256-hex bytes)
       :wasm-byte-count (alength ^bytes bytes)})))

(defn collect-goldens
  "Digest all pure-product pilot cases."
  ([] (collect-goldens (load-manifest)))
  ([manifest]
   (mapv digest-case (pure-product-cases manifest))))

(defn golden-document
  "EDN map written to pilot-golden.edn."
  [rows]
  {:kotoba.lang.conformance.golden/version 1
   :kotoba.lang.conformance.golden/wbs "T1.5"
   :kotoba.lang.conformance.golden/note
   "KIR + wasm32-kotoba-v1 digests for T1.3 pure-product pilot. CI fails on drift."
   :algorithm {:kir "artifact.core/sha256 of select-keys KIR body"
               :wasm "SHA-256 hex of raw wasm bytes"}
   :cases (mapv #(select-keys % [:id :entry :expect :kir-sha256 :wasm-sha256
                                 :wasm-byte-count])
                rows)})

(defn load-goldens
  ([]
   (if-let [url (io/resource golden-resource)]
     (edn/read-string (slurp url))
     (throw (ex-info "pilot golden missing" {:resource golden-resource}))))
  ([edn-or-map]
   (if (map? edn-or-map) edn-or-map (edn/read-string edn-or-map))))

(defn check-goldens
  "Compare live digests to golden file. Returns {:ok? :mismatches :missing :extra}."
  ([] (check-goldens (load-goldens) (collect-goldens)))
  ([golden live-rows]
   (let [g-by-id (into {} (map (juxt :id identity) (:cases golden)))
         l-by-id (into {} (map (juxt :id identity) live-rows))
         ids (set (concat (keys g-by-id) (keys l-by-id)))
         mismatches (vec
                     (keep
                      (fn [id]
                        (let [g (g-by-id id)
                              l (l-by-id id)]
                          (cond
                            (nil? g) {:id id :type :extra-live}
                            (nil? l) {:id id :type :missing-live}
                            (not (:result-ok? l))
                            {:id id :type :result-mismatch
                             :expect (:expect g) :got (:result l)}
                            (or (not= (:kir-sha256 g) (:kir-sha256 l))
                                (not= (:wasm-sha256 g) (:wasm-sha256 l)))
                            {:id id :type :digest-drift
                             :golden (select-keys g [:kir-sha256 :wasm-sha256])
                             :live (select-keys l [:kir-sha256 :wasm-sha256])}
                            :else nil)))
                      (sort ids)))]
     {:ok? (empty? mismatches)
      :case-count (count live-rows)
      :mismatches mismatches
      :wbs "T1.5"})))

(defn write-goldens!
  "Write resources/…/pilot-golden.edn from live digests. Returns path."
  ([]
   (let [rows (collect-goldens)
         doc (golden-document rows)
         ;; Prefer repo resources path when running from compiler root.
         f (io/file "resources/kotoba/lang-conformance/pilot-golden.edn")]
     (io/make-parents f)
     (spit f (with-out-str (clojure.pprint/pprint doc)))
     (.getPath f))))

(defn -main
  "CLI: clojure -M:conformance
         clojure -M:conformance --write-golden
         clojure -M:conformance --check-golden"
  [& args]
  (cond
    (some #{"--write-golden"} args)
    (let [path (write-goldens!)]
      (println "wrote" path)
      (System/exit 0))

    (some #{"--check-golden"} args)
    (let [report (check-goldens)]
      (println "lang-conformance T1.5 golden:"
               (:case-count report) "cases"
               (if (:ok? report) "ok" "DRIFT"))
      (doseq [m (:mismatches report)]
        (println " FAIL" (pr-str m)))
      (System/exit (if (:ok? report) 0 1)))

    :else
    (let [report (run-suite)]
      (println "lang-conformance T1.3 dual-backend:"
               (:passed report) "/" (:total report) "passed"
               (str "(" (:pure-product-passed report) " pure-product, "
                    (:portable-passed report) " portable)"))
      (doseq [f (:failed report)]
        (println " FAIL" (:id f) (:status f) (or (:error f) "")))
      (System/exit (if (:ok? report) 0 1)))))
