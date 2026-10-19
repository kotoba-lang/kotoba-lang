(ns kotoba.edn.skip-blanks
  "skip-blanks -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.separator :refer [separator?]]))

(defn skip-blanks
  "Index of the next character that begins a value, skipping separators and
  `;` comments."
  [text i]
  (let [n (count text)]
    (loop [i i]
      (cond
        (>= i n) i
        (separator? (.charAt ^String text i)) (recur (inc i))
        (= (.charAt ^String text i) \;)
        (recur (loop [j i]
                 (if (or (>= j n) (= (.charAt ^String text j) \newline)) j (recur (inc j)))))
        :else i))))
