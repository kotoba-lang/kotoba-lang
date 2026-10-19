(ns kotoba.edn.read-form
  "read-form, read-sequence -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.

  It holds 2 definitions, not one, because they call each
  other: read-form and read-sequence are mutually recursive, and the
  source says so itself with a (declare ...). Two definitions that call each
  other cannot be two repos with an acyclic dependency, so the unit is the
  cycle. Unison groups mutually recursive definitions the same way."
  (:require [kotoba.edn.apply-tag :refer [apply-tag]]
            [kotoba.edn.closing :refer [closing?]]
            [kotoba.edn.items-to-set :refer [items->set]]
            [kotoba.edn.pairs-to-map :refer [pairs->map]]
            [kotoba.edn.parse-atom :refer [parse-atom]]
            [kotoba.edn.qualify-map :refer [qualify-map]]
            [kotoba.edn.read-char-literal :refer [read-char-literal]]
            [kotoba.edn.read-string-literal :refer [read-string-literal]]
            [kotoba.edn.reject :refer [reject!]]
            [kotoba.edn.skip-blanks :refer [skip-blanks]]
            [kotoba.edn.token-end :refer [token-end]]))

(declare read-form read-sequence)

(defn read-sequence
  "Read forms until `close`, returning `[items next-index]`."
  [text i close opts]
  (let [n (count text)]
    (loop [i i items []]
      (let [i (skip-blanks text i)]
        (when (>= i n) (reject! "EDN collection is unterminated" {}))
        (if (= (.charAt ^String text i) close)
          [items (inc i)]
          (let [[v i'] (read-form text i opts)]
            (recur i' (conj items v))))))))

(defn read-form
  "`[value next-index]` for the one form starting at `i` (already past blanks)."
  [text i opts]
  (let [n (count text)]
    (when (>= i n) (reject! "EDN input is empty" {}))
    (let [c (.charAt ^String text i)]
      (cond
        (= c \") (read-string-literal text i)
        (= c \\) (read-char-literal text i)

        (= c \#)
        (cond
          (and (< (inc i) n) (= (.charAt ^String text (inc i)) \{))
          (let [[items i'] (read-sequence text (+ i 2) \} opts)]
            [(items->set items) i'])

          (and (< (inc i) n) (= (.charAt ^String text (inc i)) \:))
          (let [brace (loop [j (+ i 2)]
                        (cond (>= j n) (reject! "EDN namespaced map is unterminated" {})
                              (= (.charAt ^String text j) \{) j
                              :else (recur (inc j))))
                ns    (subs text (+ i 2) brace)
                [items i'] (read-sequence text (inc brace) \} opts)]
            (when (empty? ns) (reject! "EDN namespaced map has no namespace" {}))
            [(qualify-map ns (pairs->map items)) i'])

          ;; `#tag value` -- only reachable when the caller supplied :readers
          ;; or :default; preflight refuses the form otherwise.
          (and (< (inc i) n) (or (:readers opts) (:default opts)))
          (let [tag-end (token-end text (inc i))
                tag     (symbol (subs text (inc i) tag-end))
                vstart  (skip-blanks text tag-end)]
            (when (>= vstart n)
              (reject! "EDN tagged literal has no value" {:tag tag}))
            (let [[v i'] (read-form text vstart opts)]
              [(apply-tag opts tag v) i']))

          :else (reject! "EDN dispatch forms are forbidden" {}))

        (= c \() (let [[items i'] (read-sequence text (inc i) \) opts)] [(apply list items) i'])
        (= c \[) (let [[items i'] (read-sequence text (inc i) \] opts)] [items i'])
        (= c \{) (let [[items i'] (read-sequence text (inc i) \} opts)] [(pairs->map items) i'])

        (closing? c) (reject! "EDN collection delimiters do not match" {})

        :else (let [j (token-end text i)]
                (when (= j i) (reject! "EDN token is empty" {}))
                [(parse-atom (subs text i j)) j])))))
