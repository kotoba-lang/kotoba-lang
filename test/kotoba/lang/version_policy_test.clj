(ns kotoba.lang.version-policy-test
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [kotoba.lang.version-policy :as version])
  (:import [java.time LocalDate]))

(deftest checked-in-policy-has-valid-semver-and-deprecation-window
  (is (= {:valid? true :errors []} (version/validate-policy)))
  (doseq [valid ["0.4.0" "1.0.0" "1.2.3-alpha.1" "1.2.3+build.7"]]
    (is (some? (version/parse-semver valid)) valid))
  (doseq [invalid ["1" "1.2" "01.2.3" "1.02.3" "v1.2.3" "1.2.3-"]]
    (is (nil? (version/parse-semver invalid)) invalid)))

(deftest compatibility-report-is-deterministic-and-fail-closed
  (let [policy (version/read-policy)
        request {:language-profile 4 :package-contract 1
                 :release-version "0.4.0"
                 :on-date (LocalDate/parse "2026-08-01")}
        first-report (version/compatibility-report policy request)
        second-report (version/compatibility-report policy request)]
    (is (= first-report second-report))
    (is (:compatible? first-report))
    (is (= [:language-profile :package-contract :release]
           (mapv :axis (:checks first-report))))
    (is (false?
         (:compatible?
          (version/compatibility-report
           policy (assoc request :language-profile 99)))))
    (is (false?
         (:compatible?
          (version/compatibility-report
           policy (assoc request :release-version "latest")))))))

(deftest default-compatibility-request-matches-current-release
  (let [policy (version/read-policy)
        ids (version/current-profile-ids policy)
        report (version/compatibility-report
                policy (version/default-compatibility-request policy))]
    (is (= 6 (:language-profile ids)))
    (is (= 1 (:package-contract ids)))
    (is (= "0.7.0" (:release-version ids)))
    (is (:compatible? report))
    (is (:valid? (version/validate-policy policy)))))

(deftest release-tag-template-binds-language-profile
  (let [policy (version/read-policy)
        env (version/release-tag-envelope-template
             policy {:commit "c" :tree "t" :source-root "s"
                     :issued-at-ms 1 :signer "did:key:test"
                     :artifact-digests {:darwin-arm64 "sha256:a"}
                     :conformance-result {:status :passed}})]
    (is (= "v0.7.0" (:tag env)))
    (is (= 6 (:language-profile env)))
    (is (= #{:version :commit :tree :source-root :issued-at-ms
             :language-profile :package-contract :artifact-digests
             :conformance-result}
           (get-in policy [:release-tags :binds])))))

(deftest release-trust-is-external-and-active
  (let [trust (edn/read-string (slurp "lang/release-trust.edn"))
        signers (:signers trust)]
    (is (= 1 (:kotoba.lang.release-trust/version trust)))
    (is (= 1 (count signers)))
    (is (every? #(= :active (:status %)) (vals signers)))
    (is (every? #(str/starts-with? % "did:key:") (keys signers)))))
