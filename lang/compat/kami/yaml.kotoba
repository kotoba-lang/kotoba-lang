(ns kami.yaml
  "A small block-style YAML emitter — the shared serializer under the YAML-shaped kami.* formats
   (kami.ocio). `.cljc`, no SnakeYAML dependency, so the same code runs on bb/JVM and shadow-cljs.
   Maps → block mappings, vectors → block sequences (scalars or nested), keywords → plain scalars.
   OCIO-style local tags are supported via `(ytag \"ColorSpace\" value)` → `!<ColorSpace>`."
  (:require [clojure.string :as str]))

(defn ytag
  "Wrap a value with a YAML local tag: (ytag \"View\" {…}) → !<View> …."
  [name value] {::tag name ::val value})

(defn- pad [n] (apply str (repeat n "  ")))
(defn- scalar? [x] (or (string? x) (number? x) (boolean? x) (keyword? x) (nil? x)))

(defn- needs-quote?
  "True when a string would be mis-read as a non-string YAML scalar (or has structural chars) and so
   must be quoted to round-trip AS a string. Covers booleans/null, numeric-looking strings (\"1.5\",
   \"42\", \"1e3\", \"0xff\", .inf/.nan — these would parse as numbers), and indicator/whitespace chars."
  [s]
  (or (= "" s)
      (re-find #"[:#\[\]{}&*!|>'\"%@`,]" s)
      (re-find #"(?i)^(yes|no|true|false|null|~|\.inf|\.nan)$" s)
      (re-find #"^[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?$" s)   ;; int/float/sci → would coerce to a number
      (re-find #"(?i)^0[xob][0-9a-f]+$" s)                     ;; hex / octal / binary literal
      (re-find #"^[\s>?-]" s) (re-find #"\s$" s)))
(defn- scalar [x]
  (cond
    (string? x)  (if (needs-quote? x) (str \" (str/replace x "\"" "\\\"") \") x)
    (keyword? x) (name x)
    (nil? x)     "~"
    :else        (str x)))

(declare node)
(defn- node
  "Render value x at indent `ind` as the text placed AFTER a `key:` or `-` marker — either ` scalar`
   (same line) or `\\n<indented block>`."
  [x ind]
  (cond
    (and (map? x) (::tag x)) (let [t (str " !<" (::tag x) ">") v (::val x)]
                               (if (scalar? v) (str t " " (scalar v)) (str t (node v ind))))
    (scalar? x)     (str " " (scalar x))
    (map? x)        (str "\n" (str/join "\n" (for [[k v] x] (str (pad ind) (name k) ":" (node v (inc ind))))))
    (sequential? x) (if (empty? x) " []"
                        (str "\n" (str/join "\n" (for [it x] (str (pad ind) "-" (node it (inc ind)))))))
    :else           (str " " (scalar x))))

(defn yaml
  "Serialize a top-level EDN map to a block-style YAML string."
  [m] (str/join "\n" (for [[k v] m] (str (name k) ":" (node v 1)))))
