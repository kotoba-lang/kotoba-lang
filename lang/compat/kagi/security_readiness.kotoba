(ns kagi.security-readiness
  "Fail-closed deployment assurance gate. Source controls and unit tests are
  not treated as evidence that hardware, browsers, witnesses or production
  infrastructure were actually exercised."
  (:require [clojure.string :as str]
            [cbor.core :as cbor]
            [kagi.crypto :as crypto]
            [kagi.rotation-scheduler :as scheduler])
  (:import [java.time Instant]))

(def required-attestations
  #{:hardware-handle-integration
    :real-browser-passkey
    :independent-witness-deployment
    :mtls-witness-deployment
    :incident-closed-loop-drill
    :production-rotation-deployment
    :production-concurrency-soak
    :nonce-cleanup-observation
    :recovery-ceremony
    :independent-crypto-review})

(def attestation-domain "kagi/deployment-attestation/v1")

(defn attestation-bytes [control-id attestation]
  (cbor/encode (-> attestation
                   (dissoc :signature)
                   (assoc :domain attestation-domain :control-id control-id))))

(defn issue-attestation
  "Create a portable hybrid-signed attestation over one reproduced artifact."
  [provider signer control-id {:keys [issuer performed-at artifact-sha256]}]
  (when-not (contains? required-attestations control-id)
    (throw (ex-info "unknown deployment attestation control" {:control-id control-id})))
  (let [attestation {:status :passed :issuer issuer :performed-at performed-at
                     :artifact-sha256 artifact-sha256}]
    (when-not (and (not (str/blank? issuer))
                   (boolean (re-matches #"[0-9a-f]{64}" (or artifact-sha256 "")))
                   (try (Instant/parse performed-at) true (catch Exception _ false)))
      (throw (ex-info "invalid deployment attestation metadata" {:control-id control-id})))
    (assoc attestation :signature
           (crypto/sign-with provider signer (attestation-bytes control-id attestation)))))

(defn- attestation-valid? [control-id attestation provider trusted-attestors now]
  (try
    (and (= :passed (:status attestation))
         (not (str/blank? (:issuer attestation)))
         (boolean (re-matches #"[0-9a-f]{64}" (or (:artifact-sha256 attestation) "")))
         (let [performed (Instant/parse (:performed-at attestation))
               current (Instant/parse now)]
           (not (.isAfter performed current)))
         (when-let [public (get trusted-attestors (:issuer attestation))]
           (crypto/verify* provider public
                           (attestation-bytes control-id attestation)
                           (:signature attestation))))
    (catch Exception _ false)))

(defn- control [id ok? evidence]
  {:control/id id :control/ok? (boolean ok?) :control/evidence evidence})

(defn assess
  "Assess concrete deployment state at trusted ISO timestamp `now`.

  Inputs are deliberately explicit and already-redacted:
  - identity: persisted public identity metadata (never fetched secrets)
  - records: complete key inventory from all-schedule-records
  - unlock-wraps: persisted VMK unlock metadata
  - rotation-events: durable DAG events
  - jobs: durable scheduler jobs
  - attestations: signed/out-of-band evidence metadata

  An attestation is accepted only with :status :passed, nonempty :issuer,
  non-future :performed-at and a lowercase 64-hex artifact digest."
  [{:keys [identity records unlock-wraps rotation-events jobs attestations now
           crypto-provider trusted-attestors]}]
  (let [signing (:native-signing-handle identity)
        kem (:native-kem-handle identity)
        raw-fields (filter #(contains? identity %)
                           [:private-key :private-b64 :mldsa-private-b64 :kem-secret])
        overdue (scheduler/due-rotations records now)
        dead (filter #(= :dead-letter (:job/status %)) jobs)
        controls (vec
                  (concat
                   [(control :no-persisted-raw-private-keys (empty? raw-fields)
                             {:present-fields (vec raw-fields)})
                    (control :native-nonexportable-signing
                             (and (= :non-exportable-native-handle (:custody signing))
                                  (false? (:private-exported? signing))) signing)
                    (control :native-nonexportable-kem
                             (and (= :non-exportable-native-handle (:custody kem))
                                  (false? (:private-exported? kem))) kem)
                    (control :all-key-classes-inventoried
                             (= #{:item-dek :identity-signing :recipient-kem :vmk :device-unlock}
                                (set (map :key/class records)))
                             {:classes (set (map :key/class records))})
                    (control :no-overdue-rotation (empty? overdue)
                             {:overdue-count (count overdue)
                              :earliest-due-at (:rotation/due-at (first overdue))})
                    (control :no-rotation-dead-letter (empty? dead)
                             {:dead-letter-count (count dead)})
                    (control :passkey-prf-enrolled
                             (boolean (some #(= :passkey-prf (:method %)) unlock-wraps))
                             {:methods (set (map :method unlock-wraps))})
                    (control :durable-rotation-dag-present (seq rotation-events)
                             {:event-count (count rotation-events)})]
                   (for [id (sort required-attestations)]
                     (let [attestation (get attestations id)]
                       (control id (attestation-valid? id attestation crypto-provider
                                                       trusted-attestors now)
                                (if (map? attestation)
                                  (select-keys attestation
                                               [:status :issuer :performed-at :artifact-sha256])
                                  {:invalid-type (some-> attestation type str)}))))))
        failures (filterv (complement :control/ok?) controls)]
    {:security/production-ready? (empty? failures)
     :security/assessed-at now
     :security/controls controls
     :security/blockers (mapv :control/id failures)
     :security/assurance (if (empty? failures) :high :insufficient)}))
