(ns mcp.ports
  "Host-injected ports for the MCP server runtime. mcp-clj defines the protocols;
  the host supplies concrete implementations (call real tool functions, forward requests
  over a transport, …). The dispatcher in `mcp.execute` is pure orchestration over
  these — no I/O of its own.")

(defprotocol ITool
  "Side of a tool invocation. `invoke` receives the tool name (string) and the
  arguments map (string-keyed, from parsed JSON), and returns a result value that
  will be placed in the JSON-RPC response :result."
  (invoke [this tool-name args] "tool-name → args-map → result"))

(defprotocol ITransport
  "Represents an outbound JSON-RPC client — e.g. an upstream MCP host that this
  server may call in a nested/chained configuration. `call` sends a method and
  params, and returns the result map (or an {:error …} sentinel)."
  (call [this method params] "method → params → result"))
