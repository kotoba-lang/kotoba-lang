(ns kotoba.edn.form-spans
  "form-spans -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.closing :refer [closing?]]
            [kotoba.edn.matching-close :refer [matching-close]]
            [kotoba.edn.max-depth :refer [max-depth]]
            [kotoba.edn.opening :refer [opening?]]
            [kotoba.edn.reject :refer [reject!]]
            [kotoba.edn.separator :refer [separator?]]
            [kotoba.edn.span-reject :refer [span-reject!]]))

(defn form-spans
  "Index spans `[start end)` of each top-level form in `text`, in order.

  Same lexer as `preflight!` (strings, escapes, `;` comments, `#{`, `#:ns{`),
  but it records where each top-level form begins and ends instead of counting
  them. Refuses the same malformed input `preflight!` refuses; it does not
  refuse multiple forms, which is the entire point.

  `pending` is how a tagged literal stays ONE span: `#a 1` is a tag followed by
  a value, and the value's end is the form's end, so a span is not closed while
  a tag is still waiting for one. A completed value RESETS pending rather than
  decrementing it -- in `#a #b 1` each tag wraps the result of the one inside
  it, so the single `1` completes both."
  ([text] (form-spans text false))
  ([text allow-tags?]
   (let [n (count text)]
     (loop [i 0, stack [], token? false, in-string? false, escaped? false,
            in-comment? false, start nil, spans [], pending 0]
       (cond
         (>= i n)
         (do (when in-string? (span-reject! "EDN string is unterminated" i))
             (when (seq stack) (span-reject! "EDN collection is unterminated" i))
             ;; a trailing bare token IS the pending tag's value; a tag with
             ;; nothing after it is not
             (when (and (pos? pending) (not token?))
               (span-reject! "EDN tagged literal has no value" i))
             (if start (conj spans [start n]) spans))

         :else
         (let [ch    (.charAt ^String text i)
               depth (count stack)]
           (cond
             in-comment?
             (recur (inc i) stack token? in-string? false (not= ch \newline) start spans pending)

             (and in-string? escaped?)
             (recur (inc i) stack token? true false false start spans pending)

             (and in-string? (= ch \\))
             (recur (inc i) stack token? true true false start spans pending)

             in-string?
             (if (= ch \")
               (if (zero? depth)
                 (recur (inc i) stack false false false false nil (conj spans [start (inc i)]) 0)
                 (recur (inc i) stack false false false false start spans pending))
               (recur (inc i) stack token? true false false start spans pending))

             ;; A bare top-level token ends at the first character that cannot
             ;; continue it. Close the span and re-process this same character
             ;; with token? false -- progress is guaranteed because the branch
             ;; that sent us here cannot be taken twice for one index.
             (and token? (zero? depth)
                  (or (separator? ch) (closing? ch) (opening? ch)
                      (= ch \;) (= ch \") (= ch \#)))
             (recur i stack false false false false nil (conj spans [start i]) 0)

             (= ch \;)
             (recur (inc i) stack token? false false true start spans pending)

             (= ch \")
             (recur (inc i) stack false true false false
                    (if (zero? depth) (or start i) start) spans pending)

             (= ch \#)
             (let [nxt    (when (< (inc i) n) (.charAt ^String text (inc i)))
                   ns-end (when (= nxt \:)
                            (loop [j (+ i 2)]
                              (cond
                                (>= j n) nil
                                (= (.charAt ^String text j) \{) j
                                (or (separator? (.charAt ^String text j))
                                    (opening? (.charAt ^String text j))
                                    (closing? (.charAt ^String text j))) nil
                                :else (recur (inc j)))))
                   brace  (cond (= nxt \{) (inc i)
                                ns-end     ns-end)]
               (cond
                 brace
                 (let [next-stack (conj stack \})]
                   (when (> (count next-stack) max-depth)
                     (reject! "EDN nesting exceeds limit" {:limit max-depth}))
                   (recur (inc brace) next-stack false false false false
                          (if (zero? depth) (or start i) start) spans pending))

                 (and allow-tags? nxt (not (separator? nxt))
                      (not (opening? nxt)) (not (closing? nxt)))
                 (recur (loop [j (inc i)]
                          (if (or (>= j n)
                                  (separator? (.charAt ^String text j))
                                  (opening? (.charAt ^String text j))
                                  (closing? (.charAt ^String text j)))
                            j
                            (recur (inc j))))
                        stack false false false false
                        (if (zero? depth) (or start i) start) spans
                        (if (zero? depth) (inc pending) pending))

                 :else (reject! "EDN dispatch forms are forbidden" {})))

             (opening? ch)
             (let [next-stack (conj stack (matching-close ch))]
               (when (> (count next-stack) max-depth)
                 (reject! "EDN nesting exceeds limit" {:limit max-depth}))
               (recur (inc i) next-stack false false false false
                      (if (zero? depth) (or start i) start) spans pending))

             (closing? ch)
             (do
               (when (or (empty? stack) (not= ch (peek stack)))
                 (span-reject! "EDN collection delimiters do not match" i))
               (let [next-stack (pop stack)]
                 (if (empty? next-stack)
                   (recur (inc i) next-stack false false false false nil
                          (conj spans [start (inc i)]) 0)
                   (recur (inc i) next-stack false false false false start spans pending))))

             (separator? ch)
             (recur (inc i) stack false false false false start spans pending)

             :else
             (recur (inc i) stack true false false false
                    (if (and (zero? depth) (nil? start)) i start) spans pending))))))))
