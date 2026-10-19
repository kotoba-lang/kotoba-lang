(ns sheets.csv
  "CSV in and out of a tab.

  A tab rather than a workbook, because CSV has no idea a workbook has more
  than one and pretending otherwise means inventing a convention — a sheet
  name in the first line, a file per tab in a zip — that nothing on the other
  end would agree with. One tab is what the format can carry, so one tab is
  what these take and return.

  ## RFC 4180, and the parts of it that matter

  A field is quoted when it contains a comma, a quote, a carriage return or a
  newline; a quote inside a quoted field is doubled. Reading accepts more
  than writing produces: bare CRLF or LF line endings, and an unterminated
  final quote is taken as ending at the end of input rather than throwing,
  because a truncated file should import as far as it goes and say what it
  got.

  ## What a cell becomes

  Text. Every value read from CSV is a string, including one that looks like
  a number, because CSV does not say and guessing is how a part number
  becomes a float. A caller that knows better converts.

  Writing is the mirror with one exception: a formula cell writes back as
  `=EXPR`, which is what a spreadsheet would show and what this library's own
  editor shows. There is no evaluation here — `sheets` has no evaluator — so
  a formula's *value* is not something this could write even if the
  convention said to."
  (:require [clojure.string :as str]
            [sheets.model :as model]))

(def ^:private needs-quoting #"[,\"\r\n]")

(defn- write-field [value]
  (let [text (cond (nil? value) ""
                   (string? value) value
                   :else (str value))]
    (if (re-find needs-quoting text)
      (str "\"" (str/replace text "\"" "\"\"") "\"")
      text)))

(defn- cell-text
  "One cell as CSV text. A formula keeps its leading `=`."
  [cell]
  (cond
    (nil? cell) ""
    (contains? cell :sheets/formula) (str "=" (:sheets/formula cell))
    :else (:sheets/value cell)))

(defn tab-bounds
  "`[rows cols]` — the furthest row and column the tab has a cell in.

  `[0 0]` for an empty tab, which is what makes writing one produce nothing
  rather than a line of commas."
  [tab]
  (reduce (fn [[rows cols] [row col]]
            [(max rows (long row)) (max cols (long col))])
          [0 0]
          (keys (:sheets/cells tab))))

(defn tab->csv
  "A tab as CSV text, `\\r\\n` separated as the RFC says.

  Rectangular: every row is padded to the widest column the tab uses, because
  a reader that meets a short row has to guess whether the cells are missing
  or the file is."
  [tab]
  (let [[rows cols] (tab-bounds tab)]
    (if (zero? rows)
      ""
      (str/join "\r\n"
                (for [row (range 1 (inc rows))]
                  (str/join "," (for [col (range 1 (inc cols))]
                                  (write-field (cell-text (model/get-cell tab row col))))))))))

(defn parse-csv
  "CSV text as a vector of vectors of strings.

  Separate from `csv->tab` because it is the half worth testing on its own,
  and because a caller that wants the grid rather than a tab should not have
  to build one to get it."
  [text]
  ;; A vector of characters rather than a StringBuilder: this namespace is
  ;; `.cljc` and the rest of `sheets` runs on both hosts, so it does not get
  ;; to reach for a Java class.
  (let [text (str text)
        n (count text)
        at (fn [i] (subs text i (inc i)))
        done (fn [chars] (apply str chars))]
    (loop [i 0, field [], row [], rows [], quoted? false, seen-any? false]
      (if (>= i n)
        (let [row (conj row (done field))]
          (if (or seen-any? (seq field)) (conj rows row) rows))
        (let [c (at i)]
          (cond
            quoted?
            (cond
              (and (= c "\"") (< (inc i) n) (= (at (inc i)) "\""))
              (recur (+ i 2) (conj field "\"") row rows true true)
              (= c "\"") (recur (inc i) field row rows false true)
              :else (recur (inc i) (conj field c) row rows true true))

            (= c "\"") (recur (inc i) field row rows true true)
            (= c ",") (recur (inc i) [] (conj row (done field)) rows false true)

            (or (= c "\n") (= c "\r"))
            (let [skip (if (and (= c "\r") (< (inc i) n) (= (at (inc i)) "\n")) 2 1)]
              (recur (+ i skip) [] [] (conj rows (conj row (done field))) false false))

            :else (recur (inc i) (conj field c) row rows false true)))))))

(defn csv->tab
  "CSV text as a tab.

  Empty fields are left out rather than stored as empty strings: a cell that
  is not there and a cell holding `\"\"` are different things to
  `sheets.validate` and to anything counting them, and CSV cannot tell them
  apart. Leaving them out is the reading that does not invent data.

  A field beginning with `=` becomes a formula, which is the inverse of what
  `tab->csv` writes."
  ([id text] (csv->tab id text {}))
  ([id text attrs]
   (reduce
    (fn [tab [row-index row]]
      (reduce
       (fn [tab [col-index field]]
         (cond
           (str/blank? field) tab
           (str/starts-with? field "=")
           (model/put-formula tab (inc row-index) (inc col-index) (subs field 1))
           :else (model/put-cell tab (inc row-index) (inc col-index) field)))
       tab
       (map-indexed vector row)))
    (model/tab id attrs)
    (map-indexed vector (parse-csv text)))))

(defn workbook->csv
  "One tab of `workbook`, by id. Nil when there is no such tab."
  [workbook tab-id]
  (some-> (model/tab-by-id workbook tab-id) tab->csv))

(defn import-csv
  "`workbook` with `text` added as a tab.

  Replaces a tab of the same id, because importing the same file twice
  should leave one tab rather than a second one nobody asked for."
  ([workbook tab-id text] (import-csv workbook tab-id text {}))
  ([workbook tab-id text attrs]
   (model/add-tab workbook (csv->tab tab-id text attrs))))
