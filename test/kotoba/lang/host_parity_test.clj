(ns kotoba.lang.host-parity-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.host-parity :as host-parity]))

(deftest report-is-loadable-and-meets-the-declared-browser-threshold
  (let [{:keys [level status score matrix version]} (host-parity/report)]
    (is (= :l5-partial level))
    (is (= :meets-threshold status))
    (is (true? (:ok? score)))
    (is (= (:total score) (count matrix)))
    (is (pos-int? version))))
