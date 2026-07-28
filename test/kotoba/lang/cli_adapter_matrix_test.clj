(ns kotoba.lang.cli-adapter-matrix-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.cli-adapter-matrix :as m]))

(deftest matrix-matches-public-contract
  (let [r (m/validate)]
    (is (true? (:ok? r)) (pr-str (:problems r)))
    (is (= 8 (:command-count r)))
    (is (true? (:implemented-check? r)))))
