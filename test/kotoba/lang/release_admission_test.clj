(ns kotoba.lang.release-admission-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.release-admission :as admission]))

(def digest "sha256:language-release")

(def capability-token
  {:capability/version 1 :capability/audience :kotoba-lang/release
   :capability/subject :release-bot
   :capability/actions #{:release/publish}
   :capability/resources #{"kotoba-lang/kotoba-lang"}
   :capability/request-digest digest
   :capability/not-before-ms 1000 :capability/expires-at-ms 2000
   :capability/nonce "release-1"
   :capability/signature [:valid digest]})

(def base
  {:repository "kotoba-lang/kotoba-lang" :artifact-digest digest
   :capability-token capability-token
   :capability-context
   {:subject :release-bot :now-ms 1500
    :verify-signature-fn
    (fn [body signature]
      (= signature [:valid (:capability/request-digest body)]))
    :consume-nonce-fn (constantly true)}
   :hardware-signing-evidence
   {:provider-id :apple-secure-enclave :hardware-backed? true
    :provider-origin-verified? true :private-exported? false
    :sign-verified? true :unavailable-failed-closed? true}
   :telemetry-receipt
   {:receipt/version 1 :receipt/environment :production
    :receipt/authority-id :release-operations
    :receipt/artifact-digest digest :receipt/issued-at-ms 1500
    :receipt/signature [:valid digest]}
   :receipt-context
   {:environment :production :authority-id :release-operations
    :now-ms 1600 :max-age-ms 500
    :verify-signature-fn
    (fn [body signature]
      (= signature [:valid (:receipt/artifact-digest body)]))}
   :transport-profile
   {:protocol :tls-1.3 :mutual-auth? true
    :peer-id "did:web:releases.kotoba-lang.org"
    :expected-peer-id "did:web:releases.kotoba-lang.org"
    :certificate-fingerprint "sha256:current"
    :trusted-fingerprints #{"sha256:current" "sha256:next"}
    :revocation-checked? true :now "2026-07-20T00:00:00Z"
    :certificate-not-before "2026-07-01T00:00:00Z"
    :certificate-expires-at "2026-08-01T00:00:00Z"
    :require-rotation-overlap? true
    :next-certificate-fingerprint "sha256:next"}
   :restore-receipt
   {:restore-drill/status :passed :restore-drill/destructive? true
    :restore-drill/sites #{:region-a :region-b}
    :restore-drill/backups-encrypted? true
    :restore-drill/backups-immutable? true
    :restore-drill/artifact-digest digest
    :restore-drill/digest-verified? true
    :restore-drill/rto-ms 40 :restore-drill/rto-limit-ms 100
    :restore-drill/rpo-ms 20 :restore-drill/rpo-limit-ms 60}
   :restore-attestation
   {:receipt/version 1 :receipt/environment :production
    :receipt/authority-id :recovery-operations
    :receipt/artifact-digest digest :receipt/issued-at-ms 1500
    :receipt/signature [:valid digest]}
   :restore-attestation-context
   {:environment :production :authority-id :recovery-operations
    :now-ms 1600 :max-age-ms 500
    :verify-signature-fn
    (fn [body signature]
      (= signature [:valid (:receipt/artifact-digest body)]))}})

(deftest release-requires-all-three-independent-authorities
  (is (:release/allowed? (admission/evaluate base)))
  (doseq [bad [(assoc-in base [:capability-token :capability/audience] :other)
               (assoc-in base [:hardware-signing-evidence :private-exported?] true)
               (assoc-in base [:telemetry-receipt :receipt/signature]
                         [:forged digest])
               (assoc-in base [:telemetry-receipt :receipt/artifact-digest]
                         "sha256:other")
               (assoc-in base [:transport-profile :mutual-auth?] false)
               (assoc-in base [:transport-profile :peer-id] "did:web:attacker")
               (assoc-in base [:restore-receipt :restore-drill/destructive?] false)
               (assoc-in base [:restore-receipt :restore-drill/sites] #{:region-a})
               (assoc-in base [:restore-receipt :restore-drill/rto-ms] 101)
               (assoc-in base [:restore-attestation :receipt/signature]
                         [:forged digest])
               (assoc-in base [:restore-attestation :receipt/artifact-digest]
                         "sha256:other")]]
    (is (false? (:release/allowed? (admission/evaluate bad))))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"secure release admission denied"
                          (admission/admit! bad)))))
