(ns langgraph.kotoba-checkpoint
  "Convenience factory: kotoba-server-backed StateGraph checkpointer.

  Every superstep is persisted as datoms in a CID-pinned kotoba graph —
  graph execution history becomes a distributed, CACAO-gated, Pregel-indexed
  fact log queryable by Datalog and SPARQL.

  This is the bridge that connects langgraph-clj's StateGraph to
  kotoba-server's datomic.* XRPC surface.

  Usage (JVM, using jvm-host/host-caps from computer-use-clj examples):

    (require '[langgraph.kotoba-checkpoint :as kcp]
             '[langchain.kotoba-db         :as kdb]
             '[langgraph.graph             :as lg])

    (def conn (kdb/kotoba-conn \"http://149.28.207.62:8080\" \"k51...\"))
    (def cp   (kcp/checkpointer conn jvm-host-caps))

    (def g
      (-> (lg/state-graph {:messages {:reducer conj}})
          (lg/add-node :agent agent-fn)
          (lg/add-edge :agent :END)
          (lg/set-entry-point :agent)
          (lg/compile {:checkpointer cp})))

    ;; Resume from a thread — state is persisted in kotoba
    (lg/invoke g {:messages [\"hi\"]} {:thread-id \"thread-42\"})

  Schema note:
    The kotoba graph must have the checkpoint schema attributes transacted
    before first use. Call (kcp/ensure-schema! conn host-caps) once at startup
    or include the schema in your app's init transact:

      {:checkpoint/key      {:db/unique :db.unique/identity}
       :checkpoint/thread   {}
       :checkpoint/step     {}
       :checkpoint/state    {}
       :checkpoint/frontier {}
       :checkpoint/status   {}}"
  (:require [langchain.kotoba-db  :as kdb]
            [langgraph.checkpoint :as cp]))

;; Checkpoint schema as Datomic tx-data.
;; Transact this once against your graph before using the checkpointer.
(def checkpoint-schema-tx
  [{:db/ident :checkpoint/key
    :db/unique :db.unique/identity}
   {:db/ident :checkpoint/thread}
   {:db/ident :checkpoint/step}
   {:db/ident :checkpoint/state}
   {:db/ident :checkpoint/frontier}
   {:db/ident :checkpoint/status}])

(defn ensure-schema!
  "Idempotently transacts the checkpoint schema into the kotoba graph.
  Safe to call on every startup (unique identity prevents duplicates)."
  [conn host-caps]
  (let [api (kdb/kotoba-api host-caps)]
    ((:transact! api) conn checkpoint-schema-tx)))

(defn checkpointer
  "Returns a Checkpointer backed by kotoba-server XRPC.

  conn      – created with (langchain.kotoba-db/kotoba-conn url graph opts)
  host-caps – {:http-fn :json-write :json-read} (same shape as langchain.model)

  The checkpointer persists every graph superstep as datoms using
  langgraph.checkpoint/datomic-checkpointer with the kotoba-api as backend."
  [conn host-caps]
  (cp/datomic-checkpointer conn {:db-api (kdb/kotoba-api host-caps)}))
