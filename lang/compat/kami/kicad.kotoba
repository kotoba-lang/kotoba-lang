(ns kami.kicad
  "KiCad S-expressions as data — 'hiccup for boards'. A KiCad file (.kicad_pcb / .kicad_sym /
   .kicad_sch) is *already* an S-expression tree, so it maps almost 1:1 onto EDN vectors — a footprint
   / symbol / net is readable, composable data you fork and diff like a scene. This extends the
   kami.{verilog,spirv} hardware axis up to the board. `.cljc`.

   KiCad is a nested tree (not infix, not line-oriented), so it does not use kami.expr. A form is a
   vector `[head atom* child*]`; atoms disambiguate by EDN type, children are nested vectors:
     :token   keyword head/atom → bareword token   (kebab→snake: :kicad-pcb → kicad_pcb, :smd → smd)
     \"str\"    string           → \"quoted\" string  (layers, names: \"F.Cu\", \"R_0805\")
     1.25     number           → numeric literal
   A form whose children are all atoms renders inline `(at 10 20 0)`; one with nested children breaks
   onto indented lines. Put atoms before nested children (KiCad's own ordering).

     [:version 20221018]                         → (version 20221018)
     [:pad \"1\" :smd :roundrect [:at -1 0] [:size 1 1.25]]
       → (pad \"1\" smd roundrect\\n  (at -1 0)\\n  (size 1 1.25)\\n)"
  (:require [clojure.string :as str]))

(defn- token [s] (str/replace (name s) "-" "_"))   ;; :kicad-pcb → kicad_pcb, :fp-line → fp_line
(defn- qstr [s] (str \" (-> (str s) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) \"))  ;; escape "/\\

(defn- atom* [x]
  (cond
    (string? x)  (qstr x)                ;; quoted string (layer/name/uuid), internal " / \ escaped
    (keyword? x) (token x)               ;; bareword token (smd, roundrect, yes)
    (symbol? x)  (name x)
    :else        (str x)))               ;; number literal

(declare sexp)

(defn sexp
  "Compile one EDN form to a KiCad S-expression string."
  [form]
  (if (vector? form)
    (let [[head & args] form]
      (if (some vector? args)                        ;; has nested children → multi-line
        (str "(" (token head)
             (apply str (for [a args]
                          (if (vector? a)
                            (str "\n  " (str/replace (sexp a) "\n" "\n  "))
                            (str " " (atom* a)))))
             "\n)")
        (str "(" (token head)                         ;; all atoms → inline
             (apply str (map #(str " " (atom* %)) args)) ")")))
    (atom* form)))

(def ^{:doc "Compile a top-level KiCad form (e.g. a whole [:kicad-pcb …]) to source."} kicad sexp)
