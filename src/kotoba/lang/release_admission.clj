(ns kotoba.lang.release-admission
  "Fail-closed release publication admission for language/contracts artifacts."
  (:require [kotoba.security.capability :as capability]
            [kotoba.security.hardware :as hardware]
            [kotoba.security.qualification :as qualification]
            [kotoba.security.resilience :as resilience]
            [kotoba.security.transport :as transport]))

(defn evaluate
  [{:keys [repository artifact-digest capability-token capability-context
           hardware-signing-evidence telemetry-receipt receipt-context
           transport-profile restore-receipt restore-attestation
           restore-attestation-context]}]
  (let [capability-result
        (capability/evaluate
         capability-token
         (merge capability-context
                {:audience :kotoba-lang/release
                 :action :release/publish
                 :resource repository
                 :request-digest artifact-digest}))
        hardware-result
        (hardware/evaluate-signing hardware-signing-evidence)
        telemetry-result
        (qualification/verify-signed-receipt telemetry-receipt receipt-context)
        transport-result (transport/evaluate transport-profile)
        restore-result
        (resilience/evaluate-restore-receipt restore-receipt artifact-digest)
        restore-attestation-result
        (qualification/verify-signed-receipt
         restore-attestation restore-attestation-context)
        violations
        (cond-> []
          (not (:capability/allowed? capability-result))
          (conj :signed-capability)
          (not (:hardware-signing/qualified? hardware-result))
          (conj :hardware-signing)
          (not (:qualification/accepted? telemetry-result))
          (conj :immutable-remote-receipt)
          (not= artifact-digest (:qualification/artifact-digest telemetry-result))
          (conj :artifact-binding)
          (not (:transport/allowed? transport-result))
          (conj :release-transport)
          (not (:restore-drill/qualified? restore-result))
          (conj :destructive-restore)
          (not (:qualification/accepted? restore-attestation-result))
          (conj :restore-attestation)
          (not= artifact-digest
                (:qualification/artifact-digest restore-attestation-result))
          (conj :restore-attestation-binding))]
    {:release/allowed? (empty? violations)
     :release/repository repository
     :release/artifact-digest artifact-digest
     :release/violations violations
     :release/capability capability-result
     :release/hardware-signing hardware-result
     :release/telemetry telemetry-result
     :release/transport transport-result
     :release/restore restore-result
     :release/restore-attestation restore-attestation-result}))

(defn admit! [request]
  (let [result (evaluate request)]
    (when-not (:release/allowed? result)
      (throw (ex-info "secure release admission denied" result)))
    result))
