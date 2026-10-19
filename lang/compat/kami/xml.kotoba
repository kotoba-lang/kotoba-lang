(ns kami.xml
  "Hiccup → XML — the shared emitter under the XML-shaped kami.* formats (kami.materialx). This is
   hiccup in its original sense: `[:tag {attrs} & children]` where children are nested element vectors
   or text. Elements with no children self-close `<tag … />`; 2-space indented for deterministic golden
   output. `.cljc`."
  (:require [clojure.string :as str]))

(defn- esc [s] (-> (str s) (str/replace "&" "&amp;") (str/replace "<" "&lt;") (str/replace ">" "&gt;")))
(defn- esc-attr [s] (str/replace (esc s) "\"" "&quot;"))
(defn- attrs [m] (apply str (for [[k v] m]
                              (str " " (name k) "=\"" (esc-attr (if (keyword? v) (name v) v)) "\""))))  ;; :node → node, not ":node"

(declare emit)
(defn- emit [form ind]
  (let [pad (apply str (repeat ind "  "))]
    (if (vector? form)
      (let [[tag & more] form
            attr     (when (map? (first more)) (first more))
            children (if attr (rest more) more)]
        (if (seq children)
          (str pad "<" (name tag) (attrs attr) ">\n"
               (str/join "\n" (map #(emit % (inc ind)) children))
               "\n" pad "</" (name tag) ">")
          (str pad "<" (name tag) (attrs attr) " />")))   ;; self-closing leaf
      (str pad (esc form)))))                              ;; text node

(defn xml
  "Compile a hiccup form to an indented XML string (no declaration)."
  [form] (emit form 0))
