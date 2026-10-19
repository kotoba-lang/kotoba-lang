(ns kami.spice
  "SPICE netlist as data — 'hiccup for circuits'. An EDN statement list compiles to a SPICE deck, so an
   analog circuit / device model / analysis is readable, composable data you fork and diff like a
   scene. The analog counterpart to kami.verilog on the semiconductor axis. `.cljc`.

   SPICE is line-oriented (not infix/tree), so it does not use kami.expr. A statement is `[head & args]`:
     element head → letter+name then tokens   :r :c :l :v :i :d :q :m :x :e :g :f :h :j :k
       [:r 1 :in :out \"1k\"]            → R1 in out 1k
       [:v 1 :in :gnd [:dc 5]]          → V1 in 0 DC 5          ;; :gnd → node 0
       [:m 1 :d :g :s :b \"NMOS\" {:W \"1u\" :L \"0.18u\"}] → M1 d g s b NMOS W=1u L=0.18u
     source value vector → DC/AC v or NAME(args):  [:dc 5] [:ac 1] [:sin 0 1 \"1k\"] [:pulse 0 5 0 \"1n\"]
     a trailing map → key=value params
     directives → .name args:   [:tran \"1u\" \"1m\"]  [:op]  [:model :NMOS :NMOS {…}]  [:end]
     [:* \"text\"] → a * comment line.  (netlist title stmt…) prefixes the title comment line."
  (:require [clojure.string :as str]))

(def ^:private elem {:r "R" :c "C" :l "L" :v "V" :i "I" :d "D" :q "Q" :m "M" :x "X"
                     :e "E" :g "G" :f "F" :h "H" :j "J" :k "K"})

(declare tok)

(defn- params [m] (str/join " " (for [[k v] m] (str (name k) "=" v))))   ;; W=1u L=0.18u

(defn- source [[t & a]]                                                   ;; [:sin 0 1 "1k"] → SIN(0 1 1k)
  (case t
    :dc (str "DC " (str/join " " (map tok a)))
    :ac (str "AC " (str/join " " (map tok a)))
    (str (str/upper-case (name t)) "(" (str/join " " (map tok a)) ")")))

(defn- tok [x]
  (cond
    (= :gnd x)   "0"                     ;; ground is node 0
    (keyword? x) (name x)                ;; node / model name
    (string? x)  x                       ;; values keep SI suffixes ("1k", "10n", "0.18u")
    (vector? x)  (source x)              ;; source spec
    (map? x)     (params x)              ;; trailing parameter map
    :else        (str x)))

(defn- name-tok [x] (if (keyword? x) (name x) (str x)))   ;; element suffix: :R1's 1, or "a"

(defn stmt
  "Compile one EDN statement to a SPICE netlist line."
  [form]
  (let [[head & args] form]
    (cond
      (elem head)     (str/join " " (cons (str (elem head) (name-tok (first args)))
                                          (map tok (rest args))))
      (= :* head)     (str "* " (str/join " " (map #(if (string? %) % (tok %)) args)))
      (= :model head) (str ".model " (tok (first args)) " " (tok (second args))
                           (when-let [p (nth (vec args) 2 nil)] (str " (" (params p) ")")))
      (= :end head)   ".end"
      :else           (str "." (name head)                          ;; .tran / .op / .ac / .subckt …
                           (when (seq args) (str " " (str/join " " (map tok args))))))))

(defn netlist
  "Compile a deck: a title (the mandatory first comment line) then statements."
  [title & stmts]
  (str/join "\n" (cons (str "* " title) (map stmt stmts))))
