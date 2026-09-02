(ns kotoba.lang.kotoba-string-case-test
  "`lang/compat/kotoba/string/case.kotoba` and the table it reads, checked
  against java.lang.Character and java.lang.String themselves.

  The tables in `case_tables.kotoba` were GENERATED from those same JVM
  functions by `scripts/gen_case_tables.clj`, which would make this test
  circular if it compared the table to its own source. It does not: it runs
  the Kotoba code -- the block arithmetic, the UTF-8 widths, the string
  slicing, the walk -- and compares the ANSWER to a fresh call of the JVM
  function. A generator that emitted the right characters at the wrong
  offsets, or a block whose width was wrong, or a walk that lost a code
  point, all fail here.

  THE SWEEP is every code point below U+3100 plus every code point the JVM
  reports a case mapping for anywhere in Unicode, which is where the four-byte
  blocks and the SpecialCasing expansions live. The counts are printed.

  WHY THESE NAMES AND NOT clojure.string's. `clojure.string/lower-case` calls
  `String.toLowerCase()` with no locale, so its answer depends on
  `Locale.getDefault()`; `locale-is-not-a-detail` asserts that divergence
  against the JVM rather than describing it. The Clojure names stay absent in
  `lang/compat.edn` for that reason and two more, each measured below."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema])
  (:import (java.util Locale)))

(def ^:private tables-path "lang/compat/kotoba/string/case_tables.kotoba")
(def ^:private case-path "lang/compat/kotoba/string/case.kotoba")

(def ^:private harness
  "(defn lower [s :string] :string (simple-lower-case s))
   (defn upper [s :string] :string (simple-upper-case s))
   (defn upper-root [s :string] :string (upper-case-root s))
   (defn cap [s :string] :string (simple-capitalize s))
   (defn main [] :i64 0)")

(defn- body-forms [source]
  (remove #(and (seq? %) (= 'ns (first %))) (sema/read-forms source)))

(defn- unalias
  "What the project linker does to a qualified call, and only that:
  `tables/lower-slice` -> `lower-slice`. This repository has no linker to
  call, so the two modules join one compilation unit."
  [alias forms]
  (walk/postwalk
   (fn [node]
     (if (and (symbol? node) (= (str alias) (namespace node)))
       (symbol (name node))
       node))
   forms))

(def ^:private lowered
  (delay
    (let [source (->> (concat (body-forms (slurp tables-path))
                              (unalias 'tables (body-forms (slurp case-path))))
                      (map pr-str)
                      (str/join "\n"))]
      (kir/lower (sema/analyze (str source "\n" harness))))))

(defn- call [function s]
  (kir/execute @lowered function [s] {:fuel 100000000}))

(defn- one
  "The one-code-point string, which is what a table entry is about."
  [code-point]
  (String/valueOf (Character/toChars code-point)))

(defn- code-points
  "A string's code points as a seq. `.codePoints` is an IntStream, which is
  not seqable."
  [^String s]
  (vec (.toArray (.codePoints s))))

;; Every code point the JVM maps at all -- found by asking the JVM, not by
;; reading the table back, so a mapping the generator MISSED is in the sweep.
(def ^:private jvm-mapped
  (delay
    (set (filter (fn [cp]
                   (or (not= cp (Character/toLowerCase (int cp)))
                       (not= cp (Character/toUpperCase (int cp)))
                       (not= (.toUpperCase (one cp) Locale/ROOT)
                             (one (Character/toUpperCase (int cp))))))
                 (remove #(<= 0xD800 % 0xDFFF) (range 0 0x110000))))))

(def ^:private sweep
  (delay (concat (remove #(<= 0xD800 % 0xDFFF) (range 0 0x3100))
                 (sort @jvm-mapped)
                 [0x3100 0xD7FF 0xE000 0xFEFF 0x1F600 0x10FFFF])))

(deftest simple-case-mappings-are-the-jvm-s
  ;; Character.toLowerCase(int) / toUpperCase(int) -- the Unicode SIMPLE
  ;; mappings, one code point to one code point, no locale and no context.
  (let [points (vec @sweep)
        bad (for [cp points
                  :let [s (one cp)
                        got-lower (call 'lower s)
                        want-lower (one (Character/toLowerCase (int cp)))
                        got-upper (call 'upper s)
                        want-upper (one (Character/toUpperCase (int cp)))]
                  :when (or (not= got-lower want-lower) (not= got-upper want-upper))]
              {:code-point (format "U+%04X" cp)
               :kotoba [got-lower got-upper] :jvm [want-lower want-upper]})]
    (println (str "SCANNED\t" (count points) "\tcode points; the JVM maps "
                  (count @jvm-mapped) " of them"))
    (is (pos? (count @jvm-mapped)) "an empty JVM mapping set would make this sweep vacuous")
    (is (> (count points) 12000) "the sweep must actually cover U+0000..U+3100 and the mapped tail")
    (is (empty? bad) (pr-str (vec (take 8 bad))))))

(deftest upper-case-root-is-string-touppercase-root
  ;; The stronger claim: String.toUpperCase(Locale.ROOT), which is NOT the
  ;; simple mapping -- 102 code points expand to more than one character.
  (let [bad (for [cp @sweep
                  :let [s (one cp)
                        got (call 'upper-root s)
                        want (.toUpperCase s Locale/ROOT)]
                  :when (not= got want)]
              {:code-point (format "U+%04X" cp) :kotoba got :jvm want})]
    (is (empty? bad) (pr-str (vec (take 8 bad)))))
  (testing "the expansions are really there and really are not the simple mapping"
    (is (= "SS" (call 'upper-root "ß")))
    (is (= "SS" (.toUpperCase "ß" Locale/ROOT)))
    (is (= "ß" (call 'upper "ß")) "the simple mapping leaves it alone")
    (is (= "ß" (one (Character/toUpperCase (int 0xDF)))))
    (is (= "FI" (call 'upper-root "ﬁ")))
    (is (= "ʼN" (call 'upper-root "ŉ")))))

;; A claim about a STRING is not a claim about a character, so the whole-string
;; behaviour is swept too. If String.toUpperCase(ROOT) had a context rule the
;; per-code-point table would be wrong here and right above.
(def ^:private strings
  (into ["" "a" "A" "hello" "HELLO" "Hello World" "ß" "ßß" "aßb" "ﬁﬂ" "ŉ"
         "ΑΣ" "ΑΣΒ" "Σ" "σ" "ς" "ΟΔΟΣ" "οδος" "İstanbul" "i̇" "I" "ı"
         "ÅNGSTRÖM" "ångström" "ЖУРНАЛ" "журнал" "ǅungla" "Ǆ" "ǆ"
         "😀A" "a😀" "𐐀𐐨" "ＡＢＣ" "ａｂｃ" "日本語" "  a  " "á" "🇯🇵"
         "ﬀﬁﬂﬃﬄﬅﬆ" "ΐΰ" "ẖẗẘẙẚ" "ﬗ" "ᾈᾉᾊ" "ᾀᾁ"]
        ;; and a fixed-seed spray of code points the JVM maps, so the strings
        ;; are not only the ones someone thought of
        (let [rng (java.util.Random. 20260902)
              mapped (vec (sort @jvm-mapped))]
          (for [_ (range 200)]
            (apply str (for [_ (range (inc (.nextInt rng 6)))]
                         (one (nth mapped (.nextInt rng (count mapped))))))))))

(deftest whole-strings-agree-too
  (doseq [[label function oracle]
          [["simple-lower-case" 'lower
            (fn [s] (apply str (map #(one (Character/toLowerCase (int %))) (code-points s))))]
           ["simple-upper-case" 'upper
            (fn [s] (apply str (map #(one (Character/toUpperCase (int %))) (code-points s))))]
           ["upper-case-root" 'upper-root (fn [s] (.toUpperCase ^String s Locale/ROOT))]]]
    (testing label
      (let [bad (for [s strings
                      :let [got (call function s) want (oracle s)]
                      :when (not= got want)]
                  {:input s :kotoba got :jvm want})]
        (is (empty? bad) (pr-str (vec (take 5 bad))))))))

(deftest simple-capitalize-uppercases-the-first-code-point
  (let [oracle (fn [s]
                 (if (zero? (count s))
                   s
                   (let [first- (.codePointAt ^String s 0)
                         rest- (subs s (Character/charCount first-))]
                     (str (one (Character/toUpperCase (int first-)))
                          (apply str (map #(one (Character/toLowerCase (int %)))
                                          (code-points rest-)))))))
        bad (for [s strings
                  :let [got (call 'cap s) want (oracle s)]
                  :when (not= got want)]
              {:input s :kotoba got :jvm want})]
    (is (empty? bad) (pr-str (vec (take 5 bad)))))
  (testing "the first CODE POINT, not the first UTF-16 unit"
    (is (= "𐐀𐐨" (call 'cap "𐐨𐐨"))
        "an astral first character is uppercased whole")))

;; ---------------------------------------------------------------------------
;; The three reasons the Clojure names stay absent. Each is asserted against
;; the JVM, because a reason nobody measured is a reason that can quietly
;; stop being true.

(deftest locale-is-not-a-detail
  ;; clojure.string/lower-case is (.. s toString toLowerCase) -- no locale
  ;; argument, so Locale.getDefault(). A Kotoba guest has no locale to read.
  (let [turkish (Locale/forLanguageTag "tr")]
    (is (= "i" (.toLowerCase "I" Locale/ROOT)))
    (is (= "ı" (.toLowerCase "I" turkish))
        "the same call, the same input, a different ambient locale, a different answer")
    (is (not= (.toLowerCase "I" Locale/ROOT) (.toLowerCase "I" turkish)))
    (is (= "i" (call 'lower "I"))
        "this module answers the locale-independent mapping, which is why it is not called lower-case")
    (is (= "İ" (.toUpperCase "i" turkish)))
    (is (= "I" (call 'upper "i")))))

(deftest final-sigma-is-why-there-is-no-lower-case-root
  ;; String.toLowerCase(ROOT) lowercases a word-final Σ to ς. That is a
  ;; property of the surrounding string, so no per-code-point table answers it.
  (is (= "ας" (.toLowerCase "ΑΣ" Locale/ROOT)))
  (is (= "ασβ" (.toLowerCase "ΑΣΒ" Locale/ROOT)))
  (is (= "σ" (.toLowerCase "Σ" Locale/ROOT)) "alone, it is not final")
  (is (= "ασ" (call 'lower "ΑΣ"))
      "the simple mapping has no notion of final, and answers σ")
  (is (not= (call 'lower "ΑΣ") (.toLowerCase "ΑΣ" Locale/ROOT))
      "the divergence is asserted, not described")
  (testing "and U+0130, which String lowercases to two characters"
    (is (= 2 (count (.toLowerCase "İ" Locale/ROOT))))
    (is (= "i" (call 'lower "İ")))
    (is (= "i" (one (Character/toLowerCase (int 0x130))))
        "the SIMPLE mapping is one character; the String-level one is not")))

(deftest clojure-capitalize-cuts-at-a-utf16-unit
  ;; (subs s 0 1) is one UTF-16 code unit. For an astral first character that
  ;; is half a surrogate pair -- and the halves concatenate back, so the
  ;; defect does not show on every input. What it costs is measured here
  ;; rather than asserted from the source.
  (is (= "Hello" (str/capitalize "hELLO")))
  (let [astral "𐐨bc"]
    (is (= 4 (count astral))
        "four UTF-16 units for three characters: the first one is a surrogate pair")
    (is (= 1 (count (subs astral 0 1)))
        "(subs s 0 1) is ONE unit -- the high surrogate alone, not the character")
    (is (Character/isHighSurrogate (.charAt (subs astral 0 1) 0)))
    (is (= "𐐀bc" (call 'cap astral))
        "the code-point-wise answer uppercases the astral letter")
    (is (= "𐐨bc" (str/capitalize astral))
        "clojure.string/capitalize leaves it alone: (subs s 0 1) is half a pair, and .toUpperCase of a lone surrogate is itself")
    (is (not= (str/capitalize astral) (call 'cap astral)))))

;; ---------------------------------------------------------------------------

(deftest the-module-provides-exactly-what-it-claims
  (let [contract (edn/read-string (slurp "lang/compat.edn"))
        declared (get-in contract [:modules :kotoba.string.case :provides])
        source (slurp case-path)
        public (set (map (comp symbol second)
                         (re-seq #"(?m)^\(defn\s+([^\s\[]+)" source)))]
    (is (= (set declared) public)
        (str "extra=" (pr-str (set/difference public (set declared)))
             " missing=" (pr-str (set/difference (set declared) public))))
    (testing "and the three Clojure names stay absent, each with a reason"
      (let [absent (get-in contract [:modules :clojure.string :absent])]
        (doseq [name- '[lower-case upper-case capitalize]]
          (is (string? (:reason (get absent name-))) (str name- " needs a reason"))
          (is (some? (:instead (get absent name-)))
              (str name- " must point at what answers instead")))))
    (testing "and the generated table says it is generated, by a script that exists"
      (is (str/starts-with? (slurp tables-path) ";; GENERATED by scripts/gen_case_tables.clj"))
      (is (.exists (clojure.java.io/file "scripts/gen_case_tables.clj"))))))
