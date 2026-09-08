(ns kotoba.lang.clojure-string-option-index-test
  "`clojure.string/index-of` and `last-index-of` in `lang/compat/clojure/string.kotoba`,
  checked against clojure.string itself -- INCLUDING the nil.

  These two were absent from that module until 2026-09-08, and the reason on
  record was not about strings at all. `lang/compat.edn` recorded, under
  `:measured :option-return-on-wasm32`, that a function returning an option
  with `option-some` on one arm of an `if` and `option-none` on the other
  refused to lower to wasm32 -- `unsupported typed Wasm expression`, exit 70 --
  and refused for the WHOLE project even when `main` never reached it
  (amu bb51dc14, 2026-09-02). So `kotoba.string/utf16-index-of` answered the
  number and returned -1 for the absent case, and the module said so in its
  own header: the name is the warning.

  Re-measured against amu 9092ee34 on 2026-09-08, that is no longer true --
  it lowers, and the artifact RUNS and answers on wasm32, wasm32-browser and
  web alike. So the absent case is spelled as the option it always was.

  WHAT THIS FILE ASSERTS, and the distinction is the point: the ORACLE is
  `clojure.string/index-of` itself, called, with `nil` as the absent case.
  A table of expected numbers would record what someone believed Clojure
  does. Two of the cases below -- the astral one, and `last-index-of` with an
  empty needle -- are exactly where a believed table gets written wrong.

  The compiler-side guarantee is not here. That an option in return position
  lowers to wasm32 and produces the right number is asserted in amu's own
  conformance pilot (`:option-return-kit`), which RUNS the artifact on both
  the KIR and wasm32 backends. This file asserts the string semantics."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema]))

(def ^:private clojure-string-path "lang/compat/clojure/string.kotoba")
(def ^:private kotoba-string-path "lang/compat/kotoba/string.kotoba")

;; `index-of` returns `[:option :i64]`, so the harness reads BOTH arms back as
;; i64 rather than marshalling an option across the boundary: `present?`
;; answers which arm was taken and `value` answers the number on the some arm.
;; Splitting them is what lets an absent answer be distinguished from the
;; number -1, which is the entire difference this change makes.
(def ^:private harness
  "(defn present? [s :string v :string] :i64 (if-some [i (index-of s v)] 1 0))
   (defn value [s :string v :string] :i64 (if-some [i (index-of s v)] i -1))
   (defn last-present? [s :string v :string] :i64 (if-some [i (last-index-of s v)] 1 0))
   (defn last-value [s :string v :string] :i64 (if-some [i (last-index-of s v)] i -1))
   (defn main [] :i64 0)")

(defn- body-forms [source]
  (remove #(and (seq? %) (= 'ns (first %))) (sema/read-forms source)))

(defn- unalias
  "What the project linker does to a qualified call, and only that:
  `ks/utf16-index-of` -> `utf16-index-of`. The real consumer route needs no
  such thing -- `amu compile --source-path <repo>/lang/compat` resolves the
  `(:require [kotoba.string :as ks])` for real, measured 2026-09-08 -- but
  this repository has no linker to call, so the two modules join one
  compilation unit here."
  [alias forms]
  (walk/postwalk
   (fn [node]
     (if (and (symbol? node) (= (str alias) (namespace node)))
       (symbol (name node))
       node))
   forms))

(defn- defined-name [form]
  (when (and (seq? form) (contains? #{'defn 'defn-} (first form)))
    (second form)))

(defn- drop-shared-definitions
  "One compilation unit cannot define a name twice, and both modules define
  the private `code-point-width` -- the same three comparisons, because the
  UTF-8 width of a code point is a property of the code point and neither
  module can import the other's privates.

  Dropping the duplicate silently would hide a real divergence, so this
  RETURNS the dropped forms alongside and the test below asserts they were
  identical to the ones kept. If someone edits one copy, that assertion goes
  red rather than this link quietly choosing a winner."
  [kept-forms later-forms]
  (let [kept (into {} (keep (fn [f] (when-let [n (defined-name f)] [n f])) kept-forms))]
    (reduce (fn [acc f]
              (let [n (defined-name f)]
                (if (and n (contains? kept n))
                  (update acc :dropped conj [(get kept n) f])
                  (update acc :forms conj f))))
            {:forms [] :dropped []}
            later-forms)))

(def ^:private linked
  (delay
    (let [ks (vec (body-forms (slurp kotoba-string-path)))
          cs (unalias 'ks (body-forms (slurp clojure-string-path)))]
      (drop-shared-definitions ks cs))))

(def ^:private lowered
  (delay
    (let [{:keys [forms]} @linked
          source (->> (concat (body-forms (slurp kotoba-string-path)) forms)
                      (map pr-str)
                      (str/join "\n"))]
      (kir/lower (sema/analyze (str source "\n" harness))))))

(defn- call [function s value]
  (long (kir/execute @lowered function [s value] {:fuel 100000000})))

(defn- kotoba-index-of [s value]
  (when (= 1 (call 'present? s value)) (call 'value s value)))

(defn- kotoba-last-index-of [s value]
  (when (= 1 (call 'last-present? s value)) (call 'last-value s value)))

;; The inputs. Present and absent both, ASCII and multi-byte and astral, plus
;; the two empty-needle cases where Clojure's two functions differ from each
;; other. "😀" is U+1F600: one code point, four UTF-8 bytes, TWO UTF-16 units,
;; which is the number clojure.string counts in.
(def ^:private cases
  [["hello" "ll"] ["hello" "h"] ["hello" "o"] ["hello" "z"] ["hello" ""]
   ["" ""] ["" "a"] ["abab" "ab"] ["abab" "ba"] ["abab" "z"]
   ["あいう" "い"] ["あいう" "a"] ["aあb" "b"] ["aあb" "c"] ["aあb" "あ"]
   ["a😀b" "b"] ["a😀b" "😀"] ["a😀b" "z"] ["a😀" ""] ["日本語" "語"]])

(deftest index-of-agrees-with-clojure-string-including-the-nil
  (doseq [[s value] cases]
    (testing (pr-str [s value])
      (is (= (str/index-of s value) (kotoba-index-of s value))
          "index-of must agree with clojure.string/index-of, nil included"))))

(deftest last-index-of-agrees-with-clojure-string-including-the-nil
  (doseq [[s value] cases]
    (testing (pr-str [s value])
      (is (= (str/last-index-of s value) (kotoba-last-index-of s value))
          "last-index-of must agree with clojure.string/last-index-of, nil included"))))

(deftest absence-is-nil-and-not-minus-one
  (testing "the whole reason these are option-valued"
    ;; `kotoba.string/utf16-index-of` answers -1 here, and a caller can thread
    ;; -1 into arithmetic and get a number back. The option cannot be read
    ;; without deciding which arm was taken.
    (is (nil? (str/index-of "hello" "z")))
    (is (nil? (kotoba-index-of "hello" "z")))
    (is (= 0 (call 'present? "hello" "z")) "the absent arm is taken")
    (is (nil? (kotoba-last-index-of "abab" "z")))))

(deftest the-cases-a-believed-table-gets-wrong
  (testing "an astral code point is TWO UTF-16 units, not one"
    ;; A byte-offset answer would be 5 and a code-point answer would be 2.
    ;; clojure.string counts UTF-16 units, so it is 3.
    (is (= 3 (str/index-of "a😀b" "b")))
    (is (= 3 (kotoba-index-of "a😀b" "b"))))
  (testing "the empty needle: index-of answers 0, last-index-of answers the length"
    ;; String.lastIndexOf("") is the UTF-16 LENGTH, which for "a😀" is 3, not
    ;; 2 -- the difference between counting characters and counting units.
    (is (= 0 (str/index-of "a😀" "")))
    (is (= 0 (kotoba-index-of "a😀" "")))
    (is (= 3 (str/last-index-of "a😀" "")))
    (is (= 3 (kotoba-last-index-of "a😀" "")))))

(deftest the-duplicate-private-is-identical-in-both-modules
  (testing "linking the two modules dropped only definitions that were the same"
    (let [{:keys [dropped]} @linked]
      (is (seq dropped) "code-point-width is defined in both modules")
      (is (= #{'code-point-width} (set (map (comp second first) dropped)))
          "only code-point-width may be shared; a new collision has to be looked at")
      (doseq [[kept later] dropped]
        (is (= kept later)
            (str "the two modules' copies of " (second kept)
                 " have diverged; this link would silently pick one"))))))
