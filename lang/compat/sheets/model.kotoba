(ns sheets.model
  (:require [clojure.string]))

(defn workbook
  ([id] (workbook id {}))
  ([id attrs]
   (merge {:sheets/id id
           :sheets/type :workbook
           :sheets/tabs {}
           :sheets/named-ranges {}
           :sheets/charts []}
          attrs)))

(defn tab
  ([id] (tab id {}))
  ([id attrs]
   (merge {:sheets/id id
           :sheets/title id
           :sheets/cells {}}
          attrs)))

(def alphabet "ABCDEFGHIJKLMNOPQRSTUVWXYZ")

(defn column-name
  "1 → A, 26 → Z, 27 → AA.

  Bijective base-26: there is no zero digit, so the usual division has to
  borrow one before each step. Getting this wrong gives a workbook whose
  27th column is `BA`, which Excel opens without complaint and reads wrong.

  The letter comes out of `alphabet` by index rather than out of arithmetic
  on a character code. `(int ch)` does not mean the same thing on both
  hosts — on the JVM a character is a `Character` and `int` is its code
  point; in ClojureScript it is a one-character string and `int` coerces it
  to 0 — so the arithmetic version silently produced control characters and
  read every column as 1 under cljs.

  This lives here rather than in `sheets.xlsx`, where it was, because
  addressing is not a fact about a file format. It was written twice and got
  the same bug both times."
  [col]
  (loop [n (long col) out ""]
    (if (pos? n)
      (let [rem (mod (dec n) 26)]
        (recur (quot (dec n) 26) (str (subs alphabet rem (inc rem)) out)))
      out)))

(defn column-number
  "`A` → 1, `AA` → 27. The inverse of `column-name`, and nil for anything
  that is not all letters."
  [letters]
  (let [s (clojure.string/upper-case (str letters))]
    (when (seq s)
      (reduce (fn [n ch]
                (if-let [i (clojure.string/index-of alphabet ch)]
                  (+ (* 26 n) (inc i))
                  (reduced nil)))
              0
              s))))

(defn cell-key [row col]
  [(long row) (long col)])

(defn put-cell [tab row col value]
  (assoc-in tab [:sheets/cells (cell-key row col)] {:sheets/value value}))

(defn put-formula [tab row col expr]
  (assoc-in tab [:sheets/cells (cell-key row col)] {:sheets/formula expr}))

(defn put-cell-style [tab row col style]
  (assoc-in tab [:sheets/cells (cell-key row col) :sheets/style] style))

(defn get-cell [tab row col]
  (get-in tab [:sheets/cells (cell-key row col)]))

(defn add-tab [wb t]
  (assoc-in wb [:sheets/tabs (:sheets/id t)] t))

(defn add-named-range [wb id attrs]
  (assoc-in wb [:sheets/named-ranges id]
            (merge {:sheets/id id} attrs)))

(defn add-chart [wb chart]
  (update wb :sheets/charts conj chart))

(defn tab-by-id [wb id]
  (get-in wb [:sheets/tabs id]))

(defn range-values [tab row1 col1 row2 col2]
  (vec
   (for [row (range row1 (inc row2))]
     (vec
      (for [col (range col1 (inc col2))]
        (:sheets/value (get-cell tab row col)))))))

(defn seed-workbook []
  (-> (workbook "gftd-sheets")
      (add-tab (-> (tab "plan" {:sheets/title "Plan"})
                   (put-cell 1 1 "Workstream")
                   (put-cell 1 2 "Owner")
                   (put-cell 2 1 "Slides")
                   (put-cell 2 2 "GFTD")))))
