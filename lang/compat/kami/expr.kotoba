(ns kami.expr
  "Expression as data — the shared infix core under every `kami.*` format hiccup. `[:+ a b]` → (a + b),
   `[:dot a b]` → dot(a, b): vectors whose head is an operator compile to infix (unary when 1 arg),
   any other keyword head is a call. One arithmetic semantics, reused by kami.wgsl (shaders), kami.scad
   (solids), kami.verilog (RTL) … so the algebra is defined once, not re-hand-rolled per target.

   `compile` is parameterised by the target's surface conventions:
     :ident   keyword/symbol → identifier string   (default: (name k); a target may kebab→snake)
     :num     number → literal string              (default: (str n); WGSL floats it, others keep it)
     :call    (op, compiled-args) → string         (default: ident(a, …); a target may add ctors)
     :special (op, raw-xs, go) → string|nil        target-specific forms consulted at every node before
              the generic cond — return nil to fall through. `go` re-enters compile (so nesting and
              non-defaults work): WGSL's raw int `[:i n]` / swizzle `[:. e :xyz]`, Verilog's concat
              `[:cat a b]` / ternary `[:? c a b]` live here, not in the shared algebra.
   `.cljc`."
  (:refer-clojure :exclude [compile])
  (:require [clojure.string :as str]))

(def binops
  "Infix operators (and the unary ones — `[:- x]`, `[:~ x]`, `[:! x]` — when given a single arg).
   `:^` (xor) and `:~` (bit-not) are built via `keyword` — babashka's reader rejects those literals."
  (merge {:+ "+" :- "-" :* "*" :/ "/" :% "%"
          :< "<" :> ">" :<= "<=" :>= ">=" :== "==" :!= "!="
          :&& "&&" :|| "||" :& "&" :| "|" :! "!" :<< "<<" :>> ">>"}
         {(keyword "^") "^" (keyword "~") "~"}))

(defn compile
  "Compile an EDN expression `e` to an infix source string under the target conventions `opts`."
  ([e] (compile {} e))
  ([{:keys [ident num call special] :or {ident name num str} :as opts} e]
   (let [call (or call (fn [op args] (str (ident op) "(" (str/join ", " args) ")")))
         go   #(compile opts %)]
     (cond
       (number? e)                  (num e)
       (string? e)                  e                       ;; raw target passthrough (escape hatch)
       (or (keyword? e) (symbol? e)) (ident e)              ;; var / field path
       (vector? e)
       (let [[op & xs] e]
         (or (when special (special op xs go))              ;; target-specific forms (raw int, swizzle, concat, ternary)
             (cond
               (empty? xs)  (ident op)                      ;; a single-element vector is just the ident
               (binops op)  (if (= 1 (count xs))
                              (str "(" (binops op) (go (first xs)) ")")            ;; unary
                              (str "(" (str/join (str " " (binops op) " ") (map go xs)) ")"))
               :else        (call op (map go xs)))))        ;; function / constructor call
       :else (str e)))))
