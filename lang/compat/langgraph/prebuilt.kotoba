(ns langgraph.prebuilt
  "Prebuilt graphs — the classic ReAct tool-calling agent."
  (:require [langgraph.graph :as g]
            [langchain.model :as model]
            [langchain.message :as msg]
            [langchain.tool :as tool]))

(defn create-react-agent
  "Tool-calling agent loop:

      :agent → (tool calls?) → :tools → :agent → … → END

  State: {:messages [..]} with an into-reducer. Invoke with
  {:messages [(msg/user \"…\")]}; the final state's last message is
  the answer.

  opts: {:model ChatModel  :tools [tool…]  :system \"…\"
         :compile-opts {:checkpointer … :interrupt-before #{:tools} …}}"
  [{:keys [model tools system compile-opts]}]
  (let [call-model
        (fn [{:keys [messages]}]
          (let [messages (if system
                           (cons (msg/system system) messages)
                           messages)
                reply (model/-generate model (vec messages) {:tools tools})]
            {:messages [reply]}))
        run-tools
        (fn [{:keys [messages]}]
          {:messages (tool/execute-all tools (msg/last-message messages))})
        route
        (fn [{:keys [messages]}]
          (if (msg/tool-calls (msg/last-message messages))
            :tools
            g/END))]
    (-> (g/state-graph {:channels {:messages {:reducer (fnil into []) :default []}}})
        (g/add-node :agent call-model)
        (g/add-node :tools run-tools)
        (g/set-entry-point :agent)
        (g/add-conditional-edges :agent route)
        (g/add-edge :tools :agent)
        (g/compile-graph (or compile-opts {})))))
