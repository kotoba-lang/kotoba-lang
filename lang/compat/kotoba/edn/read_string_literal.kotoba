(ns kotoba.edn.read-string-literal
  "read-string-literal -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.hex-to-int :refer [hex->int]]
            [kotoba.edn.reject :refer [reject!]]))

(defn read-string-literal
  "`[value next-index]` for the string starting at the opening quote `i`."
  [text i]
  (let [n (count text)]
    (loop [j (inc i) out []]
      (when (>= j n) (reject! "EDN string is unterminated" {}))
      (let [c (.charAt ^String text j)]
        (cond
          (= c \") [(apply str out) (inc j)]

          (= c \\)
          (let [e (when (< (inc j) n) (.charAt ^String text (inc j)))]
            (cond
              (= e \") (recur (+ j 2) (conj out \"))
              (= e \\) (recur (+ j 2) (conj out \\))
              (= e \/) (recur (+ j 2) (conj out \/))
              (= e \b) (recur (+ j 2) (conj out (char 8)))
              (= e \f) (recur (+ j 2) (conj out (char 12)))
              (= e \n) (recur (+ j 2) (conj out \newline))
              (= e \r) (recur (+ j 2) (conj out \return))
              (= e \t) (recur (+ j 2) (conj out \tab))
              (= e \u)
              (let [hex (when (<= (+ j 6) n) (subs text (+ j 2) (+ j 6)))]
                (if (and hex (re-matches #"[0-9a-fA-F]{4}" hex))
                  (recur (+ j 6) (conj out (char (hex->int hex))))
                  (reject! "EDN unicode escape is malformed" {})))
              :else (reject! "EDN string escape is not recognised"
                             {:escape (str e)})))

          :else (recur (inc j) (conj out c)))))))
