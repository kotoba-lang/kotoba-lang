(ns kotoba.lang.version-policy-test
  (:require [clojure.test :refer [deftest is testing]]
            [ed25519.core :as ed]
            [kotoba.lang.version-policy :as version])
  (:import [java.time LocalDate]
           [java.util Base64]))

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

(defn signed-tag [seed overrides]
  (let [public-key (ed/pubkey-from-seed seed)
        signer (ed/did-key-from-pub public-key)
        base {:tag "v0.4.0" :version "0.4.0" :language-profile 4
              :commit "99a6da4159dca0f01f47ec81fab06f6aa4c74a0f"
              :tree "tree-sha256:abc" :source-root "sha256:def"
              :issued-at-ms 1784764800000 :signer signer}
        envelope (merge base overrides)
        signature (ed/sign seed
                           (.getBytes
                            (version/canonical-tag-body envelope) "UTF-8"))]
    (assoc envelope :signature
           (.encodeToString (Base64/getEncoder) signature))))

(deftest release-tags-bind-version-commit-tree-source-and-time
  (let [policy (version/read-policy)
        seed (byte-array (map unchecked-byte (range 32)))
        envelope (signed-tag seed {})
        trust {(:signer envelope) {:status :active}}]
    (is (:valid? (version/verify-release-tag policy trust envelope)))
    (doseq [tampered [(assoc envelope :commit "attacker")
                      (assoc envelope :version "0.4.1")
                      (assoc envelope :source-root "sha256:attacker")
                      (assoc envelope :tag "release-latest")]]
      (is (false? (:valid?
                   (version/verify-release-tag policy trust tampered)))))
    (is (= :tag/signer-untrusted
           (:code (version/verify-release-tag
                   policy {(:signer envelope) {:status :revoked}}
                   envelope))))))

(deftest default-compatibility-request-matches-current-release
  (let [policy (version/read-policy)
        ids (version/current-profile-ids policy)
        report (version/compatibility-report
                policy (version/default-compatibility-request policy))]
    (is (= 4 (:language-profile ids)))
    (is (= 1 (:package-contract ids)))
    (is (= "0.4.0" (:release-version ids)))
    (is (:compatible? report))
    (is (:valid? (version/validate-policy policy)))))

(deftest release-tag-template-binds-language-profile
  (let [policy (version/read-policy)
        env (version/release-tag-envelope-template
             policy {:commit "c" :tree "t" :source-root "s"
                     :issued-at-ms 1 :signer "did:key:test"})]
    (is (= "v0.4.0" (:tag env)))
    (is (= 4 (:language-profile env)))
    (is (contains? (get-in policy [:release-tags :binds]) :language-profile))))
