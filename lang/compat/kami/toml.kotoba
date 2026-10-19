(ns kami.toml
  "TOML as data — 'hiccup for config'. TOML is essentially an ordered nested map, so a Clojure map maps
   onto it directly — a Cargo.toml / pyproject.toml / config is composable data you fork and diff. A
   config sibling to kami.yaml/kami.json. `.cljc`.

   A map compiles to TOML: scalar/array/inline-table values become `key = value`, nested maps become
   `[table]` sections (dotted on nesting), and a vector-of-maps becomes an array of tables `[[name]]`.
   TOML's rule that bare key/values precede sub-tables is handled automatically.

     {:title \"x\"
      :package {:name \"kami\" :version \"0.1.0\" :keywords [\"edn\" \"hiccup\"]}
      :bin [{:name \"app\" :path \"src/main.rs\"}]}
     ⇒  title = \"x\"
        [package]
        name = \"kami\" …
        [[bin]]
        name = \"app\" …"
  (:require [clojure.string :as str]))

(defn- scalar [v]
  (cond
    (string? v)  (pr-str v)                ;; \"quoted\" with escapes
    (boolean? v) (str v)
    (keyword? v) (pr-str (name v))         ;; a keyword value → string
    (number? v)  (str v)
    (vector? v)  (str "[" (str/join ", " (map scalar v)) "]")                       ;; inline array
    (map? v)     (str "{ " (str/join ", " (for [[k vv] v] (str (name k) " = " (scalar vv)))) " }")  ;; inline table
    :else        (pr-str (str v))))

(defn- table-value? [v]                     ;; values that become [table] / [[table]] sections
  (or (map? v) (and (vector? v) (seq v) (every? map? v))))

(defn- lines [path m]
  (let [scalars (remove (comp table-value? val) m)
        tables  (filter (comp table-value? val) m)]
    (concat
      (for [[k v] scalars] (str (name k) " = " (scalar v)))
      (mapcat (fn [[k v]]
                (let [p (str (when (seq path) (str (str/join "." path) ".")) (name k))
                      sub (conj (vec path) (name k))]
                  (if (map? v)
                    (concat ["" (str "[" p "]")] (lines sub v))
                    (mapcat (fn [item] (concat ["" (str "[[" p "]]")] (lines sub item))) v))))
              tables))))

(defn toml
  "Compile an EDN map to a TOML document string."
  [m]
  (let [ls (lines [] m)
        ls (if (= "" (first ls)) (rest ls) ls)]   ;; no leading blank line when the doc starts with a table
    (str (str/join "\n" ls) "\n")))
