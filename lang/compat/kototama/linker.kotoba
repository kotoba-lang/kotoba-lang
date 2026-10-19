(ns kototama.linker
  "Fail-closed linker for core Wasm modules with independent linear memories.

  A link manifest declares every bridged function signature and which argument
  pairs are input or output buffers. Handles and scalar values pass directly;
  bytes are copied explicitly between consumer and provider memories."
  (:require [kototama.tender :as tender])
  (:import (com.dylibso.chicory.wasm.types ValType)))

(def ^:private valtype
  {:i32 ValType/I32 :i64 ValType/I64 :f32 ValType/F32 :f64 ValType/F64})

(defn- memory [instance] (.memory instance))

(defn- copy-memory! [from-instance from-ptr to-instance to-ptr len]
  (when (neg? (long len))
    (throw (ex-info "negative component buffer length"
                    {:kototama.linker/problem :negative-buffer-length :length len})))
  (let [bs (.readBytes (memory from-instance) (int from-ptr) (int len))]
    (.write (memory to-instance) (int to-ptr) bs 0 (int len))))

(defn validate-link
  [{:component/keys [import-id field export params result copy-in copy-out]}]
  (cond-> []
    (not (keyword? import-id)) (conj {:problem :invalid-import-id})
    (not (and (string? field) (seq field))) (conj {:problem :invalid-field})
    (not (and (string? export) (seq export))) (conj {:problem :invalid-export})
    (not (and (vector? params) (every? valtype params)))
    (conj {:problem :invalid-params})
    (not (valtype result)) (conj {:problem :invalid-result})
    (not-every? (fn [[ptr-index len-index fixed-length :as buffer]]
                  (and (nat-int? ptr-index) (nat-int? len-index)
                       (or (= 2 (count buffer))
                           (and (= 3 (count buffer))
                                (nat-int? fixed-length)))
                       (< ptr-index (count params)) (< len-index (count params))
                       (= :i32 (nth params ptr-index)) (= :i32 (nth params len-index))))
                (concat copy-in copy-out))
    (conj {:problem :invalid-buffer-pair})))

(defn bridge-host-function
  "Creates one consumer HostFunction backed by an export of PROVIDER-INSTANCE.
  COPY-OUT length is the provider's non-negative scalar result, capped by the
  consumer-supplied capacity argument. A three-item output tuple
  [ptr-index cap-index fixed-length] copies a bounded fixed-size metadata
  buffer after a successful call."
  [provider-instance {:keys [scratch-base lock]} spec]
  (let [problems (validate-link spec)]
    (when (seq problems)
      (throw (ex-info "component link manifest rejected"
                      {:kototama.linker/problems problems :link spec})))
    (let [{:component/keys [field export params result copy-in copy-out]} spec]
      (when-not (tender/has-export? provider-instance export)
        (throw (ex-info "component provider export unavailable"
                        {:kototama.linker/problem :missing-provider-export
                         :export export})))
      (tender/host-fn
       field (mapv valtype params) (valtype result)
       (fn [consumer-instance args]
         (locking lock
           (let [provider-args (aclone args)
                 pairs (vec (distinct (concat copy-in copy-out)))
                 layout (loop [remaining pairs cursor scratch-base out {}]
                          (if-let [[ptr-index len-index :as pair] (first remaining)]
                            (let [length (long (aget args len-index))
                                  next-cursor (+ cursor length)]
                              (when (or (neg? length) (> next-cursor (+ scratch-base 65536)))
                                (throw (ex-info "component bridge scratch limit exceeded"
                                                {:kototama.linker/problem :bridge-scratch-limit
                                                 :length length :limit 65536})))
                              (recur (next remaining) next-cursor
                                     (assoc out pair cursor)))
                            out))]
             (doseq [[ptr-index len-index :as pair] pairs]
               (aset provider-args ptr-index (long (get layout pair))))
             (doseq [[ptr-index len-index :as pair] copy-in]
               (copy-memory! consumer-instance (aget args ptr-index)
                             provider-instance (get layout pair)
                             (aget args len-index)))
             (let [values (.apply (.export provider-instance export) provider-args)
                   value (aget ^longs values 0)]
               (when (and (pos? value) (seq copy-out))
                 (doseq [[ptr-index cap-index fixed-length :as pair] copy-out]
                   (let [copy-length (if (nil? fixed-length) value fixed-length)]
                   (when (> copy-length (aget args cap-index))
                     (throw (ex-info "provider exceeded consumer output capacity"
                                     {:kototama.linker/problem :provider-output-overflow
                                      :result copy-length :capacity (aget args cap-index)})))
                   (copy-memory! provider-instance (get layout pair)
                                 consumer-instance (aget args ptr-index) copy-length))))
               value))))))))

(defn link-provider
  "Builds the `:provider-host-functions` map consumed by tender/open-session."
  [provider-session links]
  (let [instance (:instance provider-session)
        provider-memory (memory instance)
        previous-pages (.grow provider-memory 1)
        _ (when (neg? previous-pages)
            (throw (ex-info "provider bridge memory growth denied"
                            {:kototama.linker/problem :bridge-memory-unavailable})))
        bridge-context {:scratch-base (* previous-pages 65536) :lock (Object.)}
        duplicate-ids (->> links (map :component/import-id) frequencies
                           (keep (fn [[id n]] (when (> n 1) id))) vec)]
    (when (seq duplicate-ids)
      (throw (ex-info "duplicate component import bindings"
                      {:kototama.linker/problem :duplicate-import-bindings
                       :imports duplicate-ids})))
    (into {}
          (map (fn [link]
                 [(:component/import-id link)
                  (bridge-host-function instance bridge-context link)]))
          links)))

(def http-links
  [#:component{:import-id :http-open :field "http_open" :export "http-open"
               :params [:i32 :i32 :i32] :result :i64 :copy-in [[0 1]] :copy-out []}
   #:component{:import-id :http-write :field "http_write" :export "http-write"
               :params [:i64 :i32 :i32] :result :i32 :copy-in [[1 2]] :copy-out []}
   #:component{:import-id :http-read :field "http_read" :export "http-read"
               :params [:i64 :i32 :i32] :result :i32 :copy-in [] :copy-out [[1 2]]}
   #:component{:import-id :http-close :field "http_close" :export "http-close"
               :params [:i64] :result :i32 :copy-in [] :copy-out []}
   #:component{:import-id :http-get :field "http_get" :export "http-get"
               :params [:i32 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[0 1] [3 4]] :copy-out [[5 6]]}])

(def db-links
  [#:component{:import-id :db-open :field "db_open" :export "db-open"
               :params [:i32 :i32 :i32] :result :i64 :copy-in [[0 1]] :copy-out []}
   #:component{:import-id :db-write :field "db_write" :export "db-write"
               :params [:i64 :i32 :i32] :result :i32 :copy-in [[1 2]] :copy-out []}
   #:component{:import-id :db-read :field "db_read" :export "db-read"
               :params [:i64 :i32 :i32] :result :i32 :copy-in [] :copy-out [[1 2]]}
   #:component{:import-id :db-close :field "db_close" :export "db-close"
               :params [:i64] :result :i32 :copy-in [] :copy-out []}
   #:component{:import-id :db-exchange :field "db_exchange" :export "db-exchange"
               :params [:i32 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[0 1] [3 4]] :copy-out [[5 6]]}
   #:component{:import-id :pg-simple-query :field "pg_simple_query"
               :export "pg-simple-query"
               :params [:i32 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[0 1] [3 4]] :copy-out [[5 6]]}
   #:component{:import-id :pg-open :field "pg_open" :export "pg-open"
               :params [:i32 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i64 :copy-in [[0 1] [3 4] [5 6]] :copy-out []}
   #:component{:import-id :pg-query :field "pg_query" :export "pg-query"
               :params [:i64 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[1 2]] :copy-out [[3 4]]}
   #:component{:import-id :pg-query-state :field "pg_query_state"
               :export "pg-query-state"
               :params [:i64 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[1 2]] :copy-out [[3 4] [5 6 7]]}
   #:component{:import-id :pg-prepare :field "pg_prepare" :export "pg-prepare"
               :params [:i64 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[1 2] [3 4]]
               :copy-out [[5 6] [7 8 7]]}
   #:component{:import-id :pg-prepare-typed :field "pg_prepare_typed"
               :export "pg-prepare-typed"
               :params [:i64 :i32 :i32 :i32 :i32 :i32 :i32 :i32
                        :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[1 2] [3 4] [5 6]]
               :copy-out [[8 9] [10 11 7]]}
   #:component{:import-id :pg-execute-params2 :field "pg_execute_params2"
               :export "pg-execute-params2"
               :params [:i64 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[1 2] [3 4] [5 6]]
               :copy-out [[7 8] [9 10 7]]}
   #:component{:import-id :pg-execute-params :field "pg_execute_params"
               :export "pg-execute-params"
               :params [:i64 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[1 2] [3 4]]
               :copy-out [[5 6] [7 8 7]]}
   #:component{:import-id :pg-bind-portal :field "pg_bind_portal"
               :export "pg-bind-portal"
               :params [:i64 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[1 2] [3 4] [5 6]]
               :copy-out [[7 8] [9 10 7]]}
   #:component{:import-id :pg-fetch-portal :field "pg_fetch_portal"
               :export "pg-fetch-portal"
               :params [:i64 :i32 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[1 2]]
               :copy-out [[4 5] [6 7 7]]}
   #:component{:import-id :pg-close-portal :field "pg_close_portal"
               :export "pg-close-portal"
               :params [:i64 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[1 2]] :copy-out [[3 4] [5 6 7]]}
   #:component{:import-id :pg-copy-out :field "pg_copy_out"
               :export "pg-copy-out"
               :params [:i64 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[1 2]] :copy-out [[3 4] [5 6 7]]}
   #:component{:import-id :pg-copy-in :field "pg_copy_in"
               :export "pg-copy-in"
               :params [:i64 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[1 2] [3 4]]
               :copy-out [[5 6] [7 8 7]]}
   #:component{:import-id :pg-execute-batch :field "pg_execute_batch"
               :export "pg-execute-batch"
               :params [:i64 :i32 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[1 2]]
               :copy-out [[4 5] [6 7 7]]}
   #:component{:import-id :pg-session-reset :field "pg_session_reset"
               :export "pg-session-reset"
               :params [:i64 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [] :copy-out [[1 2] [3 4 7]]}
   #:component{:import-id :pg-close-statement :field "pg_close_statement"
               :export "pg-close-statement"
               :params [:i64 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i32 :copy-in [[1 2]] :copy-out [[3 4] [5 6 7]]}])

(def scram-links
  [#:component{:import-id :pg-open-scram :field "pg_open_scram"
               :export "pg-open-scram"
               :params [:i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i64 :copy-in [[0 1] [3 4] [5 6] [7 8] [9 10]] :copy-out []}
   #:component{:import-id :pg-open-scram-random :field "pg_open_scram_random"
               :export "pg-open-scram-random"
               :params [:i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i64 :copy-in [[0 1] [3 4] [5 6] [7 8]] :copy-out []}
   #:component{:import-id :pg-open-scram-cancellable-random
               :field "pg_open_scram_cancellable_random"
               :export "pg-open-scram-cancellable-random"
               :params [:i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32 :i32]
               :result :i64 :copy-in [[0 1] [3 4] [5 6] [7 8]]
               :copy-out [[9 10 4]]}
   #:component{:import-id :pg-cancel-authority-use
               :field "pg_cancel_authority_use"
               :export "pg-cancel-authority-use"
               :params [:i32] :result :i32 :copy-in [] :copy-out []}
   #:component{:import-id :pg-close-scram :field "pg_close_scram"
               :export "pg-close-scram"
               :params [:i64] :result :i32 :copy-in [] :copy-out []}])
