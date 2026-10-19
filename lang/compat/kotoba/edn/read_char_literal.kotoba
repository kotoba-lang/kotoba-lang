(ns kotoba.edn.read-char-literal
  "read-char-literal -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.alphanumeric :refer [alphanumeric?]]
            [kotoba.edn.hex-to-int :refer [hex->int]]
            [kotoba.edn.named-chars :refer [named-chars]]
            [kotoba.edn.reject :refer [reject!]]))

(defn read-char-literal
  "`[value next-index]` for the character literal starting at the backslash `i`.
  The character immediately after the backslash is always literal (so a closing
  paren and a semicolon both read as characters), then a name continues while
  alphanumeric."
  [text i]
  (let [n (count text)]
    (when (>= (inc i) n) (reject! "EDN character literal is empty" {}))
    (let [j   (loop [j (+ i 2)]
                (if (and (< j n) (alphanumeric? (.charAt ^String text j)))
                  (recur (inc j)) j))
          tok (subs text (inc i) j)]
      (cond
        (= 1 (count tok)) [(.charAt ^String tok 0) j]
        (contains? named-chars tok) [(get named-chars tok) j]
        (re-matches #"u[0-9a-fA-F]{4}" tok) [(char (hex->int (subs tok 1))) j]
        :else (reject! "EDN character literal is not recognised" {:literal tok})))))
