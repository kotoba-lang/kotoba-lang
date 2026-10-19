;; murakumo.infer.engine — engine adapters: shard plan → concrete process specs.
;;
;; W6 product-shell + T6.4: pure cmd-string helpers require the shipped
;; `:infer-engine` KIR on **every** platform. Host pure mirrors are gone —
;; cljs/nbb must preload shipped KIR before requiring this ns
;; (ADR-260731-w6-t64-infer-sched-rebal-engine-mirror-delete).
;; Host remains: plan vector walks (workers/serving), variable-arity CSV joins,
;; pr-str prompt quoting, optional extra-args join.

(ns murakumo.infer.engine
  (:require [kotoba.lang.text :as str]
            [murakumo.kotoba.oracle :as oracle]))

(def ^:private oid :infer-engine)

(defn- o
  "Call a pure export. Requires the shipped oracle on every platform (T6.4)."
  [export args]
  (oracle/require-ready! oid)
  (oracle/call oid export args))

(defn- o-record
  "T5.2: structural host map → call-record (requires shipped oracle)."
  [export host-map field-specs]
  (oracle/require-ready! oid)
  (oracle/call-record oid export host-map field-specs))

(def ^:private endpoint-schema
  [:record :engine/endpoint [[:host :string] [:port :i64]]])

(def ^:private rpc-server-schema
  [:record :engine/rpc-server
   [[:bin-dir :string] [:port :i64] [:device :string]
    [:cache :bool] [:cache-dir :string]]])

(def ^:private head-front-schema
  [:record :engine/head-front [[:bin-dir :string] [:model-path :string]]])

(def ^:private head-middle-schema
  [:record :engine/head-middle
   [[:rpc-csv :string] [:strategy :string] [:tsplit :string]]])

(def ^:private head-tail-schema
  [:record :engine/head-tail [[:ctx :i64] [:parallel :i64] [:port :i64]]])
(def ^:private embed-front-schema
  [:record :engine/embed-front
   [[:bin-dir :string] [:model-path :string] [:pooling :string] [:ctx :i64]]])
(def ^:private embed-back-schema
  [:record :engine/embed-back [[:parallel :i64] [:port :i64]]])
(def ^:private mlx-moe-schema
  [:record :engine/mlx-moe
   [[:venv :string] [:model-repo :string] [:port :i64]]])
(def ^:private opt-i64-schema
  [:record :engine/opt-i64
   [[:flag :string] [:value :i64] [:present :bool]]])
(def ^:private opt-str-schema
  [:record :engine/opt-str
   [[:flag :string] [:value :string] [:present :bool]]])
(def ^:private tensor-3-schema
  [:record :engine/tensor-3 [[:s0 :i64] [:s1 :i64] [:s2 :i64]]])
(def ^:private waste-schema
  [:record :engine/waste
   [[:bin-dir :string] [:subcmd :string] [:container :string]]])
(def ^:private waste-serve-schema
  [:record :engine/waste-serve
   [[:python :string] [:container :string] [:port :i64]]])
(def ^:private mlx-launch-schema
  [:record :engine/mlx-launch
   [[:venv :string] [:hosts-file :string]
    [:model-repo :string] [:max-tokens :i64]]])

(def default-rpc-port
  (oracle/i64->host (o 'default-rpc-port [])))

(defn- serving [plan] (filter (comp pos? :span) (:assignments plan)))

(defn workers
  "Serving assignments that need a remote rpc-server — everyone but the head."
  [plan]
  (remove (comp :head? :node) (serving plan)))

(defn head-span [plan]
  (or (some #(when (get-in % [:node :head?]) (:span %)) (:assignments plan)) 0))

(defn rpc-worker-cmds
  "One `rpc-server` spec per serving worker node.
   T5.2: structural map → call-record for rpc-server-cmd."
  [plan {:keys [bin-dir port cache-dir device] :or {port default-rpc-port device "MTL0"}}]
  (for [{:keys [node]} (workers plan)
        :let [cache? (not (false? (:rpc-cache? node)))
              node-port (or (:rpc-port node) port)
              dev (or (:rpc-device node) device)
              cmd (o-record 'rpc-server-cmd
                            {:cmd (oracle/record
                                   rpc-server-schema
                                   {:bin-dir bin-dir
                                    :port node-port
                                    :device dev
                                    :cache cache?
                                    :cache-dir (or cache-dir "")})}
                            [[:cmd :raw]])]]
    {:name (:name node)
     :host (:host node)
     :ip (or (:rpc-ip node) (:ip node))
     :port node-port
     :cmd cmd}))

(defn tensor-split
  "--tensor-split proportions: RPC workers then head last."
  [plan]
  (let [spans (concat (map :span (workers plan)) [(head-span plan)])]
    ;; host join; oracle has fixed tensor-split-3 for 3-way only / i64-str
    (str/join ","
              (map (fn [s]
                     (o-record 'i64-str
                               {:n s}
                               [[:n :i64]]))
                   spans))))

(defn rpc-endpoints [worker-cmds]
  (str/join ","
            (map (fn [w]
                   (o-record 'endpoint
                             {:ep (oracle/record
                                   endpoint-schema
                                   {:host (or (:ip w) (:host w))
                                    :port (:port w)})}
                             [[:ep :raw]]))
                 worker-cmds)))

(defn head-cmd
  "The head's `llama-server` — loads GGUF, drives RPC ring, serves /v1."
  [plan {:keys [bin-dir model-path port rpc-port ctx parallel strategy moe-override api-key-file flash-attn extra-args]
         :or {port 8080 rpc-port default-rpc-port ctx 4096 parallel 1
              strategy :pipeline}}]
  (let [ws (rpc-worker-cmds plan {:bin-dir bin-dir :port rpc-port})
        strat (name (or strategy :pipeline))
        rpc-csv (rpc-endpoints ws)
        tsplit (tensor-split plan)]
    (str (o-record 'head-cmd-front
                   {:h (oracle/record head-front-schema
                                      {:bin-dir bin-dir :model-path model-path})}
                   [[:h :raw]])
         (o-record 'head-cmd-middle
                   {:h (oracle/record head-middle-schema
                                      {:rpc-csv rpc-csv :strategy strat :tsplit tsplit})}
                   [[:h :raw]])
         (when moe-override (str " -ot " (pr-str moe-override)))
         (o-record 'head-cmd-tail
                   {:h (oracle/record head-tail-schema
                                      {:ctx ctx :parallel parallel :port port})}
                   [[:h :raw]])
         (when (seq api-key-file) (str " --api-key-file " (pr-str api-key-file)))
         (when flash-attn (str " --flash-attn " flash-attn))
         (when (seq extra-args) (str " " (str/join " " extra-args))))))

;; ── mlx ring ────────────────────────────────────────────────────────────────

(defn mlx-hosts
  "mlx.launch hosts JSON structure."
  [plan]
  (vec (for [{:keys [node]} (serving plan)]
         {:ssh (:host node) :ips [(or (:ip node) (:host node))]})))

(defn mlx-launch-cmd
  [plan {:keys [hosts-file venv model-repo prompt max-tokens]
         :or {max-tokens 128}}]
  (str (o-record 'mlx-launch-front
                 {:x (oracle/record mlx-launch-schema
                                    {:venv venv
                                     :hosts-file hosts-file
                                     :model-repo model-repo
                                     :max-tokens max-tokens})}
                 [[:x :raw]])
       " --prompt " (pr-str (or prompt "Name three Japanese cities."))))

;; ── mlx-moe ─────────────────────────────────────────────────────────────────

(defn mlx-moe-cmd
  "mu-hashmi/mlx-moe `serve` invocation (single-node)."
  [{:keys [venv model-repo port capacity pin-top-k kv-bits profile warmup extra-args]
    :or {port 8080}}]
  (str (o-record 'mlx-moe-front
                 {:x (oracle/record mlx-moe-schema
                                    {:venv (or venv "")
                                     :model-repo model-repo
                                     :port port})}
                 [[:x :raw]])
       (o-record 'opt-i64-flag
                 {:x (oracle/record opt-i64-schema
                                    {:flag " --capacity"
                                     :value (or capacity 0)
                                     :present (boolean capacity)})}
                 [[:x :raw]])
       (o-record 'opt-i64-flag
                 {:x (oracle/record opt-i64-schema
                                    {:flag " --pin-top-k"
                                     :value (or pin-top-k 0)
                                     :present (boolean pin-top-k)})}
                 [[:x :raw]])
       (o-record 'opt-i64-flag
                 {:x (oracle/record opt-i64-schema
                                    {:flag " --kv-bits"
                                     :value (or kv-bits 0)
                                     :present (boolean kv-bits)})}
                 [[:x :raw]])
       (o-record 'opt-str-flag
                 {:x (oracle/record opt-str-schema
                                    {:flag " --profile"
                                     :value (or profile "")
                                     :present (boolean profile)})}
                 [[:x :raw]])
       (o-record 'opt-str-flag
                 {:x (oracle/record opt-str-schema
                                    {:flag " --warmup"
                                     :value (or warmup "")
                                     :present (boolean warmup)})}
                 [[:x :raw]])
       (when (seq extra-args) (str " " (str/join " " extra-args)))))

;; ── waste ───────────────────────────────────────────────────────────────────

(defn waste-cmd
  "sqliteai/waste CLI invocation (single-node, disk-streamed experts).

   `budget` is passed in BYTES. waste's parse_size takes a bare number as
   bytes, so the planner's resolved figure goes through exactly rather than
   being rounded to whole GiB — and the whole point of passing it is that the
   engine's own default is floor + 3 working sets, which on a model smaller
   than the machine leaves most of RAM unused (murakumo.infer.waste/budget
   :saturating-budget-bytes)."
  [{:keys [bin-dir container subcmd prompt budget ctx max-tokens threads
           learn? verify? json? extra-args]
    :or {subcmd "run"}}]
  (str (o-record 'waste-front
                 {:x (oracle/record waste-schema
                                    {:bin-dir (or bin-dir ".")
                                     :subcmd subcmd
                                     :container container})}
                 [[:x :raw]])
       (when (seq prompt) (str " " (pr-str prompt)))
       (o-record 'opt-i64-flag
                 {:x (oracle/record opt-i64-schema
                                    {:flag " --budget" :value (or budget 0)
                                     :present (boolean budget)})}
                 [[:x :raw]])
       (o-record 'opt-i64-flag
                 {:x (oracle/record opt-i64-schema
                                    {:flag " --ctx" :value (or ctx 0)
                                     :present (boolean ctx)})}
                 [[:x :raw]])
       (o-record 'opt-i64-flag
                 {:x (oracle/record opt-i64-schema
                                    {:flag " -n" :value (or max-tokens 0)
                                     :present (boolean max-tokens)})}
                 [[:x :raw]])
       (o-record 'opt-i64-flag
                 {:x (oracle/record opt-i64-schema
                                    {:flag " --threads" :value (or threads 0)
                                     :present (boolean threads)})}
                 [[:x :raw]])
       ;; --learn records which experts a run used so the next open starts
       ;; warm; --verify checksums every expert record as it is read, which
       ;; is what you want on a container that just crossed a USB cable
       (when learn? " --learn")
       (when verify? " --verify")
       (when json? " --json")
       (when (seq extra-args) (str " " (str/join " " extra-args)))))

(defn waste-serve-cmd
  "waste's OpenAI-compatible server (serve/, a Python front end over
   libwaste.dylib). Same /v1 surface the llama.cpp head serves, so the
   gateway and the Anthropic bridge need no engine-specific branch.

   `bind` defaults to 0.0.0.0 rather than serve/'s own 127.0.0.1: a head the
   fleet cannot reach is not a head."
  [{:keys [python container port bind budget ctx threads verify? extra-args]
    :or {python "python3" port 8000 bind "0.0.0.0"}}]
  (str (o-record 'waste-serve-front
                 {:x (oracle/record waste-serve-schema
                                    {:python python
                                     :container container
                                     :port port})}
                 [[:x :raw]])
       (o-record 'opt-str-flag
                 {:x (oracle/record opt-str-schema
                                    {:flag " --host" :value (or bind "")
                                     :present (boolean (seq bind))})}
                 [[:x :raw]])
       (o-record 'opt-i64-flag
                 {:x (oracle/record opt-i64-schema
                                    {:flag " --budget" :value (or budget 0)
                                     :present (boolean budget)})}
                 [[:x :raw]])
       (o-record 'opt-i64-flag
                 {:x (oracle/record opt-i64-schema
                                    {:flag " --ctx" :value (or ctx 0)
                                     :present (boolean ctx)})}
                 [[:x :raw]])
       (o-record 'opt-i64-flag
                 {:x (oracle/record opt-i64-schema
                                    {:flag " --threads" :value (or threads 0)
                                     :present (boolean threads)})}
                 [[:x :raw]])
       (when verify? " --verify")
       (when (seq extra-args) (str " " (str/join " " extra-args)))))

;; ── llamacpp-embed ──────────────────────────────────────────────────────────

(defn embed-head-cmd
  "Single-node llama.cpp embedding server."
  [{:keys [bin-dir model-path port ctx pooling parallel api-key-file extra-args]
    :or {port 8091 ctx 8192 pooling "mean" parallel 4}}]
  (str (o-record 'embed-head-front
                 {:x (oracle/record embed-front-schema
                                    {:bin-dir bin-dir
                                     :model-path model-path
                                     :pooling pooling
                                     :ctx ctx})}
                 [[:x :raw]])
       (o-record 'embed-head-back
                 {:x (oracle/record embed-back-schema
                                    {:parallel parallel :port port})}
                 [[:x :raw]])
       (when (seq api-key-file) (str " --api-key-file " (pr-str api-key-file)))
       (when (seq extra-args) (str " " (str/join " " extra-args)))))

(defn commands
  "Plan + engine + opts → {:workers [...] :head {...}} process specs."
  [plan engine opts]
  (case engine
    :llamacpp-rpc {:workers (vec (rpc-worker-cmds plan opts))
                   :head {:cmd (head-cmd plan opts)}}
    :mlx-ring {:hosts (mlx-hosts plan)
               :head {:cmd (mlx-launch-cmd plan opts)}}
    :mlx-moe {:head {:cmd (mlx-moe-cmd opts)}}
    ;; waste is single-node by construction: the container is one file tree on
    ;; one machine's NVMe and there is no ring to lay out
    :waste {:head {:cmd (waste-serve-cmd opts)}}
    :llamacpp-embed {:head {:cmd (embed-head-cmd opts)}}))
