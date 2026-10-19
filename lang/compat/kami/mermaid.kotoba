(ns kami.mermaid
  "Mermaid flowcharts as data — 'hiccup for diagrams'. Mermaid is a concise text diagram language, so a
   flowchart maps onto EDN directly — a process / decision tree is composable data you fork and diff
   like a scene. A diagram sibling to kami.dot (Markdown/web-native rather than GraphViz). `.cljc`.

   A flowchart is a statement list (not infix), so no kami.expr. Statements:
     [:node :A {:label \"Start\"}]        → A[Start]            (shape :square default)
     [:node :B {:label \"Go?\" :shape :diamond}] → B{Go?}      (:round (), :stadium ([]), :circle (()),
                                                                  :hexagon {{}}, :subroutine [[]], :cylinder [()])
     [:--> :A :B]                       → A --> B
     [:--> :B :C {:label \"yes\"}]        → B -->|yes| C
     [:--- a b] (line) · [:-.-> a b] (dotted) · [:==> a b] (thick) · [:--x a b] · [:--o a b]
     [:subgraph :grp stmt…]             → subgraph grp … end
   Top level:  (flowchart :LR stmt…)   (dir :LR/:TD/:TB/:RL/:BT)"
  (:require [clojure.string :as str]))

(defn- id [x] (if (keyword? x) (name x) (str x)))

(defn- mlabel
  "A node/edge label. Mermaid breaks on shape/flow delimiters in raw text, so a label containing any of
   []{}()|\"#<> is wrapped in double quotes (with internal \" → #quot;); a plain label is left bare."
  [s]
  (let [s (str s)]
    (if (re-find #"[\[\]{}()|\"#<>]" s)
      (str \" (str/replace s "\"" "#quot;") \")
      s)))

(def ^:private shapes {:square ["[" "]"] :round ["(" ")"] :stadium ["([" "])"]
                       :diamond ["{" "}"] :circle ["((" "))"] :hexagon ["{{" "}}"]
                       :subroutine ["[[" "]]"] :cylinder ["[(" ")]"]})
(def ^:private arrows {:--> "-->" :--- "---" :-.-> "-.->" :==> "==>" :--x "--x" :--o "--o"})

(defn- node-decl [nid {:keys [label shape] :or {shape :square}}]
  (if label (let [[o c] (shapes shape)] (str (id nid) o (mlabel label) c)) (id nid)))

(declare stmt)
(defn- block [stmts] (str/join "\n" (map #(str "  " (str/replace (stmt %) "\n" "\n  ")) stmts)))

(defn stmt
  "Compile one EDN flowchart statement to a Mermaid line."
  [form]
  (let [[op & more] form]
    (cond
      (= op :node)     (node-decl (first more) (or (second more) {}))
      (= op :subgraph) (let [[nm & body] more] (str "subgraph " (id nm) "\n" (block body) "\nend"))
      (arrows op)      (let [[a b opts] more]
                         (str (id a) " " (arrows op)
                              (when (:label opts) (str "|" (mlabel (:label opts)) "|")) " " (id b)))
      :else            (id op))))

(defn flowchart
  "Compile a flowchart: a direction (:LR/:TD/:TB/:RL/:BT) then statements."
  [dir & stmts]
  (str "flowchart " (id dir) "\n" (block stmts)))
