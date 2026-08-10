(ns kotoba.lang.pure-product-examples
  "T2.3: pure-product examples under examples/ compile + KIR-execute.

  Manifest: examples/pure-product-examples.edn
  Run: clojure -M:pure-product-examples
  Requires sibling checkout of kotoba-lang/amu (or override COMPILER_ROOT)."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [kotoba.compiler.core :as compiler]
            [kotoba.kir :as kir]))

(def manifest-resource "examples/pure-product-examples.edn")

(defn load-manifest
  ([] (load-manifest (or (System/getenv "KOTOBA_LANG_ROOT") ".")))
  ([root]
   (let [f (io/file root "examples/pure-product-examples.edn")]
     (when-not (.exists f)
       (throw (ex-info "pure-product examples manifest missing"
                       {:path (.getPath f)})))
     (edn/read-string (slurp f)))))

(defn- resolve-export [sym]
  (cond
    (symbol? sym) sym
    (keyword? sym) (symbol (name sym))
    (string? sym) (symbol sym)
    :else (throw (ex-info "export must be symbol/keyword/string" {:export sym}))))

(defn run-example
  "Compile PATH under pure-product; execute each case. Returns result map."
  [root {:keys [path cases]}]
  (let [f (io/file root path)
        src (slurp f)
        compiled
        (try
          (compiler/compile-source src :wasm32-kotoba-v1 {}
                                   {:language-profile :pure-product})
          (catch Exception e
            {:error :compile :message (.getMessage e)
             :class (.getName (class e))}))]
    (if (:error compiled)
      {:path path :ok? false :error (:error compiled)
       :message (:message compiled) :cases []}
      (let [kir (:kir compiled)
            case-results
            (mapv
             (fn [{:keys [export args expect]}]
               (let [sym (resolve-export export)
                     args (or args [])]
                 (try
                   (let [got (kir/execute kir sym args)
                         ok? (= got expect)]
                     {:export sym :ok? ok? :got got :expect expect
                      :args args})
                   (catch Exception e
                     {:export sym :ok? false
                      :error (.getMessage e)
                      :expect expect :args args}))))
             cases)
            failed (filterv (complement :ok?) case-results)]
        {:path path
         :ok? (empty? failed)
         :cases case-results
         :failed failed}))))

(defn run-all
  "Run every manifest example. Returns {:ok? bool :results [...] :failed [...]}."
  ([] (run-all (or (System/getenv "KOTOBA_LANG_ROOT") ".")))
  ([root]
   (let [m (load-manifest root)
         results (mapv #(run-example root %) (:examples m))
         failed (filterv (complement :ok?) results)]
     {:ok? (empty? failed)
      :total (count results)
      :passed (- (count results) (count failed))
      :failed failed
      :results results
      :version (:kotoba.pure-product-examples/version m)})))

(defn -main [& _args]
  (let [root (or (System/getenv "KOTOBA_LANG_ROOT") ".")
        r (run-all root)]
    (println "T2.3 pure-product examples:"
             (:passed r) "/" (:total r)
             (if (:ok? r) "passed" "FAILED"))
    (doseq [ex (:results r)]
      (if (:ok? ex)
        (println "  OK " (:path ex)
                 (str "(" (count (:cases ex)) " cases)"))
        (do
          (println "  FAIL" (:path ex)
                   (or (:message ex) (pr-str (:failed ex))))
          (doseq [c (:failed ex)]
            (println "       " (:export c)
                     "got" (pr-str (or (:got c) (:error c)))
                     "expect" (pr-str (:expect c)))))))
    (System/exit (if (:ok? r) 0 1))))
