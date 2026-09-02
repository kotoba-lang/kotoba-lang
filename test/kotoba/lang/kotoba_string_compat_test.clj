(ns kotoba.lang.kotoba-string-compat-test
  "`lang/compat/kotoba/string.kotoba`, checked against byte arithmetic.

  There is no Clojure oracle for `byte-index-of`: `clojure.string/index-of`
  answers a CHARACTER index, and the whole reason this function has a
  different name is that the two numbers are different. Calling it as the
  oracle would assert the defect.

  So the oracle here is built from the input's own UTF-8 encoding -- a naive
  search over `(.getBytes s \"UTF-8\")`, written independently of the Kotoba
  source, sharing no code with it and taking no position on code-point
  boundaries at all. Where the two agree, they agree for the reason the
  function claims: UTF-8 is self-synchronizing, so a byte-level occurrence of
  a valid needle can only begin on a code-point boundary, which is the only
  place the Kotoba scan looks.

  `clojure.string/index-of` is still called, in `the-name-is-the-point`, but
  as the CONTRAST: the cases recorded there are the ones where believing the
  Clojure name would slice a string in half."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema]))

(def ^:private module (slurp "lang/compat/kotoba/string.kotoba"))

;; A library: no `main`, so it is admitted through its exports, and the entry
;; has to reach them or the caller learns nothing.
(def ^:private harness
  "(defn bio [s :string needle :string] :i64 (byte-index-of s needle))
   (defn uio [s :string needle :string] :i64 (utf16-index-of s needle))
   (defn ulio [s :string needle :string] :i64 (utf16-last-index-of s needle))
   (defn main [] :i64 0)")

(def ^:private lowered
  (delay
    (let [body (->> (sema/read-forms module)
                    (remove #(and (seq? %) (= 'ns (first %))))
                    (map pr-str)
                    (str/join "\n"))]
      (kir/lower (sema/analyze (str body "\n" harness))))))

(defn- byte-index-of [s needle]
  (long (kir/execute @lowered 'bio [s needle])))

;; The oracle: a naive search over the UTF-8 bytes, with no notion of a code
;; point anywhere in it.
(defn- oracle [^String s ^String needle]
  (let [haystack (.getBytes s "UTF-8")
        pattern (.getBytes needle "UTF-8")
        n (alength haystack)
        m (alength pattern)]
    (if (zero? m)
      0
      (loop [i 0]
        (cond
          (> (+ i m) n) -1
          (every? #(= (aget haystack (+ i (long %))) (aget pattern (long %))) (range m)) i
          :else (recur (inc i)))))))

(def ^:private cases
  [["hello" "he"] ["hello" "lo"] ["hello" "ell"] ["hello" "l"] ["hello" "z"]
   ["hello" ""] ["" ""] ["" "a"] ["a" "ab"]
   ;; multi-byte: every one of these has a byte offset that is not the
   ;; character index clojure.string would answer.
   ["あいう" "あ"] ["あいう" "い"]
   ["あいう" "う"] ["あいう" "いう"]
   ["あいう" "a"]
   ["aあb" "あ"] ["aあb" "b"] ["aあb" "aあ"] ["aあb" "あb"]
   ["日本語" "本"] ["日本語" "語"]
   ["日本語" "日本"]
   ;; a needle that occurs twice: the first occurrence wins.
   ["ababa" "ba"] ["あかあか" "か"]
   ;; a 4-byte code point, so widths 1, 2, 3 and 4 are all exercised.
   ["a😀b" "b"] ["a😀b" "😀"]
   ["😀😀" "😀"]
   ;; a needle whose bytes cannot occur at all, and an empty one, against a
   ;; haystack that is a single multi-byte character.
   ["あ" "a"] ["あ" ""]])

(deftest matches-byte-arithmetic
  (doseq [[s needle] cases]
    (testing (pr-str [s needle])
      (is (= (oracle s needle) (byte-index-of s needle))
          (str "byte-index-of " (pr-str s) " " (pr-str needle)
               " answered " (byte-index-of s needle)
               ", UTF-8 byte search says " (oracle s needle))))))

(deftest the-answer-is-usable-where-it-says-it-is
  (testing "the offset slices the needle back out with string-substring's arithmetic"
    (doseq [[s needle] cases
            :let [index (byte-index-of s needle)]
            :when (not (neg? index))]
      (let [bytes (.getBytes ^String s "UTF-8")
            width (alength (.getBytes ^String needle "UTF-8"))]
        (is (= needle (String. bytes (int index) (int width) "UTF-8"))
            (str "slicing " (pr-str s) " at byte " index
                 " for " width " bytes did not give back " (pr-str needle)))))))

(deftest the-name-is-the-point
  (testing "the byte offset and clojure.string's character index really differ"
    ;; If these ever became equal the function would still be correct, but the
    ;; argument for its name would have evaporated. They are asserted rather
    ;; than assumed.
    (doseq [[s needle] [["aあb" "b"] ["日本語" "語"]
                        ["あいう" "いう"]
                        ["a😀b" "b"]]]
      (is (not= (long (str/index-of s needle)) (byte-index-of s needle))
          (str (pr-str [s needle]) " no longer diverges: clojure.string says "
               (str/index-of s needle) ", bytes say " (byte-index-of s needle)))))
  (testing "and on pure ASCII they agree, which is what makes the divergence quiet"
    (doseq [[s needle] [["hello" "lo"] ["ababa" "ba"] ["hello" "z"]]]
      (is (= (long (or (str/index-of s needle) -1)) (byte-index-of s needle))))))

(deftest the-contract-and-the-source-agree
  (let [contract (edn/read-string (slurp "lang/compat.edn"))
        module-entry (get-in contract [:modules :kotoba.string])]
    (testing "lang/compat.edn carries the module"
      (is (= "lang/compat/kotoba/string.kotoba" (:path module-entry)))
      (is (= #{'byte-index-of 'utf16-index-of 'utf16-last-index-of} (:provides module-entry))))
    (testing "and the source's public names are exactly that"
      (let [public (set (map (comp symbol second)
                             (re-seq #"(?m)^\(defn\s+([^\s\[]+)" module)))]
        (is (= (:provides module-entry) public))))
    (testing "clojure.string/index-of stays absent, and points here -- at both halves"
      (let [absent (get-in contract [:modules :clojure.string :absent 'index-of])]
        (is (string? (:reason absent)))
        (is (= #{'kotoba.string/byte-index-of 'kotoba.string/utf16-index-of} (:instead absent)))
        (is (str/includes? (:reason absent) "option")
            "the reason on record is the nil half, not the index half, which is answered")))
    (testing "and last-index-of points at its utf16 counterpart"
      (is (= 'kotoba.string/utf16-last-index-of
             (get-in contract [:modules :clojure.string :absent 'last-index-of :instead]))))
    (testing "nothing named index-of is exported from the clojure.string module"
      (let [clojure-module (slurp "lang/compat/clojure/string.kotoba")]
        (is (empty? (filter #{"index-of"}
                            (map second (re-seq #"(?m)^\(defn\s+([^\s\[]+)"
                                                clojure-module)))))))))

;; ---------------------------------------------------------------------------
;; 2026-09-02: utf16-index-of / utf16-last-index-of. Here the oracle IS
;; clojure.string, because the number is the same number -- a UTF-16 code-unit
;; index -- and only nil is spelled -1.

(defn- utf16-index-of [s needle]
  (long (kir/execute @lowered 'uio [s needle])))

(defn- utf16-last-index-of [s needle]
  (long (kir/execute @lowered 'ulio [s needle])))

(def ^:private utf16-cases
  (concat cases
          [["😀a😀a" "a"] ["😀a😀a" "😀a"] ["a😀b😀b" "b"] ["😀" ""] ["😀" "😀"]
           ["xx" "xxx"] ["🇯🇵x" "x"] ["aあaあ" "あ"] ["ab" "ab"]]))

(deftest utf16-index-of-matches-clojure-string
  (let [bad (for [[s needle] utf16-cases
                  :let [want (long (or (str/index-of s needle) -1))
                        got (utf16-index-of s needle)]
                  :when (not= want got)]
              {:input [s needle] :clojure want :kotoba got})]
    (is (empty? bad) (pr-str (vec bad)))))

(deftest utf16-last-index-of-matches-clojure-string
  (let [bad (for [[s needle] utf16-cases
                  :let [want (long (or (str/last-index-of s needle) -1))
                        got (utf16-last-index-of s needle)]
                  :when (not= want got)]
              {:input [s needle] :clojure want :kotoba got})]
    (is (empty? bad) (pr-str (vec bad))))
  (testing "the empty needle answers the UTF-16 length, as String.lastIndexOf does"
    (is (= 3 (str/last-index-of "a😀" "")))
    (is (= 3 (utf16-last-index-of "a😀" "")))))

(deftest the-two-indexes-really-differ
  ;; The whole reason there are two names. Astral code points make the UTF-16
  ;; index differ from the code-point count as well, which is why the emoji
  ;; cases are here and not only the kana ones.
  (doseq [[s needle] [["aあb" "b"] ["a😀b" "b"] ["日本語" "語"] ["😀a😀a" "a"]]]
    (is (not= (utf16-index-of s needle) (byte-index-of s needle))
        (pr-str [s needle])))
  (is (= 3 (utf16-index-of "a😀b" "b")) "one unit for a, two for the pair")
  (is (= 5 (byte-index-of "a😀b" "b")) "one byte for a, four for the emoji"))
