(ns gen-case-tables
  "Writes `lang/compat/kotoba/string/case_tables.kotoba` from the Unicode
  SIMPLE case mapping, read off the JVM and overlaid with the Unicode 16.0
  mappings a Unicode-15 JDK (Temurin 21 / CI) answers as identity.

    clojure -M scripts/gen_case_tables.clj

  WHY A GENERATOR AND NOT A HAND-WRITTEN TABLE. The whitespace predicate in
  `lang/compat/clojure/string.kotoba` is eleven ranges and was written out by
  hand. Case mapping is 1,488 lowercase and 1,505 uppercase code points, plus
  102 that `String.toUpperCase` expands to more than one character. A hand
  table of that size is a table of what someone believed Unicode says; this
  one is derived from Unicode (via Character on a current JDK, plus the
  published 16.0 rows a Unicode-15 Character lacks) and the generator is
  checked in so the derivation can be repeated when a JDK moves.

  WHY THE OUTPUT IS STRING LITERALS AND NOT ARITHMETIC. Kotoba has no
  code-point-to-string constructor -- `string-from-i64` writes a NUMBER in
  decimal, and every other string op takes a string apart rather than
  building a character. So a mapped character cannot be COMPUTED from its
  code point; it has to be present in the source. Each block below is one
  literal holding the mapped characters of a contiguous code-point range in
  order (unmapped code points inside the range carry themselves), indexed by
  `(* width (- point lo))`. A block is cut whenever the UTF-8 width of the
  targets changes or the next mapped code point is more than :max-gap away,
  so the literals stay proportional to what is actually mapped.

  WHY THE RANGE TEST IS `(if (>= p lo) (<= p hi) false)` AND NOT `(and ...)`.
  An unannotated `and` is i64, and a cond test position wants a bool. The
  nested form is the one measured to compile (2026-09-02, amu ca869d79e)."
  (:import (java.util Locale)))

(def ^:private out-path "lang/compat/kotoba/string/case_tables.kotoba")
(def ^:private max-gap 16)
(def ^:private code-points (range 0 0x110000))
(def ^:private surrogates (set (range 0xD800 0xE000)))

(defn- utf8-width [cp]
  (cond (< cp 0x80) 1 (< cp 0x800) 2 (< cp 0x10000) 3 :else 4))

(defn- one [cp] (String/valueOf (Character/toChars cp)))

;; Unicode 16.0 Simple_Uppercase_Mapping / Simple_Lowercase_Mapping for
;; code points that are identity in Unicode 15.0 (JDK 21 Character).
;; Same rows as test/kotoba/lang/kotoba_string_case_test.clj. Applied
;; AFTER the JVM read so regenerating on CI's Temurin 21 does not drop
;; the published mappings. Source: UnicodeData.txt 16.0.0 fields 12/13.
(def ^:private unicode-16-over-15
  (into {}
        (map (fn [[cp upper lower]] [cp {:upper upper :lower lower}])
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
              [0x10D85 0x10D65 0x10D85]])))

(defn- simple-lower ^long [cp]
  (if-let [m (unicode-16-over-15 cp)]
    (long (:lower m))
    (long (Character/toLowerCase (int cp)))))

(defn- simple-upper ^long [cp]
  (if-let [m (unicode-16-over-15 cp)]
    (long (:upper m))
    (long (Character/toUpperCase (int cp)))))

(defn- blocks
  "Contiguous ranges of code points whose targets under `f` all have the same
  UTF-8 width, cut at a gap wider than `max-gap`. Surrogates are never spanned:
  they are not scalar values and could not be written into a literal."
  [f]
  (let [changed (vec (remove surrogates (filter #(not= % (f %)) code-points)))]
    (loop [cs changed out []]
      (if (empty? cs)
        out
        (let [lo (first cs)
              width (utf8-width (f lo))
              extendable? (fn [prev c]
                            (and (<= (- c prev) max-gap)
                                 (not-any? #(or (surrogates %)
                                                (not= width (utf8-width (f %))))
                                           (range (inc prev) (inc c)))))
              hi (loop [prev lo remaining (next cs)]
                   (let [c (first remaining)]
                     (if (and c (extendable? prev c))
                       (recur c (next remaining))
                       prev)))]
          (recur (drop-while #(<= % hi) cs)
                 (conj out {:lo lo :hi hi :width width
                            :literal (apply str (map #(one (f %)) (range lo (inc hi))))})))))))

(defn- special-uppercase
  "The code points where `String.toUpperCase(Locale.ROOT)` is not the
  one-character `Character.toUpperCase`. SpecialCasing.txt's unconditional
  entries, read off the JVM rather than off the file."
  []
  (for [cp code-points
        :when (not (surrogates cp))
        :let [expanded (.toUpperCase (one cp) Locale/ROOT)]
        :when (not= expanded (one (Character/toUpperCase (int cp))))]
    {:point cp :string expanded}))

(defn- clause [{:keys [lo hi width literal]}]
  (str "      (if (>= point " lo ") (<= point " hi ") false)\n"
       "      (string-substring " (pr-str literal)
       " (* " width " (- point " lo ")) (* " width " (+ 1 (- point " lo "))))\n"))

(defn- slice-fn [name- doc blocks-]
  (str ";; " doc "\n"
       ";; " (count blocks-) " blocks, "
       (reduce + (map (comp count #(.getBytes ^String % "UTF-8") :literal) blocks-))
       " literal bytes.\n"
       "(defn " name- " [s :string index :i64 width :i64] :string\n"
       "  (let [point (string-code-point-at s index)]\n"
       "    (cond\n"
       (apply str (map clause blocks-))
       "      :else (string-substring s index (+ index width)))))\n"))

(defn -main [& _]
  (let [lower (blocks simple-lower)
        upper (blocks simple-upper)
        special (special-uppercase)
        source
        (str
";; GENERATED by scripts/gen_case_tables.clj. Do not edit.
;;
;; The Unicode SIMPLE case mappings, as Kotoba data. Read off
;; java.lang.Character and java.lang.String at generation time, then
;; overlaid with the Unicode 16.0 mappings a Unicode-15 JDK answers as
;; identity, and checked against that same Unicode oracle in
;; test/kotoba/lang/kotoba_string_case_test.clj.
;;
;; A mapped character cannot be COMPUTED here: Kotoba has no
;; code-point-to-string constructor (`string-from-i64` writes a number in
;; decimal). So each block is a literal holding the mapped characters of a
;; contiguous range in order -- code points inside the range that map to
;; themselves carry themselves -- indexed by `(* width (- point lo))`. Every
;; character in one block has the same UTF-8 width, which is what makes the
;; index arithmetic exact; a block is cut where the width changes.
;;
;; Each function answers for ONE code point and takes the string it came out
;; of, because a code point that is not in any table has to be returned as
;; itself and the only way to produce it is to slice the original bytes.
;;
;; Truth is a bool here, and the range test is spelled
;; `(if (>= point lo) (<= point hi) false)`: an unannotated `and` is i64 and
;; a cond test position wants a bool.
;;
;; Read off JDK " (System/getProperty "java.version") " on 2026-09-02, then
;; overlaid with Unicode 16.0 Simple_Uppercase_Mapping /
;; Simple_Lowercase_Mapping for the rows Unicode 15.0 / JDK 21 Character
;; lacks (U+019B, U+0264, U+1C89, U+1C8A and their partners; Garay). The
;; contract is the published Unicode SIMPLE mapping, not whatever Character
;; the host JDK happens to carry. The oracle sweeps against that mapping.

(ns kotoba.string.case-tables
  (:export [lower-slice upper-slice upper-root-slice]))

"
         (slice-fn "lower-slice"
                   "Unicode SIMPLE lowercase mapping, one code point to one code point (Character.toLowerCase(int), plus Unicode 16.0 rows a Unicode-15 JDK lacks)."
                   lower)
         "\n"
         (slice-fn "upper-slice"
                   "Unicode SIMPLE uppercase mapping, one code point to one code point (Character.toUpperCase(int), plus Unicode 16.0 rows a Unicode-15 JDK lacks)."
                   upper)
         "\n"
";; java.lang.String.toUpperCase(Locale.ROOT) differs from the simple mapping
;; at exactly " (count special) " code points, where it expands to more than one
;; character -- SpecialCasing.txt's unconditional entries. They are here as
;; whole literals rather than as an index, because the expansion is not one
;; character wide and there is nothing to index. Everything else falls
;; through to the simple table.
;;
;; There is no lower-root-slice. String.toLowerCase(Locale.ROOT) needs the
;; Final_Sigma condition, which is a property of the SURROUNDING string and
;; not of the code point; see lang/compat.edn :absent lower-case.
(defn upper-root-slice [s :string index :i64 width :i64] :string
  (let [point (string-code-point-at s index)]
    (cond\n"
         (apply str (for [{:keys [point string]} special]
                      (str "      (= point " point ") " (pr-str string) "\n")))
         "      :else (upper-slice s index width))))\n")]
    (spit out-path source)
    (println (str "WROTE\t" out-path
                  "\tlower-blocks=" (count lower)
                  "\tupper-blocks=" (count upper)
                  "\tspecial-upper=" (count special)
                  "\tbytes=" (count (.getBytes ^String source "UTF-8"))))))

(-main)
