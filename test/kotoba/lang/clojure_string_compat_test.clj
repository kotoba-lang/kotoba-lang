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
            [clojure.set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema]))

(def ^:private module (slurp "lang/compat/clojure/string.kotoba"))

;; Since 2026-09-08 this module carries `(:require [kotoba.string :as ks])`,
;; because `index-of` wraps `kotoba.string/utf16-index-of` rather than
;; repeating its scan. `amu compile --source-path <repo>/lang/compat` resolves
;; that for real; this repository has no linker to call, so the two modules
;; join one compilation unit here, exactly as kotoba_string_case_test does
;; with its tables. `index-of` itself is asserted against clojure.string in
;; clojure_string_option_index_test; what is asserted here is only that the
;; module still LOWERS and that the eight older names still answer.
(def ^:private required-module (slurp "lang/compat/kotoba/string.kotoba"))

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
   ;; `join` takes a [:list :string], and a list can only be CONSTRUCTED with
   ;; a statically known item count -- `typed-list-new` is a constructor over
   ;; literal items and kotoba-sema's `canonical-list-operations` is exactly
   ;; #{typed-list-new typed-list-nth}, with no conj, cons or append (measured
   ;; 2026-09-08). So the harness carries one entry per arity rather than
   ;; passing a list in from the host.
   (defn j0 [sep :string] :string (join sep (typed-list-new [:list :string])))
   (defn j1 [sep :string a :string] :string
     (join sep (typed-list-new [:list :string] a)))
   (defn j2 [sep :string a :string b :string] :string
     (join sep (typed-list-new [:list :string] a b)))
   (defn j3 [sep :string a :string b :string c :string] :string
     (join sep (typed-list-new [:list :string] a b c)))
   (defn main [] :i64 0)")

;; The module's own reader drops its `ns` form, rather than a regex over the
;; text: a unit admits one namespace form, and the harness has to join the same
;; unit because this repository has no project linker to call.
(defn- body-forms [source]
  (remove #(and (seq? %) (= 'ns (first %))) (sema/read-forms source)))

(defn- unalias
  "What the project linker does to a qualified call, and only that:
  `ks/utf16-index-of` -> `utf16-index-of`."
  [alias forms]
  (walk/postwalk
   (fn [node]
     (if (and (symbol? node) (= (str alias) (namespace node)))
       (symbol (name node))
       node))
   forms))

;; Both modules define the private `code-point-width` -- the same three
;; comparisons, because the UTF-8 width of a code point is a property of the
;; code point and neither module can import the other's privates. One unit
;; cannot define a name twice, so the later copy is dropped; that the two are
;; identical is asserted in clojure_string_option_index_test rather than
;; assumed here.
(defn- defined-name [form]
  (when (and (seq? form) (contains? #{'defn 'defn-} (first form)))
    (second form)))

(def ^:private lowered
  (delay
    (let [required (vec (body-forms required-module))
          taken (set (keep defined-name required))
          own (remove #(contains? taken (defined-name %))
                      (unalias 'ks (body-forms module)))
          body (->> (concat required own) (map pr-str) (str/join "\n"))]
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
                               (re-seq #"(?m)^\(defn\s+([^\s\[]+)" module)))))))
  (testing "and a name that LANDED is provided, not still listed absent"
    ;; index-of and last-index-of moved out of :absent on 2026-09-08. A name
    ;; can be in exactly one of the two, and the one it is in has to match the
    ;; source -- otherwise the authority records a reason for an absence that
    ;; is not an absence, which is the failure mode this whole file exists for.
    (let [contract (edn/read-string (slurp "lang/compat.edn"))
          cs (get-in contract [:modules :clojure.string])
          landed (:landed cs)
          public (set (map (comp symbol second)
                           (re-seq #"(?m)^\(defn\s+([^\s\[]+)" module)))]
      (is (seq landed))
      (is (every? #(string? (:measured (val %))) landed)
          "a landed name carries the date and the oracle it was measured against")
      (is (empty? (clojure.set/intersection (set (keys landed)) (set (keys (:absent cs)))))
          "a name cannot be both landed and absent")
      (is (every? public (keys landed))
          "every landed name has to actually be in the source"))))

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

;; ---------------------------------------------------------------------------
;; 2026-09-08: join, and the accessor that made it writable.
;;
;; `lang/compat.edn` recorded join as absent because it "consumes a sequence of
;; strings: the same [:list :string] with no accessor". The accessor landed on
;; both backends on 2026-09-08 (kotoba-script `typedListNth`, kotoba-wasm
;; `list-nth-i64` / `list-nth-ref`); the KIR reference interpreter this test
;; runs on already had it, which is why this test can be the oracle for the
;; SEMANTICS while the compiled artifacts are the evidence for the LOWERING.

(def ^:private join-cases
  ;; [separator items]. The three shapes Clojure's join is defined by -- empty,
  ;; single, many -- plus separators and items that are themselves empty or
  ;; multi-byte, because `string-concat` here is over UTF-8 bytes.
  [["," []] ["," ["a"]] ["," ["a" "b"]] ["," ["a" "b" "c"]]
   ["" ["a" "b"]] ["" []] ["" ["a"]]
   ["、" ["あ" "い" "う"]] ["," ["" ""]] ["," ["" "a" ""]]
   ["--" ["x" "y"]] ["," ["😀" "b"]] ["😀" ["a" "b"]]
   [" " ["日本語" "です"]] ["," ["aあb"]] ["\n" ["a" "b"]]])

(deftest join-matches-clojure-string
  (let [entry {0 'j0 1 'j1 2 'j2 3 'j3}
        results (for [[separator items] join-cases]
                  (let [got (kir/execute @lowered (entry (count items))
                                         (cons separator items))
                        want (str/join separator items)]
                    {:separator separator :items items :kotoba got :clojure want
                     :agrees? (= got want)}))
        bad (remove :agrees? results)]
    ;; A COUNT, not a boolean: `failures * 1000 + checks`, so a run that
    ;; checked nothing (0) is distinguishable from a clean run (16) and from a
    ;; single regression (1016). A boolean cannot tell those apart.
    (println (str "JOIN-CHECKED\t" (+ (* 1000 (count bad)) (count results))
                  "\t(failures*1000 + cases)"))
    (is (= (count join-cases) (count results)))
    (is (pos? (count results)) "an empty case set would make this vacuous")
    (is (empty? bad) (str "join disagrees with clojure.string on " (pr-str (vec bad))))))

(deftest the-list-accessor-reads-and-traps
  ;; The gap `lang/compat.edn` recorded was that a [:list :string] could be
  ;; built and counted and never read. This is the read, and the two ends of
  ;; the range, asserted rather than described.
  (let [source (str "(defn at [i :i64] :string "
                    "(nth (typed-list-new [:list :string] \"a\" \"bb\" \"ccc\") i))\n"
                    "(defn n [] :i64 "
                    "(vector-count (typed-list-new [:list :string] \"a\" \"bb\" \"ccc\")))\n"
                    "(defn main [] :i64 0)")
        program (kir/lower (sema/analyze source))]
    (is (= 3 (long (kir/execute program 'n []))))
    (is (= "a" (kir/execute program 'at [0])))
    (is (= "bb" (kir/execute program 'at [1])))
    (is (= "ccc" (kir/execute program 'at [2])))
    (doseq [out-of-range [3 -1]]
      (is (thrown? Throwable (kir/execute program 'at [out-of-range]))
          (str "index " out-of-range " must trap, as vector nth without a default does")))))

(deftest join-s-one-argument-arity-is-refused-not-answered
  ;; clojure.string/join also has a one-argument arity, (join coll), which
  ;; concatenates with no separator. There is no multi-arity here, so the
  ;; caller gets an arity refusal at check time -- never a different answer.
  ;; Asserted, because "it would be refused" is exactly the kind of claim that
  ;; quietly stops being true.
  (let [body (->> (sema/read-forms module)
                  (remove #(and (seq? %) (= 'ns (first %))))
                  (map pr-str)
                  (str/join "\n"))
        one-arg (str body "\n(defn main [] :string (join (typed-list-new [:list :string] \"a\")))")]
    (is (thrown? Throwable (kir/lower (sema/analyze one-arg))))))
