;; Host side of the prelude twin differential: prints order, then for each helper and two non-helpers its text and has?.
(ns host-prelude
  (:require [clojure.string :as str]
            [kotoba.compiler.refactor.prelude :as p]))
(let [out (volatile! [])
      w (fn [s] (vswap! out conj s))]
  (doseq [n p/order] (w (str "O " n)))
  (doseq [n (concat p/order ["nope" ""])]
    (w (str "H " n " " (if (contains? p/defs n) 1 0)))
    (w (str "T " n "\n" (get p/defs n "") "\nEND")))
  (println (str/join "\n" @out)))
