(ns kami.usd
  "USD (Pixar Universal Scene Description) as data — 'hiccup for scenes'. The USDA (ASCII) form is
   already a declarative, hierarchical scene description, so it maps almost 1:1 onto EDN — a prim
   hierarchy / material / xform is composable data you fork and diff like the rest of the kami.* world,
   and it bridges the GPU axis (kami.wgsl/spirv) to the Pixar/Omniverse axis. `.cljc`.

   USDA is a tree (not infix/line/pair), so it does not use kami.expr. A prim is
   `[spec type? name meta? & body]`; body mixes attributes, relationships, and nested prims:
     spec     :def / :over / :class           → def / over / class
     type     a string \"Xform\"/\"Sphere\"/…     (omit for a typeless prim)
     name     keyword/string                  → quoted \"name\"
     meta     a map {:kind \"component\"}        → ( … ) prim metadata
     [:attr \"double\" :radius 2]               → double radius = 2
     [:attr \"color3f[]\" \"primvars:displayColor\" [[1 0 0]]] → color3f[] … = [(1, 0, 0)]
     [:rel :material:binding [:path \"/W/m\"]]  → rel material:binding = </W/m>
   Values follow USDA types: a vector of scalars is a tuple (1, 2, 3); a vector of vectors is an array
   of tuples [(…), …]; [:array …] is a scalar array [1, 2, 3]; [:asset \"p\"] → @p@; [:path \"/p\"] →
   </p>; a keyword/string is a quoted token/string. Top level: (usda {layer-meta} prim…)."
  (:require [clojure.string :as str]))

(defn- pname [x] (if (keyword? x) (name x) (str x)))   ;; prim/property name (keeps ns colons in strings)

(defn- qstr [s] (str \" (-> (str s) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) \"))  ;; escape "/\\

(declare val*)
(defn- val* [v]
  (cond
    (and (vector? v) (= :array (first v))) (str "[" (str/join ", " (map val* (rest v))) "]")
    (and (vector? v) (= :asset (first v))) (str "@" (second v) "@")
    (and (vector? v) (= :path  (first v))) (str "<" (second v) ">")
    (vector? v) (if (every? vector? v)                                   ;; array of tuples
                  (str "[" (str/join ", " (map val* v)) "]")
                  (str "(" (str/join ", " (map val* v)) ")"))            ;; tuple (float3 / color3f / …)
    (string? v)  (qstr v)                    ;; quoted string, internal " / \ escaped
    (keyword? v) (qstr (name v))             ;; token literal
    :else        (str v)))                    ;; number

(defn- attr [[_ typ nm value]] (str typ " " (pname nm) " = " (val* value)))
(defn- rel  [[_ nm value]]     (str "rel " (pname nm) " = " (val* value)))

;; composition-arc metadata that USD list-edits — emitted with the idiomatic `prepend`.
(def ^:private list-op #{:references :payload :inherits :specializes :apiSchemas :variantSets})
(defn- meta-line [k v]
  (str (when (list-op k) "prepend ") (pname k) " = " (val* v)))

(defn- indent [s n]
  (let [p (apply str (repeat n " "))] (str p (str/replace s "\n" (str "\n" p)))))

(declare item)
(defn- block [items] (str/join "\n" (map #(indent (item %) 4) items)))

(defn prim
  "Compile one [spec type? name meta? & body] prim form to a USDA prim block. Prim metadata (a map
   after the name) may carry composition arcs — :references :payload :inherits :apiSchemas
   :variantSets — which are emitted as `prepend …`; other keys (e.g. :kind) emit as-is."
  [form]
  (let [[spec & r] form
        typ  (when (string? (first r)) (first r))
        r    (if typ (rest r) r)
        nm   (first r)
        r    (rest r)
        meta (when (map? (first r)) (first r))
        body (if meta (rest r) r)]
    (str (name spec) (when typ (str " " typ)) " \"" (pname nm) "\""
         (when (seq meta)
           (str " (\n" (str/join "\n" (for [[k v] meta] (str "    " (meta-line k v)))) "\n)"))
         (if (seq body) (str "\n{\n" (block body) "\n}") "\n{\n}"))))

(defn- variant-set
  "[:variant-set \"name\" {\"variant\" [body…] …}] → a USD variantSet block."
  [[_ vname variants]]
  (str "variantSet \"" vname "\" = {\n"
       (str/join "\n" (for [[vn vbody] variants]
                        (str "    \"" vn "\" {\n"
                             (str/join "\n" (map #(indent (item %) 8) vbody))
                             "\n    }")))
       "\n}"))

(defn- item [form]
  (case (first form)
    :attr        (attr form)
    :rel         (rel form)
    :variant-set (variant-set form)
    (prim form)))

(defn usda
  "Compile a USDA layer: optional {layer-metadata} then top-level prims."
  [opts & prims]
  (str "#usda 1.0\n"
       (when (seq opts)
         (str "(\n" (str/join "\n" (for [[k v] opts] (str "    " (pname k) " = " (val* v)))) "\n)\n"))
       "\n"
       (str/join "\n\n" (map item prims))
       "\n"))
