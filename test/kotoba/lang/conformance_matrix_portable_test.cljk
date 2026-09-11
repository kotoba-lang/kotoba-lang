(ns kotoba.lang.conformance-matrix-portable-test
  "The genuinely portable slice of kotoba.lang.conformance-matrix-test.

  Every function under test here (`validate-matrix`, `pure-product-cases`,
  `case-classes`, `cases-for-class`, `required-backends-for`,
  `validate-execution`, `validate-claims`, `backends`) takes the manifest /
  surface-status map as an explicit argument -- only `load-manifest` and
  `load-surface-status` touch a file, via a `slurp-repo-file` whose `:cljs`
  branch deliberately throws (`... requires text inject on cljs`).

  This file builds a small synthetic manifest and surface-status instead of
  reading the real ones, in the same spirit as
  kotoba/test/kotoba/deploy_adapter_portable_test.cljc's synthetic fixtures:
  it tests the validation LOGIC, not that this repository's specific
  `lang/conformance/manifest.edn` currently has >=30 cases. Real-data
  assertions (`manifest-version-2-and-matrix-valid`'s case-count/pure-product-
  count floors, `backends-catalog-has-t13-pair`, etc.) stay in
  kotoba.lang.conformance-matrix-test (.clj)."
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer [deftest is testing] :include-macros true])
            [kotoba.lang.conformance-matrix :as cm]))

(def ^:private manifest
  {:kotoba.lang.conformance/version 2
   :kotoba.lang.conformance/wbs "T1.2"
   :backends {:kir {:status :required-for-pure-product}
             :wasm32-kotoba-v1 {:status :required-for-pure-product}
             :compiler-admit {:status :required-for-negative}}
   :case-classes {:pure-product-run {:profile :pure-product
                                     :required-backends cm/pure-product-required}
                 :negative-admit {:profile :negative
                                  :required-backends #{:compiler-admit}}}
   :cases [{:id :ok-case :class :pure-product-run
           :required-backends cm/pure-product-required
           :executed-by {:kir "amu" :wasm32-kotoba-v1 "kotoba"}}
          {:id :neg-case :class :negative-admit
           :required-backends #{:compiler-admit}}]})

(deftest a-well-formed-manifest-validates
  (let [v (cm/validate-matrix manifest)]
    (is (true? (:ok? v)) (pr-str (:problems v)))
    (is (= 2 (:case-count v)))
    (is (= 1 (:pure-product-count v)))))

(deftest pure-product-cases-are-tagged-with-the-shared-required-set
  (let [pp (cm/pure-product-cases manifest)]
    (is (= [:ok-case] (mapv :id pp)))
    (doseq [c pp]
      (is (= cm/pure-product-required (cm/required-backends-for manifest c))))))

(deftest cases-for-class-filters-by-class
  (is (= [:neg-case] (mapv :id (cm/cases-for-class manifest :negative-admit))))
  (is (= [] (cm/cases-for-class manifest :no-such-class))))

(deftest a-case-naming-an-unknown-class-is-refused
  (let [m (update manifest :cases conj {:id :orphan :class :ghost-class
                                        :required-backends #{}})]
    (is (false? (:ok? (cm/validate-matrix m))))
    (is (some #(and (= :unknown-class (:type %)) (= :orphan (:id %)))
              (:problems (cm/validate-matrix m))))))

(deftest a-pure-product-case-must-declare-exactly-the-shared-required-set
  (let [m (assoc-in manifest [:cases 0 :required-backends] #{:kir})]
    (is (false? (:ok? (cm/validate-matrix m))))
    (is (some #(= :pure-product-case-backends (:type %)) (:problems (cm/validate-matrix m))))))

;; --- validate-execution ----------------------------------------------------

(deftest execution-record-agrees-with-required-backends
  ;; `min-cases-with-execution-record` is a fixed floor (6 in the real
  ;; manifest); this synthetic manifest has one recorded case on purpose, so
  ;; `:ok?` is false for the FLOOR reason only -- not because the one record
  ;; itself disagrees with `:required-backends`.
  (let [v (cm/validate-execution manifest)]
    (is (= 1 (:recorded v)))
    (is (= [{:type :execution-record-floor :got 1 :need cm/min-cases-with-execution-record}]
           (:problems v))
        "the only problem must be the floor, not a backend/record mismatch")))

(deftest dropping-a-required-backend-from-the-declaration-goes-red
  (let [m (assoc-in manifest [:cases 0 :required-backends] #{:wasm32-kotoba-v1})
        v (cm/validate-execution m)]
    (is (false? (:ok? v)))
    (is (some #(and (= :backend-recorded-but-not-required (:type %))
                    (= :kir (:backend %)))
              (:problems v)))))

(deftest a-backend-cannot-be-both-executed-and-deferred
  (let [m (assoc-in manifest [:cases 0 :unexecuted-backends]
                    {:kir {:as-of "2026-09-09" :reason "r" :closes-when "c"}})
        v (cm/validate-execution m)]
    (is (false? (:ok? v)))
    (is (some #(= :backend-both-executed-and-deferred (:type %)) (:problems v)))))

(deftest a-deferral-without-a-date-or-closing-condition-is-refused
  (let [m (-> manifest
              (assoc-in [:cases 0 :required-backends] #{:kir :wasm32-kotoba-v1 :compiler-admit})
              (assoc-in [:cases 0 :unexecuted-backends] {:compiler-admit {:reason "later"}}))
        v (cm/validate-execution m)]
    (is (false? (:ok? v)))
    (is (= [:as-of :closes-when]
           (-> (filter #(= :deferral-missing-keys (:type %)) (:problems v))
               first :missing)))))

;; --- validate-claims ---------------------------------------------------

(def ^:private surface-status
  {:invariants {}
   :collections {}
   :other-gaps {:some-gap {:conformance :ok-case}}})

(deftest a-linked-case-that-exists-is-accepted
  (let [v (cm/validate-claims manifest surface-status)]
    (is (true? (:ok? v)) (pr-str (:problems v)))
    (is (= 1 (:linked-case-count v)))))

(deftest a-linked-case-that-does-not-exist-and-names-no-other-manifest-is-refused
  (let [ss (assoc-in surface-status [:other-gaps :some-gap :conformance] :no-such-case)
        v (cm/validate-claims manifest ss)]
    (is (false? (:ok? v)))
    (is (= :conformance-case-unknown (-> v :problems first :type)))))

(deftest a-linked-case-declared-in-another-manifest-is-not-a-gap
  (let [ss (-> surface-status
               (assoc-in [:other-gaps :some-gap :conformance] :elsewhere)
               (assoc-in [:other-gaps :some-gap :conformance-manifest] "compiler pilot manifest"))]
    (is (true? (:ok? (cm/validate-claims manifest ss))))))

(deftest a-measured-compiler-rejection-must-carry-the-orphan-note
  (let [ss (assoc-in surface-status [:other-gaps :some-gap :measurement] {:result :rejected})
        v (cm/validate-claims manifest ss)]
    (is (false? (:ok? v)))
    (is (= :measured-rejection-without-orphan-note (-> v :problems first :type)))))

(deftest an-orphan-note-without-a-measured-rejection-is-stale
  (let [ss (assoc-in surface-status [:other-gaps :some-gap :orphaned-conformance]
                     {:case :ok-case :executed-by :none})
        v (cm/validate-claims manifest ss)]
    (is (false? (:ok? v)))
    (is (some #(= :orphan-note-without-measured-rejection (:type %)) (:problems v)))))
