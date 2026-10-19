(ns kami.step
  "STEP (ISO 10303-21) as data — 'hiccup for mechanical CAD'. STEP Part 21 is the text exchange format
   for mechanical CAD solids/assemblies (the pro counterpart to kami.scad's CSG and kami.dxf's 2-D
   drawings), a flat list of numbered entity instances. It maps onto EDN directly — a B-rep / placement
   is composable data you fork and diff. A fresh mechanical-CAD domain. `.cljc`.

   An entity is `[id :entity-type & args]`; args render by EDN type:
     \"s\"          → 's'            (string; internal ' doubled)
     1.5 / 0       → 1.5 / 0.       (STEP reals carry a '.')
     [:ref 7]      → #7             (instance reference)
     [:list a b]   → (a, b)         (aggregate)
     :$ :*         → $ *            (unset / derived)   ·   :steel → .STEEL.  (enumeration)
     [3 :cartesian-point \"\" [:list 0 0 0]] → #3 = CARTESIAN_POINT('', (0., 0., 0.));
   Top level wraps entities in the HEADER/DATA sections:
     (step {:description \"d\" :name \"p.step\" :schema \"AUTOMOTIVE_DESIGN\"} entity…)"
  (:require [clojure.string :as str]))

(defn- token [s] (str/upper-case (str/replace (name s) "-" "_")))   ;; :axis2-placement-3d → AXIS2_PLACEMENT_3D

(defn- sreal [v]                                   ;; STEP real: 0 → "0.", 2.0 → "2.", 1.5 → "1.5"
  (let [s (str (double v))]
    (if (str/ends-with? s ".0") (subs s 0 (dec (count s))) s)))

(declare val*)
(defn- val* [v]
  (cond
    (string? v)                          (str "'" (str/replace v "'" "''") "'")
    (and (vector? v) (= :ref  (first v))) (str "#" (second v))
    (and (vector? v) (= :list (first v))) (str "(" (str/join ", " (map val* (rest v))) ")")
    (= :$ v)                             "$"
    (= :* v)                             "*"
    (keyword? v)                         (str "." (token v) ".")   ;; enumeration value
    (number? v)                          (sreal v)
    :else                                (str v)))

(defn entity
  "Compile one [id :entity-type & args] form to a STEP instance line."
  [[id etype & args]]
  (str "#" id " = " (token etype) "(" (str/join ", " (map val* args)) ");"))

(defn step
  "Compile a STEP Part 21 file: header opts {:description :name :schema :author :org} then entities."
  [{:keys [description name schema author org] :or {schema "AUTOMOTIVE_DESIGN"}} & entities]
  (str "ISO-10303-21;\n"
       "HEADER;\n"
       "FILE_DESCRIPTION(('" (or description "") "'), '2;1');\n"
       "FILE_NAME('" (or name "") "', '', ('" (or author "") "'), ('" (or org "") "'), '', '', '');\n"
       "FILE_SCHEMA(('" schema "'));\n"
       "ENDSEC;\n"
       "DATA;\n"
       (str/join "\n" (map entity entities))
       "\nENDSEC;\n"
       "END-ISO-10303-21;\n"))
