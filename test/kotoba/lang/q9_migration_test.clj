(ns kotoba.lang.q9-migration-test
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]))

(def q9 (edn/read-string (slurp "lang/q9-migration.edn")))
(def soak (edn/read-string (slurp "lang/q9-wave1-tranche-1-soak.edn")))

(deftest authorized-waves-are-dependency-closed
  (let [authorized (get-in q9 [:current-decision :authorized-waves])]
    (doseq [wave authorized]
      (is (set/subset? (get-in q9 [:wave-dependencies wave]) authorized)))))

(deftest fleet-cannot-be-marked-complete-by-file-count
  (is (false? (get-in q9 [:current-decision :fleet-complete])))
  (is (= :ten-pilots-extracted-awaiting-soak
         (get-in q9 [:waves :wave-1 :next-tranche-status])))
  (is (true? (get-in q9 [:rollback-policy :oracle-retained-until-soak])))
  (is (true? (get-in q9 [:rollback-policy :production-deploy-requires-separate-authority]))))

(deftest every-disposition-has-evidence-requirements
  (doseq [[disposition rule] (:dispositions q9)]
    (testing (name disposition)
      (is (seq (:requires rule))))))

(deftest actual-ci-and-soak-evidence-is-fail-closed
  (is (false? (get-in q9 [:soak-evidence :local-preflight-is-ci-evidence])))
  (is (= 3 (get-in soak [:requirements :distinct-runs-per-repository])))
  (is (= 604800 (get-in soak [:requirements :minimum-soak-seconds])))
  (is (true? (get-in soak [:requirements :same-qualification-artifacts])))
  (is (every? empty? (map :runs (:repositories soak))))
  (is (false? (get-in soak [:gate :ready])))
  (is (false? (get-in soak [:gate :consumer-cutover-authorized]))))
