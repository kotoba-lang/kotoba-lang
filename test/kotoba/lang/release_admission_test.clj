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
      (= signature [:valid (:receipt/artifact-digest body)]))}
   :crypto-policy
   {:kotoba.security/crypto-policy-version 1 :mode :hybrid-required
    :hybrid-epoch-floor 1}
   :artifact-envelope
   {:envelope/algorithms [:x25519 :ml-kem-768 :aes-256-gcm]
    :envelope/provider {:provider/id :release-crypto
                        :provider/fips-validated false}
    :envelope/epoch 2 :envelope/kem? true :envelope/hybrid? true
    :envelope/artifact-digest digest}
   :abac-attributes
   {:subject {:id :release-bot :role :publisher :clearance :restricted
              :tenant "kotoba-lang"}
    :resource {:tenant "kotoba-lang" :trust :release :classification :internal}
    :environment {:surface :ci :network-zone :private
                  :device-trusted? true :now "2026-07-20T00:00:00Z"}
    :purpose :language-release}
   :abac-policy
   {:policy/id :kotoba-lang/release
    :subject/ids #{:release-bot} :subject/roles #{:publisher}
    :resource/ids #{"kotoba-lang/kotoba-lang"}
    :resource/trust #{:release} :action/ids #{:release/publish}
    :action/capabilities #{:artifact/publish}
    :environment/surfaces #{:ci} :environment/network-zones #{:private}
    :environment/require-device-trust? true
    :purpose/allowed #{:language-release} :tenant/isolation? true}
   :approvals
   [{:approval/version 1 :approval/approver :alice :approval/role :security
     :approval/request-digest digest :approval/not-before-ms 1000
     :approval/expires-at-ms 2000 :approval/signature [:valid :alice digest]}
    {:approval/version 1 :approval/approver :bob :approval/role :release
     :approval/request-digest digest :approval/not-before-ms 1000
     :approval/expires-at-ms 2000 :approval/signature [:valid :bob digest]}]
   :approval-context
   {:initiator :release-bot :required-roles #{:security :release}
    :min-approvals 2 :now-ms 1500
    :verify-signature-fn
    (fn [body signature]
      (= signature [:valid (:approval/approver body)
                    (:approval/request-digest body)]))}})

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
                         "sha256:other")
               (assoc-in base [:artifact-envelope :envelope/algorithms]
                         [:x25519])
               (assoc-in base [:artifact-envelope :envelope/hybrid?] false)
               (assoc-in base [:artifact-envelope :envelope/artifact-digest]
                         "sha256:other")
               (assoc-in base [:abac-attributes :subject :id] :attacker)
               (assoc-in base [:abac-attributes :environment :device-trusted?]
                         false)
               (assoc base :approvals [(first (:approvals base))])
               (assoc-in base [:approvals 1 :approval/approver] :alice)
               (assoc-in base [:approvals 0 :approval/signature] [:forged])]]
    (is (false? (:release/allowed? (admission/evaluate bad))))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"secure release admission denied"
                          (admission/admit! bad)))))
