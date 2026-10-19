(ns kotoba.edn.preflight
  "preflight! -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.closing :refer [closing?]]
            [kotoba.edn.matching-close :refer [matching-close]]
            [kotoba.edn.max-depth :refer [max-depth]]
            [kotoba.edn.max-token-chars :refer [max-token-chars]]
            [kotoba.edn.opening :refer [opening?]]
            [kotoba.edn.reject :refer [reject!]]
            [kotoba.edn.separator :refer [separator?]]))

(defn preflight!
  "Bound and syntax-check `text` before any value is built. `allow-tags?` is
  false unless the caller supplied a `:readers` or `:default` option: a tagged
  literal is admitted by the LEXER only when someone has said what to do with
  it, so the strict default cannot be widened by accident."
  ([text] (preflight! text false))
  ([text allow-tags?]
  (loop [index 0 stack [] token-length 0 token? false
         in-string? false escaped? false in-comment? false forms 0]
    (if (>= index (count text))
      (do
        (when in-string? (reject! "EDN string is unterminated" {}))
        (when (seq stack) (reject! "EDN collection is unterminated" {}))
        (when (zero? forms) (reject! "EDN input is empty" {}))
        (when (> forms 1) (reject! "EDN input contains trailing forms" {}))
        text)
      (let [ch (.charAt text index)
            depth (count stack)]
        (cond
          in-comment?
          (recur (inc index) stack 0 false false false
                 (not= ch \newline) forms)

          (and in-string? escaped?)
          (recur (inc index) stack 0 false true false false forms)

          (and in-string? (= ch \\))
          (recur (inc index) stack 0 false true true false forms)

          in-string?
          (recur (inc index) stack 0 false (not= ch \") false false forms)

          (= ch \;)
          (recur (inc index) stack 0 false false false true forms)

          (= ch \")
          (recur (inc index) stack 0 false true false false
                 (if (zero? depth) (inc forms) forms))

          (= ch \#)
          ;; Two dispatch forms are admitted and no others: `#{` (a set) and
          ;; `#:ns{` (a namespaced map). Both are pure data shapes -- neither
          ;; names a reader, so neither can run anything. Every other `#` is
          ;; still refused, tagged literals included.
          (let [nxt (when (< (inc index) (count text)) (.charAt text (inc index)))
                ;; for `#:ns{`, walk the namespace token to the brace
                ns-end (when (= nxt \:)
                         (loop [j (+ index 2)]
                           (cond
                             (>= j (count text)) nil
                             (= (.charAt text j) \{) j
                             (or (separator? (.charAt text j))
                                 (opening? (.charAt text j))
                                 (closing? (.charAt text j))) nil
                             :else (recur (inc j)))))
                brace-at (cond (= nxt \{) (inc index)
                               ns-end     ns-end)]
            (cond
              brace-at
              (let [next-stack (conj stack \})]
                (when (> (count next-stack) max-depth)
                  (reject! "EDN nesting exceeds limit" {:limit max-depth}))
                (recur (inc brace-at) next-stack 0 false false false false
                       (if (zero? depth) (inc forms) forms)))

              ;; `#tag value` -- skip the TAG token here and let the value that
              ;; follows be lexed and counted normally, so `#inst "x"` is one
              ;; top-level form and `#inst "x" #inst "y"` is still two (and so
              ;; still refused as trailing).
              (and allow-tags? nxt (not (separator? nxt))
                   (not (opening? nxt)) (not (closing? nxt)))
              (recur (loop [j (inc index)]
                       (if (or (>= j (count text))
                               (separator? (.charAt text j))
                               (opening? (.charAt text j))
                               (closing? (.charAt text j)))
                         j
                         (recur (inc j))))
                     stack 0 false false false false forms)

              :else (reject! "EDN dispatch forms are forbidden" {})))

          (opening? ch)
          (let [next-stack (conj stack (matching-close ch))]
            (when (> (count next-stack) max-depth)
              (reject! "EDN nesting exceeds limit" {:limit max-depth}))
            (recur (inc index) next-stack 0 false false false false
                   (if (zero? depth) (inc forms) forms)))

          (closing? ch)
          (do
            (when (or (empty? stack) (not= ch (peek stack)))
              (reject! "EDN collection delimiters do not match" {}))
            (recur (inc index) (pop stack) 0 false false false false forms))

          (separator? ch)
          (recur (inc index) stack 0 false false false false forms)

          :else
          (let [next-length (if token? (inc token-length) 1)]
            (when (> next-length max-token-chars)
              (reject! "EDN token exceeds limit" {:limit max-token-chars}))
            (recur (inc index) stack next-length true false false false
                   (if (and (zero? depth) (not token?)) (inc forms) forms)))))))))
