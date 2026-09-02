(ns kotoba.lang.kotoba-string-case-test
  "`lang/compat/kotoba/string/case.kotoba` and the table it reads.

  THE CONTRACT is the Unicode SIMPLE case mapping (UnicodeData.txt fields
  12/13, one code point to one code point, no locale, no context).
  `Character.toLowerCase(int)` / `toUpperCase(int)` implement that mapping
  for the Unicode version the host JDK carries -- they are a convenient
  oracle, not the authority. CI and this repository's test host are
  Temurin 21 / Unicode 15.0. Unicode 16.0 published simple mappings that
  a Unicode-15 Character answers as identity (U+019B, U+0264, U+1C89,
  U+1C8A in the U+0000..U+3100 sweep, plus their new partners and the
  Garay case pairs). Kotoba follows the published mapping. The JVM is
  the thing that can be behind; that skew is asserted, not treated as
  the expected answer.

  The tables in `case_tables.kotoba` were GENERATED from a JDK that
  already carried Unicode 16, then checked here by running the Kotoba
  code -- the block arithmetic, the UTF-8 widths, the string slicing,
  the walk -- against the Unicode oracle (JVM Character, overlaid with
  the Unicode 16.0 mappings a Unicode-15 JDK lacks). A generator that
  emitted the right characters at the wrong offsets, or a block whose
  width was wrong, or a walk that lost a code point, all fail here.

  THE SWEEP is every code point below U+3100 plus every code point the
  JVM reports a case mapping for anywhere in Unicode plus the Unicode
  16.0 mappings the host JDK may not know, which is where the four-byte
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

;; Unicode 16.0 Simple_Uppercase_Mapping / Simple_Lowercase_Mapping for
;; code points that are identity in Unicode 15.0 (JDK 21 Character).
;; Source: https://www.unicode.org/Public/16.0.0/ucd/UnicodeData.txt
;; fields 12 and 13. Each row is [code-point simple-upper simple-lower].
;;
;; The four that sit inside the U+0000..U+3100 sweep -- U+019B, U+0264,
;; U+1C89, U+1C8A -- are why a Unicode-15 JVM oracle goes red. The rest
;; are their new partners and the Garay case pairs; a Unicode-15 JVM
;; never puts them in `jvm-mapped`, so they have to be named here or
;; they drop out of the sweep.
(def ^:private unicode-16-over-15-rows
  [[0x019B 0xA7DC 0x019B]
   [0x0264 0xA7CB 0x0264]
   [0x1C89 0x1C89 0x1C8A]
   [0x1C8A 0x1C89 0x1C8A]
   [0xA7CB 0xA7CB 0x0264]
   [0xA7CC 0xA7CC 0xA7CD]
   [0xA7CD 0xA7CC 0xA7CD]
   [0xA7DA 0xA7DA 0xA7DB]
   [0xA7DB 0xA7DA 0xA7DB]
   [0xA7DC 0xA7DC 0x019B]
   [0x10D50 0x10D50 0x10D70]
   [0x10D51 0x10D51 0x10D71]
   [0x10D52 0x10D52 0x10D72]
   [0x10D53 0x10D53 0x10D73]
   [0x10D54 0x10D54 0x10D74]
   [0x10D55 0x10D55 0x10D75]
   [0x10D56 0x10D56 0x10D76]
   [0x10D57 0x10D57 0x10D77]
   [0x10D58 0x10D58 0x10D78]
   [0x10D59 0x10D59 0x10D79]
   [0x10D5A 0x10D5A 0x10D7A]
   [0x10D5B 0x10D5B 0x10D7B]
   [0x10D5C 0x10D5C 0x10D7C]
   [0x10D5D 0x10D5D 0x10D7D]
   [0x10D5E 0x10D5E 0x10D7E]
   [0x10D5F 0x10D5F 0x10D7F]
   [0x10D60 0x10D60 0x10D80]
   [0x10D61 0x10D61 0x10D81]
   [0x10D62 0x10D62 0x10D82]
   [0x10D63 0x10D63 0x10D83]
   [0x10D64 0x10D64 0x10D84]
   [0x10D65 0x10D65 0x10D85]
   [0x10D70 0x10D50 0x10D70]
   [0x10D71 0x10D51 0x10D71]
   [0x10D72 0x10D52 0x10D72]
   [0x10D73 0x10D53 0x10D73]
   [0x10D74 0x10D54 0x10D74]
   [0x10D75 0x10D55 0x10D75]
   [0x10D76 0x10D56 0x10D76]
   [0x10D77 0x10D57 0x10D77]
   [0x10D78 0x10D58 0x10D78]
   [0x10D79 0x10D59 0x10D79]
   [0x10D7A 0x10D5A 0x10D7A]
   [0x10D7B 0x10D5B 0x10D7B]
   [0x10D7C 0x10D5C 0x10D7C]
   [0x10D7D 0x10D5D 0x10D7D]
   [0x10D7E 0x10D5E 0x10D7E]
   [0x10D7F 0x10D5F 0x10D7F]
   [0x10D80 0x10D60 0x10D80]
   [0x10D81 0x10D61 0x10D81]
   [0x10D82 0x10D62 0x10D82]
   [0x10D83 0x10D63 0x10D83]
   [0x10D84 0x10D64 0x10D84]
   [0x10D85 0x10D65 0x10D85]])

(def ^:private unicode-16-over-15
  (into {} (map (fn [[cp upper lower]] [cp {:upper upper :lower lower}])
                unicode-16-over-15-rows)))

(defn- simple-lower-cp [cp]
  (if-let [m (unicode-16-over-15 cp)]
    (:lower m)
    (int (Character/toLowerCase (int cp)))))

(defn- simple-upper-cp [cp]
  (if-let [m (unicode-16-over-15 cp)]
    (:upper m)
    (int (Character/toUpperCase (int cp)))))

(defn- simple-lower-str [cp] (one (simple-lower-cp cp)))
(defn- simple-upper-str [cp] (one (simple-upper-cp cp)))

(defn- upper-root-str
  "String.toUpperCase(Locale.ROOT), except where the host JDK's Unicode
  is behind the published simple mapping -- then the Unicode SIMPLE
  uppercase. None of the Unicode 16.0 additions have a SpecialCasing
  expansion, so the two coincide once the JDK catches up."
  [^String s]
  (apply str
         (map (fn [cp]
                (if (contains? unicode-16-over-15 cp)
                  (one (get-in unicode-16-over-15 [cp :upper]))
                  (.toUpperCase ^String (one cp) Locale/ROOT)))
              (code-points s))))

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
                 (sort (keys unicode-16-over-15))
                 [0x3100 0xD7FF 0xE000 0xFEFF 0x1F600 0x10FFFF])))

(deftest simple-case-mappings-are-unicode-simple
  ;; Unicode SIMPLE mappings, one code point to one code point, no locale
  ;; and no context. Character.toLowerCase(int) / toUpperCase(int) are
  ;; the oracle where the host JDK's Unicode version has the mapping;
  ;; unicode-16-over-15 is the oracle where it does not.
  (let [points (vec @sweep)
        bad (for [cp points
                  :let [s (one cp)
                        got-lower (call 'lower s)
                        want-lower (simple-lower-str cp)
                        got-upper (call 'upper s)
                        want-upper (simple-upper-str cp)]
                  :when (or (not= got-lower want-lower) (not= got-upper want-upper))]
              {:code-point (format "U+%04X" cp)
               :kotoba [got-lower got-upper] :unicode [want-lower want-upper]
               :jvm [(one (Character/toLowerCase (int cp)))
                     (one (Character/toUpperCase (int cp)))]})]
    (println (str "SCANNED\t" (count points) "\tcode points; the JVM maps "
                  (count @jvm-mapped) " of them; Unicode 16 adds "
                  (count unicode-16-over-15) " the host JDK may lack"))
    (is (pos? (count @jvm-mapped)) "an empty JVM mapping set would make this sweep vacuous")
    (is (> (count points) 12000) "the sweep must actually cover U+0000..U+3100 and the mapped tail")
    (is (empty? bad) (pr-str (vec (take 8 bad))))))

(deftest unicode-16-simple-mappings-are-not-the-host-jdk
  ;; The four sweep-visible points, plus their published partners. Kotoba
  ;; must match Unicode even when Character answers identity.
  (doseq [[cp upper lower] unicode-16-over-15-rows]
    (let [s (one cp)
          label (format "U+%04X" cp)]
      (is (= (one lower) (call 'lower s)) (str label " simple-lower"))
      (is (= (one upper) (call 'upper s)) (str label " simple-upper"))
      (is (= (one upper) (call 'upper-root s)) (str label " upper-root"))))
  (testing "a Unicode-15 JVM answers identity; Kotoba must not follow it off Unicode"
    (doseq [cp [0x019B 0x0264 0x1C89 0x1C8A]]
      (let [jvm-upper (one (Character/toUpperCase (int cp)))
            uni-upper (simple-upper-str cp)
            kotoba-upper (call 'upper (one cp))]
        (is (= uni-upper kotoba-upper) (format "U+%04X follows Unicode" cp))
        (when (not= jvm-upper uni-upper)
          (is (not= jvm-upper kotoba-upper)
              (format "U+%04X must not copy a lagging Character.toUpperCase" cp)))))))

(deftest upper-case-root-is-string-touppercase-root
  ;; The stronger claim: String.toUpperCase(Locale.ROOT), which is NOT the
  ;; simple mapping -- 102 code points expand to more than one character --
  ;; except where the host JDK's simple mapping is behind Unicode 16.0.
  (let [bad (for [cp @sweep
                  :let [s (one cp)
                        got (call 'upper-root s)
                        want (upper-root-str s)]
                  :when (not= got want)]
              {:code-point (format "U+%04X" cp) :kotoba got :unicode want
               :jvm (.toUpperCase ^String s Locale/ROOT)})]
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
            (fn [s] (apply str (map simple-lower-str (code-points s))))]
           ["simple-upper-case" 'upper
            (fn [s] (apply str (map simple-upper-str (code-points s))))]
           ["upper-case-root" 'upper-root upper-root-str]]]
    (testing label
      (let [bad (for [s strings
                      :let [got (call function s) want (oracle s)]
                      :when (not= got want)]
                  {:input s :kotoba got :unicode want})]
        (is (empty? bad) (pr-str (vec (take 5 bad))))))))

(deftest simple-capitalize-uppercases-the-first-code-point
  (let [oracle (fn [s]
                 (if (zero? (count s))
                   s
                   (let [first- (.codePointAt ^String s 0)
                         rest- (subs s (Character/charCount first-))]
                     (str (simple-upper-str first-)
                          (apply str (map simple-lower-str (code-points rest-)))))))
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
