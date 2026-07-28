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

(deftest on-disk-matrix-matches-regenerated
  (let [r (sm/check-matrix!)]
    (is (true? (:ok? r))
        (str "regenerate with: clojure -M -m kotoba.lang.surface-matrix ; "
             (pr-str (:problems r))))
    (is (.exists (io/file sm/surface-matrix-path)))))
