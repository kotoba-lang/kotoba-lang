(ns langchain.kotobase-persist
  "Optional adapter wiring `langchain.db/create-conn`'s duck-typed `persist`
  port (ADR-2607150000) to a real `kotoba-lang/kotobase` `kotobase.store/
  IStore` (`kotobase.local/LocalStore` for OSS-standalone, `kotobase.
  kotobase/KotobaseStore` for kotobase.net-backed). `langchain.db` itself
  never `:require`s `kotobase.store` -- only this namespace does, kept out
  of langchain's main `:deps` (see `deps.edn`'s `:kotobase` alias) the
  same way `langchain.jvm` keeps http-kit/jsonista out of the zero-dep core.

  Distinct from `langchain.kotoba-db` (a full remote Datomic-shaped
  `langchain.db/api` replacement talking directly to a kotoba-server's
  `ai.gftd.apps.kotobase.datomic.*` XRPC namespace, no client-side query
  engine at all): this namespace instead persists the append-only tx log
  behind `langchain.db`'s own pure, client-side Datalog engine (`with`/
  `q`/`pull`) via `kotobase.store/IStore`'s append-only stream shape --
  the engine still runs locally, only the durability layer changes.

    (require '[kotobase.local :as local]
             '[langchain.db :as db]
             '[langchain.kotobase-persist :as kp])

    (def store (local/local-store))
    (def conn (db/create-conn schema (kp/persist-for store \"my-thread\")))
    (db/transact! conn tx-data)          ; persisted via store's -append
    ;; ... process restart ...
    (def conn2 (db/create-conn schema (kp/persist-for store \"my-thread\")))
    (db/q '[:find ?e :where [?e :thread/id _]] (db/db conn2))  ; replayed"
  (:require [kotobase.store :as st]))

(defn persist-for
  "A `langchain.db/create-conn` `persist` map (`{:append :read}`) backed by
  STORE (any `kotobase.store/IStore`) and STREAM (the append-only stream
  name -- one `langchain.db` conn maps to one stream; use a distinct
  stream name per conn/thread/checkpoint that needs its own tx history)."
  [store stream]
  {:append (fn [event] (st/-append store stream event))
   :read (fn [since] (st/-read store stream since))})
