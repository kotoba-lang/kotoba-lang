#!/usr/bin/env bb
(require '[clojure.edn :as edn]
         '[clojure.java.io :as io])

;; Run the exact same pure CLJC logic as the test suite: load the namespace
;; source directly so the gate cannot drift from the contract implementation.
(load-file "src/kotoba/lang/capability_values.cljc")
(alias 'caps 'kotoba.lang.capability-values)

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
                         result (caps/check-case tc data)]
                     (if (:ok? result)
                       (do (println "ok" (:id tc)) true)
                       (do (println "FAIL" (:id tc) "->" (pr-str (:actual result)))
                           false)))))]
    (when (some false? results)
      (fail "capability conformance cases failed"
            {:failed (count (remove true? results))}))))
