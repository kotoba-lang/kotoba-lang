(ns sheets.edit
  "Inserting and removing rows and columns, with the formulas following.

  Its own namespace because it needs both halves: `sheets.model` holds the
  cells and `sheets.formula` knows what a reference is, and `formula`
  already requires `model` — so a structural edit that has to rewrite
  formulas cannot live in either without a cycle.

  ## Why this is allowed where sorting is not

  `model/sort-range` refuses a range holding a formula, because sorting
  moves rows arbitrarily and there is no rule that keeps a reference
  pointing at the same value. Inserting and removing are different: every
  row at or after the cut moves by the same amount, in one direction, so
  there *is* a rule — the one every spreadsheet uses — and following it is
  what makes the edit safe rather than what makes it complicated.

  ## What follows

  Cells move. Formulas are rewritten in every tab, because a formula in one
  sheet can name a cell in another, and one that referred to a removed row
  becomes `#REF!` — a spreadsheet's own answer, and a better one than an
  address that now means a different cell. Named ranges pointing at the tab
  move too.

  Charts do not: `:sheets/data-range` is text on the workbook and shifting
  it is the same call, but a chart also carries a tab name that may be a
  title rather than an id, and guessing which resolves to which is how the
  three `names-of`/`charts-of` bugs happened. `unfollowed` says which charts
  were left alone so a caller can say so rather than discover it."
  (:require [clojure.string :as str]
            [sheets.formula :as formula]
            [sheets.model :as model]))

(defn- shift-cells
  "The tab's cells with the ones at or after `at` moved by `n`.

  A removal drops what was in the band it removed. The cells are a map keyed
  by `[row col]`, so this is a rebuild rather than a shuffle — and rebuilding
  is what keeps two cells from landing on one key when a shift overlaps."
  [tab axis at n]
  (let [row? (= :row axis)]
    (assoc tab :sheets/cells
           (reduce-kv
            (fn [acc [row col] cell]
              (let [value (if row? row col)]
                (cond
                  (< value at) (assoc acc [row col] cell)
                  (and (neg? n) (< value (+ at (- n)))) acc
                  :else (let [moved (+ value n)]
                          (assoc acc (if row? [moved col] [row moved]) cell)))))
            {}
            (:sheets/cells tab)))))

(defn- shift-formulas [tab axis at n tab-name home]
  (assoc tab :sheets/cells
         (reduce-kv
          (fn [acc key cell]
            (assoc acc key
                   (if-let [expr (:sheets/formula cell)]
                     (assoc cell :sheets/formula
                            (formula/shift-refs expr axis at n tab-name home))
                     cell)))
          {}
          (:sheets/cells tab))))

(defn- tab-name
  "What formulas in other tabs call this one.

  Its title, falling back to its id — the same rule `xlsx`, `names-of` and
  `charts-of` settled on, and for the same reason: the key a tab is stored
  under and the name a person writes are different things."
  [wb tab-id]
  (or (:sheets/title (model/tab-by-id wb tab-id)) tab-id))

(defn- shift-named-ranges [wb axis at n name]
  (if-not (seq (:sheets/named-ranges wb))
    wb
    (assoc wb :sheets/named-ranges
           (reduce-kv
            (fn [acc id range]
              (assoc acc id
                     (if (and (:sheets/range range)
                              (= name (:sheets/tab range)))
                       (update range :sheets/range
                               #(formula/shift-refs % axis at n nil nil))
                       range)))
            {}
            (:sheets/named-ranges wb)))))

(defn unfollowed
  "What a shift will not move, one entry per thing, shaped like a problem.

  Charts. Their range is text like any other and shifting it is the same
  call, but a chart names its tab by title or by id and resolving that
  wrongly is the bug this library has already made three times. So they are
  reported rather than guessed at."
  [wb tab-id]
  (let [name (tab-name wb tab-id)]
    (vec (for [chart (:sheets/charts wb)
               :when (contains? #{nil tab-id name} (:sheets/tab chart))]
           {:sheets/severity :info
            :sheets/code :chart/range-not-shifted
            :sheets/id (:sheets/id chart)
            :sheets/msg (str "グラフの範囲 " (:sheets/data-range chart)
                             " は行や列の挿入・削除に追随しません。")}))))

(defn- shift
  [wb tab-id axis at n]
  (if-not (model/tab-by-id wb tab-id)
    wb
    (let [name (tab-name wb tab-id)
          moved (-> (model/tab-by-id wb tab-id) (shift-cells axis at n))
          wb (assoc-in wb [:sheets/tabs tab-id] moved)]
      (-> (reduce (fn [acc [id sheet]]
                    (assoc-in acc [:sheets/tabs id]
                              (shift-formulas sheet axis at n name
                                              (or (:sheets/title sheet) id))))
                  wb
                  (:sheets/tabs wb))
          (shift-named-ranges axis at n name)))))

(defn insert-rows
  "`n` rows before row `at` in `tab-id`."
  ([wb tab-id at] (insert-rows wb tab-id at 1))
  ([wb tab-id at n] (if (pos? n) (shift wb tab-id :row at n) wb)))

(defn delete-rows
  "`n` rows from row `at` in `tab-id`."
  ([wb tab-id at] (delete-rows wb tab-id at 1))
  ([wb tab-id at n] (if (pos? n) (shift wb tab-id :row at (- n)) wb)))

(defn insert-cols
  ([wb tab-id at] (insert-cols wb tab-id at 1))
  ([wb tab-id at n] (if (pos? n) (shift wb tab-id :col at n) wb)))

(defn delete-cols
  ([wb tab-id at] (delete-cols wb tab-id at 1))
  ([wb tab-id at n] (if (pos? n) (shift wb tab-id :col at (- n)) wb)))
