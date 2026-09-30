(ns hdump (:require ["node:fs" :as fs] [clojure.string :as str] [kotoba.compiler.kotoba-reader :as hr]))
(def tf (js/Function. "x" "return typeof x"))
(defn span [x] (let [m (meta x)] (if (and m (:line m)) (+ (* (:line m) 4294967296) (:column m)) 0)))
(defn d [x]
  (cond
    (nil? x) ["0 0 0 0"]
    (boolean? x) [(str "1 0 " (if x 1 0) " 0")]
    (or (integer? x) (= "bigint" (tf x))) (if (number? x) [(str "2 0 " x " 0")] [(str "2 0 " (str x) " 0")])
    (string? x) [(str "3 0 " x " 0")]
    (keyword? x) [(str "4 0 " (subs (str x) 1) " 0")]
    (symbol? x) [(str "5 " (span x) " " x " 0")]
    (and (seq? x) (:kotoba.reader/f64-literal (meta x))) [(str "10 " (span x) " " (second x) " 0")]
    (seq? x) (cons (str "6 " (span x) " 0 " (count x)) (mapcat d x))
    (vector? x) (cons (str "7 " (span x) " 0 " (count x)) (mapcat d x))
    (set? x) (cons (str "8 " (span x) " 0 " (count x)) (mapcat d x))
    (map? x) (cons (str "9 " (span x) " 0 " (* 2 (count x))) (mapcat (fn [[k v]] (concat (d k) (d v))) x))
    :else [(str "? " x)]))
(let [src (str (.readFileSync fs (first *command-line-args*) "utf8"))]
  (println (str/join "\n" (mapcat d (hr/read-forms src)))))
