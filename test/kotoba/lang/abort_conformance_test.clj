(ns kotoba.lang.abort-conformance-test
  "Runs the abort ability's conformance cases (lang/abort-ability.edn, slices
  1 and 2) through the frontend and KIR, so `:conformance-vectors-positive-and-
  negative :met` in `surface-status.edn` is something this repository
  measures rather than states.

  The positive cases execute on KIR and must answer the manifest's `:expect`. The negative cases are pinned by their EXACT rejection message:
  `:error-contains` is compared with `=`, not `includes?`, because a fixture
  refused for some other reason -- an unbound symbol, a type error in the
  fixture itself -- would otherwise count as the refusal it was written to
  demonstrate. A control asserts that each negative fixture parses and that
  its refusal carries a stable `:kotoba.error/abort-*` code, so the message
  and the reason are pinned together."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema]))

(def ^:private conformance-root "lang/conformance")

(defn- abort-cases []
  (->> (:cases (edn/read-string (slurp (str conformance-root "/manifest.edn"))))
       (filter #(some-> (:entry %) (str/starts-with? "abort/")))))

(defn- source-of [case]
  (slurp (str conformance-root "/" (:entry case))))

(defn- refusal-of [source]
  (try (do (sema/analyze source) nil)
       (catch Throwable e e)))

(deftest the-manifest-declares-the-cases-the-contract-lists
  (let [contract (edn/read-string (slurp "lang/abort-ability.edn"))
        declared (into #{} (map :id) (abort-cases))]
    (is (= (set (concat (get-in contract [:conformance :positive])
                        (get-in contract [:conformance :negative])))
           declared))
    ;; A floor, not a fixture count: a slice may add cases, and this assertion
    ;; exists so a set that lost its positives (or its negatives) cannot pass
    ;; as an empty sweep. Slice 1 landed 3 and 6; slice 2 landed 3 and 4 more
    ;; and retired one negative, whose program is now refused elsewhere.
    (is (<= 6 (count (filter #(= :run (:kind %)) (abort-cases)))))
    (is (<= 10 (count (filter #(= :expect-error (:kind %)) (abort-cases)))))
    (is (empty? (set/intersection declared
                                  (set (keys (get-in contract [:conformance :retired])))))
        "a retired case is not still declared")))

(deftest every-positive-case-executes-to-its-expected-value
  (doseq [case (filter #(= :run (:kind %)) (abort-cases))]
    (testing (str (:id case))
      (is (= #{:kir} (:required-backends case)))
      (let [hir (sema/analyze (source-of case))
            value (kir/execute (kir/lower hir) (symbol (:function case)) (:args case))]
        (is (= (get-in case [:expect :kotoba]) (long value)))
        (is (not-any? #(and (seq? %) (contains? '#{throw try catch} (first %)))
                      (tree-seq coll? seq (mapv :body (:functions hir))))
            "no ambient form survives elaboration")))))

(deftest every-negative-case-is-refused-with-exactly-the-pinned-message
  (doseq [case (filter #(= :expect-error (:kind %)) (abort-cases))]
    (testing (str (:id case))
      (let [refusal (refusal-of (source-of case))]
        (is (some? refusal) "the fixture was admitted")
        (when refusal
          (is (= (:error-contains case) (ex-message refusal)))
          (is (str/starts-with? (str (:kotoba.error/code (ex-data refusal)))
                                ":kotoba.error/abort-")
              (pr-str (ex-data refusal))))))))

(deftest the-fixture-directory-and-the-manifest-agree
  (let [on-disk (->> (file-seq (java.io.File. (str conformance-root "/abort")))
                     (filter #(.isFile ^java.io.File %))
                     (map #(subs (.getPath ^java.io.File %) (inc (count conformance-root))))
                     set)
        declared (into #{} (map :entry) (abort-cases))]
    (is (= on-disk declared)
        "every fixture is a case and every case is a fixture")))
