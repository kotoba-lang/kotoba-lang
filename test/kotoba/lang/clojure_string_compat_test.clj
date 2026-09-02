(ns kotoba.lang.clojure-string-compat-test
  "`lang/compat/clojure/string.kotoba`, checked against clojure.string itself.

  The oracle here is the real function. Asserting a table of expected values
  would only record what someone believed clojure.string does; calling it says
  what it does, and the two hazards below are precisely the cases where a
  believed table would have been written wrong.

  This is also the first test in this repository that RUNS Kotoba source. The
  authority owns `lang/stdlib/`, `lang/conformance/` and now `lang/compat/`,
  and until this file every one of them was checked by regex and sha256 -- that
  a name is present, not that it computes the right answer."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema]))

(def ^:private module (slurp "lang/compat/clojure/string.kotoba"))

;; The module is a library: no `main`, so it is admitted through its exports.
;; Calling it needs an entry, and the entry has to reach every export or the
;; frontend prunes nothing but the caller learns nothing either.
(def ^:private harness
  "(defn sw [s :string p :string] :i64 (if (starts-with? s p) 1 0))
   (defn ew [s :string p :string] :i64 (if (ends-with? s p) 1 0))
   (defn inc? [s :string p :string] :i64 (if (includes? s p) 1 0))
   (defn bl [s :string] :i64 (if (blank? s) 1 0))
   (defn tr [s :string] :string (trim s))
   (defn trl [s :string] :string (triml s))
   (defn trr [s :string] :string (trimr s))
   (defn rv [s :string] :string (reverse s))
   (defn ws [point :i64] :i64 (if (whitespace? point) 1 0))
   (defn main [] :i64 0)")

;; The module's own reader drops its `ns` form, rather than a regex over the
;; text: a unit admits one namespace form, and the harness has to join the same
;; unit because this repository has no project linker to call.
(def ^:private lowered
  (delay
    (let [body (->> (sema/read-forms module)
                    (remove #(and (seq? %) (= 'ns (first %))))
                    (map pr-str)
                    (str/join "\n"))]
      (kir/lower (sema/analyze (str body "\n" harness))))))

(defn- call [function s argument]
  (= 1 (long (kir/execute @lowered function [s argument]))))

(def ^:private cases
  [["hello" "he"] ["hello" "lo"] ["hello" "ell"] ["hello" ""] ["" ""] ["" "a"]
   ["あいう" "あ"] ["あいう" "う"] ["あいう" "いう"] ["あいう" "a"] ["あ" "a"]
   ["aあb" "aあ"] ["aあb" "あb"] ["aあb" "b"] ["ab" "abc"] ["日本語" "本"]])

(defn- disagreements [function oracle]
  (remove (fn [[s argument]] (= (call function s argument) (oracle s argument))) cases))

(deftest matches-clojure-string
  (doseq [[label function oracle] [["starts-with?" 'sw str/starts-with?]
                                   ["ends-with?" 'ew str/ends-with?]
                                   ["includes?" 'inc? str/includes?]]]
    (testing label
      (is (empty? (disagreements function oracle))
          (str label " disagrees with clojure.string on "
               (pr-str (vec (disagreements function oracle))))))))

(deftest the-two-cases-that-a-naive-implementation-traps-on
  (testing "a one-byte prefix against a three-byte character"
    ;; `(string-substring "あ" 0 1)` REFUSES -- it splits a code point -- so the
    ;; substring implementation of starts-with? raises where the answer is
    ;; false. Comparing by code point is why this returns instead of trapping.
    (is (false? (call 'sw "あ" "a")))
    (is (false? (str/starts-with? "あ" "a"))))
  (testing "an empty needle"
    ;; `string-contains?` refuses an empty needle (`empty-string-search-needle`)
    ;; where clojure.string answers true, which is the whole reason includes?
    ;; is not an alias.
    (is (true? (call 'inc? "hello" "")))
    (is (true? (str/includes? "hello" "")))))

(deftest the-module-provides-exactly-what-it-claims
  (testing "the contract and the source agree on the public names"
    (let [contract (edn/read-string (slurp "lang/compat.edn"))
          declared (get-in contract [:modules :clojure.string :provides])
          public (set (map (comp symbol second)
                           (re-seq #"(?m)^\(defn\s+([^\s\[]+)" module)))]
      (is (= (set declared) public))))
  (testing "and the absent ones are absent, with a reason each"
    (let [contract (edn/read-string (slurp "lang/compat.edn"))
          absent (get-in contract [:modules :clojure.string :absent])]
      (is (seq absent))
      (is (every? #(string? (:reason (val %))) absent))
      (is (empty? (filter (set (keys absent))
                          (map (comp symbol second)
                               (re-seq #"(?m)^\(defn\s+([^\s\[]+)" module))))))))

;; ---------------------------------------------------------------------------
;; 2026-09-02: blank? / trim / triml / trimr / reverse.

(defn- call1 [function s]
  (kir/execute @lowered function [s]))

;; Every kind of whitespace Java has an opinion about, on both sides of
;; something, plus the ones it says are NOT whitespace: U+00A0, U+2007 and
;; U+202F (non-breaking) and U+0085 (NEL). U+3000 is the one an ASCII-only
;; answer gets wrong.
(def ^:private whitespace-cases
  ["" " " "\t" "\n" "a" " a " "\t\ta\n" "\u3000a\u3000" "\u00a0a\u00a0" "\u2007a"
   "\u202fa\u202f" "\u0085a\u0085" "\u2028a\u2029" "\u1680a\u205f" " あ " "あ\u3000い"
   "😀 " " 😀" "\u3000\u3000" "\u00a0" "\u0085" "  " "\u000b\u000c" "\u001c\u001f a"
   "x\u2007" "\u2000\u2001\u2006 a \u2008\u200a" "日本語　" "\r\n"])

(deftest blank-and-the-trims-match-clojure-string
  (doseq [[label function oracle] [["blank?" 'bl #(if (str/blank? %) 1 0)]
                                   ["trim" 'tr str/trim]
                                   ["triml" 'trl str/triml]
                                   ["trimr" 'trr str/trimr]]]
    (testing label
      (let [bad (for [s whitespace-cases
                      :let [got (call1 function s) want (oracle s)]
                      :when (not= got want)]
                  {:input s :kotoba got :clojure want})]
        (is (empty? bad) (str label " disagrees with clojure.string on " (pr-str (vec bad))))))))

(def ^:private reverse-cases
  ["" "a" "ab" "あいう" "aあb" "a😀b" "😀😀" "日本語" "😀" "a\u0301" "🇯🇵" "\u2028x" "hello" "aあ😀"])

(deftest reverse-matches-clojure-string
  ;; StringBuilder.reverse keeps a surrogate pair together, which is why this
  ;; can be exact on astral input at all; the emoji cases are the assertion.
  (let [bad (for [s reverse-cases
                  :let [got (call1 'rv s) want (str/reverse s)]
                  :when (not= got want)]
              {:input s :kotoba got :clojure want})]
    (is (empty? bad) (pr-str (vec bad))))
  (is (= "😀あa" (call1 'rv "aあ😀")))
  (is (= "😀あa" (str/reverse "aあ😀"))
      "if the JVM ever reversed by UTF-16 unit this would be a corrupt pair, and reverse could not be exact"))

;; The predicate underneath all four, held to java.lang.Character/isWhitespace
;; -- the int overload, i.e. over code points -- for every code point below
;; U+3100 (which contains every range in the table and its neighbours) and,
;; separately, for EVERY code point the JVM says is whitespace, found by
;; asking the JVM, not by reading the table back.
(deftest the-whitespace-predicate-is-the-jvm-s
  (let [jvm-whitespace (set (filter #(Character/isWhitespace (int %)) (range 0 0x110000)))
        sweep (concat (range 0 0x3100) jvm-whitespace
                      [0x3100 0xD7FF 0xE000 0xFEFF 0x1F600 0x10FFFF])
        bad (for [point sweep
                  :let [want (Character/isWhitespace (int point))
                        got (= 1 (long (kir/execute @lowered 'ws [point])))]
                  :when (not= want got)]
              {:code-point (format "U+%04X" point) :jvm want :kotoba got})]
    (println (str "SCANNED\t" (count sweep) "\tcode points; JVM whitespace set has " (count jvm-whitespace)))
    (is (pos? (count jvm-whitespace)) "an empty JVM set would make this sweep vacuous")
    (is (empty? bad) (pr-str (vec bad)))
    (testing "the four Java excludes, and NEL, are not whitespace on either side"
      (doseq [point [0x00A0 0x2007 0x202F 0x0085]]
        (is (false? (Character/isWhitespace (int point))))
        (is (= 0 (long (kir/execute @lowered 'ws [point])))
            (format "U+%04X must not be whitespace" point))))
    (testing "and U+3000 is"
      (is (true? (Character/isWhitespace (int 0x3000))))
      (is (= 1 (long (kir/execute @lowered 'ws [0x3000])))))))
