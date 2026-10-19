(ns kami.verilog
  "Verilog RTL as data — 'hiccup for hardware'. An EDN module compiles to Verilog-2001 source, so a
   counter / FSM / datapath is readable, composable data you fork and diff like a scene instead of an
   opaque .v file — and the same EDN can later drive simulation, synthesis, or a kotoba datom log for
   time-travel over a design. `.cljc` — golden on bb/JVM, previewable in the browser (shadow-cljs).

   Expressions reuse the shared kami.expr algebra (`[:+ a b]` → (a + b); `&,|,^,~,<<,>>,?:` too); the
   Verilog-only forms are `:special`s: `[:cat a b]` → {a, b}, `[:? c a b]` → (c ? a : b),
   `[:bit s i]` → s[i], `[:slice s hi lo]` → s[hi:lo]. Bit-literals pass as strings: \"8'hFF\".

   Ports (ANSI header):  [:input :clk]  ·  [:output :reg :q 8]  ·  [:input :d [7 0]]
   Body / procedural:
     [:wire :y 8] [:reg :q 4]            → wire [7:0] y;  reg [3:0] q;
     [:param :N 8] [:localparam :M 3]    → parameter N = 8;  localparam M = 3;
     [:assign lhs e]                     → assign lhs = e;
     [:always sens stmt…]                → always @(sens) begin … end   (always bracketed)
       sens: :*  ·  [:posedge :clk]  ·  [[:posedge :clk] [:negedge :rst]]
     [:<= lhs e]  (nonblocking)  ·  [:set lhs e]  (blocking)  ·  [:if c [then…] [else…]]
     [:case e [[v0 [stmt…]] … [:default [stmt…]]]]   → case (e) … endcase
     [:inst :dff :u1 {:clk :clk :d :d :q :q}]   → dff u1 (.clk(clk), .d(d), .q(q));
   Top level:  (module :name [port…] item…)  ·  (module :name {:params [[:N 8]]} [port…] item…)"
  (:require [clojure.string :as str]
            [kami.expr :as kx]))

(defn- ident [s] (str/replace (name s) "-" "_"))   ;; :data-in → data_in (Verilog has no '-')

(defn- v-special [op xs go]
  (case op
    :cat   (str "{" (str/join ", " (map go xs)) "}")                                   ;; {a, b, c}
    :?     (str "(" (go (first xs)) " ? " (go (second xs)) " : " (go (nth xs 2)) ")")  ;; ternary
    :bit   (str (go (first xs)) "[" (go (second xs)) "]")                              ;; sig[i]
    :slice (str (go (first xs)) "[" (go (second xs)) ":" (go (nth xs 2)) "]")          ;; sig[hi:lo]
    nil))

(defn expr
  "Compile an EDN expression to a Verilog expression string."
  [e] (kx/compile {:ident ident :special v-special} e))

(defn- width
  "A bus width spec → `[hi:lo] ` (trailing space) or `` for a 1-bit net.
   A number N → [N-1:0]; a [hi lo] vector → [hi:lo], where hi/lo may be expressions
   (so a parametrised `[[:- :WIDTH 1] 0]` → [(WIDTH - 1):0])."
  [w]
  (cond
    (nil? w)     ""
    (number? w)  (str "[" (dec w) ":0] ")
    (vector? w)  (str "[" (expr (first w)) ":" (expr (second w)) "] ")
    :else        ""))

(defn- port
  "An ANSI port spec [dir type? name width?] → its declaration string."
  [[dir & more]]
  (let [typ  (when (#{:reg :wire} (first more)) (first more))
        more (if typ (rest more) more)
        [nm w] more]
    (str (name dir) " " (when typ (str (name typ) " ")) (width w) (ident nm))))

(declare stmt)

(defn- pblock
  "Procedural body: a lone statement inline, several wrapped in begin … end."
  [stmts]
  (if (= 1 (count stmts))
    (stmt (first stmts))
    (str "begin\n"
         (str/join "\n" (map #(str "  " (str/replace (stmt %) "\n" "\n  ")) stmts))
         "\nend")))

(defn- indent [s] (str/replace s "\n" "\n  "))

(defn- begin-end                                        ;; always-blocks read clearest fully bracketed
  [stmts]
  (str "begin\n" (str/join "\n" (map #(str "  " (indent (stmt %))) stmts)) "\nend"))

(defn- edge [[e sig]] (str (name e) " " (ident sig)))   ;; [:posedge :clk] → posedge clk

(defn- sens [s]
  (cond
    (= :* s)            "*"
    (keyword? (first s)) (edge s)                        ;; one edge: [:posedge :clk]
    :else               (str/join " or " (map edge s)))) ;; list: [[:posedge :clk] [:negedge :rst]]

(defn stmt
  "Compile one EDN body/procedural form to a Verilog statement string."
  [s]
  (let [[op & xs] s]
    (case op
      :wire       (str "wire " (width (second xs)) (ident (first xs)) ";")
      :reg        (str "reg "  (width (second xs)) (ident (first xs)) ";")
      :param      (str "parameter "  (ident (first xs)) " = " (expr (second xs)) ";")
      :localparam (str "localparam " (ident (first xs)) " = " (expr (second xs)) ";")
      :assign     (str "assign " (expr (first xs)) " = " (expr (second xs)) ";")
      :<=         (str (expr (first xs)) " <= " (expr (second xs)) ";")   ;; nonblocking
      :set        (str (expr (first xs)) " = "  (expr (second xs)) ";")   ;; blocking
      :always     (str "always @(" (sens (first xs)) ") " (begin-end (rest xs)))
      :if         (str "if (" (expr (first xs)) ") " (pblock (second xs))
                       (when (> (count xs) 2) (str "\nelse " (pblock (nth xs 2)))))
      :case       (str "case (" (expr (first xs)) ")\n"
                       (str/join "\n"
                         (for [[v body] (second xs)]
                           (str "  " (indent (str (if (= :default v) "default" (expr v))
                                                   ": " (pblock body))))))
                       "\nendcase")
      :inst       (let [[mod nm conns] xs]
                    (str (ident mod) " " (ident nm) " (\n"
                         (str/join ",\n" (for [[p sig] conns] (str "  ." (ident p) "(" (expr sig) ")")))
                         "\n);"))
      (str (expr s) ";"))))   ;; bare expression / system task ($display …)

(defn module
  "Compile a module form to Verilog.
     (module :name [port…] item…)
     (module :name {:params [[:WIDTH 8] …]} [port…] item…)   ;; Verilog-2001 #(parameter …) header
   ports is a vector of ANSI port specs; body is item statements."
  [name & more]
  (let [opts          (when (map? (first more)) (first more))
        [ports & body] (if opts (rest more) more)
        params        (:params opts)]
    (str "module " (ident name)
         (when (seq params)
           (str " #(\n"
                (str/join ",\n" (for [[pn pv] params] (str "  parameter " (ident pn) " = " (expr pv))))
                "\n)"))
         " (\n"
         (str/join ",\n" (map #(str "  " (port %)) ports))
         "\n);\n"
         (str/join "\n" (map #(str "  " (indent (stmt %))) body))
         "\nendmodule")))
