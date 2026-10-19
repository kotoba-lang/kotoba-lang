(ns kototama.wasm-fields
  "Contract import id -> Wasm import field name under module \"kotoba\".

   Extracted from `kototama.guest` (2026-09-07) so that `kototama.browser`
   can depend on this table without depending on `kototama.guest`, which in
   turn lets `kototama.guest/maturity-levels` derive its R2 entry from
   `kototama.browser/parity-score` -- one source for the R2 status instead of
   three hand-copied ones. `kototama.guest` re-exports both names unchanged."
  (:require [kototama.contract :as contract]))

(def wasm-field-by-import-id
  "Map contract import id → field name under module \"kotoba\".
   Must match kototama.tender host-fn field strings and kotoba wasm emit."
  {:gen-keypair "gen_keypair"
   :sign "sign"
   :verify "verify"
   :sha256-hex "sha256_hex"
   :http-post "http_post"
   :log-read "log_read"
   :log-write "log_write"
   :clock-monotonic "clock_monotonic"
   :random-bytes "random_bytes"
   :kagi-sign "kagi_sign"
   :llm-infer "llm_infer"
   ;; Second wave (com-junkawasaki/root ADR-2607230943). :http-fetch's
   ;; field matches kotoba-core-contracts' pre-existing "http/fetch"
   ;; (id 205) entry verbatim -- see contract.cljc's :http-fetch comment.
   :http-fetch "http_fetch"
   :cbor-encode "cbor_encode"
   :json-encode "json_encode"
   :json-extract-field "json_extract_field"
   ;; Third wave (com-junkawasaki/root, this ADR). A SEPARATE field name
   ;; from :http-post's own "http_post" -- see contract.cljc's
   ;; :http-post-headers comment for why this couldn't just widen
   ;; http-post's own arity.
   :http-post-headers "http_post_headers"
   :transport-connect "transport_connect"
   :tls-open "tls_open"
   :tls-server-end-point "tls_server_end_point"
   :transport-write "transport_write"
   :transport-read "transport_read"
   :transport-close "transport_close"
   :pg-cancel-register "pg_cancel_register"
   :pg-cancel "pg_cancel"
   :pg-pool-open "pg_pool_open"
   :pg-pool-acquire "pg_pool_acquire"
   :pg-pool-query "pg_pool_query"
   :pg-pool-release "pg_pool_release"
   :pg-pool-stats "pg_pool_stats"
   :pg-pool-health "pg_pool_health"
   :pg-pool-drain "pg_pool_drain"
   :pg-pool-close "pg_pool_close"
   :pg-open "pg_open"
   :pg-query "pg_query"
   :pg-simple-query "pg_simple_query"
   :pg-prepare "pg_prepare"
   :pg-session-reset "pg_session_reset"
   :pg-close-statement "pg_close_statement"
   :pg-query-state "pg_query_state"
   :pg-prepare-typed "pg_prepare_typed"
   :pg-execute-params2 "pg_execute_params2"
   :pg-execute-params "pg_execute_params"
   :pg-bind-portal "pg_bind_portal"
   :pg-fetch-portal "pg_fetch_portal"
   :pg-close-portal "pg_close_portal"
   :pg-copy-out "pg_copy_out"
   :pg-copy-in "pg_copy_in"
   :pg-execute-batch "pg_execute_batch"
   :scram-sha256 "scram_sha256"
   :pg-open-scram "pg_open_scram"
   :pg-open-scram-random "pg_open_scram_random"
   :pg-open-scram-cancellable-random "pg_open_scram_cancellable_random"
   :pg-cancel-authority-use "pg_cancel_authority_use"
   :pg-close-scram "pg_close_scram"
   :http-get-stream "http_get_stream"
   :object-get-stream "object_get_stream"
   :object-put-block "object_put_block"
   :object-compare-and-set-ref "object_compare_and_set_ref"})

(defn wasm-field-name
  "Canonical Wasm import field for a contract import id, or nil."
  [id]
  (get wasm-field-by-import-id (contract/import-id id)))
