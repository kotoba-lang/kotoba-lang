(ns kami.cue
  "CUE as data — 'hiccup for typed config'. CUE is a struct-based config + schema language (config that
   carries its own types/constraints), and a CUE struct is essentially an ordered map, so an EDN map
   maps onto it directly — a typed config / schema is composable data you fork and diff. A config
   sibling to kami.yaml/toml/json, but one that can also express types. `.cljc`.

   A map compiles to a CUE struct: string→\"quoted\", number/bool bare, vector→[a, b], nested map→
   { … }. A keyword VALUE is a bare identifier — so it doubles as a type/reference: {:age :int} →
   age: int. A `:#Name` key is a CUE definition. Bare-identifier values (string/int/bool/number) let
   the same builder write schemas as well as concrete config.

     {:host \"localhost\" :port 8080 :tags [\"a\" \"b\"]
      :server {:timeout 30 :tls true}
      :#Person {:name :string :age :int}}"
  (:require [clojure.string :as str]))

(defn- pad [n] (apply str (repeat n "  ")))

(declare fields)
(defn- val* [v ind]
  (cond
    (string? v)  (pr-str v)
    (boolean? v) (str v)
    (number? v)  (str v)
    (keyword? v) (name v)                       ;; bare identifier — a type (:int) or reference
    (vector? v)  (str "[" (str/join ", " (map #(val* % ind) v)) "]")
    (map? v)     (str "{\n" (fields v (inc ind)) "\n" (pad ind) "}")
    :else        (str v)))

(defn- fields [m ind]
  (str/join "\n" (for [[k v] m] (str (pad ind) (name k) ": " (val* v ind)))))

(defn cue
  "Compile an EDN map to a CUE document string."
  [m] (str (fields m 0) "\n"))
