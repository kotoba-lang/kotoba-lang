(ns kotoba.lang.normative-coverage-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.normative-coverage :as coverage]))

(deftest guest-grammar-rules-are-consumer-bound
  (let [authority (slurp "lang/guest-grammar.edn")
        rules (coverage/source-rules
               {:kind :guest-grammar-rules :path "lang/guest-grammar.edn"})]
    (is (> (count rules) 100))
    (is (= authority (slurp "../kotoba/resources/kotoba/lang/guest-grammar.edn")))
    (is (= authority (slurp "../compiler/resources/kotoba/lang/guest-grammar.edn")))))

(deftest every-registered-normative-rule-maps-to-an-existing-test
  (let [result (coverage/validate-registry)]
    (is (:valid? result) (pr-str result))
    (is (> (:rule-count result) 150))
    (is (= (:rule-count result) (:mapped-rule-count result)))))

(deftest all-declared-normative-sources-are-attestable
  (let [result (coverage/validate-registry)]
    (is (:attestable? result) (pr-str result))
    (is (empty? (:residual-gaps result)))))
