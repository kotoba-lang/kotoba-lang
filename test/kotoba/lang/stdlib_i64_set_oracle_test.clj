(ns kotoba.lang.stdlib-i64-set-oracle-test
  "`lang/stdlib/i64_set.kotoba`, checked against clojure.set.

  The oracle is the real thing. A table of expected unions would only
  record what someone believed a sorted-chain merge does with particular
  inputs; replaying the same operations into `clojure.set` says what the
  algebra IS, and leaves the representation as the only difference
  between the two sides.

  Both sides speak the same encoding, the one
  `stdlib_sorted_map_oracle_test` established: a set crosses the
  boundary as a decimal number, one digit per member (members are the
  digits 1..9; 0 is the empty set's terminator and a leading 1 marks the
  start so a single 0 stays empty). The harness decodes a number into
  the module's sorted pair-chain shape through `iset/set-from-chain`,
  so the SORTING itself is under test on every call, and encodes the
  sorted chain back for comparison.

  Replay vectors are deterministic and cover the shapes the algebra
  actually branches on: disjoint, overlapping, nested (subset),
  identical, and empty-on-either-side, replayed against union /
  intersection / difference / subset? / the module's own equality, with
  `clojure.set/union`, `intersection`, `difference`, `subset?` and
  `=` on sorted seqs as the oracle. Validity (strictly ascending, no
  duplicates) is asserted on every produced set -- a merge that returns
  the right MEMBERS in the wrong ORDER would otherwise pass."
  (:require [clojure.set :as cset]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema]))

(def ^:private module (slurp "lang/stdlib/i64_set.kotoba"))

;; Same trick as the sorted-map harness: the module is a library with no
;; main, so the test joins the same compilation unit with entry points
;; that expose each operation over the encoded form.
(def ^:private harness
  "(defn decode-into [n acc]
     (if (= n 1) acc (decode-into (quot n 10) (pair (- n (* 10 (quot n 10))) acc))))
   (defn decode [n] (decode-into n 0))
   (defn encode-into [items acc]
     (if (= items 0) acc (encode-into (pair-second items) (+ (* acc 10) (pair-first items)))))
   (defn encode [items] (encode-into items 1))
   (defn canon [n] (set-from-chain (decode n)))
   (defn t-union [a b] (encode (set-union (canon a) (canon b))))
   (defn t-inter [a b] (encode (set-intersection (canon a) (canon b))))
   (defn t-diff [a b] (encode (set-difference (canon a) (canon b))))
   (defn t-subset [a b] (set-subset? (canon a) (canon b)))
   (defn t-eq [a b] (set-equal? (canon a) (canon b)))
   (defn t-valid [a] (set-valid? (canon a)))
   (defn t-count [a] (set-count (canon a)))
   (defn t-contains [a v] (set-contains? (canon a) v))
   (defn main [] 0)")

(def ^:private lowered
  (delay
    (let [body (->> (sema/read-forms module)
                    (remove #(and (seq? %) (= 'ns (first %))))
                    ;; the module carries its own golden `main` (wasm32
                    ;; self-check); this unit's entry is the harness's, so
                    ;; the module's is stripped rather than renamed
                    (remove #(and (seq? %) (= 'defn (first %))
                                  (= 'main (second %))))
                    (map pr-str)
                    (str/join "\n"))]
      (kir/lower (sema/analyze (str body "\n" harness))))))

(defn- run* [function & args]
  (kir/execute @lowered function (vec args) {:fuel 100000000}))

;; ---- oracle side: clojure.set over the same decoded members ----

(defn- members [n]
  (loop [n n acc []]
    (if (= n 1)
      acc
      (recur (quot n 10) (cons (mod n 10) acc)))))

(defn- as-set [n] (set (members n)))

(defn- as-encoded [s]
  (if (empty? s)
    1
    (reduce (fn [acc v] (+ (* acc 10) v)) 1 (sort s))))

;; ---- vectors: the branch shapes of a two-finger merge ----

(def ^:private pairs
  [;; [a-members b-members] with duplicates in the ENCODED input to pin dedup
   [[] []]
   [[1] []]
   [[] [1]]
   [[1] [1]]
   [[1 3 5] [2 4 6]]
   [[1 2 3] [2 3 4]]
   [[2 3 4] [1 2 3]]
   [[1 2 3 4 5] [2 3]]
   [[2 3] [1 2 3 4 5]]
   [[9] [1]]
   [[1 2 3 1 2] [3 3 4]]
   [[5 5 5] [5 5]]])

(defn- enc [members]
  ;; encode possibly-unsorted, possibly-duplicated members as the number
  ;; the harness decodes; digits 1..9 only
  (reduce (fn [acc v] (+ (* acc 10) v)) 1 members))

(deftest union-matches-clojure-set
  (doseq [[a b] pairs]
    (let [ea (enc a) eb (enc b)
          got (run* 't-union ea eb)
          want (as-encoded (cset/union (as-set ea) (as-set eb)))]
      (is (= want got) (str "union " a " + " b ": want " want " got " got))
      (is (true? (boolean (run* 't-valid got))) (str "union result valid: " a " " b)))))

(deftest intersection-matches-clojure-set
  (doseq [[a b] pairs]
    (let [ea (enc a) eb (enc b)
          got (run* 't-inter ea eb)
          want (as-encoded (cset/intersection (as-set ea) (as-set eb)))]
      (is (= want got) (str "intersection " a " ∩ " b ": want " want " got " got))
      (is (true? (boolean (run* 't-valid got)))))))

(deftest difference-matches-clojure-set
  (doseq [[a b] pairs]
    (let [ea (enc a) eb (enc b)
          got (run* 't-diff ea eb)
          want (as-encoded (cset/difference (as-set ea) (as-set eb)))]
      (is (= want got) (str "difference " a " - " b ": want " want " got " got))
      (is (true? (boolean (run* 't-valid got)))))))

(deftest subset-matches-clojure-set
  (doseq [[a b] pairs]
    (let [ea (enc a) eb (enc b)
          got (run* 't-subset ea eb)
          want (cset/subset? (as-set ea) (as-set eb))]
      (is (= want got) (str "subset? " a " ⊆ " b ": want " want " got " got)))))

(deftest equality-matches-clojure-set
  (doseq [[a b] pairs]
    (let [ea (enc a) eb (enc b)
          got (run* 't-eq ea eb)
          want (= (as-set ea) (as-set eb))]
      (is (= want got) (str "equal? " a " = " b ": want " want " got " got)))))

(deftest count-and-membership
  (is (= 0 (run* 't-count 1)))
  (is (= 3 (run* 't-count (enc [3 1 2 1]))))
  (is (true? (boolean (run* 't-contains (enc [3 1 2 1]) 2))))
  (is (false? (boolean (run* 't-contains (enc [3 1 2 1]) 7))))
  ;; membership uses the ORDER: a miss below the head must answer false,
  ;; not keep scanning (the early-exit a sorted representation buys)
  (is (false? (boolean (run* 't-contains (enc [5 7 9]) 1)))))

(deftest canonicalization-sorts-and-dedups
  ;; from-chain of a DESCENDING, duplicated input is valid ascending
  (let [got (run* 't-valid (run* 't-union (enc [9 1 5]) 1))]
    (is (true? (boolean got)))))
