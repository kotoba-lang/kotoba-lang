(ns kotoba.lang.host-parity-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.host-parity :as host-parity]))

(deftest report-is-loadable-and-honestly-reports-the-full-browser-gap
  (let [{:keys [level status score matrix version]} (host-parity/report)]
    (is (= :l5-partial level))
    (is (= :meets-profile status))
    (is (true? (:ok? score)))
    (is (= 59 (:total score)))
    (is (= 10 (:browser-yes score)))
    (is (= 10 (:required-total score)))
    (is (= 10 (:required-yes score)))
    (is (true? (:classification-complete? score)))
    (is (= (:total score) (count matrix)))
    (is (= 2 version))))
