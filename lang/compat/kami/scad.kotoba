(ns kami.scad
  "OpenSCAD as data — 'hiccup for solids'. An EDN CSG tree compiles to an OpenSCAD source string, so
   a part / fixture / printable is readable, composable data you fork like a scene instead of an
   opaque .scad script. `.cljc` — the same compiler runs on shadow-cljs (browser preview) and bb/JVM
   (CI golden + headless `openscad -o part.stl part.scad`).

   A node is `[op arg* child*]`: leading non-solid elements are arguments, trailing `[:kw …]` vectors
   are child solids. Args: a number/string is positional, a map is named (key=value), a vector is a
   point/size `[x, y, z]` (kebab→snake on op & keys; `$fn`/`$fa` keys pass through).

     [:cube 10]                         → cube(10);
     [:cube [10 20 30]]                 → cube([10, 20, 30]);
     [:cube {:size [2 3 4] :center true}] → cube(size=[2, 3, 4], center=true);
     [:sphere {:r 5 :$fn 64}]           → sphere(r=5, $fn=64);
     [:translate [10 0 0] [:cube 5]]    → translate([10, 0, 0]) { cube(5); }
     [:difference [:cube 10] [:sphere 6]] → difference() { cube(10); sphere(6); }
     [:translate [[:* :i :step] 0 0] …]  → translate([(i * step), 0, 0]) { … }  ;; expr in a coord
     [:cylinder {:h [:+ :h 2] …}]       → cylinder(h=(h + 2), …);              ;; expr in a named arg
     [:def :wall 2]                     → wall = 2;
     [:module :pillar [:h :r] body…]    → module pillar(h, r) { … }   (call it with [:pillar 10 2])
     [:for [:i [:range 0 5]] child…]    → for (i = [0:5]) { … }       ([:range a s b] → [a:s:b])"
  (:require [clojure.string :as str]
            [kami.expr :as kx]))

(defn- ident [s] (str/replace (name s) "-" "_"))   ;; :linear-extrude → linear_extrude; :$fn → $fn

(defn- expr [e] (kx/compile {:ident ident} e))     ;; arithmetic/calls — shared infix algebra, SCAD idents

(defn- qstr [s] (str \" (-> (str s) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) \"))  ;; escape "/\\

(declare val*)

(defn- val* [v]
  (cond
    (boolean? v) (str v)                                   ;; true/false (e.g. :center true) — before number?
    (number? v)  (str v)                                   ;; ints stay ints, floats stay floats (SCAD takes both)
    (string? v)  (qstr v)                                  ;; quoted string literal, internal " / \ escaped
    (keyword? v) (name v)                                  ;; bareword (an enum-ish value)
    (vector? v)  (if (keyword? (first v))                  ;; keyword head = expression; numbers = a point/size
                   (expr v)
                   (str "[" (str/join ", " (map val* v)) "]"))
    :else        (str v)))

(defn- named [m] (str/join ", " (for [[k v] m] (str (ident k) "=" (val* v)))))
(defn- arg* [a] (if (map? a) (named a) (val* a)))

(defn- child-form? [x] (and (vector? x) (keyword? (first x))))  ;; a nested solid vs. a point/size arg

(defn- iter
  "A `for` iterable → OpenSCAD range/list. [:range a b] → [a:b], [:range a s b] → [a:s:b];
   a plain vector is a value list [1, 2, 3]; anything else passes through val*."
  [x]
  (cond
    (and (vector? x) (= :range (first x))) (str "[" (str/join ":" (map val* (rest x))) "]")
    (vector? x)                            (str "[" (str/join ", " (map val* x)) "]")
    :else                                  (val* x)))

(declare node)
(defn- block [forms] (str/join "\n" (map #(str "  " (str/replace (node %) "\n" "\n  ")) forms)))

(defn node
  "Compile one EDN solid form to an OpenSCAD source string."
  [form]
  (cond
    (string? form) form                                    ;; raw passthrough (escape hatch)
    (vector? form)
    (let [[op & more] form]
      (case op
        :def    (str (ident (first more)) " = " (val* (second more)) ";")
        :module (let [[nm params & body] more]
                  (str "module " (ident nm) "(" (str/join ", " (map ident params)) ") {\n"
                       (block body) "\n}"))
        :for    (let [[[v it] & body] more]                ;; [:for [:i [:range 0 5]] child…]
                  (str "for (" (ident v) " = " (iter it) ") {\n" (block body) "\n}"))
        :raw    (apply str more)
        ;; generic primitive / transform / boolean operator:
        (let [args     (take-while (complement child-form?) more)
              children (filter child-form? more)
              head     (str (ident op) "(" (str/join ", " (map arg* args)) ")")]
          (if (seq children)
            (str head " {\n" (block children) "\n}")
            (str head ";")))))
    :else (str form)))

(defn scad
  "Compile a sequence of top-level EDN forms to a full OpenSCAD source string."
  [& forms]
  (str/join "\n" (map node (if (and (= 1 (count forms)) (sequential? (first forms))
                                     (not (keyword? (ffirst forms))))
                             (first forms) forms))))
