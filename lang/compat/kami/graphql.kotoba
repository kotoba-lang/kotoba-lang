(ns kami.graphql
  "GraphQL SDL as data — 'hiccup for API schemas'. A GraphQL schema is a declarative type system, so it
   maps onto EDN directly — an API contract is composable data you fork and diff like a scene. The
   query-API sibling to kami.proto on the schema/IDL axis. `.cljc`.

   SDL is a tree of type definitions (not infix), so no kami.expr. Items:
     [:type :User {:implements [:Node]} field…] → type User implements Node { … }
     [:interface :Node field…]  ·  [:input :UserInput field…]
     [:field :id :ID!]                  → id: ID!          (non-null/list live in the type token)
     [:field :user {:id :ID!} :User]    → user(id: ID!): User    (args map)
     [:field :tags [:list! :String!]]   → tags: [String!]!       ([:list t]→[t], [:list! t]→[t]!)
     [:enum :Role :ADMIN :USER]         → enum Role { ADMIN USER }
     [:union :Result :User :Post]       → union Result = User | Post
     [:scalar :DateTime]  ·  [:schema {:query :Query :mutation :Mutation}]
   Type tokens are keywords — `:ID!`/`:String!` carry their own non-null `!`. Top: (graphql item…)"
  (:require [clojure.string :as str]))

(defn- id [x] (if (keyword? x) (name x) (str x)))

(defn- gtype [t]
  (cond
    (and (vector? t) (= :list  (first t))) (str "[" (gtype (second t)) "]")
    (and (vector? t) (= :list! (first t))) (str "[" (gtype (second t)) "]!")
    (keyword? t) (name t)
    :else        (str t)))

(defn- field [[_ nm a b]]
  (let [[args typ] (if (map? a) [a b] [nil a])]
    (str (id nm)
         (when (seq args) (str "(" (str/join ", " (for [[an at] args] (str (id an) ": " (gtype at)))) ")"))
         ": " (gtype typ))))

(declare item)
(defn- block [fields] (str/join "\n" (map #(str "  " (field %)) fields)))

(defn item
  "Compile one EDN GraphQL definition to an SDL string."
  [form]
  (let [[op & more] form]
    (case op
      :type      (let [[nm & r] more
                       opts   (when (map? (first r)) (first r))
                       fields (if opts (rest r) r)]
                   (str "type " (id nm)
                        (when (seq (:implements opts))
                          (str " implements " (str/join " & " (map id (:implements opts)))))
                        " {\n" (block fields) "\n}"))
      :interface (let [[nm & fields] more] (str "interface " (id nm) " {\n" (block fields) "\n}"))
      :input     (let [[nm & fields] more] (str "input " (id nm) " {\n" (block fields) "\n}"))
      :enum      (let [[nm & vals] more]
                   (str "enum " (id nm) " {\n" (str/join "\n" (map #(str "  " (id %)) vals)) "\n}"))
      :union     (let [[nm & types] more] (str "union " (id nm) " = " (str/join " | " (map id types))))
      :scalar    (str "scalar " (id (first more)))
      :schema    (str "schema {\n"
                      (str/join "\n" (for [[k v] (first more)] (str "  " (id k) ": " (id v)))) "\n}")
      (str (id op)))))

(defn graphql
  "Compile a sequence of GraphQL definitions to an SDL document string."
  [& defs] (str/join "\n\n" (map item defs)))
