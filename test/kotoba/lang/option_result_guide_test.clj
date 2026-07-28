(ns kotoba.lang.option-result-guide-test
  "T4.3: option/result guide + golden structural checks."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]))

(def guide-path "docs/lang/option-result-guide.md")
(def golden-path "examples/option-result/guide_golden.kotoba")
(def pva-path "examples/product-value-abi-v1/claim_sub.kotoba")

(deftest guide-exists-and-covers-layers
  (let [g (slurp guide-path)]
    (is (str/includes? g "if-some"))
    (is (str/includes? g "[:option"))
    (is (str/includes? g "option-value"))
    (is (str/includes? g "unwrap-ok"))
    (is (str/includes? g "has-*") "must document forbidden sentinel pattern")
    (is (str/includes? g "T4.1"))
    (is (str/includes? g "product-value-abi-v1"))))

(deftest guide-golden-exports-if-some-patterns
  (let [src (slurp golden-path)]
    (is (str/includes? src "if-some"))
    (is (str/includes? src "[:option :i64]"))
    (is (str/includes? src "[:option :string]"))
    (is (str/includes? src "absent-default"))
    (is (str/includes? src "present-payload"))
    (is (str/includes? src "ttl-or-default"))
    (is (str/includes? src ":export"))))

(deftest pva-golden-still-present
  (let [src (slurp pva-path)]
    (is (str/includes? src "if-some"))
    (is (str/includes? src "claim-sub"))
    (is (str/includes? src "string-from-i64"))))
