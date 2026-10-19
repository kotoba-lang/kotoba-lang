(ns kekkai.cli
  "Minimal JVM entrypoint for `kekkai.query` against an EDN-seeded MemStore —
  no StateGraph/checkpointer/advisor spun up, just a status read. For a
  process boundary consumer (murakumo runs on babashka, kekkai rides
  langgraph/JVM) that needs one node's admission status without an
  in-process require across runtimes.

  Usage: `clojure -M -m kekkai.cli <ledger.edn> <node-id>` — prints the
  status (\"authorized\"/\"pending\"/\"expired\"/\"revoked\"/\"unknown\") and
  exits 0 on \"authorized\", 1 otherwise (so callers can also just check the
  exit code).

  <ledger.edn> holds the same shape as `kekkai.store/demo-data`'s :nodes map
  (at minimum {:nodes {\"<id>\" {:status \"authorized\" ...}}})."
  (:require [clojure.edn :as edn]
            [kekkai.query :as query]
            [kekkai.store :as store]))

(defn -main [ledger-path node-id]
  (let [data (edn/read-string (slurp ledger-path))
        st (store/->MemStore (atom (merge {:ledger [] :assessments {}} data)))
        status (query/status-of st node-id)]
    (println status)
    (System/exit (if (= "authorized" status) 0 1))))
