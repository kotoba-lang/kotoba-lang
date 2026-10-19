(ns kami.materialx
  "MaterialX as data — 'hiccup for materials'. MaterialX (the open material/shader-graph interchange,
   used across DCCs and renderers) is XML, so it is the most literal hiccup of all: an element is
   `[:tag {attrs} & children]`. This wraps the kami.xml emitter with the `<?xml?>` declaration and the
   `<materialx>` root so a node graph / surface material is composable data you fork and diff. Extends
   the GPU/shading axis (pairs with kami.wgsl/spirv). `.cljc`, built on kami.xml.

     [:nodegraph {:name \"NG\"}
       [:constant {:name \"c\" :type \"color3\"} [:input {:name \"value\" :type \"color3\" :value \"1, 0, 0\"}]]
       [:output {:name \"out\" :type \"color3\" :nodename \"c\"}]]
     (materialx {:version \"1.38\"} nodegraph surfacematerial…)  ⇒ a full <?xml?> <materialx> document"
  (:require [kami.xml :as xml]
            [clojure.string :as str]))

(defn materialx
  "A MaterialX document: optional {:version …} (defaults 1.38) then top-level elements as hiccup."
  [opts & body]
  (str "<?xml version=\"1.0\"?>\n"
       (xml/xml (into [:materialx (merge {:version "1.38"} opts)] body))))

(defn value
  "Format a MaterialX value attribute: a vector → comma-joined (1, 0, 0); else str."
  [v] (if (vector? v) (str/join ", " (map str v)) (str v)))
