(ns expr.core
  "Expression as data: shared infix compilation core."
  (:refer-clojure :exclude [compile])
  (:require [clojure.string :as str]))

(def binops
  (merge {:+ "+" :- "-" :* "*" :/ "/" :% "%"
          :< "<" :> ">" :<= "<=" :>= ">=" :== "==" :!= "!="
          :&& "&&" :|| "||" :& "&" :| "|" :! "!" :<< "<<" :>> ">>"}
         {(keyword "^") "^" (keyword "~") "~"}))

(defn compile
  "Compile an EDN expression `e` to an infix source string under target conventions."
  ([e] (compile {} e))
  ([{:keys [ident num call special] :or {ident name num str} :as opts} e]
   (let [call (or call (fn [op args] (str (ident op) "(" (str/join ", " args) ")")))
         go #(compile opts %)]
     (cond
       (number? e) (num e)
       (string? e) e
       (or (keyword? e) (symbol? e)) (ident e)
       (vector? e) (let [[op & xs] e]
                     (or (when special (special op xs go))
                         (cond
                           (empty? xs) (ident op)
                           (binops op) (if (= 1 (count xs))
                                         (str "(" (binops op) (go (first xs)) ")")
                                         (str "(" (str/join (str " " (binops op) " ") (map go xs)) ")"))
                           :else (call op (map go xs)))))
       :else (str e)))))
