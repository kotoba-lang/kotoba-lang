(ns aiueos.sealed-state
  "AES-256-GCM component-state snapshots with external key custody,
  monotonic versions, bounded files, verified backup/restore and deletion."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import [java.nio.charset StandardCharsets]
           [java.nio.file Files StandardCopyOption]
           [java.security SecureRandom]
           [java.util Base64]
           [javax.crypto AEADBadTagException Cipher]
           [javax.crypto.spec GCMParameterSpec SecretKeySpec]))

(def format-version 1)
(def max-plaintext-bytes (* 1024 1024))
(def max-snapshot-bytes (* 2 1024 1024))
(def ^:private rng (SecureRandom.))
(def ^:private io-lock (Object.))
(def ^:private snapshot-keys
  #{:format/version :component :state-version :key-id :created-at-ms
    :nonce :ciphertext})

(defn generate-key []
  (let [key (byte-array 32)]
    (.nextBytes rng key)
    key))

(defn- fail! [kind message data]
  (throw (ex-info message
                  (merge {:type :sealed-state-failure :kind kind} data))))

(defn- resolve-key [keyring key-id]
  (let [key (get keyring key-id)]
    (when-not key
      (fail! :missing-key "component state key is unavailable"
             {:key-id key-id}))
    (when-not (= 32 (alength ^bytes key))
      (fail! :invalid-key "component state requires a 256-bit AES key"
             {:key-id key-id}))
    key))

(defn- utf8 [value]
  (.getBytes (str value) StandardCharsets/UTF_8))

(defn- b64-encode [^bytes value]
  (.encodeToString (Base64/getEncoder) value))

(defn- b64-decode [value]
  (.decode (Base64/getDecoder) ^String value))

(defn- aad [snapshot]
  (pr-str
   (array-map
    :format/version (:format/version snapshot)
    :component (:component snapshot)
    :state-version (:state-version snapshot)
    :key-id (:key-id snapshot)
    :created-at-ms (:created-at-ms snapshot))))

(defn- read-snapshot [path]
  (let [file (io/file path)]
    (when-not (.exists file)
      (fail! :missing-snapshot "component state snapshot does not exist"
             {:path (str path)}))
    (when (> (.length file) max-snapshot-bytes)
      (fail! :snapshot-too-large "component state snapshot exceeds bound"
             {:actual-bytes (.length file)
              :maximum-bytes max-snapshot-bytes}))
    (let [snapshot (try
                     (edn/read-string (slurp file))
                     (catch Exception error
                       (fail! :invalid-format "invalid component state EDN"
                              {:cause (.getMessage error)})))]
      (when-not (and (map? snapshot)
                     (= snapshot-keys (set (keys snapshot)))
                     (= format-version (:format/version snapshot))
                     (keyword? (:component snapshot))
                     (pos-int? (:state-version snapshot))
                     (keyword? (:key-id snapshot))
                     (nat-int? (:created-at-ms snapshot))
                     (string? (:nonce snapshot))
                     (string? (:ciphertext snapshot)))
        (fail! :invalid-format "component state snapshot shape is invalid" {}))
      snapshot)))

(defn restore!
  "Authenticate and restore a snapshot for EXPECTED-COMPONENT at or above
  MINIMUM-VERSION. A lower version is rollback and fails before release."
  [path keyring expected-component minimum-version]
  (let [snapshot (read-snapshot path)]
    (when-not (= expected-component (:component snapshot))
      (fail! :wrong-component "component state identity mismatch"
             {:expected expected-component :actual (:component snapshot)}))
    (when (< (:state-version snapshot) minimum-version)
      (fail! :rollback "component state version is below monotonic minimum"
             {:minimum minimum-version
              :actual (:state-version snapshot)}))
    (let [cipher (Cipher/getInstance "AES/GCM/NoPadding")
          plaintext
          (try
            (.init cipher Cipher/DECRYPT_MODE
                   (SecretKeySpec.
                    (resolve-key keyring (:key-id snapshot)) "AES")
                   (GCMParameterSpec. 128 (b64-decode (:nonce snapshot))))
            (.updateAAD cipher (utf8 (aad snapshot)))
            (.doFinal cipher (b64-decode (:ciphertext snapshot)))
            (catch AEADBadTagException _
              (fail! :authentication-failed
                     "component state authentication failed"
                     {:component expected-component
                      :state-version (:state-version snapshot)})))]
      (when (> (alength plaintext) max-plaintext-bytes)
        (fail! :plaintext-too-large "component state plaintext exceeds bound"
               {:actual-bytes (alength plaintext)
                :maximum-bytes max-plaintext-bytes}))
      (let [state (try
                    (edn/read-string
                     (String. plaintext StandardCharsets/UTF_8))
                    (catch Exception error
                      (fail! :invalid-plaintext
                             "component state plaintext is invalid EDN"
                             {:cause (.getMessage error)})))]
        (when-not (map? state)
          (fail! :invalid-plaintext
                 "component state plaintext must be a map" {}))
        {:component expected-component
         :state-version (:state-version snapshot)
         :key-id (:key-id snapshot)
         :created-at-ms (:created-at-ms snapshot)
         :state state}))))

(defn- atomic-write! [path content]
  (let [target (io/file path)
        parent (or (.getParentFile target) (io/file "."))
        _ (.mkdirs parent)
        temp (java.io.File/createTempFile "sealed-state-" ".edn" parent)]
    (try
      (spit temp content)
      (try
        (Files/move (.toPath temp) (.toPath target)
                    (into-array StandardCopyOption
                                [StandardCopyOption/REPLACE_EXISTING
                                 StandardCopyOption/ATOMIC_MOVE]))
        (catch java.nio.file.AtomicMoveNotSupportedException _
          (Files/move (.toPath temp) (.toPath target)
                      (into-array StandardCopyOption
                                  [StandardCopyOption/REPLACE_EXISTING]))))
      (finally
        (when (.exists temp) (.delete temp))))))

(defn seal!
  "Seal STATE at a strictly increasing STATE-VERSION."
  [path keyring key-id component state-version created-at-ms state]
  (when-not (and (keyword? component)
                 (pos-int? state-version)
                 (nat-int? created-at-ms)
                 (map? state))
    (fail! :invalid-input "invalid component state snapshot input" {}))
  (let [plaintext (utf8 (pr-str state))]
    (when (> (alength plaintext) max-plaintext-bytes)
      (fail! :plaintext-too-large "component state plaintext exceeds bound"
             {:actual-bytes (alength plaintext)
              :maximum-bytes max-plaintext-bytes}))
    (locking io-lock
      (let [target (io/file path)]
        (when (.exists target)
          (let [current (restore! path keyring component 0)]
            (when-not (> state-version (:state-version current))
              (fail! :rollback
                     "component state version must increase"
                     {:current (:state-version current)
                      :requested state-version}))))
        (let [nonce (byte-array 12)
              metadata {:format/version format-version
                        :component component
                        :state-version state-version
                        :key-id key-id
                        :created-at-ms created-at-ms}
              cipher (Cipher/getInstance "AES/GCM/NoPadding")]
          (.nextBytes rng nonce)
          (.init cipher Cipher/ENCRYPT_MODE
                 (SecretKeySpec. (resolve-key keyring key-id) "AES")
                 (GCMParameterSpec. 128 nonce))
          (.updateAAD cipher (utf8 (aad metadata)))
          (let [snapshot (assoc metadata
                                :nonce (b64-encode nonce)
                                :ciphertext
                                (b64-encode (.doFinal cipher plaintext)))]
            (atomic-write! path (pr-str snapshot))
            (select-keys snapshot
                         [:component :state-version :key-id
                          :created-at-ms])))))))

(defn backup!
  "Verify before copying encrypted snapshot bytes."
  [path backup-path keyring component minimum-version]
  (let [receipt (restore! path keyring component minimum-version)
        target (io/file backup-path)]
    (when-let [parent (.getParentFile target)] (.mkdirs parent))
    (Files/copy (.toPath (io/file path)) (.toPath target)
                (into-array StandardCopyOption
                            [StandardCopyOption/REPLACE_EXISTING]))
    (select-keys receipt [:component :state-version :key-id])))

(defn restore-backup!
  "Verify backup before atomically replacing active snapshot."
  [backup-path path keyring component minimum-version]
  (let [receipt (restore! backup-path keyring component minimum-version)]
    (atomic-write! path (slurp backup-path))
    receipt))

(defn delete!
  "Authenticate the target before deleting it. Key deletion in the external
  keyring provides crypto-erasure independently of filesystem remanence."
  [path keyring component minimum-version]
  (let [receipt (restore! path keyring component minimum-version)
        deleted? (Files/deleteIfExists (.toPath (io/file path)))]
    {:deleted? deleted?
     :component component
     :state-version (:state-version receipt)
     :key-id (:key-id receipt)}))
