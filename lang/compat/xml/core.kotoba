(ns xml.core
  "Hiccup to XML emitter."
  (:require [clojure.string :as str]))

(defn- qualified-name
  "The full \"prefix:local\" tag/attr name for a namespaced keyword
  (:p/sp -> \"p:sp\"), or just its name for a plain one (:workbook ->
  \"workbook\") -- Clojure's own namespace/name split maps directly onto
  XML's prefix:localname syntax, so a namespace-prefixed OOXML tag
  round-trips through xml.parse -> xml.core correctly instead of silently
  losing its prefix."
  [k]
  (if-let [ns (namespace k)] (str ns ":" (name k)) (name k)))

(defn- esc [s] (-> (str s) (str/replace "&" "&amp;") (str/replace "<" "&lt;") (str/replace ">" "&gt;")))
(defn- esc-attr [s] (str/replace (esc s) "\"" "&quot;"))
(defn- attrs [m]
  (apply str (for [[k v] m]
               (str " " (if (keyword? k) (qualified-name k) (name k))
                    "=\"" (esc-attr (if (keyword? v) (qualified-name v) v)) "\""))))

(declare emit)
(defn- emit [form ind]
  (let [pad (apply str (repeat ind "  "))]
    (if (vector? form)
      (let [[tag & more] form
            attr (when (map? (first more)) (first more))
            children (if attr (rest more) more)
            tag-name (qualified-name tag)]
        (if (seq children)
          (str pad "<" tag-name (attrs attr) ">\n"
               (str/join "\n" (map #(emit % (inc ind)) children))
               "\n" pad "</" tag-name ">")
          (str pad "<" tag-name (attrs attr) " />")))
      (str pad (esc form)))))

(defn xml
  "Compile a hiccup form to an indented XML string."
  [form]
  (emit form 0))
