(ns kagitaba.import.admission
  "Fail-closed authorization for importing a secret-bearing archive.

  This boundary does not claim that hardware signing encrypts plaintext 1PUX data."
  (:require [kotoba.security.capability :as capability]
            [kotoba.security.hardware :as hardware]
            [kotoba.security.qualification :as qualification]))

(defn evaluate
  [{:keys [archive-digest capability-token capability-context
           hardware-signing-evidence audit-receipt receipt-context]}]
  (let [cap (capability/evaluate
             capability-token
             (merge capability-context
                    {:audience :kagitaba/import
                     :action :secret-archive/import
                     :resource archive-digest
                     :request-digest archive-digest}))
        signing (hardware/evaluate-signing hardware-signing-evidence)
        receipt (qualification/verify-signed-receipt audit-receipt receipt-context)
        violations (cond-> []
                     (not (:capability/allowed? cap)) (conj :signed-capability)
                     (not (:hardware-signing/qualified? signing)) (conj :hardware-signing)
                     (not (:qualification/accepted? receipt)) (conj :remote-audit-receipt)
                     (not= archive-digest (:qualification/artifact-digest receipt))
                     (conj :archive-binding))]
    {:import/allowed? (empty? violations)
     :import/archive-digest archive-digest
     :import/violations violations
     :import/capability cap
     :import/hardware-signing signing
     :import/audit-receipt receipt}))

(defn admit! [request]
  (let [result (evaluate request)]
    (when-not (:import/allowed? result)
      (throw (ex-info "secret archive import denied" result)))
    result))
