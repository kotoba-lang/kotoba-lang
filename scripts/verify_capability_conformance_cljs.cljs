;; CI6 — cross-implementation conformance for the capability layer.
;;
;; The identity half is covered by verify_code_identity_cljs.cljs. This is the
;; other half CI6 asks for: target/quota/revocation/receipt evidence, checked
;; under a second implementation rather than only under Clojure.
;;
;; Every case in lang/capability-conformance/manifest.edn is re-run here under
;; ClojureScript (nbb) against the same fixtures and the same .cljc checkers
;; the JVM test suite uses.
;;
;; What this proves: both implementations reach the same verdict on every
;; fixture, including which denial reason fires. That is stronger than "both
;; pass", because the manifest pins the expected reason per negative case
;; (:denied / :expected-reason / :expected-problem) — a checker that denied for
;; the wrong reason would fail here, not slip through.
;;
;; What it does NOT prove: that the two implementations produce byte-identical
;; receipts. Receipts are compared field-wise by the checkers, not hashed. If
;; receipt bytes ever become a cross-implementation contract, that needs its
;; own frozen-vector table like the identity one.
;;
;; Run from the repository root:
;;
;;   nbb --classpath src scripts/verify_capability_conformance_cljs.cljs

(require '[kotoba.lang.capability-values :as caps]
         '[kotoba.lang.capability-host :as host]
         '[kotoba.lang.capability-cacao :as cacao]
         '[cljs.reader :as reader]
         '["fs" :as fs])

(def root "lang/capability-conformance/")

(defn read-edn [path]
  (reader/read-string (fs/readFileSync path "utf8")))

(def manifest (read-edn (str root "manifest.edn")))

(defn run-case [tc]
  (let [data (read-edn (str root (:file tc)))]
    (case (:type tc)
      :host-dispatch     (host/check-case tc data)
      :component-binding (host/check-binding-case tc data)
      :cacao-grants      (cacao/check-case tc data)
      (caps/check-case tc data))))

(def results
  (mapv (fn [tc]
          (let [outcome (try (run-case tc)
                             (catch :default e
                               {:ok? false :actual {:threw (.-message e)}}))]
            (assoc outcome :id (:id tc) :type (:type tc))))
        (:cases manifest)))

(def failures (remove :ok? results))

(doseq [f failures]
  (println "FAIL" (:id f) "(" (name (:type f)) ")")
  (println "  actual:" (pr-str (:actual f))))

(println)
(if (seq failures)
  (do (println (count failures) "of" (count results)
               "capability conformance cases disagree under ClojureScript")
      (js/process.exit 1))
  (println "ok:" (count results)
           "capability conformance cases reach the same verdict under ClojureScript"
           (str "(" (count (filter #(= :component-binding (:type %)) results))
                " component-binding, "
                (count (filter #(= :host-dispatch (:type %)) results))
                " host-dispatch)")))
