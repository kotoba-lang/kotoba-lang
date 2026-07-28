(ns kotoba.lang.conformance-matrix-test
  "T1.2: required-backends matrix structural tests."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.conformance-matrix :as cm]))

(deftest manifest-version-2-and-matrix-valid
  (let [m (cm/load-manifest)
        v (cm/validate-matrix m)]
    (is (= 2 (:kotoba.lang.conformance/version m)))
    (is (= "T1.2" (:kotoba.lang.conformance/wbs m)))
    (is (true? (:ok? v)) (str "problems: " (pr-str (:problems v))))
    (is (>= (:case-count v) 30))
    (is (>= (:pure-product-count v) 15)
        "pure-product cases drive T1.3 dual-backend gate")))

(deftest pure-product-requires-kir-and-wasm
  (let [m (cm/load-manifest)
        pp (cm/pure-product-cases m)]
    (is (seq pp))
    (doseq [c pp]
      (is (= cm/pure-product-required
             (cm/required-backends-for m c))
          (str (:id c))))))

(deftest case-classes-cover-all-cases
  (let [m (cm/load-manifest)
        classes (set (keys (cm/case-classes m)))]
    (doseq [c (cm/cases m)]
      (is (contains? classes (:class c)) (str (:id c) " " (:class c))))))

(deftest negative-cases-use-compiler-admit
  (let [m (cm/load-manifest)
        neg (cm/cases-for-class m :negative-admit)]
    (is (seq neg))
    (doseq [c neg]
      (is (= #{:compiler-admit} (:required-backends c)) (str (:id c))))))

(deftest backends-catalog-has-t13-pair
  (let [m (cm/load-manifest)
        b (cm/backends m)]
    (is (contains? b :kir))
    (is (contains? b :wasm32-kotoba-v1))
    (is (= :required-for-pure-product (get-in b [:kir :status])))
    (is (= :required-for-pure-product (get-in b [:wasm32-kotoba-v1 :status])))))
