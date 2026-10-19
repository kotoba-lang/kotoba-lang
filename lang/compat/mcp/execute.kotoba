(ns mcp.execute
  "A pure JSON-RPC dispatcher for an MCP server. State is plain data — no I/O of its
  own. The host injects two ports (`mcp.ports`):

    ITool      invoke  [tool-name args] → result   — run the real tool
    ITransport call    [method params] → result    — outbound JSON-RPC client (optional)

  `handle` takes a JSON-RPC request map (string keys, as a JSON parser would produce)
  and returns a JSON-RPC response map (string keys, ready to serialise).

  Supported methods:
    \"initialize\"     — returns serverInfo + capabilities
    \"tools/list\"     — tool descriptors sorted by name
    \"tools/call\"     — validates :required params, then ITool/invoke; -32602 on bad params
    \"resources/list\" — resource descriptors sorted by uri
    \"prompts/list\"   — prompt descriptors sorted by name
    Any other method  — error -32601 Method Not Found"
  (:require [mcp.model :as m]
            [mcp.ports :as p]))

;; --- JSON-RPC response helpers ---

(defn- ok  [id result] {"jsonrpc" "2.0" "id" id "result" result})
(defn- err [id code message]
  {"jsonrpc" "2.0" "id" id "error" {"code" code "message" message}})

;; --- schema validation for tools/call ---

(defn- schema-error
  "Return an error message string if `args` (string-keyed map) violates the
  JSON-Schema-as-EDN `schema`, or nil if the args are acceptable."
  [schema args]
  (when (and schema (= "object" (:type schema)))
    (let [required (:required schema [])]
      (first (keep (fn [r]
                     (when-not (contains? args r)
                       (str "missing required param: \"" r "\"")))
                   required)))))

;; --- tools/call result → MCP CallToolResult envelope ---

(defn- error-result? [result]
  (and (map? result)
       (or (contains? result :error) (contains? result "error"))))

(defn- call-tool-result
  "Wrap an ITool/invoke result in an MCP CallToolResult envelope
  ({\"content\" [{\"type\" \"text\" ...}] \"isError\" bool} plus \"structuredContent\"
  when the result is a map). MCP clients (Claude Code included) render the text
  block and read structuredContent for the machine-readable payload. If the tool
  already returned a CallToolResult (a map carrying a string \"content\" key) it is
  passed through unchanged. Text is EDN (pr-str) — execute stays pure/JSON-free;
  the transport JSON-encodes the whole response (keyword keys → strings)."
  [result]
  (if (and (map? result) (contains? result "content"))
    result
    (cond-> {"content" [{"type" "text" "text" (pr-str result)}]
             "isError" (error-result? result)}
      (map? result) (assoc "structuredContent" result))))

;; --- tool/resource/prompt → JSON-ready descriptor ---

(defn- schema-to-data [s]
  (when s
    (let [ss (into {} (map (fn [[k v]] [(if (keyword? k) (name k) k) v]) s))]
      (cond-> ss
        (get ss "properties")
        (update "properties"
                (fn [props]
                  (into {} (map (fn [[k v]]
                                  [k (into {} (map (fn [[k2 v2]]
                                                     [(if (keyword? k2) (name k2) k2) v2])
                                                   v))])
                                props))))))))

(defn- tool-descriptor [t]
  (cond-> {"name" (:mcp/name t)}
    (:mcp/description t)  (assoc "description" (:mcp/description t))
    (:mcp/input-schema t) (assoc "inputSchema" (schema-to-data (:mcp/input-schema t)))))

(defn- resource-descriptor [r]
  (cond-> {"uri" (:mcp/uri r)}
    (:mcp/name r) (assoc "name" (:mcp/name r))
    (:mcp/mime r) (assoc "mimeType" (:mcp/mime r))))

(defn- argument-descriptor [a]
  (cond-> {"name" (:mcp/name a)}
    (contains? a :mcp/required) (assoc "required" (boolean (:mcp/required a)))))

(defn- prompt-descriptor [pr]
  (cond-> {"name" (:mcp/name pr)}
    (:mcp/arguments pr)
    (assoc "arguments" (mapv argument-descriptor (:mcp/arguments pr)))))

;; --- dispatcher ---

(defn handle
  "Pure JSON-RPC dispatcher. `ports` is a map {:tool ITool :transport ITransport}.
  `model` is an EDN manifest. `request` is a JSON-RPC map with string keys.
  Returns a JSON-RPC response map with string keys."
  [ports model request]
  (let [method (get request "method")
        params (get request "params" {})
        id     (get request "id")]
    (case method
      "initialize"
      (ok id {"serverInfo"   {"name"    (get-in model [:mcp/server :mcp/name])
                              "version" (get-in model [:mcp/server :mcp/version])}
              "capabilities" {"tools"     {}
                              "resources" {}
                              "prompts"   {}}})

      "tools/list"
      (ok id {"tools" (mapv tool-descriptor (m/tools model))})

      "tools/call"
      (let [tool-name (get params "name")
            args      (get params "arguments" {})
            tool      (m/tool model tool-name)]
        (if-not tool
          (err id -32601 (str "tool not found: \"" tool-name "\""))
          (let [errmsg (schema-error (:mcp/input-schema tool) args)]
            (if errmsg
              (err id -32602 errmsg)
              (ok id (call-tool-result (p/invoke (:tool ports) tool-name args)))))))

      "resources/list"
      (ok id {"resources" (mapv resource-descriptor (m/resources model))})

      "prompts/list"
      (ok id {"prompts" (mapv prompt-descriptor (m/prompts model))})

      ;; unknown method
      (err id -32601 (str "Method not found: \"" method "\"")))))

;; --- host-free defaults ---

(defn default-ports
  "ITool/invoke echoes args as {:echo args} — enough to exercise dispatch without a
  real tool implementation. ITransport/call returns an {:error …} sentinel (no
  outbound transport is wired). Replace both for real work."
  []
  {:tool      (reify p/ITool
                (invoke [_ _ args] {:echo args}))
   :transport (reify p/ITransport
                (call [_ _ _] {:error "no transport injected"}))})
