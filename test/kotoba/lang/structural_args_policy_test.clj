
(ns kotoba.lang.structural-args-policy-test
  "T5.1: structural-args policy present and forbids new packs."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(deftest pure-product-structural-args-policy
  (let [p (edn/read-string (slurp "lang/pure-product-profile.edn"))
        s (:structural-args p)]
    (is (= "T5.1" (:wbs s)))
    (is (true? (:forbidden-new-public-packs s)))
    (is (= 5 (:max-parameters s)))
    (is (= :record (first (:preference s))))
    (is (str/includes? (str (:adr s)) "t51"))))

(deftest adr-exists
  (is (.exists (java.io.File. "docs/adr/ADR-reliability-t51-structural-args.md")))
  (let [a (slurp "docs/adr/ADR-reliability-t51-structural-args.md")]
    (is (str/includes? a "max-parameters = 5"))
    (is (str/includes? a "base-N"))
    (is (str/includes? a "T5.2"))))
