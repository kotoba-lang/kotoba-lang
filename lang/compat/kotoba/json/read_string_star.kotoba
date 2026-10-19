(ns kotoba.json.read-string-star
  "read-string* -- addressed on its own.

  Split out of json.core on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.json.codepoint :refer [codepoint]])
)

(defn read-string* [s i]
  (loop [i (inc i) out []]
    (let [ch (nth s i nil)]
      (cond
        (nil? ch) (throw (ex-info "unterminated JSON string" {:pos i}))
        (= ch \") [(apply str out) (inc i)]
        (= ch \\) (let [e (nth s (inc i) nil)]
                    (case e
                      \" (recur (+ i 2) (conj out \"))
                      \\ (recur (+ i 2) (conj out \\))
                      \/ (recur (+ i 2) (conj out \/))
                      \b (recur (+ i 2) (conj out \backspace))
                      \f (recur (+ i 2) (conj out \formfeed))
                      \n (recur (+ i 2) (conj out \newline))
                      \r (recur (+ i 2) (conj out \return))
                      \t (recur (+ i 2) (conj out \tab))
                      \u (recur (+ i 6) (conj out (char (codepoint s (+ i 2)))))
                      (throw (ex-info "invalid JSON escape" {:pos i :escape e}))))
        :else (recur (inc i) (conj out ch))))))
