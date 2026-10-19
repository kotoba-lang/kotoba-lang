(ns kami.graphml
  "GraphML as data — 'hiccup for graph interchange'. GraphML is the standard XML interchange format for
   graphs, so it maps onto EDN directly — a graph with typed attributes is composable data you fork and
   diff. The standard-interchange sibling to kami.dot / kami.mermaid on the graph axis. `.cljc`, built
   on kami.xml.

   Shorthand items (compiled to the GraphML XML structure):
     [:key {:id \"d0\" :for :node :attr.name \"label\" :attr.type :string}] → <key …/>  (attr declaration)
     [:node :a [:data \"d0\" \"Start\"]]   → <node id=\"a\"> <data key=\"d0\">Start</data> </node>
     [:node :b]                          → <node id=\"b\"/>
     [:edge :a :b [:data \"d1\" \"1.5\"]]   → <edge source=\"a\" target=\"b\"> … </edge>
   `<key>` items live under <graphml>; nodes/edges under <graph>. Top:
     (graphml {:id \"G\" :edgedefault :directed} item…)"
  (:require [kami.xml :as xml]))

(defn- el
  "Convert a GraphML shorthand item to a kami.xml hiccup element."
  [form]
  (let [[op & more] form]
    (case op
      :key  [:key (first more)]
      :data (let [[k text] more] [:data {:key k} text])
      :node (let [[id & data] more] (into [:node {:id (name id)}] (map el data)))
      :edge (let [[src tgt & data] more]
              (into [:edge {:source (name src) :target (name tgt)}] (map el data)))
      form)))

(defn graphml
  "Compile a GraphML document: opts {:id :edgedefault} then key/node/edge items."
  [{:keys [id edgedefault] :or {id "G" edgedefault :directed}} & body]
  (let [keys  (filter #(= :key (first %)) body)
        elems (remove #(= :key (first %)) body)]
    (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
         (xml/xml
           (into [:graphml {:xmlns "http://graphml.graphdrawing.org/xmlns"}]
                 (concat (map el keys)
                         [(into [:graph {:id id :edgedefault (name edgedefault)}] (map el elems))]))))))
