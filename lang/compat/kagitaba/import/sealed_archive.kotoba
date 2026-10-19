(ns kagitaba.import.sealed-archive
  "Production admission for an encrypted, secret-bearing archive.

  Authorization and hybrid-envelope validation complete before ciphertext is
  handed to a caller-supplied decryptor. Cryptography remains provider-owned."
  (:require [kotoba.security.abac :as abac]
            [kotoba.security.approval :as approval]
            [kotoba.security.crypto-policy :as crypto-policy]
            [kotoba.security.effect :as effect]
            [kotoba.security.qualification :as qualification]
            [kotoba.security.resilience :as resilience]
            [kagitaba.import.admission :as import-admission]))

(def default-policy
  {:kotoba.security/crypto-policy-version 1
   :mode :hybrid-required
   :hybrid-epoch-floor 1})

(defn evaluate
  [{:keys [envelope ciphertext ciphertext-digest verify-digest-fn
           ciphertext-size-fn max-ciphertext-bytes
           abac-attributes abac-policy approvals approval-context
           restore-receipt restore-attestation restore-attestation-context]
    :as request}]
  (let [crypto-result (crypto-policy/check-production-envelope
                       default-policy envelope)
        authorization-result (import-admission/evaluate
                              (assoc request :archive-digest ciphertext-digest))
        ciphertext-present? (and (some? ciphertext)
                                 (or (not (coll? ciphertext)) (seq ciphertext)))
        digest-verified? (and (ifn? verify-digest-fn)
                              (true? (verify-digest-fn ciphertext
                                                       ciphertext-digest)))
        ciphertext-size (when (ifn? ciphertext-size-fn)
                          (ciphertext-size-fn ciphertext))
        size-allowed? (and (nat-int? ciphertext-size)
                           (nat-int? max-ciphertext-bytes)
                           (<= ciphertext-size max-ciphertext-bytes))
        abac-result
        (abac/evaluate
         (-> abac-attributes
             (assoc :resource
                    (merge (:resource abac-attributes)
                           {:id ciphertext-digest}))
             (assoc :action
                    (merge (:action abac-attributes)
                           {:id :secret-archive/import
                            :capabilities #{:archive/decrypt}})))
         abac-policy)
        approval-result
        (approval/evaluate approvals
                           (assoc approval-context
                                  :request-digest ciphertext-digest))
        restore-result
        (resilience/evaluate-restore-receipt restore-receipt ciphertext-digest)
        restore-attestation-result
        (qualification/verify-signed-receipt
         restore-attestation restore-attestation-context)
        violations (cond-> []
                     (not (:valid? crypto-result)) (conj :hybrid-envelope)
                     (not (:import/allowed? authorization-result))
                     (conj :import-authorization)
                     (not ciphertext-present?) (conj :ciphertext-required)
                     (not digest-verified?) (conj :ciphertext-digest)
                     (not size-allowed?) (conj :ciphertext-size)
                     (not (:abac/allowed? abac-result)) (conj :import-abac)
                     (not (:approval/allowed? approval-result))
                     (conj :independent-approval-quorum)
                     (not (:restore-drill/qualified? restore-result))
                     (conj :restore-readiness)
                     (not (:qualification/accepted? restore-attestation-result))
                     (conj :restore-attestation)
                     (not= ciphertext-digest
                           (:qualification/artifact-digest
                            restore-attestation-result))
                     (conj :restore-attestation-binding))]
    {:sealed-import/allowed? (empty? violations)
     :sealed-import/violations violations
     :sealed-import/crypto crypto-result
     :sealed-import/authorization authorization-result
     :sealed-import/ciphertext-size ciphertext-size
     :sealed-import/abac abac-result
     :sealed-import/approval approval-result
     :sealed-import/restore restore-result
     :sealed-import/restore-attestation restore-attestation-result
     :sealed-import/ciphertext-digest ciphertext-digest}))

(defn decrypt-admitted!
  "Invoke decrypt-fn only after every admission check succeeds."
  [{:keys [decrypt-fn ciphertext envelope ciphertext-digest] :as request}]
  (try
    (effect/guard!
     {:evaluate evaluate
      :request request
      :approved? :sealed-import/allowed?
      :action :secret-archive/decrypt
      :resource :sealed-archive
      :digest ciphertext-digest
      :effect
      (fn [result]
        (when-not (ifn? decrypt-fn)
          (throw (ex-info "sealed archive decryptor required"
                          (assoc result
                                 :sealed-import/violations
                                 [:decryptor-required]))))
        (assoc result :sealed-import/plaintext
               (decrypt-fn envelope ciphertext)))})
    (catch clojure.lang.ExceptionInfo error
      (if (= :decision-denied (:security.effect/problem (ex-data error)))
        (throw (ex-info "sealed archive import denied" (ex-data error) error))
        (throw error)))))
