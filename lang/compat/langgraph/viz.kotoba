(ns langgraph.viz
  "Graph → Mermaid flowchart string."
  (:require [clojure.string :as str]
            [langgraph.graph :as g]))

(defn- node-id [n]
  (cond
    (= g/START n) "START"
    (= g/END n) "END"
    :else (str/replace (name n) #"[^A-Za-z0-9_]" "_")))

(defn mermaid
  "Accepts a graph builder map or a CompiledGraph."
  [graph]
  (let [graph (or (:graph graph) graph)
        {:keys [nodes edges conditional]} graph
        lines (concat
               ["flowchart TD"
                "  START([start])"
                "  END([end])"]
               (for [n (keys nodes)]
                 (str "  " (node-id n) "[\"" (name n) "\"]"))
               (for [[from tos] edges, to tos]
                 (str "  " (node-id from) " --> " (node-id to)))
               (for [[from _] conditional]
                 (str "  " (node-id from) " -.->|condition| END")))]
    (str/join "\n" lines)))
