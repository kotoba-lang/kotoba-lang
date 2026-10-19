(ns kotoba.edn.parse-atom
  "parse-atom -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.parse-number :refer [parse-number]]
            [kotoba.edn.reject :refer [reject!]]))

(defn parse-atom [tok]
  (cond
    (= tok "nil")   nil
    (= tok "true")  true
    (= tok "false") false

    (= (.charAt ^String tok 0) \:)
    (cond
      (= tok ":")                  (reject! "EDN keyword is empty" {})
      (= (subs tok 0 (min 2 (count tok))) "::")
      (reject! "EDN auto-resolved keywords are forbidden" {:token tok})
      :else (keyword (subs tok 1)))

    ;; A number starts with a DIGIT, optionally signed. A leading dot does
    ;; not: `.`, `.5`, `-.5`, `...` and `.x` are all symbols to clojure.edn,
    ;; and routing them to the number parser made this reader refuse
    ;; `guest-grammar.edn` and `surface-status.edn` -- two of the workspace's
    ;; own resource files, whose grammars use a bare `.` as a symbol.
    (re-matches #"[+-]?[0-9].*" tok) (parse-number tok)

    :else (symbol tok)))
