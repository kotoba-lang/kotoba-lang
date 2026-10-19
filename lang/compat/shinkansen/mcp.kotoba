(ns shinkansen.mcp
  "MCP tool surface for a shinkansen app (the agent-facing half).

  Shape follows kotoba-server's `word mcp` stdio server: JSON-RPC 2.0
  over stdin/stdout, methods `initialize`, `tools/list`, `tools/call`.
  Tools are DECLARED here and DISPATCHED to caller-provided functions —
  this namespace never touches the network or the store itself, so the
  same declarations drive the stdio server, a Worker route, and tests.

  The four tools map onto the yataverse lake API (ADR-2609131630) plus
  the shinkansen state chain:

    lake_list     GET /api/v1/lake/blocks   cursor-paged block listing
    lake_head     GET /api/v1/lake/head     current IPNI advertisement tip
    lake_fetch    GET /ipfs/{cid}           bytes of one block
    lake_dispatch (app)                    drive the app's state chain

  `lake_dispatch` is a TRANSPORT of `shinkansen.invoke` (SPEC §1.6): the
  tool arguments name the artifact and the event, the SESSION names the
  principal and carries the grant (ctx :principal / :grant, bound once by
  whoever started the server), and the handler receives one envelope.
  The agent's token is never a tool argument — an argument is a log line.

  Every tool answers {:ok bool …} and NEVER throws: an MCP tool result
  that throws surfaces as a protocol error, not a tool error."
  (:require [clojure.string :as str]
            [shinkansen.invoke :as invoke]))

(def ^:const protocol-version "2025-06-18")

(defn- tool-def [name description input-schema]
  {:name name :description description :inputSchema input-schema})

(defn tools
  "The tool declarations. `base-url` is the lake origin the caller reads
  (e.g. https://yataverse.com). Kept as a function so tests can point it
  anywhere and a Worker can bind it to its own host."
  [base-url]
  [(tool-def
    "lake_list"
    "List recent content-addressed blocks in the yataverse data lake. Returns CID, byte size, upload time, and a direct gateway URL per block. Cursor-paged."
    {:type "object"
     :properties {"limit" {:type "integer" :description "max blocks per page (default 50, max 200)"}
                  "cursor" {:type "string" :description "opaque cursor from a previous page"}}})
   (tool-def
    "lake_head"
    "The current IPNI advertisement chain tip for the lake, plus the publisher and retrieval hosts."
    {:type "object" :properties {}})
   (tool-def
    "lake_fetch"
    "Fetch one content-addressed block by CID. Returns bytes metadata; the bytes themselves are at {cid}.ipfs.yataverse.com (byte-verified, no-transform)."
    {:type "object"
     :properties {"cid" {:type "string" :description "CIDv1 base32 (bafk…)"}
                  "format" {:type "string" :enum ["meta" "text"] :description "meta = metadata only; text = decode as UTF-8 text"}}})
   (tool-def
    "lake_dispatch"
    "Dispatch an event to an app's content-addressed state chain. The app is named by its artifact CID; the caller's identity and grant come from this session, not from the arguments. Returns the new db value, its CID, and the chain entry — or the refusal, by name."
    {:type "object"
     :properties {"artifact" {:type "string" :description "the app's CIDv1 base32 label (baf…) — WHAT is being driven"}
                  "event" {:type "array" :description "re-frame event vector, e.g. [\"todo/add\" \"milk\"]"}}
     :required ["artifact" "event"]})])

(defn- lake-url [base path]
  (str (str/replace base #"/*$" "") path))

(defn handle-call
  "Dispatch one tools/call. Handlers map:
    {:lake-list (fn [{:keys [limit cursor]}) → {:ok …}]
     :lake-head (fn [] → …)
     :lake-fetch (fn [{:keys [cid format]}) → …
     :lake-dispatch (fn [envelope]) → …}      a shinkansen.invoke envelope
  `session` is {:principal did :grant …} — the identity the server was
  started with; `lake_dispatch` folds it into the envelope
  (`invoke/from-mcp`). A missing handler answers
  {:ok false :error \"tool not implemented\"} — declared but unimplemented
  is a caller bug and must be visible."
  ([tool-name args handlers] (handle-call tool-name args handlers {}))
  ([tool-name args handlers session]
  (case tool-name
    "lake_list" ((or (:lake-list handlers) (fn [_] {:ok false :error "tool not implemented"}))
                 (update args :limit #(or % 50)))
    "lake_head" ((or (:lake-head handlers) (fn [_] {:ok false :error "tool not implemented"})) args)
    "lake_fetch" (let [cid (:cid args)]
                   (cond
                     (str/blank? cid) {:ok false :error "cid is required"}
                     (not (re-matches #"bafk[a-z2-7]+" (str cid)))
                     {:ok false :error "cid must be a CIDv1 base32 label (bafk…)"}
                     :else ((or (:lake-fetch handlers) (fn [_] {:ok false :error "tool not implemented"})) args)))
    "lake_dispatch" ((or (:lake-dispatch handlers) (fn [_] {:ok false :error "tool not implemented"}))
                     (invoke/from-mcp args session))
    {:ok false :error (str "unknown tool: " tool-name)})))

(defn- json-rpc-ok [id result]
  {:jsonrpc "2.0" :id id :result result})

(defn- json-rpc-error [id code message]
  {:jsonrpc "2.0" :id id :error {:code code :message message}})

(defn handle-request
  "One JSON-RPC request (as a map) → response map. Pure; the stdio loop
  in the caller reads lines, parses, and prints. Method set is the MCP
  subset the tools need — nothing else is answered (fail closed)."
  [req ctx]
  (let [id (:id req)
        method (:method req)]
    (case method
      "initialize"
      (json-rpc-ok id {:protocolVersion protocol-version
                       :capabilities {:tools {}}
                       :serverInfo {:name "shinkansen" :version "0.1.0"}})
      "tools/list"
      (json-rpc-ok id {:tools (tools (or (:base-url ctx) "https://yataverse.com"))})
      "tools/call"
      (let [params (:params req)
            tool (:name params)
            args (or (:arguments params) {})
            result (handle-call tool args (:handlers ctx)
                                (select-keys ctx [:principal :grant]))]
        ;; A handler may answer synchronously or with a Promise (the live
        ;; stdio handlers fetch over HTTP). Flatten to a promise of a
        ;; response either way — the caller decides how to await it.
        (if (instance? js/Promise result)
          (.then result #(json-rpc-ok id {:content [{:type "text" :text (binding [*print-readably* true] (pr-str %))}]}))
          (json-rpc-ok id {:content [{:type "text" :text (binding [*print-readably* true] (pr-str result))}]})))
      (json-rpc-error id -32601 (str "method not found: " (or method ""))))))
