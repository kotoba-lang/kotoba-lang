(ns kami.spirv
  "SPIR-V assembly as data — 'hiccup for the GPU IR'. A vector of EDN instructions compiles to SPIR-V
   textual assembly (the `spirv-as` / `spirv-dis` surface), so a shader module's SSA is readable,
   composable data you fork and diff like a scene — a sibling to kami.wgsl on the GPU side. `.cljc`.

   SPIR-V is flat SSA (no infix expressions), so unlike kami.{scad,verilog,wgsl} this does NOT use the
   shared kami.expr core — each instruction is one line. NOTE: this is the assembly-as-data layer; a
   real WGSL-AST → SPIR-V compiler (a type table + SSA pass) is separate, future work.

   One instruction is a vector. Operand kinds disambiguate by EDN type:
     :id      keyword   → %id reference        (kebab→snake)
     Sym      symbol    → enum / bareword token (Shader, GLSL450, None, Fragment, Output)
     42       number    → numeric literal
     \"main\"  string    → \"quoted\" string literal
   Opcode is the keyword that starts with \"Op\". A result-producing instruction leads with its id:
     [:OpCapability Shader]               → OpCapability Shader
     [:OpEntryPoint Fragment :main \"main\" :color] → OpEntryPoint Fragment %main \"main\" %color
     [:float :OpTypeFloat 32]             → %float = OpTypeFloat 32
     [:OpStore :color :white]             → OpStore %color %white
   (In .clj code quote the instruction vector — '[:OpCapability Shader] — so enums stay symbols; in an
   .edn data file no quoting is needed.)"
  (:require [clojure.string :as str]))

(defn- ident [s] (str/replace (name s) "-" "_"))

(defn- operand [x]
  (cond
    (keyword? x) (str "%" (ident x))     ;; id reference
    (symbol? x)  (name x)                ;; enum / bareword token
    (string? x)  (str \" x \")           ;; string literal
    :else        (str x)))               ;; numeric literal

(defn- op? [x] (and (keyword? x) (str/starts-with? (name x) "Op")))   ;; opcode vs. result id

(defn- spaced [operands] (apply str (map #(str " " (operand %)) operands)))

(defn inst
  "Compile one EDN instruction to a SPIR-V assembly line."
  [form]
  (let [[a b] form]
    (if (op? a)
      (str (name a) (spaced (rest form)))                  ;; no result: OpName operand…
      (str "%" (ident a) " = " (name b) (spaced (drop 2 form))))))   ;; %id = OpName operand…

(defn asm
  "Compile a sequence of EDN instructions to a SPIR-V assembly module string."
  [& insts]
  (str/join "\n" (map inst (if (and (= 1 (count insts)) (sequential? (first insts))
                                     (not (keyword? (ffirst insts))))
                             (first insts) insts))))
