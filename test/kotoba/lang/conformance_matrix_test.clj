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

;; --- surface-status <-> manifest agreement --------------------------------

(deftest recorded-claims-and-required-backends-agree
  (let [v (cm/validate-claims (cm/load-manifest) (cm/load-surface-status))]
    (is (true? (:ok? v)) (str "problems: " (pr-str (:problems v))))
    (is (pos? (:linked-case-count v))
        "no surface-status entry links a conformance case, so this gate is inert")))

(deftest a-measured-compiler-rejection-must-carry-the-orphan-note
  ;; The gap this gate closes: :nested-let-destructuring is :pure-product-run,
  ;; so it declares #{:kir :wasm32-kotoba-v1} required, while surface-status
  ;; measured the compiler rejecting the shape. Neither file was wrong alone.
  (let [m (cm/load-manifest)
        ss (cm/load-surface-status)
        stripped (update-in ss [:other-gaps :nested-destructuring]
                            dissoc :orphaned-conformance)
        v (cm/validate-claims m stripped)]
    (is (false? (:ok? v)))
    (is (= :measured-rejection-without-orphan-note
           (-> v :problems first :type)))))

(deftest an-orphan-note-without-a-measurement-is-stale
  (let [m (cm/load-manifest)
        ss (cm/load-surface-status)
        stripped (update-in ss [:other-gaps :nested-destructuring] dissoc :measurement)
        v (cm/validate-claims m stripped)]
    (is (false? (:ok? v)))
    (is (some #(= :orphan-note-without-measured-rejection (:type %)) (:problems v))
        "a note claiming the compiler rejects the surface outlives the
         measurement it was based on unless something checks")))

(deftest a-case-declared-in-another-manifest-must-say-so
  (let [m (cm/load-manifest)
        ss (cm/load-surface-status)
        stripped (update-in ss [:other-gaps :record-schema-values]
                            dissoc :conformance-manifest)
        v (cm/validate-claims m stripped)]
    (is (false? (:ok? v)))
    (is (= :conformance-case-unknown (-> v :problems first :type))
        ":record-kit lives in the compiler's pilot manifest; without
         :conformance-manifest the link is indistinguishable from a dangling one")))

(deftest backends-catalog-has-t13-pair
  (let [m (cm/load-manifest)
        b (cm/backends m)]
    (is (contains? b :kir))
    (is (contains? b :wasm32-kotoba-v1))
    (is (= :required-for-pure-product (get-in b [:kir :status])))
    (is (= :required-for-pure-product (get-in b [:wasm32-kotoba-v1 :status])))))
