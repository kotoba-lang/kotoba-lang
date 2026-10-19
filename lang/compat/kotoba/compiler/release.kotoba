(ns kotoba.compiler.release
  (:require [kotoba.verifier.signing :as signing]
            [kotoba.kir.target :as target])
  (:import [java.io FileInputStream]
           [java.nio.charset StandardCharsets]
           [java.nio.file Files LinkOption Path Paths]
           [java.security MessageDigest]))

(def ^:private max-file-bytes (* 1024 1024 1024))
(defn- reject! [message data]
  (throw (ex-info message (merge {:phase :signature} data))))
(defn- hex [bytes]
  (apply str (map #(format "%02x" (bit-and (int %) 0xff)) bytes)))
(defn- regular-path [value]
  (when-not (and (string? value) (seq value)) (reject! "release file path is required" {}))
  (let [path (.toAbsolutePath (Paths/get value (make-array String 0)))]
    (when-not (and (Files/isRegularFile path (make-array LinkOption 0))
                   (not (Files/isSymbolicLink path)))
      (reject! "release file must be a non-symlink regular file" {:path value}))
    (when (> (Files/size path) max-file-bytes)
      (reject! "release file exceeds byte limit" {:limit max-file-bytes}))
    path))
(defn file-identity [value]
  (let [^Path path (regular-path value)
        digest (MessageDigest/getInstance "SHA-256")
        buffer (byte-array 65536)]
    (with-open [input (FileInputStream. (.toFile path))]
      (loop []
        (let [read (.read input buffer)]
          (when (pos? read) (.update digest buffer 0 read) (recur)))))
    (let [name (str (.getFileName path))]
      (when (or (> (count name) 255) (some #(Character/isISOControl ^Character %) name))
        (reject! "release filename is not SPDX-safe" {}))
      {:name name :sha256 (hex (.digest digest)) :size (Files/size path)})))
(defn- spdx-id [sha] (str "SPDXRef-File-" (subs sha 0 16)))
(defn sbom-bytes [artifact-path]
  (let [{:keys [name sha256 size]} (file-identity artifact-path)
        file-id (spdx-id sha256)
        text (str "SPDXVersion: SPDX-2.3\n"
                  "DataLicense: CC0-1.0\n"
                  "SPDXID: SPDXRef-DOCUMENT\n"
                  "DocumentName: kotoba-release-" (subs sha256 0 16) "\n"
                  "DocumentNamespace: https://kotoba-lang.org/spdx/" sha256 "\n"
                  "Creator: Tool: kotoba-compiler\n"
                  "Created: 1970-01-01T00:00:00Z\n\n"
                  "FileName: ./" name "\n"
                  "SPDXID: " file-id "\n"
                  "FileChecksum: SHA256: " sha256 "\n"
                  "FileSize: " size "\n"
                  "LicenseConcluded: NOASSERTION\n"
                  "CopyrightText: NOASSERTION\n\n"
                  "Relationship: SPDXRef-DOCUMENT DESCRIBES " file-id "\n")]
    (.getBytes text StandardCharsets/UTF_8)))
(defn- canonical-sbom! [artifact-path sbom-path]
  (let [^Path path (regular-path sbom-path)]
    (when (> (Files/size path) (* 1024 1024))
      (reject! "release SBOM exceeds byte limit" {:limit (* 1024 1024)}))
    (when-not (MessageDigest/isEqual (sbom-bytes artifact-path) (Files/readAllBytes path))
      (reject! "release SBOM is not canonical for artifact" {}))))
(defn- sha256? [value] (and (string? value) (boolean (re-matches #"[0-9a-f]{64}" value))))
(defn attest [artifact-path sbom-path target-name key not-before expires]
  (when-not (contains? target/profiles target-name)
    (reject! "release target is unsupported" {:target target-name}))
  (when-not (and (integer? not-before) (integer? expires) (< not-before expires))
    (reject! "release validity interval rejected" {}))
  (when-not (signing/valid-key? key) (reject! "release signing key rejected" {}))
  (canonical-sbom! artifact-path sbom-path)
  (let [statement {:format :kotoba.release-statement/v1
                   :subject (file-identity artifact-path)
                   :sbom (file-identity sbom-path)
                   :target target-name :target-profile (target/profile target-name)
                   :builder :kotoba-compiler/v1 :signer (:signer key)
                   :public-key (:public-key key)
                   :not-before not-before :expires expires}]
    {:format :kotoba.release-attestation/v1 :statement statement
     :signature (signing/sign-value key statement)}))
(defn verify! [envelope artifact-path sbom-path trust now]
  (signing/validate-trust! trust)
  (let [statement (:statement envelope)
        expected-fields #{:format :subject :sbom :target :target-profile :builder
                          :signer :public-key :not-before :expires}
        {:keys [subject sbom target target-profile signer public-key not-before expires]} statement]
    (when-not (and (= #{:format :statement :signature} (set (keys envelope)))
                   (= :kotoba.release-attestation/v1 (:format envelope))
                   (= expected-fields (set (keys statement)))
                   (= :kotoba.release-statement/v1 (:format statement))
                   (= :kotoba-compiler/v1 (:builder statement))
                   (contains? target/profiles target)
                   (= target-profile (target/profile target))
                   (= #{:name :sha256 :size} (set (keys subject)))
                   (= #{:name :sha256 :size} (set (keys sbom)))
                   (every? sha256? [(:sha256 subject) (:sha256 sbom)])
                   (every? #(and (integer? %) (<= 0 % max-file-bytes)) [(:size subject) (:size sbom)])
                   (= signer (signing/signer-id public-key))
                   (integer? not-before) (integer? expires) (< not-before expires)
                   (string? (:signature envelope)))
      (reject! "release attestation schema rejected" {}))
    (when-not (signing/verify-value public-key statement (:signature envelope))
      (reject! "release attestation signature rejected" {}))
    (when-not (contains? (:trusted-signers trust) signer) (reject! "release signer is not trusted" {}))
    (when (contains? (:revoked-signers trust) signer) (reject! "release signer is revoked" {}))
    (when (contains? (:revoked-artifacts trust) (:sha256 subject))
      (reject! "release artifact is revoked" {}))
    (when (or (< now not-before) (>= now expires)) (reject! "release attestation is outside validity" {:now now}))
    (when-not (= subject (file-identity artifact-path)) (reject! "release artifact identity mismatch" {}))
    (when-not (= sbom (file-identity sbom-path)) (reject! "release SBOM identity mismatch" {}))
    (canonical-sbom! artifact-path sbom-path)
    {:verified? true :target target :subject subject :sbom sbom :signer signer}))
