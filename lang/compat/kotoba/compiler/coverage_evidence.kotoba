(ns kotoba.compiler.coverage-evidence
  (:require [kotoba.artifact.core :as artifact]
            [kotoba.verifier.signing :as signing]))

(def ^:private claim-keys
  #{:platform :paths :target-profiles :conformance-sha256 :runtime-sha256
    :ci-run-url :tested-at :expires})
(def ^:private statement-keys
  (conj claim-keys :format :signer :public-key))
(def ^:private envelope-keys #{:format :statement :signature})
(def ^:private paths #{:native :wasm})

(defn- reject! [message phase]
  (throw (ex-info message {:phase phase})))

(defn- sha256? [value]
  (and (string? value) (boolean (re-matches #"[0-9a-f]{64}" value))))

(defn- valid-claim? [claim]
  (and (map? claim) (= claim-keys (set (keys claim)))
       (keyword? (:platform claim))
       (set? (:paths claim)) (seq (:paths claim))
       (every? paths (:paths claim))
       (set? (:target-profiles claim)) (seq (:target-profiles claim))
       (every? keyword? (:target-profiles claim))
       (sha256? (:conformance-sha256 claim))
       (sha256? (:runtime-sha256 claim))
       (string? (:ci-run-url claim)) (<= 1 (count (:ci-run-url claim)) 2048)
       (integer? (:tested-at claim)) (pos? (:tested-at claim))
       (integer? (:expires claim)) (< (:tested-at claim) (:expires claim))))

(defn sign [claim key]
  (when-not (valid-claim? claim)
    (reject! "coverage evidence claim rejected" :coverage))
  (when-not (signing/valid-key? key)
    (reject! "coverage evidence signing key rejected" :sign))
  (let [statement (assoc claim :format :kotoba.coverage-evidence-statement/v1
                         :signer (:signer key) :public-key (:public-key key))]
    {:format :kotoba.signed-coverage-evidence/v1
     :statement statement
     :signature (signing/sign-value key statement)}))

(defn verify [envelope trust now]
  (when-not (and (map? envelope) (= envelope-keys (set (keys envelope)))
                 (= :kotoba.signed-coverage-evidence/v1 (:format envelope)))
    (reject! "coverage evidence envelope rejected" :coverage))
  (signing/validate-trust! trust)
  (let [{:keys [statement signature]} envelope
        claim (select-keys statement claim-keys)
        {:keys [signer public-key tested-at expires]} statement
        digest (artifact/sha256 statement)]
    (when-not (and (map? statement) (= statement-keys (set (keys statement)))
                   (= :kotoba.coverage-evidence-statement/v1 (:format statement))
                   (valid-claim? claim)
                   (= signer (signing/signer-id public-key))
                   (string? signature))
      (reject! "coverage evidence statement rejected" :coverage))
    (when-not (signing/verify-value public-key statement signature)
      (reject! "coverage evidence signature rejected" :signature))
    (when-not (contains? (:trusted-signers trust) signer)
      (reject! "coverage evidence signer is not trusted" :trust))
    (when (contains? (:revoked-signers trust) signer)
      (reject! "coverage evidence signer is revoked" :trust))
    (when (contains? (:revoked-artifacts trust) digest)
      (reject! "coverage evidence is revoked" :trust))
    (when (< now tested-at)
      (reject! "coverage evidence is not yet valid" :trust))
    (when (>= now expires)
      (reject! "coverage evidence is expired" :trust))
    {:digest digest :platform (:platform statement) :paths (:paths statement)
     :target-profiles (:target-profiles statement) :signer signer}))

(defn verify-bundle [bundle trust now]
  (when-not (and (vector? bundle) (<= (count bundle) 256))
    (reject! "coverage evidence bundle rejected" :coverage))
  (let [verified (mapv #(verify % trust now) bundle)]
    (when-not (= (count verified) (count (set (map :digest verified))))
      (reject! "duplicate coverage evidence rejected" :coverage))
    verified))
