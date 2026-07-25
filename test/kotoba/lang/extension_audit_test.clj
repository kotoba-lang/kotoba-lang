(ns kotoba.lang.extension-audit-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.extension-audit :as audit]))

(deftest every-extension-collision-has-a-current-typed-denial
  (let [result (audit/validate)]
    (is (:valid? result))
    (is (= 1082 (:paths result)))
    (is (= 792 (:collisions result)))
    (is (= 858 (:typed-exceptions result)))
    (is (empty? (:errors result)))))

(deftest typed-exceptions-deny-canonical-admission
  (let [data (audit/read-audit)
        collision (first (filter #(contains? audit/collision-classes
                                             (:classification %))
                                 (:entries data)))
        canonical (first (filter #(= :canonical-verified
                                    (:classification %))
                                 (:entries data)))]
    (is (false? (:allowed? (audit/canonical-path-decision
                            data (:path collision)))))
    (is (= :deny
           (get-in collision [:typed-exception :canonical-admission])))
    (is (true? (:allowed? (audit/canonical-path-decision
                           data (:path canonical)))))
    (is (false? (:allowed? (audit/canonical-path-decision
                            data "orgs/unknown/new.kotoba"))))))

(deftest missing-exception-and-digest-drift-fail-closed
  (let [data (audit/read-audit)
        collision-index
        (first (keep-indexed
                (fn [index entry]
                  (when (contains? audit/exception-classes
                                   (:classification entry))
                    index))
                (:entries data)))
        missing (update-in data [:entries collision-index]
                           dissoc :typed-exception)
        drift (assoc-in data [:entries 0 :sha256]
                        (apply str (repeat 64 "0")))]
    (is (some #(= :incomplete-typed-exception (:kind %))
              (:errors (audit/validate missing))))
    (is (some #(= :digest-drift (:kind %))
              (:errors (audit/validate drift))))))
