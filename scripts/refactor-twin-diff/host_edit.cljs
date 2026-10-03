;; Host side of the edit twin differential: amu's src/kotoba/compiler/refactor/edit.cljk (nbb) prints the lines
;; probe/probe/edit_dump.kotoba prints for the twin. Usage: kbb --classpath <amu>/src host_edit.cljs <src> <scenario>
(ns host-edit
  (:require ["node:fs" :as fs]
            [clojure.string :as str]
            [kotoba.compiler.refactor.edit :as ed]))

(defn untok [t] (if (= t "~") "" t))
(defn num [s] (js/parseInt s 10))

(defn run [src-path scen-path]
  (let [src (.toString (fs/readFileSync src-path) "utf8")
        scen (.toString (fs/readFileSync scen-path) "utf8")
        edits (vec (for [l (str/split-lines scen)
                         :let [p (str/split l #" ")]
                         :when (and (>= (count p) 6) (= "E" (first p)))]
                     {:s (num (nth p 1)) :e (num (nth p 2)) :text (untok (nth p 3)) :rule (untok (nth p 4))
                      :ord (num (nth p 5))}))
        [kept dropped] (ed/resolve-conflicts edits)
        out (volatile! [])
        w (fn [s] (vswap! out conj s))
        dl (fn [tag xs] (doseq [x xs] (w (str tag " " (:s x) " " (:e x) " " (or (:ord x) 0) " " (:text x) " " (:rule x)))))]
    (dl "K" kept) (dl "D" dropped)
    (let [n (min 14 (count edits))]
      (doseq [a (range n) b (range (inc a) n)]
        (let [x (nth edits a) y (nth edits b)]
          (w (str "C " a " " b " " (if (ed/conflicts? x y) 1 0) " " (if (ed/nested? x y) 1 0) " " (if (ed/nested? y x) 1 0))))))
    (w (str "A " (ed/apply-edits src kept) "\nEND"))
    (let [starts (ed/line-starts src)]
      (doseq [s starts] (w (str "L " s)))
      (doseq [off (range 0 (inc (count src)) 1)]
        (w (str "O " off " " (ed/offset->line starts off) " [" (ed/line-indent src off) "]"))))
    (println (str/join "\n" @out))))

(let [[a b] *command-line-args*] (run a b))
