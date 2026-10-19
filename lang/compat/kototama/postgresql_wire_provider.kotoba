(ns kototama.postgresql-wire-provider
  "T8.4 fail-closed inject surface for component-link PostgreSQL wire imports.

  Real wire sessions live behind component-linked Wasm guests + SCRAM (see
  docs/postgresql-qualification.edn). This provider only supplies ABI-matched
  HostFunctions that always fail closed, so host-parity live runners can prove
  actor:host linkage without a live database."
  (:require [kototama.tender :as tender])
  (:import (com.dylibso.chicory.wasm.types ValType)))

(defn- i32s [n]
  (vec (repeat n ValType/I32)))

(defn fail-closed-inject-provider
  "Return `:host-functions` map for wire imports, each deny-closed.

  - `:pg-open` / SCRAM open variants return handle 0 (i64)
  - other i32-result imports return -1"
  []
  (let [deny-i32 (fn [_ _] -1)
        deny-i64 (fn [_ _] 0)
        open-fn
        (tender/host-fn
         "pg_open"
         [ValType/I32 ValType/I32 ValType/I32 ValType/I32
          ValType/I32 ValType/I32 ValType/I32]
         ValType/I64
         deny-i64)
        query-fn
        (tender/host-fn
         "pg_query"
         [ValType/I64 ValType/I32 ValType/I32 ValType/I32 ValType/I32]
         ValType/I32
         deny-i32)
        simple-fn
        (tender/host-fn
         "pg_simple_query"
         [ValType/I32 ValType/I32 ValType/I32 ValType/I32
          ValType/I32 ValType/I32 ValType/I32]
         ValType/I32
         deny-i32)
        prepare-fn
        (tender/host-fn
         "pg_prepare"
         (into [ValType/I64] (i32s 8))
         ValType/I32
         deny-i32)
        session-reset-fn
        (tender/host-fn
         "pg_session_reset"
         [ValType/I64 ValType/I32 ValType/I32 ValType/I32 ValType/I32]
         ValType/I32
         deny-i32)
        close-stmt-fn
        (tender/host-fn
         "pg_close_statement"
         (into [ValType/I64] (i32s 6))
         ValType/I32
         deny-i32)
        pg_query_state_fn
        (tender/host-fn
         "pg_query_state"
         (into [ValType/I64] (i32s 6))
         ValType/I32
         deny-i32)
        pg_prepare_typed_fn
        (tender/host-fn
         "pg_prepare_typed"
         (into [ValType/I64] (i32s 11))
         ValType/I32
         deny-i32)
        pg_execute_params2_fn
        (tender/host-fn
         "pg_execute_params2"
         (into [ValType/I64] (i32s 10))
         ValType/I32
         deny-i32)
        pg_execute_params_fn
        (tender/host-fn
         "pg_execute_params"
         (into [ValType/I64] (i32s 8))
         ValType/I32
         deny-i32)
        pg_bind_portal_fn
        (tender/host-fn
         "pg_bind_portal"
         (into [ValType/I64] (i32s 10))
         ValType/I32
         deny-i32)
        pg_fetch_portal_fn
        (tender/host-fn
         "pg_fetch_portal"
         (into [ValType/I64] (i32s 7))
         ValType/I32
         deny-i32)
        pg_close_portal_fn
        (tender/host-fn
         "pg_close_portal"
         (into [ValType/I64] (i32s 6))
         ValType/I32
         deny-i32)
        pg_copy_out_fn
        (tender/host-fn
         "pg_copy_out"
         (into [ValType/I64] (i32s 6))
         ValType/I32
         deny-i32)
        pg_copy_in_fn
        (tender/host-fn
         "pg_copy_in"
         (into [ValType/I64] (i32s 8))
         ValType/I32
         deny-i32)
        pg_execute_batch_fn
        (tender/host-fn
         "pg_execute_batch"
         (into [ValType/I64] (i32s 7))
         ValType/I32
         deny-i32)
        ;; SCRAM session open returns i64 handle 0 fail-closed (same as :pg-open).
        pg_open_scram_fn
        (tender/host-fn
         "pg_open_scram"
         (i32s 11)
         ValType/I64
         deny-i64)
        pg_open_scram_random_fn
        (tender/host-fn
         "pg_open_scram_random"
         (i32s 9)
         ValType/I64
         deny-i64)
        pg_open_scram_cancellable_random_fn
        (tender/host-fn
         "pg_open_scram_cancellable_random"
         (i32s 11)
         ValType/I64
         deny-i64)
        pg_cancel_authority_use_fn
        (tender/host-fn
         "pg_cancel_authority_use"
         [ValType/I32]
         ValType/I32
         deny-i32)
        pg_close_scram_fn
        (tender/host-fn
         "pg_close_scram"
         [ValType/I64]
         ValType/I32
         deny-i32)]
    {:host-functions
     {:pg-open open-fn
      :pg-query query-fn
      :pg-simple-query simple-fn
      :pg-prepare prepare-fn
      :pg-session-reset session-reset-fn
      :pg-close-statement close-stmt-fn
      :pg-query-state pg_query_state_fn
      :pg-prepare-typed pg_prepare_typed_fn
      :pg-execute-params2 pg_execute_params2_fn
      :pg-execute-params pg_execute_params_fn
      :pg-bind-portal pg_bind_portal_fn
      :pg-fetch-portal pg_fetch_portal_fn
      :pg-close-portal pg_close_portal_fn
      :pg-copy-out pg_copy_out_fn
      :pg-copy-in pg_copy_in_fn
      :pg-execute-batch pg_execute_batch_fn
      :pg-open-scram pg_open_scram_fn
      :pg-open-scram-random pg_open_scram_random_fn
      :pg-open-scram-cancellable-random pg_open_scram_cancellable_random_fn
      :pg-cancel-authority-use pg_cancel_authority_use_fn
      :pg-close-scram pg_close_scram_fn}
     :close! (fn [])}))
