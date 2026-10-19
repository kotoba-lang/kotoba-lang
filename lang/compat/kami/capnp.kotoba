(ns kami.capnp
  "Cap'n Proto schema as data — 'hiccup for zero-copy wire schemas'. A .capnp schema is a declarative
   struct/enum/interface tree with explicit field ordinals, so it maps onto EDN directly — a serialized
   message / RPC contract is composable data you fork and diff. A serialization-IDL sibling to
   kami.proto and kami.graphql. `.cljc`.

   The schema is a tree (not infix), so no kami.expr. Items (ordinals are the `@N` numbers):
     [:field :name 0 :Text]             → name @0 :Text;
     [:field :phones 3 [:List :Phone]]  → phones @3 :List(Phone);
     [:struct :Person field… nested…]   → struct Person { … }   (nestable struct/enum)
     [:enum :Color [:red 0] [:green 1]] → enum Color { red @0; green @1; }
     [:interface :Greeter [:method :hi 0 {:req :Text} {:rep :Text}]]
                                        → interface Greeter { hi @0 (req :Text) -> (rep :Text); }
   Types are keywords (:Text :UInt32 :Bool …) or [:List t]. A file needs a 64-bit id:
     (capnp \"0xdbb9ad1f14bf0b36\" item…)  → @0x…; then the definitions."
  (:require [clojure.string :as str]))

(defn- id [x] (if (keyword? x) (name x) (str x)))

(defn- ctype [t]
  (cond
    (and (vector? t) (= :List (first t))) (str "List(" (ctype (second t)) ")")
    (keyword? t) (name t)
    :else        (str t)))

(defn- params [m] (str "(" (str/join ", " (for [[k v] m] (str (id k) " :" (ctype v)))) ")"))

(declare item)
(defn- block [items] (str/join "\n" (map #(str "  " (str/replace (item %) "\n" "\n  ")) items)))

(defn item
  "Compile one EDN Cap'n Proto declaration to a schema string."
  [form]
  (let [[op & more] form]
    (case op
      :field     (let [[nm ord typ] more] (str (id nm) " @" ord " :" (ctype typ) ";"))
      :enum      (let [[nm & vals] more]
                   (str "enum " (id nm) " {\n"
                        (str/join "\n" (for [[vn vord] vals] (str "  " (id vn) " @" vord ";"))) "\n}"))
      :struct    (let [[nm & body] more] (str "struct " (id nm) " {\n" (block body) "\n}"))
      :interface (let [[nm & body] more] (str "interface " (id nm) " {\n" (block body) "\n}"))
      :method    (let [[nm ord ps rs] more]
                   (str (id nm) " @" ord " " (params ps) " -> " (params rs) ";"))
      (str (id op) ";"))))

(defn capnp
  "Compile a .capnp file: a 64-bit file id (e.g. \"0xdbb9ad1f14bf0b36\") then top-level items."
  [file-id & body]
  (str "@" file-id ";\n\n" (str/join "\n\n" (map item body)) "\n"))
