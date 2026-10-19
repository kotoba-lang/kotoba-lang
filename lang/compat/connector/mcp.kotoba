(ns connector.mcp
  "Project a connector registry onto MCP.

  This is the reason the descriptor carries a JSON-Schema input schema rather
  than something of its own: an MCP manifest wants exactly that, so the
  projection is a rename and not a translation.

  It builds on `kotoba-lang/org-anthropic-mcp` (mcp-clj) rather than emitting
  the `:mcp/*` shape directly. Re-deriving a sibling's wire shape is how two
  spellings of one contract start, and the manifest is small enough that the
  duplication would look harmless right up until the two drift."
  (:require [connector.invoke :as invoke]
            [connector.model :as m]
            [connector.registry :as reg]
            [mcp.model :as mcp]
            [mcp.ports :as mcp-ports]))

(defn manifest
  "An MCP server manifest offering every tool in `registry`.

  Tool descriptions carry the connector name and the effect. A model choosing
  between `google_calendar_list_events` and `gmail_send_message` is choosing
  between reading and sending, and that distinction should not depend on it
  recognising a naming convention."
  [registry server-name version]
  (reduce (fn [srv d]
            (reduce (fn [s t]
                      (mcp/add-tool s (:connector/name t)
                                    {:description
                                     (str "[" (:connector/name d) "] "
                                          (or (:connector/description t) (:connector/name t))
                                          (when (= :write (:connector/effect t)) " (write)"))
                                     :input-schema (:connector/input-schema t)}))
                    srv (m/tools d)))
          (mcp/server server-name version)
          (reg/descriptors registry)))

(defn tool-port
  "An `mcp.ports/ITool` that runs registry tools.

  ctx is `connector.invoke/call`'s: {:http <IHttp> :tokens <ITokens>}.

  Arguments arrive from JSON with string keys and are passed through unchanged
  — a connector's `request` is written against that, so nothing here has to
  guess whether a key was meant to be a keyword."
  [registry ctx]
  (reify mcp-ports/ITool
    (invoke [_ tool-name args]
      (invoke/call registry tool-name args ctx))))
