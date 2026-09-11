#!/usr/bin/env bb
(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[babashka.deps :as deps])

;; capability-host's strict causal path validates the shared grant contract.
;; Resolve exactly those declared dependencies (and their transitive
;; contracts), while keeping this script on the same source implementation
;; as the tests. After 832a44b, capability_values.cljc / capability_cacao.cljc
;; require kotoba.lang.coll and kotoba.lang.text instead of clojure.set /
;; clojure.string; grant alone no longer puts those namespaces on the bb
;; classpath (CI: FileNotFoundException on kotoba.lang.coll).
(let [declared (edn/read-string (slurp "deps.edn"))
      coordinates '[io.github.kotoba-lang/grant
                    io.github.kotoba-lang/coll
                    io.github.kotoba-lang/text]]
  (deps/add-deps {:paths ["src"]
                  :deps (into {}
                              (keep (fn [c]
                                      (when-let [coord (get-in declared [:deps c])]
                                        [c coord])))
                              coordinates)}))

;; Run the exact same pure CLJC logic as the test suite: load the namespace
;; source directly so the gate cannot drift from the contract implementation.
(load-file "src/kotoba/lang/capability_values.cljk")
(load-file "src/kotoba/lang/causal_receipt.cljk")
(load-file "src/kotoba/lang/capability_host.cljk")
(load-file "src/kotoba/lang/capability_cacao.cljk")
(alias 'caps 'kotoba.lang.capability-values)
(alias 'host 'kotoba.lang.capability-host)
(alias 'cacao 'kotoba.lang.capability-cacao)

(def root (io/file "."))
(def manifest-path "lang/capability-conformance/manifest.edn")

(defn read-edn [path]
  (edn/read-string (slurp (io/file root path))))

(defn fail [msg data]
  (throw (ex-info msg data)))

(let [manifest (read-edn manifest-path)]
  (when-not (= 1 (:kotoba.lang.capability.conformance/version manifest))
    (fail "capability conformance version 1 required" manifest))
  (let [results (doall
                 (for [tc (:cases manifest)]
                   (let [data (read-edn (str "lang/capability-conformance/"
                                             (:file tc)))
                         result (case (:type tc)
                                  :host-dispatch (host/check-case tc data)
                                  :component-binding (host/check-binding-case tc data)
                                  :cacao-grants (cacao/check-case tc data)
                                  (caps/check-case tc data))]
                     (if (:ok? result)
                       (do (println "ok" (:id tc)) true)
                       (do (println "FAIL" (:id tc) "->" (pr-str (:actual result)))
                           false)))))]
    (when (some false? results)
      (fail "capability conformance cases failed"
            {:failed (count (remove true? results))}))))
