(ns kotoba.shell.stack-e2e
  "Load-bearing process-boundary slice: aiueos decides, kototama executes,
   shell commits, and kotobase persists one correlated receipt."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def stack-schema "kotoba.shell.stack-e2e.v0")
(def audit-stream :kotoba.shell/stack-e2e)

(defn- subprocess
  "Run one stack participant with a bounded wait.

  The stack gate is often invoked on a laptop while a dependency is cold.  A
  participant must never be able to hang the shell CLI indefinitely, and the
  output reader must run concurrently so a verbose compiler cannot fill the
  process pipe and deadlock the gate."
  ([dir args] (subprocess dir args 60000))
  ([dir args timeout-ms]
   (let [p (-> (ProcessBuilder. ^"[Ljava.lang.String;" (into-array String args))
               (.directory (io/file dir))
               (.redirectErrorStream true)
               (.start))
         output-future (future (slurp (.getInputStream p)))
         completed? (.waitFor p (long timeout-ms) java.util.concurrent.TimeUnit/MILLISECONDS)
         _ (when-not completed? (.destroyForcibly p))
         output (deref output-future 2000 "")]
     {:exit (when completed? (.exitValue p))
      :output output
      :timed-out? (not completed?)})))

(defn- last-edn [output]
  (some->> (str/split-lines output)
           (keep #(try (edn/read-string %) (catch Exception _ nil)))
           last))

(defn run
  [{:keys [wasm-path source-path app-source-path aiueos-dir kototama-dir kotobase-dir
           shell-commit store-instance correlation-id]
    :or {correlation-id "kotoba-shell-stack-e2e"}}]
  (load-file (str (io/file kotobase-dir "src/kotobase/store.cljc")))
  (load-file (str (io/file kotobase-dir "src/kotobase/local.cljc")))
  (let [local-store (ns-resolve 'kotobase.local 'local-store)
        append! (ns-resolve 'kotobase.store '-append)
        read! (ns-resolve 'kotobase.store '-read)
        db (or store-instance (local-store))
        ai-expr (str "(require '[aiueos.cli :as c]) (println (pr-str "
                     "(c/command-result (c/read-contract) :verify "
                     "{:aiueos/manifest {:aiueos/component :kotoba-shell/e2e "
                     ":aiueos/kind :service :aiueos/trust :verified "
                     ":aiueos/imports #{} :aiueos/exports #{}}})))")
        ai-run (subprocess aiueos-dir ["clojure" "-M" "-e" ai-expr])
        ai-result (last-edn (:output ai-run))
        granted? (and (not (:timed-out? ai-run))
                      (zero? (:exit ai-run))
                      (= :grant (:aiueos/decision ai-result)))
        ;; A cold kototama/Clojure dependency cache can exceed one minute on
        ;; CI and fresh workstations. Keep the generic participant timeout
        ;; tight, but give the compiler-backed tender boundary three minutes.
        tender-run (when granted?
                     (subprocess kototama-dir ["clojure" "-M:cli" "run" wasm-path] 180000))
        guest-ok? (and tender-run (not (:timed-out? tender-run))
                       (zero? (:exit tender-run))
                       (re-find #":result 120" (:output tender-run)))
        surface (when guest-ok? (shell-commit [[:dom/create-element 1 :main]
                                               [:dom/set-text 1 "Kotoba ready"]
                                               [:dom/set-root 1]]))
        surface-ok? (true? (:kotoba.cli/ok? surface))
        receipt {:schema stack-schema :correlation-id correlation-id
                 :source {:path source-path :present? (.isFile (io/file source-path))}
                 :app-source {:path app-source-path :present? (.isFile (io/file app-source-path))}
                 :aiueos {:granted? granted? :exit (:exit ai-run)
                          :timed-out? (:timed-out? ai-run)}
                 :kototama {:ok? (boolean guest-ok?) :result (when guest-ok? 120)
                            :exit (:exit tender-run)
                            :timed-out? (:timed-out? tender-run)}
                 :shell {:committed? surface-ok?
                         :ops-count (get-in surface [:kotoba.cli/data :kotoba.shell/ops-count])}
                 :ready? (and granted? guest-ok? surface-ok?
                              (.isFile (io/file source-path))
                              (.isFile (io/file app-source-path)))}
        persisted (append! db audit-stream receipt)
        replay (last (read! db audit-stream 0))]
    (assoc receipt :kotobase {:persisted? (= persisted replay) :seq (:seq replay)}
                   :ready? (and (:ready? receipt) (= persisted replay)))))
