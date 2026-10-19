(ns kami.dot
  "GraphViz DOT as data — 'hiccup for graphs'. A DOT graph is a nested statement list, so it maps onto
   EDN directly — a dependency / state-machine / pipeline diagram is composable data you fork and diff
   like a scene. A general-purpose visualization sibling in the kami.* family. `.cljc`.

   DOT is a tree of statements (not infix), so no kami.expr. Statements:
     [:node {:shape :box}]              → node [shape=box];        (default node attrs)
     [:edge {:color :gray}]             → edge [color=gray];       (default edge attrs)
     [:graph-attr {:rankdir \"LR\"}]      → rankdir=\"LR\";            (graph-level attrs)
     [:n :a {:label \"Start\"}]           → a [label=\"Start\"];       (a node)
     [:-> :a :b {:label \"go\"}]          → a -> b [label=\"go\"];     (directed edge)
     [:-- :a :b]                        → a -- b;                  (undirected edge)
     [:subgraph :cluster_0 stmt…]       → subgraph cluster_0 { … }
   Attr values: a string is quoted (\"Start\"), a keyword/number is a bare id (box, 12).
   Top level:  (dot :digraph :G stmt…)  ·  (dot :graph nil stmt…)"
  (:require [clojure.string :as str]))

(defn- id [x] (if (keyword? x) (name x) (str x)))
;; escape \ first (else a label like "C:\new" is read by Graphviz as the \n / \l line-break escapes),
;; then the quote.
(defn- aval [v] (cond (string? v)  (str \" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) \")
                      (keyword? v) (name v)
                      :else        (str v)))
(defn- attrs [m] (when (seq m) (str " [" (str/join ", " (for [[k v] m] (str (name k) "=" (aval v)))) "]")))

(declare stmt)
(defn- block [stmts] (str/join "\n" (map #(str "  " (str/replace (stmt %) "\n" "\n  ")) stmts)))

(defn stmt
  "Compile one EDN statement to a DOT statement string."
  [form]
  (let [[op & more] form]
    (case op
      :node       (str "node" (attrs (first more)) ";")
      :edge       (str "edge" (attrs (first more)) ";")
      :graph-attr (str/join "\n" (for [[k v] (first more)] (str (name k) "=" (aval v) ";")))
      :n          (str (id (first more)) (attrs (second more)) ";")
      :->         (str (id (first more)) " -> " (id (second more)) (attrs (nth more 2 nil)) ";")
      :--         (str (id (first more)) " -- " (id (second more)) (attrs (nth more 2 nil)) ";")
      :subgraph   (let [[nm & body] more] (str "subgraph " (id nm) " {\n" (block body) "\n}"))
      (str (id op) (attrs (first more)) ";"))))   ;; bare node fallback

(defn dot
  "Compile a graph: kind (:digraph/:graph), an optional name, then statements."
  [kind name & stmts]
  (str (clojure.core/name kind) (when name (str " " (id name))) " {\n" (block stmts) "\n}"))
