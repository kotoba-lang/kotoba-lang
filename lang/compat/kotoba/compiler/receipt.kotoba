(ns kotoba.compiler.receipt
  (:require [kotoba.compiler.authority :as authority]
            [kotoba.compiler.effect-row :as effect-row]
            [kotoba.artifact.core :as artifact]
            [kotoba.artifact.runtime-identity :as runtime-identity]
            [kotoba.verifier.signing :as signing]))

(def statuses #{:ok :trap :denied})
(def ^:private receipt-fields-v1
  #{:format :artifact-envelope-sha256 :artifact-sha256 :signer :target :entry
    :required-effects :policy-sha256 :input-sha256 :output-sha256 :fuel :status
    :started-at :finished-at :parent :admission-sha256 :receipt-sha256 :executor})
(def ^:private receipt-fields-v2 (conj receipt-fields-v1 :authority-decision))

(defn- verify-schema! [receipt]
  (let [fields (case (:format receipt)
                 :kotoba.run-receipt/v1 receipt-fields-v1
                 :kotoba.run-receipt/v2 receipt-fields-v2
                 nil)]
    (when-not (and (map? receipt)
                 (= fields (set (keys receipt)))
                 (map? (:fuel receipt))
                 (= #{:initial :remaining :consumed} (set (keys (:fuel receipt)))))
      (throw (ex-info "run receipt schema rejected" {:phase :receipt})))
    (when (= :kotoba.run-receipt/v2 (:format receipt))
      (authority/validate-decision! (:authority-decision receipt))))
  receipt)

(defn- receipt-hash [receipt]
  (artifact/sha256 (dissoc receipt :receipt-sha256 :executor)))

(defn valid-hash? [receipt]
  (and (string? (:receipt-sha256 receipt))
       (= (:receipt-sha256 receipt) (receipt-hash receipt))))

(defn- verify-executor! [receipt trust]
  (signing/validate-trust! trust)
  (let [executor (:executor receipt)
        statement {:format :kotoba.receipt-attestation/v1
                   :receipt-sha256 (:receipt-sha256 receipt)
                   :executor (:signer executor)}]
    (when-not (and (map? executor)
                   (= #{:signer :public-key :signature} (set (keys executor)))
                   (string? (:signer executor))
                   (string? (:public-key executor))
                   (string? (:signature executor))
                   (= (:signer executor) (signing/signer-id (:public-key executor)))
                   (contains? (:trusted-signers trust) (:signer executor))
                   (not (contains? (:revoked-signers trust) (:signer executor)))
                   (signing/verify-value (:public-key executor) statement
                                         (:signature executor)))
      (throw (ex-info "executor receipt attestation rejected" {:phase :receipt})))
    (:signer executor)))

(defn create
  [envelope trust policy input output
   {:keys [now started-at finished-at status target entry fuel-initial fuel-remaining parent
           executor-key authority-decision]}]
  (let [{kexe :artifact signer :signer} (signing/verify envelope trust now)
        required (:effects kexe)
        policy-result (effect-row/check {:effects required} policy)
        parent-sha (when parent
                     (do (verify-schema! parent)
                         (when-not (valid-hash? parent)
                           (throw (ex-info "parent receipt integrity mismatch" {:phase :receipt})))
                         (verify-executor! parent trust)
                         (:receipt-sha256 parent)))]
    (when-let [runtime (and (map? output) (:runtime output))]
      (runtime-identity/admit! runtime trust))
    (when-not (contains? statuses status)
      (throw (ex-info "invalid receipt status" {:phase :receipt :status status})))
    (when-not (and (integer? started-at) (integer? finished-at) (<= started-at finished-at))
      (throw (ex-info "invalid receipt time interval" {:phase :receipt})))
    (when-not (and (integer? fuel-initial) (integer? fuel-remaining)
                   (<= 0 fuel-remaining fuel-initial))
      (throw (ex-info "invalid receipt fuel accounting" {:phase :receipt})))
    (when-not (and (= target (:target kexe)) (symbol? entry)
                   (contains? (:exports kexe) entry))
      (throw (ex-info "receipt target or entry does not match artifact"
                      {:phase :receipt :target target :entry entry})))
    (when-not (signing/valid-key? executor-key)
      (throw (ex-info "receipt requires a valid executor signing key" {:phase :receipt})))
    (when authority-decision
      (authority/validate-decision! authority-decision))
    (let [body (cond-> {:format (if authority-decision
                                 :kotoba.run-receipt/v2
                                 :kotoba.run-receipt/v1)
                :artifact-envelope-sha256 (artifact/sha256 envelope)
                :artifact-sha256 (:sha256 kexe)
                :signer signer
                :target target :entry entry
                :required-effects required
                :policy-sha256 (artifact/sha256 policy)
                :input-sha256 (artifact/sha256 input)
                :output-sha256 (artifact/sha256 output)
                :fuel {:initial fuel-initial :remaining fuel-remaining
                       :consumed (- fuel-initial fuel-remaining)}
                :status status :started-at started-at :finished-at finished-at
                :parent parent-sha
                :admission-sha256 (artifact/sha256 policy-result)}
                 authority-decision
                 (assoc :authority-decision authority-decision))
          receipt-sha (artifact/sha256 body)
          executor-statement {:format :kotoba.receipt-attestation/v1
                              :receipt-sha256 receipt-sha
                              :executor (:signer executor-key)}]
      (assoc body :receipt-sha256 receipt-sha
             :executor {:signer (:signer executor-key)
                        :public-key (:public-key executor-key)
                        :signature (signing/sign-value executor-key executor-statement)}))))

(defn verify
  [receipt envelope trust policy input output {:keys [now parent authority-decision]}]
  (verify-schema! receipt)
  (when-not (valid-hash? receipt)
    (throw (ex-info "receipt integrity mismatch" {:phase :receipt})))
  (let [{kexe :artifact signer :signer} (signing/verify envelope trust now)
        required (:effects kexe)
        policy-result (effect-row/check {:effects required} policy)
        expected-parent (some-> parent :receipt-sha256)
        fuel (:fuel receipt)]
    (when-let [runtime (and (map? output) (:runtime output))]
      (runtime-identity/admit! runtime trust))
    (when (and parent (not (valid-hash? parent)))
      (throw (ex-info "parent receipt integrity mismatch" {:phase :receipt})))
    (verify-executor! receipt trust)
    (when-not (and (= (:artifact-envelope-sha256 receipt) (artifact/sha256 envelope))
                   (= (:artifact-sha256 receipt) (:sha256 kexe))
                   (= (:signer receipt) signer)
                   (= (:target receipt) (:target kexe))
                   (contains? (:exports kexe) (:entry receipt))
                   (= (:required-effects receipt) required)
                   (= (:policy-sha256 receipt) (artifact/sha256 policy))
                   (= (:admission-sha256 receipt) (artifact/sha256 policy-result))
                   (= (:input-sha256 receipt) (artifact/sha256 input))
                   (= (:output-sha256 receipt) (artifact/sha256 output))
                   (= (:authority-decision receipt) authority-decision)
                   (= (:parent receipt) expected-parent)
                   (contains? statuses (:status receipt))
                   (integer? (:started-at receipt)) (integer? (:finished-at receipt))
                   (<= (:started-at receipt) (:finished-at receipt))
                   (integer? (:initial fuel)) (integer? (:remaining fuel))
                   (<= 0 (:remaining fuel) (:initial fuel))
                   (= (:consumed fuel) (- (:initial fuel) (:remaining fuel))))
      (throw (ex-info "receipt evidence mismatch" {:phase :receipt})))
    (cond-> {:verified? true :receipt-sha256 (:receipt-sha256 receipt)
             :artifact-sha256 (:artifact-sha256 receipt) :status (:status receipt)}
      authority-decision (assoc :authority-decision authority-decision))))

(defn verify-chain [receipts trust]
  (when-not (vector? receipts)
    (throw (ex-info "receipt chain must be a vector" {:phase :receipt})))
  (when (empty? receipts)
    (throw (ex-info "receipt chain must not be empty" {:phase :receipt})))
  (when (> (count receipts) 10000)
    (throw (ex-info "receipt chain exceeds verification limit" {:phase :receipt})))
  (loop [remaining receipts parent nil seen #{}]
    (if-let [receipt (first remaining)]
      (do
        (verify-schema! receipt)
        (when-not (valid-hash? receipt)
          (throw (ex-info "receipt chain integrity mismatch" {:phase :receipt})))
        (verify-executor! receipt trust)
        (when-not (= (:parent receipt) (some-> parent :receipt-sha256))
          (throw (ex-info "receipt parent link mismatch" {:phase :receipt})))
        (when (contains? seen (:receipt-sha256 receipt))
          (throw (ex-info "receipt chain cycle" {:phase :receipt})))
        (recur (next remaining) receipt (conj seen (:receipt-sha256 receipt))))
      {:verified? true :scope :executor-attested-chain/v1
       :count (count receipts) :head (some-> parent :receipt-sha256)})))
