(ns kami.proto
  "Protocol Buffers schema as data — 'hiccup for wire schemas'. A .proto file is a declarative message/
   service schema, so it maps onto EDN directly — an API contract / message layout is composable data
   you fork and diff like a scene. A schema/IDL sibling in the kami.* family (next to kami.wgsl's WIT
   lineage). `.cljc`.

   proto3 is a tree of declarations (not infix), so no kami.expr. Items:
     [:field :string :name 1]           → string name = 1;
     [:repeated :string :emails 3]      → repeated string emails = 3;
     [:optional :int32 :age 4]          → optional int32 age = 4;
     [:map :string :int32 :counts 5]    → map<string, int32> counts = 5;
     [:message :Person field…]          → message Person { … }     (nestable)
     [:enum :Phone [:MOBILE 0] [:HOME 1]] → enum Phone { MOBILE = 0; HOME = 1; }
     [:oneof :id field…]                → oneof id { … }
     [:service :Greeter rpc…]  ·  [:rpc :SayHello :Req :Reply]  ·  [:rpc :Chat [:stream :Msg] :Ack]
   Types/names are keywords or strings (a message ref like :Address → Address).
   Top level:  (proto {:package \"ex\" :syntax \"proto3\" :imports [\"google/...\"]} item…)"
  (:require [clojure.string :as str]))

(defn- id [x] (if (keyword? x) (name x) (str x)))
(defn- rpc-arg [a] (if (vector? a) (str (id (first a)) " " (id (second a))) (id a)))  ;; [:stream :Msg] → stream Msg

(declare item)
(defn- block [items] (str/join "\n" (map #(str "  " (str/replace (item %) "\n" "\n  ")) items)))

(defn item
  "Compile one EDN proto declaration to a .proto statement string."
  [form]
  (let [[op & more] form]
    (case op
      :field    (let [[t n num] more] (str (id t) " " (id n) " = " num ";"))
      :repeated (let [[t n num] more] (str "repeated " (id t) " " (id n) " = " num ";"))
      :optional (let [[t n num] more] (str "optional " (id t) " " (id n) " = " num ";"))
      :map      (let [[k v n num] more] (str "map<" (id k) ", " (id v) "> " (id n) " = " num ";"))
      :enum     (let [[nm & vals] more]
                  (str "enum " (id nm) " {\n"
                       (str/join "\n" (for [[vn vnum] vals] (str "  " (id vn) " = " vnum ";"))) "\n}"))
      :oneof    (let [[nm & body] more] (str "oneof " (id nm) " {\n" (block body) "\n}"))
      :message  (let [[nm & body] more] (str "message " (id nm) " {\n" (block body) "\n}"))
      :service  (let [[nm & body] more] (str "service " (id nm) " {\n" (block body) "\n}"))
      :rpc      (let [[nm req resp] more] (str "rpc " (id nm) " (" (rpc-arg req) ") returns (" (rpc-arg resp) ");"))
      (str (id op) ";"))))

(defn proto
  "Compile a .proto file: opts {:syntax :package :imports} then top-level items."
  [{:keys [syntax package imports]} & body]
  (str "syntax = \"" (or syntax "proto3") "\";\n"
       (when package (str "\npackage " package ";\n"))
       (apply str (for [i imports] (str "import \"" i "\";\n")))
       "\n"
       (str/join "\n\n" (map item body))
       "\n"))
