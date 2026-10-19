(ns github.workflow.harness
  "CLI harness for the portable GitHub workflow contract."
  (:require [clojure.edn :as edn]
            [github.workflow :as workflow]))

(defn run-fixture [fixture owner repo]
  (let [snapshot (workflow/execute-plan (workflow/fixture-transport fixture)
                                        {:owner owner :repo repo})]
    {:snapshot snapshot :health (workflow/health snapshot)}))

(defn -main [& [fixture-path owner repo]]
  (when-not (and fixture-path owner repo)
    (binding [*out* *err*]
      (println "usage: clojure -M:harness <fixture.edn> <owner> <repo>"))
    (System/exit 2))
  (let [result (run-fixture (edn/read-string (slurp fixture-path)) owner repo)]
    (prn result)
    (System/exit (if (get-in result [:health :ready?]) 0 1))))
