#!/usr/bin/env bb
(require '[clojure.edn :as edn]
         '[clojure.java.io :as io])

;; Run the exact same pure CLJC logic as the test suite: load the namespace
;; source directly so the gate cannot drift from the contract implementation.
(load-file "src/kotoba/lang/capability_values.cljc")
(load-file "src/kotoba/lang/capability_host.cljc")
(load-file "src/kotoba/lang/capability_cacao.cljc")
(alias 'caps 'kotoba.lang.capability-values)
(alias 'host 'kotoba.lang.capability-host)
(alias 'cacao 'kotoba.lang.capability-cacao)

(def root (io/file "."))
(def manifest-path "lang/capability-conformance/manifest.edn")

(defn read-edn [path]
  (edn/read-string (slurp (io/file root path))))

;; lang/capability-conformance/manifest.edn is stored as Datomic/Datascript
;; tx-data (see schema.edn / scripts/edn-datomize.cljs
;; `wrap-map-preserve-ns!`): :kotoba.lang.capability.conformance/version was
;; already namespaced and kept as-is; the plain :cases key got prefixed to
;; :kotoba.lang.capability.conformance/cases and, being a vector-of-maps,
;; pr-str'd into a blob string. Reverse both back to the original
;; {:kotoba.lang.capability.conformance/version _ :cases [...]} shape. The
;; individual fixture files under lang/capability-conformance/{positive,
;; negative}/*.edn are intentionally left untransformed (see repo notes),
;; so `data` reads below stay plain read-edn.
(defn- unblob [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- reconstitute-capability-conformance-manifest [tx-data]
  (let [e (dissoc (first tx-data) :db/id)]
    {:kotoba.lang.capability.conformance/version
     (:kotoba.lang.capability.conformance/version e)
     :cases (unblob (:kotoba.lang.capability.conformance/cases e))}))

(defn fail [msg data]
  (throw (ex-info msg data)))

(let [manifest (reconstitute-capability-conformance-manifest (read-edn manifest-path))]
  (when-not (= 1 (:kotoba.lang.capability.conformance/version manifest))
    (fail "capability conformance version 1 required" manifest))
  (let [results (doall
                 (for [tc (:cases manifest)]
                   (let [data (read-edn (str "lang/capability-conformance/"
                                             (:file tc)))
                         result (case (:type tc)
                                  :host-dispatch (host/check-case tc data)
                                  :cacao-grants (cacao/check-case tc data)
                                  (caps/check-case tc data))]
                     (if (:ok? result)
                       (do (println "ok" (:id tc)) true)
                       (do (println "FAIL" (:id tc) "->" (pr-str (:actual result)))
                           false)))))]
    (when (some false? results)
      (fail "capability conformance cases failed"
            {:failed (count (remove true? results))}))))
