(ns asn1.oid
  "The object identifiers the signing stack needs, by name.

  A registry rather than literals at each use site, for one reason that is worth
  more than tidiness: an OID typo is **silent**. `1.2.840.113549.1.1.11` and
  `1.2.840.113549.1.1.5` are `sha256WithRSA` and `sha1WithRSA`, and a verifier
  that compares against the wrong one either refuses every valid signature or
  accepts a digest algorithm it meant to have retired. Named once, a typo is a
  missing key, and a missing key is an error.

  `describe` exists for the other direction: an error message that says
  `unsupported algorithm 1.2.840.113549.1.1.4` sends a reader to a search
  engine, and one that says `md5WithRSA` does not."
  (:require [clojure.string :as str]))

(def oids
  "name → dotted string. Grouped by what each group is for."
  {;; ── digests (NIST) ──────────────────────────────────────────────────────
   :sha1 "1.3.14.3.2.26"
   :sha256 "2.16.840.1.101.3.4.2.1"
   :sha384 "2.16.840.1.101.3.4.2.2"
   :sha512 "2.16.840.1.101.3.4.2.3"

   ;; ── signature algorithms (PKCS#1 / X9.62) ───────────────────────────────
   :rsa-encryption "1.2.840.113549.1.1.1"
   :sha1-with-rsa "1.2.840.113549.1.1.5"
   :sha256-with-rsa "1.2.840.113549.1.1.11"
   :sha384-with-rsa "1.2.840.113549.1.1.12"
   :sha512-with-rsa "1.2.840.113549.1.1.13"
   :rsassa-pss "1.2.840.113549.1.1.10"
   :ec-public-key "1.2.840.10045.2.1"
   :ecdsa-with-sha256 "1.2.840.10045.4.3.2"
   :ecdsa-with-sha384 "1.2.840.10045.4.3.3"
   :ecdsa-with-sha512 "1.2.840.10045.4.3.4"
   :prime256v1 "1.2.840.10045.3.1.7"
   :secp384r1 "1.3.132.0.34"
   :ed25519 "1.3.101.112"

   ;; ── CMS (RFC 5652) ──────────────────────────────────────────────────────
   :data "1.2.840.113549.1.7.1"
   :signed-data "1.2.840.113549.1.7.2"
   :content-type "1.2.840.113549.1.9.3"
   :message-digest "1.2.840.113549.1.9.4"
   :signing-time "1.2.840.113549.1.9.5"
   :counter-signature "1.2.840.113549.1.9.6"
   :cms-algorithm-protection "1.2.840.113549.1.9.52"
   :signing-certificate-v2 "1.2.840.113549.1.9.16.2.47"

   ;; ── RFC 3161 timestamping ───────────────────────────────────────────────
   :ct-tst-info "1.2.840.113549.1.9.16.1.4"
   :signature-time-stamp-token "1.2.840.113549.1.9.16.2.14"
   :kp-time-stamping "1.3.6.1.5.5.7.3.8"

   ;; ── X.509 (RFC 5280) ────────────────────────────────────────────────────
   :basic-constraints "2.5.29.19"
   :key-usage "2.5.29.15"
   :extended-key-usage "2.5.29.37"
   :subject-key-identifier "2.5.29.14"
   :authority-key-identifier "2.5.29.35"
   :subject-alt-name "2.5.29.17"
   :crl-distribution-points "2.5.29.31"
   :certificate-policies "2.5.29.32"

   ;; ── X.520 naming attributes ─────────────────────────────────────────────
   :common-name "2.5.4.3"
   :surname "2.5.4.4"
   :serial-number "2.5.4.5"
   :country-name "2.5.4.6"
   :locality-name "2.5.4.7"
   :state-or-province-name "2.5.4.8"
   :organization-name "2.5.4.10"
   :organizational-unit-name "2.5.4.11"
   :given-name "2.5.4.42"
   :email-address "1.2.840.113549.1.9.1"

   ;; ── RFC 4998 evidence record syntax ─────────────────────────────────────
   :ers-evidence-record "1.2.840.113549.1.9.16.2.49"

   ;; ── JPKI（公的個人認証サービス）─────────────────────────────────────────
   ;; 1.2.392.200149.8.5 は総務省 JPKI の arc。下位は署名用証明書の
   ;; 基本4情報（氏名・生年月日・性別・住所）を運ぶ subjectAltName otherName で、
   ;; **値は個人情報そのもの**なので、この registry は識別子だけを持ち、
   ;; 読み出しは呼び出し側の明示的な要求に限る（org-jpki 側の決定）。
   :jpki-arc "1.2.392.200149.8.5"
   :jpki-basic-four-name "1.2.392.200149.8.5.5.1"
   :jpki-basic-four-birth-date "1.2.392.200149.8.5.5.2"
   :jpki-basic-four-sex "1.2.392.200149.8.5.5.3"
   :jpki-basic-four-address "1.2.392.200149.8.5.5.4"})

(def by-dotted
  "dotted string → name, for `describe` and for matching a parsed OID."
  (into {} (map (fn [[k v]] [v k])) oids))

(defn dotted
  "The dotted string for a named OID. Throws on an unknown name, which is the
  whole point — a mistyped keyword must not become nil and then match nothing."
  [name-kw]
  (or (get oids name-kw)
      (throw (ex-info (str "unknown OID name: " name-kw)
                      {:type :asn1/unknown-oid-name
                       :name name-kw
                       :known (vec (sort (map str (keys oids))))}))))

(defn named
  "The name for a dotted string, or nil when it is not one this knows."
  [dotted-string]
  (get by-dotted dotted-string))

(defn describe
  "A dotted OID rendered for a human: `sha256WithRSA (1.2.840.113549.1.1.11)`
  when known, the dotted form alone when not."
  [dotted-string]
  (if-let [n (named dotted-string)]
    (str (str/replace (str n) #"^:" "") " (" dotted-string ")")
    dotted-string))

(defn is?
  "Whether `dotted-string` is the named OID. Reads at the call site as
  `(oid/is? alg :sha256-with-rsa)` rather than a string comparison whose
  correctness depends on the literal beside it."
  [dotted-string name-kw]
  (= dotted-string (dotted name-kw)))

(def digest-algorithms
  "Digest OID name → the algorithm name a platform provider wants, and the
  digest length in bytes. The length is here because a `messageImprint` whose
  digest length does not match its stated algorithm is a mismatch worth naming
  before any hashing happens."
  {:sha1 {:jca "SHA-1" :length 20}
   :sha256 {:jca "SHA-256" :length 32}
   :sha384 {:jca "SHA-384" :length 48}
   :sha512 {:jca "SHA-512" :length 64}})

(def signature-algorithms
  "Signature OID name → what it is made of. A verifier needs all three parts and
  guessing any of them from the others is how `ecdsa-with-sha256` ends up
  verified as RSA."
  {:sha1-with-rsa {:digest :sha1 :key :rsa :jca "SHA1withRSA"}
   :sha256-with-rsa {:digest :sha256 :key :rsa :jca "SHA256withRSA"}
   :sha384-with-rsa {:digest :sha384 :key :rsa :jca "SHA384withRSA"}
   :sha512-with-rsa {:digest :sha512 :key :rsa :jca "SHA512withRSA"}
   :ecdsa-with-sha256 {:digest :sha256 :key :ec :jca "SHA256withECDSA"}
   :ecdsa-with-sha384 {:digest :sha384 :key :ec :jca "SHA384withECDSA"}
   :ecdsa-with-sha512 {:digest :sha512 :key :ec :jca "SHA512withECDSA"}})

(def ^:private retired
  "Algorithms that exist on the wire and must not be honoured.

  Listed so a refusal can say WHY. A verifier that simply fails to find
  `md5WithRSA` in `signature-algorithms` reports 'unsupported', which reads like
  a gap in this library rather than a signature nobody should accept."
  {:sha1-with-rsa "SHA-1 は衝突が実証済み（SHAttered, 2017）。既存署名の検証のためだけに残す。"
   :sha1 "SHA-1 は衝突が実証済み。新規の digest には使わない。"})

(defn retirement-note
  "Why this algorithm should not be used, or nil."
  [name-kw]
  (get retired name-kw))
