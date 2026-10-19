(ns kotoba.compiler.cache
  (:require [kotoba.artifact.core :as artifact]
            [kotoba.compiler.provenance :as provenance]
            [kotoba.verifier.signing :as signing]
            [kotoba.verifier :as verifier]))

(def entry-schema :kotoba.incremental-cache/v1)
(def statement-schema :kotoba.incremental-cache-statement/v1)
(def ^:private byte-array-class (Class/forName "[B"))
(def ^:private max-canonical-bytes (* 64 1024 1024))

(defn- reject [reason message]
  (throw (ex-info message {:phase :cache :reason reason})))
(defn- sha256? [value]
  (and (string? value) (boolean (re-matches #"[0-9a-f]{64}" value))))

(defn- freeze [value]
  (cond
    (= byte-array-class (class value))
    {:kotoba.cache/format :bytes-v1
     :kotoba.cache/value (mapv #(bit-and (int %) 0xff) value)}
    (map? value) (into (empty value) (map (fn [[k v]] [(freeze k) (freeze v)])) value)
    (vector? value) (mapv freeze value)
    (set? value) (set (map freeze value))
    (sequential? value) {:kotoba.cache/format :list-v1
                         :kotoba.cache/value (mapv freeze value)}
    :else value))

(defn- thaw [value]
  (cond
    (and (map? value)
         (= #{:kotoba.cache/format :kotoba.cache/value} (set (keys value)))
         (= :bytes-v1 (:kotoba.cache/format value))
         (vector? (:kotoba.cache/value value))
         (every? #(and (integer? %) (<= 0 % 255)) (:kotoba.cache/value value)))
    (byte-array (map unchecked-byte (:kotoba.cache/value value)))
    (and (map? value)
         (= #{:kotoba.cache/format :kotoba.cache/value} (set (keys value)))
         (= :list-v1 (:kotoba.cache/format value))
         (vector? (:kotoba.cache/value value)))
    (apply list (map thaw (:kotoba.cache/value value)))
    (map? value) (into (empty value) (map (fn [[k v]] [(thaw k) (thaw v)])) value)
    (vector? value) (mapv thaw value)
    (set? value) (set (map thaw value))
    :else value))

(defn store [source policy build-metadata result key not-before expires]
  (when-not (and (signing/valid-key? key) (integer? not-before) (integer? expires)
                 (< not-before expires))
    (reject :invalid-signer "cache signer or validity interval rejected"))
  (provenance/verify! source policy build-metadata result)
  (let [payload (freeze result)
        payload-bytes (artifact/canonical-bytes payload)]
    (when (> (alength payload-bytes) max-canonical-bytes)
      (reject :oversized "cache payload exceeds canonical byte limit"))
    (let [statement {:format statement-schema
                     :compiler "kotoba-compiler/1"
                     :target (:target result)
                     :provenance-sha256 (get-in result [:provenance :sha256])
                     :payload-sha256 (artifact/sha256 payload)
                     :signer (:signer key) :public-key (:public-key key)
                     :not-before not-before :expires expires}]
      {:format entry-schema :statement statement :payload payload
       :signature (signing/sign-value key statement)})))

(defn admit! [source target policy build-metadata envelope trust now]
  (signing/validate-trust! trust)
  (let [statement (:statement envelope)
        expected-statement-fields
        #{:format :compiler :target :provenance-sha256 :payload-sha256
          :signer :public-key :not-before :expires}]
    (when-not (and (= #{:format :statement :payload :signature} (set (keys envelope)))
                   (= entry-schema (:format envelope))
                   (map? statement)
                   (= expected-statement-fields (set (keys statement)))
                   (= statement-schema (:format statement))
                   (= "kotoba-compiler/1" (:compiler statement))
                   (= target (:target statement))
                   (every? sha256? [(:provenance-sha256 statement) (:payload-sha256 statement)])
                   (= (:payload-sha256 statement) (artifact/sha256 (:payload envelope)))
                   (= (:signer statement) (signing/signer-id (:public-key statement)))
                   (integer? (:not-before statement)) (integer? (:expires statement))
                   (< (:not-before statement) (:expires statement))
                   (string? (:signature envelope)))
      (reject :schema "cache envelope schema or identity rejected"))
    (when-not (signing/verify-value (:public-key statement) statement (:signature envelope))
      (reject :signature "cache signature rejected"))
    (when-not (contains? (:trusted-signers trust) (:signer statement))
      (reject :untrusted "cache signer is not trusted"))
    (when (contains? (:revoked-signers trust) (:signer statement))
      (reject :revoked "cache signer is revoked"))
    (when (or (contains? (:revoked-artifacts trust) (:payload-sha256 statement))
              (contains? (:revoked-artifacts trust) (:provenance-sha256 statement)))
      (reject :revoked "cache payload or provenance is revoked"))
    (when (or (< now (:not-before statement)) (>= now (:expires statement)))
      (reject :expired "cache entry is outside its validity interval"))
    (let [result (thaw (:payload envelope))]
      (when-not (= (:provenance-sha256 statement) (get-in result [:provenance :sha256]))
        (reject :identity "cache provenance identity rejected"))
      (provenance/verify! source policy build-metadata result)
      (when (= :kexe/v1 (:format result)) (verifier/verify-artifact! (:artifact result)))
      {:hit? true :signer (:signer statement) :result result})))
