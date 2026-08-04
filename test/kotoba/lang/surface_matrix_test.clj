(ns kotoba.lang.surface-matrix-test
  "T2.2: surface-matrix generation + check."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.surface-matrix :as sm]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(deftest surface-status-valid
  (let [s (sm/load-surface-status)
        v (sm/validate-status s)]
    (is (true? (:ok? v)) (pr-str (:problems v)))
    (is (number? (:kotoba.lang.surface-status/version s)))))

(deftest render-contains-core-sections
  (let [md (sm/render-markdown (sm/load-surface-status))]
    (is (str/includes? md "# Kotoba language surface matrix"))
    (is (str/includes? md "## Security / language invariants"))
    (is (str/includes? md "## Collections"))
    (is (str/includes? md "`no-ambient-authority`"))
    (is (str/includes? md "WBS: **T2.2**"))))

(deftest computed-invoke-keeps-dynamic-heads-visible-without-redundant-types
  (let [surface (sm/load-surface-status)
        closure (get-in surface [:other-gaps :first-class-closure-values])]
    (is (= "(invoke closure zero-to-four-args) in a closed typed result context"
           (get-in closure [:syntax :contextual-typed-computed-call])))
    (is (= :i64
           (get-in closure [:computed-invoke-result-context
                            :fallback-without-context])))
    (is (= :accepted-when-ambiguous
           (get-in closure [:computed-invoke-result-context
                            :explicit-descriptor])))
    (is (some #{:contextual-computed-invoke-result-inference}
              (:supports closure)))))

(deftest computed-record-maps-stay-nominal-exact-and-total
  (let [surface (sm/load-surface-status)
        records (get-in surface [:other-gaps :protocol-and-record-dispatch])
        constructor (get-in records [:record-data :map-constructor :compiler])]
    (is (= :exact-literal-map (:leaf constructor)))
    (is (= :declared-nominal-record (:context constructor)))
    (is (= '#{if if-not if-let if-some cond case condp let do}
           (:computed-through constructor)))
    (is (= :all-result-paths-required (:totality constructor)))
    (is (= :rejected (:runtime-map-guessing constructor)))
    (is (not-any? #{:dynamic-map-constructor}
                  (:missing records)))))

(deftest on-disk-matrix-matches-regenerated
  (let [r (sm/check-matrix!)]
    (is (true? (:ok? r))
        (str "regenerate with: clojure -M -m kotoba.lang.surface-matrix ; "
             (pr-str (:problems r))))
    (is (.exists (io/file sm/surface-matrix-path)))))
