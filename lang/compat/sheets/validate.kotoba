(ns sheets.validate)

(defn problem [severity code id msg]
  {:sheets/severity severity :sheets/code code :sheets/id id :sheets/msg msg})

(defn cell-problems [tab]
  (for [[[row col] cell] (:sheets/cells tab)
        :when (or (< row 1) (< col 1)
                  (and (contains? cell :sheets/value)
                       (contains? cell :sheets/formula)))]
    (problem :error :cell/invalid [(:sheets/id tab) row col] "cell address or payload is invalid")))

(defn named-range-problems [wb]
  (for [[id range] (:sheets/named-ranges wb)
        :when (or (nil? (:sheets/tab range))
                  (nil? (:sheets/range range)))]
    (problem :error :named-range/invalid id "named range requires :sheets/tab and :sheets/range")))

(defn chart-problems [wb]
  (for [chart (:sheets/charts wb)
        :when (or (nil? (:sheets/id chart))
                  (nil? (:sheets/data-range chart)))]
    (problem :error :chart/invalid (:sheets/id chart) "chart requires :sheets/id and :sheets/data-range")))

(defn problems [wb]
  (vec (concat
        (mapcat cell-problems (vals (:sheets/tabs wb)))
        (named-range-problems wb)
        (chart-problems wb))))

(defn valid? [wb]
  (not-any? #(= :error (:sheets/severity %)) (problems wb)))
