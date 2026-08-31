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
