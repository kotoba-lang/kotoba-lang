(ns kototama.unspsc.react
  "A genuine ReAct tool-calling loop per UNSPSC actor.

      :agent → (tool calls?) → :tools → :agent → … → END

  Each actor exposes its commodity capability as tools (inspect_requirements,
  validate_line); the model reasons, calls tools, observes results, and loops
  until it can state a verdict. Built on langgraph-clj's create-react-agent, so
  it runs identically on JVM and in the browser (all .cljc).

  Charter: the model is the Murakumo gateway in production (org/murakumo-model);
  pass any ChatModel. Inference stays Murakumo-only at runtime."
  (:require [langgraph.prebuilt :as pre]
            [kototama.unspsc.capability :as cap]))

(defn actor-tools
  "The commodity's capability surface, as ReAct tools."
  [taxon]
  [{:name "inspect_requirements"
    :description "Return the required procurement/verification fields and checks for this commodity."
    :schema {:type "object" :properties {}}
    :fn (fn [_] (select-keys (cap/run taxon {}) [:domain :required :checks]))}
   {:name "validate_line"
    :description (str "Validate a buyer's procurement line (a map of field -> value) for this "
                      "commodity. Returns missing fields, per-check pass/fail, and overall ok.")
    :schema {:type "object"
             :properties {:line {:type "object"
                                 :description "field -> value map for the procurement line"}}
             :required ["line"]}
    :fn (fn [{:keys [line]}] (cap/run taxon (or line {})))}])

(defn react-actor
  "Compiles a ReAct loop actor for a taxon. `model` is any ChatModel
  (Murakumo in prod, mock in tests). compile-opts forwards to langgraph
  (e.g. {:checkpointer cp} for kotoba-Datom persistence)."
  ([taxon model] (react-actor taxon model {}))
  ([taxon model compile-opts]
   (pre/create-react-agent
    {:model model
     :tools (actor-tools taxon)
     :system (str "You are UNSPSC commodity actor " (:code taxon)
                  " \"" (:title taxon) "\". First call inspect_requirements, then "
                  "call validate_line with the buyer's line. State the verdict: "
                  "OK, or exactly which fields are missing or failing.")
     :compile-opts compile-opts})))
