(ns tayori.cli
  "Minimal JVM entrypoint for `tayori.query` against an EDN-seeded MemStore —
  no StateGraph/checkpointer/advisor spun up, just a status read. For a
  process boundary consumer that needs one thread's draft status without an
  in-process require across runtimes.

  Usage: `clojure -M -m tayori.cli <ledger.edn> <thread-id>` — prints the
  draft status (\"proposed\"/\"sent\"/\"none\") and exits 0 on \"sent\", 1
  otherwise (so callers can also just check the exit code).

  <ledger.edn> holds the same shape as `tayori.store/demo-data`'s :drafts map
  (at minimum {:drafts {\"<thread-id>\" {:status :sent}}})."
  (:require [clojure.edn :as edn]
            [tayori.query :as query]
            [tayori.store :as store]))

(defn -main [ledger-path thread-id]
  (let [data (edn/read-string (slurp ledger-path))
        st (store/->MemStore (atom (merge {:ledger [] :drafts {} :revisions {}} data)))
        status (query/draft-status st thread-id)]
    (println status)
    (System/exit (if (= "sent" status) 0 1))))
