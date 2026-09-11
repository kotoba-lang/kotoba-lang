(ns kotoba.lang.q9-migration-test
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [kotoba.lang.q9-migration :as migration]))

(def q9 (edn/read-string (slurp "lang/q9-migration.edn")))
(def soak (edn/read-string (slurp "lang/q9-wave1-tranche-1-soak.edn")))

(deftest authorized-waves-are-dependency-closed
  (let [authorized (get-in q9 [:current-decision :authorized-waves])]
    (doseq [wave authorized]
      (is (set/subset? (get-in q9 [:wave-dependencies wave]) authorized)))))

(deftest fleet-cannot-be-marked-complete-by-file-count
  (is (false? (get-in q9 [:current-decision :fleet-complete])))
  (is (= :twenty-pilots-extracted-awaiting-soak
         (get-in q9 [:waves :wave-1 :next-tranche-status])))
  (is (true? (get-in q9 [:rollback-policy :oracle-retained-until-soak])))
  (is (true? (get-in q9 [:rollback-policy :production-deploy-requires-separate-authority]))))

(deftest every-disposition-has-evidence-requirements
  (doseq [[disposition rule] (:dispositions q9)]
    (testing (name disposition)
      (is (seq (:requires rule))))))

(deftest migration-unit-is-a-whole-component-built-by-both-public-paths
  (is (= 3 (:kotoba.lang.q9/version q9)))
  (is (= :whole-component (get-in q9 [:scope :migration-unit])))
  (is (true? (get-in q9 [:scope :decision-only-extraction-forbidden])))
  (is (true? (get-in q9 [:scope :whole-component-build-required])))
  (is (true? (get-in q9 [:scope :jvm-dependency-forbidden])))
  (doseq [disposition migration/compiled-dispositions]
    (testing (name disposition)
      (let [required (set (get-in q9 [:dispositions disposition :requires]))]
        (is (set/subset? migration/required-build-evidence required)))))
  (is (= "kotoba compile <entry.kotoba|entry.cljk> --target <target> --output <artifact>"
         (get-in q9 [:whole-component-build-contract
                     :public-cli :source-build])))
  (is (= "kotoba rad build --project <repository> --profile release"
         (get-in q9 [:whole-component-build-contract
                     :public-cli :package-build])))
  (is (= :verified-native-executable
         (get-in q9 [:whole-component-build-contract
                     :public-cli :distribution])))
  (is (= "amu compile <entry.kotoba|entry.cljk> --target <target> --jvm-free --output <artifact>"
         (get-in q9 [:whole-component-build-contract :amu :compile])))
  (is (= :fail-closed
         (get-in q9 [:whole-component-build-contract :amu :jvm-fallback])))
  (is (false? (get-in q9 [:whole-component-build-contract
                           :oracle-parity :jvm-required])))
  (is (= #{"java" "javac" "clojure" "clj"}
         (set (get-in q9 [:whole-component-build-contract
                          :acceptance-environment :forbidden-processes]))))
  (is (= :babashka-native
         (get-in q9 [:whole-component-build-contract
                     :policy-gate :runtime])))
  (is (false? (get-in q9 [:whole-component-build-contract
                           :policy-gate :jvm-required])))
  (is (some #{:decision-core-only-shadow}
            (get-in q9 [:whole-component-build-contract :forbidden])))
  (is (false? (get-in q9 [:component-migration-model
                           :decision-only-slices-allowed]))))

(deftest migration-state-machine-rejects-the-old-decision-core-model
  (let [weakened (-> q9
                     (assoc-in [:scope :migration-unit] :decision)
                     (assoc-in [:scope :decision-only-extraction-forbidden] false))
        codes (set (map :code (migration/validation-errors weakened)))]
    (is (contains? codes :q9/decision-slice-migration-enabled)))
  (let [weakened (update-in q9 [:dispositions :clj-kotoba :requires]
                            #(vec (remove #{:amu-compile} %)))
        errors (migration/validation-errors weakened)]
    (is (some #(and (= :q9/missing-whole-component-build-gate (:code %))
                    (= :clj-kotoba (:disposition %)))
              errors)))
  (let [weakened (-> q9
                     (assoc-in [:scope :jvm-dependency-forbidden] false)
                     (assoc-in [:whole-component-build-contract
                                :amu :jvm-fallback] :allowed))
        codes (set (map :code (migration/validation-errors weakened)))]
    (is (contains? codes :q9/decision-slice-migration-enabled))
    (is (contains? codes :q9/build-contract-weakened))))

(deftest actual-ci-and-soak-evidence-is-fail-closed
  (is (false? (get-in q9 [:soak-evidence :local-preflight-is-ci-evidence])))
  (is (= 2 (:kotoba.lang.q9.soak/version soak)))
  (is (= "fleet-ci/tip-verify/v1" (get-in soak [:requirements :policy])))
  (is (= "fleet-ci/*" (get-in soak [:requirements :signer-grant])))
  (is (= 3 (get-in soak [:requirements :distinct-receipts-per-repository])))
  (is (= 604800 (get-in soak [:requirements :minimum-soak-seconds])))
  (is (true? (get-in soak [:requirements :same-qualification-artifacts])))
  (is (re-matches #"[0-9a-f]{40}" (get-in soak [:gate :root-evidence-sha])))
  (let [runs (map :runs (:repositories soak))
        minimum (apply min (map count runs))]
    (is (= minimum (get-in soak [:gate :actual-green-fleet-receipts-per-repository])))
    (is (every? #(every? (fn [run]
                           (and (:signer-enrolled run)
                                (:signature-verified run)
                                (= :pass (:outcome run))))
                         %)
                runs)))
  (is (false? (get-in soak [:gate :ready])))
  (is (false? (get-in soak [:gate :consumer-cutover-authorized]))))

(deftest migration-state-machine-rejects-premature-grade-a-claims
  (is (:valid? (migration/report q9)))
  (let [premature (-> q9
                      (assoc-in [:current-decision :fleet-complete] true)
                      (assoc-in [:current-decision
                                 :production-deploy-authorized] true))
        codes (set (map :code (migration/validation-errors premature)))]
    (is (contains? codes :q9/premature-fleet-completion))
    (is (contains? codes :q9/premature-production-authorization))))

(deftest qualified-waves-require-evidence-and-explicit-production-authority
  (let [without-evidence
        (assoc-in q9 [:waves :wave-2 :status] :qualified)]
    (is (= :q9/qualified-without-evidence
           (:code (first (migration/validation-errors without-evidence))))))
  (let [all-qualified
        (reduce (fn [program wave]
                  (-> program
                      (assoc-in [:waves wave :status] :qualified)
                      (assoc-in [:waves wave :evidence] ["immutable-proof"])))
                q9 migration/all-waves)]
    (is (some #(= :q9/missing-explicit-production-authorization (:code %))
              (migration/validation-errors all-qualified)))))
