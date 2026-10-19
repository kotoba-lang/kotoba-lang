(ns prolly-tree.schema
  "Mechanical schema for the Prolly Tree substrate blocks."
  (:require [ipld.schema :as ipld-schema]))

(def node-schema
  {:type :union
   :discriminator "kind"
   :members
   {"leaf"
    {:type :struct
     :fields {"kind" {:type :kind :kind :string}
              "entries" {:type :list
                         :items {:type :tuple
                                 :items [{:type :kind :kind :string}
                                         {:type :any}]}}}}
    "internal"
    {:type :struct
     :fields {"kind" {:type :kind :kind :string}
              "children" {:type :list
                          :items {:type :tuple
                                  :items [{:type :kind :kind :string}
                                          {:type :kind :kind :link}]}}}}}})

(defn unify-node
  "Unify a decoded substrate block as `ProllyNode`, or fail with its path."
  [node]
  (:value (ipld-schema/require-unify "ProllyNode" node-schema node)))
