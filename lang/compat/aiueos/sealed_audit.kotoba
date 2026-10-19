(ns aiueos.sealed-audit
  "Encrypted, integrity-chained audit storage for production profiles.

  Keys are supplied by id through a keyring and are never serialized beside
  the log. Each record is independently AES-256-GCM sealed and binds its
  sequence number, key id, and predecessor hash as authenticated data. The
  outer SHA-256 chain makes reordering/replacement diagnostics deterministic;
  GCM supplies cryptographic authenticity and confidentiality. Detecting
  suffix truncation requires an externally retained signed head/checkpoint."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.nio.charset StandardCharsets]
           [java.nio.file Files StandardCopyOption]
           [java.security KeyFactory KeyPairGenerator MessageDigest
            PrivateKey PublicKey SecureRandom Signature]
           [java.security.spec X509EncodedKeySpec]
           [java.util Base64]
           [javax.crypto AEADBadTagException Cipher]
           [javax.crypto.spec GCMParameterSpec SecretKeySpec]))

(def format-version 1)
(def ^:private zero-hash (apply str (repeat 64 "0")))
(def ^:private rng (SecureRandom.))
(def ^:private io-lock (Object.))

(defn generate-key
  "Generate a fresh 256-bit AES key. Key custody/persistence belongs to the
  caller's KMS/HSM adapter, not the audit directory."
  []
  (let [key (byte-array 32)]
    (.nextBytes rng key)
    key))

(defn- utf8-bytes [value]
  (.getBytes (str value) StandardCharsets/UTF_8))

(defn- b64-encode [^bytes value]
  (.encodeToString (Base64/getEncoder) value))

(defn- b64-decode [value]
  (.decode (Base64/getDecoder) ^String value))

(defn- hex [^bytes value]
  (apply str (map #(format "%02x" (bit-and (int %) 0xff)) value)))

(defn- sha256-bytes [^bytes value]
  (hex (.digest (MessageDigest/getInstance "SHA-256") value)))

(defn- sha256 [value]
  (sha256-bytes (utf8-bytes value)))

(defn- fail! [kind message data]
  (throw (ex-info message (merge {:type :sealed-audit-failure :kind kind} data))))

(defn- resolve-key [keyring key-id]
  (let [key (get keyring key-id)]
    (when-not key
      (fail! :missing-key "sealed audit key is unavailable" {:key-id key-id}))
    (when-not (= 32 (alength ^bytes key))
      (fail! :invalid-key "sealed audit requires a 256-bit AES key"
             {:key-id key-id :actual-bytes (alength ^bytes key)}))
    key))

(defn- aad [{:keys [seq key-id previous-hash]}]
  (pr-str (array-map :format/version format-version
                     :seq seq :key-id key-id :previous-hash previous-hash)))

(defn- seal [key metadata plaintext]
  (let [nonce (byte-array 12)
        cipher (Cipher/getInstance "AES/GCM/NoPadding")]
    (.nextBytes rng nonce)
    (.init cipher Cipher/ENCRYPT_MODE
           (SecretKeySpec. key "AES")
           (GCMParameterSpec. 128 nonce))
    (.updateAAD cipher (utf8-bytes (aad metadata)))
    {:nonce (b64-encode nonce)
     :ciphertext (b64-encode (.doFinal cipher (utf8-bytes plaintext)))}))

(defn- open [key record]
  (let [cipher (Cipher/getInstance "AES/GCM/NoPadding")]
    (try
      (.init cipher Cipher/DECRYPT_MODE
             (SecretKeySpec. key "AES")
             (GCMParameterSpec. 128 (b64-decode (:nonce record))))
      (.updateAAD cipher (utf8-bytes (aad record)))
      (String. (.doFinal cipher (b64-decode (:ciphertext record)))
               StandardCharsets/UTF_8)
      (catch AEADBadTagException _
        (fail! :authentication-failed
               "sealed audit record authentication failed"
               {:seq (:seq record) :key-id (:key-id record)})))))

(defn- record-hash [record]
  (sha256 (pr-str (dissoc record :record-hash))))

(defn- parse-lines [path]
  (let [file (io/file path)]
    (if-not (.exists file)
      []
      (with-open [reader (io/reader file)]
        (mapv (fn [index line]
                (when (str/blank? line)
                  (fail! :invalid-format "blank line in sealed audit log"
                         {:line (inc index)}))
                (try
                  (edn/read-string line)
                  (catch Exception error
                    (fail! :invalid-format "invalid sealed audit EDN"
                           {:line (inc index) :cause (.getMessage error)}))))
              (range)
              (line-seq reader))))))

(defn read!
  "Verify and decrypt all records. Any malformed record, missing key,
  sequence discontinuity, chain break, or GCM failure throws."
  [path keyring]
  (loop [records (parse-lines path)
         expected-seq 0
         previous-hash zero-hash
         entries []]
    (if-let [record (first records)]
      (do
        (when-not (map? record)
          (fail! :invalid-format "sealed audit record must be a map"
                 {:seq expected-seq}))
        (when-not (= format-version (:format/version record))
          (fail! :unsupported-version "unsupported sealed audit format"
                 {:seq expected-seq :version (:format/version record)}))
        (when-not (= expected-seq (:seq record))
          (fail! :sequence-break "sealed audit sequence is discontinuous"
                 {:expected expected-seq :actual (:seq record)}))
        (when-not (= previous-hash (:previous-hash record))
          (fail! :chain-break "sealed audit predecessor hash mismatch"
                 {:seq expected-seq}))
        (when-not (= (record-hash record) (:record-hash record))
          (fail! :record-hash-mismatch "sealed audit record hash mismatch"
                 {:seq expected-seq}))
        (let [plaintext (open (resolve-key keyring (:key-id record)) record)
              entry (try
                      (edn/read-string plaintext)
                      (catch Exception error
                        (fail! :invalid-plaintext "decrypted audit entry is invalid EDN"
                               {:seq expected-seq :cause (.getMessage error)})))]
          (recur (next records)
                 (inc expected-seq)
                 (:record-hash record)
                 (conj entries entry))))
      entries)))

(defn generate-head-signing-key-pair []
  (.generateKeyPair (KeyPairGenerator/getInstance "Ed25519")))

(defn head-public-key-base64 [key-pair]
  (.encodeToString (Base64/getEncoder) (.getEncoded (.getPublic key-pair))))

(defn chain-head
  "Return the externally retainable head after verifying the complete log."
  [path keyring]
  (read! path keyring)
  (let [records (parse-lines path)
        file (io/file path)]
    {:head/version 1
     :record-count (count records)
     :last-record-hash (or (:record-hash (peek records)) zero-hash)
     :log-sha256 (if (.exists file)
                   (sha256-bytes (Files/readAllBytes (.toPath file)))
                   (sha256-bytes (byte-array 0)))}))

(defn- head-bytes [head]
  (utf8-bytes
   (pr-str (array-map
            :head/version (:head/version head)
            :record-count (:record-count head)
            :last-record-hash (:last-record-hash head)
            :log-sha256 (:log-sha256 head)))))

(defn sign-chain-head
  "Sign a chain head with an externally held Ed25519 key."
  [head ^PrivateKey private-key]
  (let [signer (Signature/getInstance "Ed25519")]
    (.initSign signer private-key)
    (.update signer (head-bytes head))
    (assoc head :signature
           (.encodeToString (Base64/getEncoder) (.sign signer)))))

(defn- decode-head-public-key [value]
  (if (instance? PublicKey value)
    value
    (.generatePublic
     (KeyFactory/getInstance "Ed25519")
     (X509EncodedKeySpec. (.decode (Base64/getDecoder) ^String value)))))

(defn verify-chain-head!
  "Verify the head signature, then compare it to the current verified log.
  Detects valid-prefix/suffix truncation that the internal predecessor chain
  alone cannot detect."
  [path keyring signed-head public-key]
  (let [valid-signature?
        (try
          (let [verifier (Signature/getInstance "Ed25519")]
            (.initVerify verifier (decode-head-public-key public-key))
            (.update verifier (head-bytes signed-head))
            (.verify verifier
                     (.decode (Base64/getDecoder)
                              ^String (:signature signed-head))))
          (catch Exception _ false))]
    (when-not valid-signature?
      (fail! :invalid-chain-head-signature
             "sealed audit chain-head signature is invalid" {}))
    (let [actual (chain-head path keyring)
          expected (dissoc signed-head :signature)]
      (when-not (= expected actual)
        (fail! :chain-head-mismatch
               "sealed audit log does not match retained chain head"
               {:expected expected :actual actual}))
      actual)))

(defn read-with-chain-head!
  [path keyring signed-head public-key]
  (verify-chain-head! path keyring signed-head public-key)
  (read! path keyring))

(defn append!
  "Verify the complete existing chain, then append one sealed record. The
  selected key must be present as a 32-byte value under key-id."
  [path keyring key-id entry]
  (locking io-lock
    (read! path keyring)
    (let [records (parse-lines path)
          seq (count records)
          previous-hash (or (:record-hash (peek records)) zero-hash)
          metadata {:seq seq :key-id key-id :previous-hash previous-hash}
          encrypted (seal (resolve-key keyring key-id) metadata (pr-str entry))
          record (merge (array-map :format/version format-version)
                        metadata encrypted)
          record (assoc record :record-hash (record-hash record))
          file (io/file path)]
      (when-let [parent (.getParentFile file)]
        (.mkdirs parent))
      (with-open [writer (io/writer file :append true)]
        (.write writer (pr-str record))
        (.write writer "\n"))
      record)))

(defn- replace-log! [path keyring key-id entries]
  (let [target (io/file path)
        parent (or (.getParentFile target) (io/file "."))
        temp (java.io.File/createTempFile "sealed-audit-" ".edn" parent)]
    (try
      (doseq [entry entries]
        (append! temp keyring key-id entry))
      (Files/move (.toPath temp) (.toPath target)
                  (into-array StandardCopyOption
                              [StandardCopyOption/REPLACE_EXISTING
                               StandardCopyOption/ATOMIC_MOVE]))
      (catch java.nio.file.AtomicMoveNotSupportedException _
        (Files/move (.toPath temp) (.toPath target)
                    (into-array StandardCopyOption
                                [StandardCopyOption/REPLACE_EXISTING])))
      (finally
        (when (.exists temp) (.delete temp))))))

(defn prune!
  "Apply an explicit retention cutoff and atomically reseal retained entries.
  Entries lacking a numeric :aiueos/ts fail closed instead of being deleted."
  [path keyring key-id cutoff-epoch-secs]
  (locking io-lock
    (let [entries (read! path keyring)
          invalid (keep-indexed
                   (fn [index entry]
                     (when-not (number? (:aiueos/ts entry)) index))
                   entries)]
      (when (seq invalid)
        (fail! :missing-retention-timestamp
               "cannot apply retention to entries without timestamps"
               {:indexes (vec invalid)}))
      (let [retained (filterv #(>= (:aiueos/ts %) cutoff-epoch-secs) entries)]
        (replace-log! path keyring key-id retained)
        {:deleted (- (count entries) (count retained))
         :retained (count retained)
         :cutoff-epoch-secs cutoff-epoch-secs}))))

(defn backup!
  "Copy the encrypted bytes to backup-path and return its SHA-256 digest."
  [path backup-path]
  (let [source (io/file path)
        target (io/file backup-path)]
    (when-not (.exists source)
      (fail! :missing-source "sealed audit source does not exist" {:path (str path)}))
    (when-let [parent (.getParentFile target)] (.mkdirs parent))
    (Files/copy (.toPath source) (.toPath target)
                (into-array StandardCopyOption
                            [StandardCopyOption/REPLACE_EXISTING]))
    (sha256-bytes (Files/readAllBytes (.toPath target)))))

(defn restore!
  "Verify a backup completely before replacing the active log."
  [backup-path path keyring]
  (read! backup-path keyring)
  (let [source (io/file backup-path)
        target (io/file path)]
    (when-let [parent (.getParentFile target)] (.mkdirs parent))
    (Files/copy (.toPath source) (.toPath target)
                (into-array StandardCopyOption
                            [StandardCopyOption/REPLACE_EXISTING]))
    (read! path keyring)))
