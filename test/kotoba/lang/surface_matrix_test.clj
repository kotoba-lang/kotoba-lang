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

(deftest extend-protocol-default-is-static-and-closed
  (let [records (get-in (sm/load-surface-status)
                        [:other-gaps :protocol-and-record-dispatch])
        extension (get-in records [:extensions :extend-protocol])]
    (is (= :sealed-module-record-specialization (:default extension)))
    (is (true? (:explicit-precedence extension)))
    (is (false? (:dynamic-fallback extension)))
    (is (contains? (get-in records [:extensions :implemented])
                   'extend-protocol))
    (is (= [:legacy-zero-sentinel-removal] (:missing records)))))

(deftest canonical-data-host-boundary-does-not-revive-physical-source-syntax
  (let [surface (sm/load-surface-status)
        boundary (get-in surface [:other-gaps :data-host-argument])
        replacement (:canonical-replacement boundary)]
    (is (= :qualified-typed-ability (:source-boundary replacement)))
    (is (= :forbidden (:physical-source-forms replacement)))
    (is (= "kotoba.value.v1" (get-in replacement [:wire :codec])))
    (is (= :kotoba.ability-wire-adapter/v2
           (get-in replacement [:wire :format])))
    (is (= #{:i64 :f32 :f64 :string :bytes :keyword :symbol :bool :document
             :map :option-i64 :result-i64 :vector-i64 :vector-f64
             :record :variant :option :result :vector :list :set :ref}
           (:types replacement)))
    (is (= {:authority :typed-ability-descriptor
            :physical-wire :wit-canonical-abi
            :byte-tunneling false}
           (:component-parity replacement)))
    (is (= #{:kotoba-wasm}
           (get-in boundary [:legacy-surface :backends])))
    (is (= [:async-provider-contract] (:missing boundary)))))

(deftest on-disk-matrix-matches-regenerated
  (let [r (sm/check-matrix!)]
    (is (true? (:ok? r))
        (str "regenerate with: clojure -M -m kotoba.lang.surface-matrix ; "
             (pr-str (:problems r))))
    (is (.exists (io/file sm/surface-matrix-path)))))
