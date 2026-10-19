(ns storj.node.certificate
  "The two certificates a Storj identity is made of, as bytes.

  `storj.node.identity` reads a chain and decides whether to believe it; this
  writes the chain a node presents. Both halves are here for the same reason:
  what a Storj certificate must contain is a fact about the protocol, and a
  host that assembled it from an X.509 library's defaults would produce
  something subtly its own.

  ## What Storj actually mints

  `peertls.CATemplate` and `LeafTemplate` are short, and everything else is
  Go's `x509.CreateCertificate` filling in defaults. The parts that matter:

  - **Subject and issuer are both `O=Storj`** — on the CA *and* the leaf. That
    is not cosmetic. Go omits the authorityKeyIdentifier extension when the
    issuer and subject names are equal, which is why a real Storj leaf has
    three extensions rather than four, and why adding an AKI here would
    produce a certificate no Storj node has ever emitted.
  - **Validity is Go's zero time, twice** — `00010101000000Z`, year 1. Not an
    oversight on Storj's part: node certificates do not expire, and expiry is
    handled by the network rather than by X.509. It also means this namespace
    needs no clock.
  - **The CA carries a subjectKeyIdentifier and the leaf does not**, because
    Go computes one only for a CA.
  - **The identity version rides in extension `2.999.2.1` as a single raw
    byte**, not an ASN.1 INTEGER — the same asymmetry `storj.node.identity`
    reads back.

  ## Serial numbers

  128 random bits, and the INTEGER encoding adds a leading zero whenever the
  top bit is set, so a Storj serial is 16 or 17 bytes on the wire. Nothing
  reads it; it exists because X.509 requires it."
  (:require [storj.node.der :as der]))

(def organization "Storj")

(def zero-time
  "Go's zero time as a GeneralizedTime. Both `notBefore` and `notAfter`."
  "00010101000000Z")

(def serial-number-bytes 16)

(def oids
  {:organization-name       "2.5.4.10"
   :ecdsa-with-sha256       "1.2.840.10045.4.3.2"
   :key-usage               "2.5.29.15"
   :basic-constraints       "2.5.29.19"
   :subject-key-identifier  "2.5.29.14"
   :ext-key-usage           "2.5.29.37"
   :server-auth             "1.3.6.1.5.5.7.3.1"
   :client-auth             "1.3.6.1.5.5.7.3.2"
   :identity-version        "2.999.2.1"})

(def key-usage-bits
  "Bit positions in the KeyUsage BIT STRING, numbered from the most
  significant bit of the first octet — RFC 5280 §4.2.1.3."
  {:digital-signature 0
   :key-encipherment  2
   :key-cert-sign     5})

(defn- fail [msg data]
  (throw (ex-info (str "storj.node.certificate: " msg) data)))

;; ── pieces ──────────────────────────────────────────────────────────────────

(defn- storj-name []
  (der/encode-sequence
   (der/encode-set
    (der/encode-sequence (der/encode-oid (:organization-name oids))
                         (der/encode-printable-string organization)))))

(defn- ecdsa-sha256-algorithm []
  ;; ECDSA algorithm identifiers carry no parameters at all — not even the
  ;; explicit NULL that RSA uses. A NULL here parses, and then the signature
  ;; is over different bytes than the peer computed.
  (der/encode-sequence (der/encode-oid (:ecdsa-with-sha256 oids))))

(defn- validity []
  (der/encode-sequence (der/encode-generalized-time zero-time)
                       (der/encode-generalized-time zero-time)))

(defn- extension
  ([oid value] (extension oid value false))
  ([oid value critical?]
   (apply der/encode-sequence
          (concat [(der/encode-oid oid)]
                  (when critical? [(der/encode-boolean true)])
                  [(der/encode-octet-string value)]))))

(defn key-usage
  "A KeyUsage extension value for a set of usages.

  The unused-bit count is not padding: it is derived from the highest bit set,
  so `keyCertSign` alone encodes as one octet with two unused bits and
  `digitalSignature | keyEncipherment` as one with five. Emitting a fixed
  count produces a BIT STRING that decodes to different usages than intended."
  [usages]
  (let [bits (mapv #(or (key-usage-bits %) (fail "unknown key usage" {:usage %})) usages)
        top  (apply max bits)
        octs (reduce (fn [v b]
                       (update v (quot b 8) bit-or (bit-shift-right 0x80 (mod b 8))))
                     (vec (repeat (inc (quot top 8)) 0))
                     bits)]
    (der/encode-bit-string octs (- 7 (mod top 8)))))

(defn- basic-constraints [ca?]
  (if ca?
    (der/encode-sequence (der/encode-boolean true))
    (der/encode-sequence)))

(defn- ext-key-usage []
  (der/encode-sequence (der/encode-oid (:server-auth oids))
                       (der/encode-oid (:client-auth oids))))

(defn identity-version-extension
  "Extension `2.999.2.1`, whose value is the version as a bare byte."
  [version]
  (extension (:identity-version oids) [version]))

;; ── the certificates ────────────────────────────────────────────────────────

(defn- tbs
  [{:keys [serial spki-der extensions]}]
  (der/encode-sequence
   (der/encode-explicit 0 (der/encode-integer [2]))   ; v3
   (der/encode-integer serial)
   (ecdsa-sha256-algorithm)
   (storj-name)                                       ; issuer
   (validity)
   (storj-name)                                       ; subject
   (vec spki-der)
   ;; `[3] EXPLICIT Extensions`, and `Extensions` is itself a SEQUENCE OF —
   ;; two nested constructions, not one. Dropping the inner SEQUENCE produces
   ;; a certificate whose extensions parse as the *fields* of the first
   ;; extension, which is how this was first written and how it was caught.
   (der/encode-explicit 3 (apply der/encode-sequence extensions))))

(defn ca-tbs
  "The signed body of a Storj CA certificate.

  `subject-key-id` is passed in rather than computed: it is SHA-1 of the
  public key's BIT STRING contents, and hashing is the host's job — this
  namespace places bytes, it does not derive them."
  [{:keys [serial spki-der subject-key-id version]}]
  (tbs {:serial serial
        :spki-der spki-der
        :extensions [(extension (:key-usage oids) (key-usage [:key-cert-sign]) true)
                     (extension (:basic-constraints oids) (basic-constraints true) true)
                     (extension (:subject-key-identifier oids)
                                (der/encode-octet-string subject-key-id))
                     (identity-version-extension version)]}))

(defn leaf-tbs
  "The signed body of a Storj leaf certificate.

  No subjectKeyIdentifier and no authorityKeyIdentifier — the first because
  Go computes one only for a CA, the second because the issuer and subject
  names are identical."
  [{:keys [serial spki-der]}]
  (tbs {:serial serial
        :spki-der spki-der
        :extensions [(extension (:key-usage oids)
                                (key-usage [:digital-signature :key-encipherment]) true)
                     (extension (:ext-key-usage oids) (ext-key-usage))
                     (extension (:basic-constraints oids) (basic-constraints false) true)]}))

(defn certificate
  "A complete certificate: the signed body, the algorithm, and the signature.

  The algorithm appears twice in an X.509 certificate — inside the body and
  beside the signature — and the two are required to agree. Both come from
  here so they cannot drift."
  [tbs-der signature]
  (der/encode-sequence (vec tbs-der)
                       (ecdsa-sha256-algorithm)
                       (der/encode-bit-string signature)))

(defn public-key-bits
  "The subjectPublicKey BIT STRING contents of a DER SubjectPublicKeyInfo.

  For an EC key this is the uncompressed point, and it — not the whole
  SubjectPublicKeyInfo — is what a subjectKeyIdentifier hashes. The node id
  hashes the whole thing. Two hashes of two different slices of the same
  structure, which is a good reason to name the slice."
  [spki-der]
  (der/bit-string-bytes (second (der/children (der/parse spki-der)))))
