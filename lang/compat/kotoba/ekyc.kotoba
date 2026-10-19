(ns kotoba.ekyc
  "Non-face-to-face (非対面) identity-verification method catalog and
  structural verification-record validation -- pure data contracts.

  A kotoba-lang capability library seeded for cloud-itonami-isic-6493 (a
  factoring-business governed actor) and any other actor that needs to
  reject an unrecognized or evidentially-incomplete identity-verification
  method *before* accepting a client/debtor. No network, no I/O, no
  biometric matching, no liveness detection, no document forensics -- this
  library models the STRUCTURAL/LEGAL shape of a verification record that a
  real vendor integration would populate, not the biometric processing
  itself. Portable (.cljc) across JVM / ClojureScript / SCI / GraalVM.

  This namespace models a *different* layer than `ekyc.core`/`ekyc.model`
  in this same repo: those model a provider session lifecycle (host ports,
  evidence custody, VC issuance) for wiring to a real eKYC vendor.
  `kotoba.ekyc` models which method a specified business operator is
  legally allowed to rely on, and what evidence combination that method
  requires, independent of any provider -- the layer a `PolicyGovernor`
  checks structurally before a session's chosen method + evidence set is
  accepted as legally valid.

  Primary source (Japan, seed jurisdiction): 犯罪による収益の移転防止に関す
  る法律施行規則 (Act on Prevention of Transfer of Criminal Proceeds,
  Enforcement Regulation; commonly 犯収法施行規則) Article 6, Paragraph 1 --
  the enumerated 本人特定事項の確認方法 (identity-particulars confirmation
  methods), sub-items ホ/ヘ/ト/ル/カ/ワ/ヨ for natural-person customers and
  ロ/ホ for corporate customers. Verified against the current consolidated
  text via e-Gov 法令検索 (law id 420M60000F5A001, as amended, retrieved
  2026-07-14) and cross-checked against 金融庁 (FSA) reference material
  \"犯罪収益移転防止法におけるオンラインで完結可能な本人確認方法の概要\"
  and 警察庁 JAFIC guidance. A 2027-04-01 reform is scheduled to abolish
  the ホ (image-only) method and renumber the remaining letters -- this
  catalog models the law as currently in force; see docs/adr/0001.

  Secondary source (multi-jurisdiction consistency with the rest of this
  fleet's JPN/USA/GBR/DEU facts catalogs): NIST Special Publication
  800-63A-4, Digital Identity Guidelines: Identity Proofing and Enrollment
  (final, July 2025 -- the current revision, superseding 800-63A-3's IAL
  framework). `ial-approx` on each method is an HONEST, APPROXIMATE
  best-effort mapping onto NIST's Identity Assurance Levels (IAL1/IAL2/
  IAL3); the two frameworks classify identity evidence on different axes
  (犯収法 enumerates specific method *combinations*, NIST scores evidence by
  *strength category* -- FAIR/STRONG/SUPERIOR -- and proofing *attendance
  type*), so several methods do not map cleanly and are flagged as such
  rather than forced onto a level."
  (:require [clojure.set :as set]))

;; ---------------------------------------------------------------------------
;; Method catalog -- 犯収法施行規則 第6条第1項 (individual: 第1号, corporate: 第3号)
;;
;; :ekyc.method/required-evidence is a vector of OR-groups (each a set of
;; evidence kinds, any one of which satisfies that slot); ALL groups must be
;; satisfied -- i.e. AND-of-ORs, matching the statute's "A の画像 又は B の
;; 情報...を受けるとともに、C を行う" (A-or-B, together with C) shape.
;;
;; :ekyc.method/document-fields maps an evidence kind that carries printed or
;; encoded identity particulars to the fields the statute requires that kind
;; to expose. Kinds not listed (e.g. a bank-transfer confirmation) carry no
;; printed particulars of their own.
;; ---------------------------------------------------------------------------

(def method-catalog
  [{:ekyc.method/id                  :ho
    :ekyc.method/jurisdiction        "JPN"
    :ekyc.method/subject             :individual
    :ekyc.method/citation            "犯罪による収益の移転防止に関する法律施行規則 第6条第1項第1号ホ"
    :ekyc.method/citation-en         "APTCP Enforcement Regulation Art.6(1)(i)(ho)"
    :ekyc.method/label               "Photo-ID image + live facial image"
    :ekyc.method/summary             "Customer transmits, via the operator's specified software, a photo-ID image showing name/address/DOB/photo/thickness-and-other-features plus a live image of their face."
    :ekyc.method/required-evidence   [#{:id-document-image} #{:facial-image}]
    :ekyc.method/document-fields     {:id-document-image #{:name :address :dob :photo :thickness-features}}
    :ekyc.method/live-capture-required? true
    :ekyc.method/live-capture-kinds  #{:id-document-image :facial-image}
    :ekyc.method/scheduled-abolition "2027-04-01 reform abolishes this method (counterfeit-document risk); see docs/adr/0001"
    :ekyc.method/ial-approx          :ial1
    :ekyc.method/ial-note            "One piece of evidence only (no independent second document or cryptographic trust anchor); satisfies NIST IAL1's 1-STRONG-piece floor with an added (IAL1-optional) facial comparison, but falls short of IAL2's 2-piece/SUPERIOR minimum."}

   {:ekyc.method/id                  :he
    :ekyc.method/jurisdiction        "JPN"
    :ekyc.method/subject             :individual
    :ekyc.method/citation            "犯罪による収益の移転防止に関する法律施行規則 第6条第1項第1号ヘ"
    :ekyc.method/citation-en         "APTCP Enforcement Regulation Art.6(1)(i)(he)"
    :ekyc.method/label               "IC-chip read (photo ID) + live facial image"
    :ekyc.method/summary             "Customer transmits, via the operator's specified software, the semiconductor IC-chip data embedded in a photo ID (name/address/DOB/photo) plus a live image of their face."
    :ekyc.method/required-evidence   [#{:ic-chip-read} #{:facial-image}]
    :ekyc.method/document-fields     {:ic-chip-read #{:name :address :dob :photo}}
    :ekyc.method/live-capture-required? true
    :ekyc.method/live-capture-kinds  #{:facial-image}
    :ekyc.method/ial-approx          :ial2
    :ekyc.method/ial-note            "IC-chip data is issuer-signed and cryptographically validated to a trust anchor -- analogous to NIST SUPERIOR evidence (one piece sufficient for IAL2); facial comparison aligns with the IAL2 Biometric Pathway. Cannot reach IAL3: proofing is remote/software-mediated, and IAL3 requires on-site attended proofing regardless of evidence strength."}

   {:ekyc.method/id                  :to-1
    :ekyc.method/jurisdiction        "JPN"
    :ekyc.method/subject             :individual
    :ekyc.method/citation            "犯罪による収益の移転防止に関する法律施行規則 第6条第1項第1号ト(1)"
    :ekyc.method/citation-en         "APTCP Enforcement Regulation Art.6(1)(i)(to)(1)"
    :ekyc.method/label               "Single-issuance ID image/IC-chip + other-operator confirmation-record match"
    :ekyc.method/summary             "Customer transmits a single-issuance-only ID's image or IC-chip data, and declares customer-only-knowable matching information that another specified business operator, holding its own saved confirmation record for this customer, confirms matches that record."
    :ekyc.method/required-evidence   [#{:id-document-image :ic-chip-read} #{:other-operator-record-inquiry}]
    :ekyc.method/document-fields     {:id-document-image #{:name :address :dob :thickness-features}
                                       :ic-chip-read       #{:name :address :dob}}
    :ekyc.method/live-capture-required? true
    :ekyc.method/live-capture-kinds  #{:id-document-image}
    :ekyc.method/ial-approx          :not-directly-comparable
    :ekyc.method/ial-note            "Relies on a prior identity-proofing event performed and recorded by a different specified business operator (a transitive-reliance / federation-like trust model). NIST 800-63A-4 scores evidence strength within a single proofing event and treats reliance on another party's proofing as federation (SP 800-63C), which is out of 800-63A's scope -- no honest IAL number applies without knowing that other operator's own proofing rigor."}

   {:ekyc.method/id                  :to-2
    :ekyc.method/jurisdiction        "JPN"
    :ekyc.method/subject             :individual
    :ekyc.method/citation            "犯罪による収益の移転防止に関する法律施行規則 第6条第1項第1号ト(2)"
    :ekyc.method/citation-en         "APTCP Enforcement Regulation Art.6(1)(i)(to)(2)"
    :ekyc.method/label               "Single-issuance ID image/IC-chip + transfer to customer's own verified bank account"
    :ekyc.method/summary             "Customer transmits a single-issuance-only ID's image or IC-chip data; the operator transfers funds into the customer's own deposit account (previously identity-verified, with records, by another specified business operator) and receives a copy of the passbook evidencing the transfer."
    :ekyc.method/required-evidence   [#{:id-document-image :ic-chip-read} #{:own-account-transfer-confirmation}]
    :ekyc.method/document-fields     {:id-document-image #{:name :address :dob :thickness-features}
                                       :ic-chip-read       #{:name :address :dob}}
    :ekyc.method/live-capture-required? true
    :ekyc.method/live-capture-kinds  #{:id-document-image}
    :ekyc.method/ial-approx          :not-directly-comparable
    :ekyc.method/ial-note            "Same transitive-reliance shape as to-1, evidenced through a bank transfer + passbook copy rather than a declared-match inquiry. No independent IAL number applies."}

   {:ekyc.method/id                  :ru
    :ekyc.method/jurisdiction        "JPN"
    :ekyc.method/subject             :individual
    :ekyc.method/citation            "犯罪による収益の移転防止に関する法律施行規則 第6条第1項第1号ル"
    :ekyc.method/citation-en         "APTCP Enforcement Regulation Art.6(1)(i)(ru)"
    :ekyc.method/label               "Card-substitute electromagnetic record (digital My Number Card equivalent)"
    :ekyc.method/summary             "Customer transmits the カード代替電磁的記録 (card-substitute electromagnetic record under 番号利用法 -- a smartphone-embedded My Number Card equivalent) carrying name/address/DOB/photo, sent via a Prime-Minister-certified transmission program and confirmed via a certified verification program."
    :ekyc.method/required-evidence   [#{:card-substitute-electromagnetic-record}]
    :ekyc.method/document-fields     {:card-substitute-electromagnetic-record #{:name :address :dob :photo}}
    :ekyc.method/live-capture-required? false
    :ekyc.method/live-capture-kinds  #{}
    :ekyc.method/ial-approx          :ial2
    :ekyc.method/ial-note            "A cryptographically signed government-issued digital credential verified by a certified program -- analogous to NIST's Mobile Driver's License / digital Verifiable Credential SUPERIOR-evidence examples (one piece sufficient for IAL2). Remote/unattended, so cannot reach IAL3."}

   {:ekyc.method/id                  :ka
    :ekyc.method/jurisdiction        "JPN"
    :ekyc.method/subject             :individual
    :ekyc.method/citation            "犯罪による収益の移転防止に関する法律施行規則 第6条第1項第1号カ"
    :ekyc.method/citation-en         "APTCP Enforcement Regulation Art.6(1)(i)(ka)"
    :ekyc.method/label               "Public Personal Authentication signature certificate (My Number Card) + e-signature"
    :ekyc.method/summary             "Customer transmits the 署名用電子証明書 (signature-use electronic certificate recorded on the My Number Card, issued by 地方公共団体情報システム機構 / J-LIS under the Public Personal Authentication Act) plus the electronically-signed transaction information it confirms."
    :ekyc.method/required-evidence   [#{:public-personal-auth-certificate} #{:electronic-signature}]
    :ekyc.method/document-fields     {:public-personal-auth-certificate #{:name :address :dob}}
    :ekyc.method/live-capture-required? false
    :ekyc.method/live-capture-kinds  #{}
    :ekyc.method/ial-approx          :ial2
    :ekyc.method/ial-note            "PKI certificate validated via digital-signature verification to a government (J-LIS) trust anchor -- analogous to NIST's PIV-Card SUPERIOR-evidence example. Remote/unattended, so cannot reach IAL3 regardless of the certificate's cryptographic strength."}

   {:ekyc.method/id                  :wa
    :ekyc.method/jurisdiction        "JPN"
    :ekyc.method/subject             :individual
    :ekyc.method/citation            "犯罪による収益の移転防止に関する法律施行規則 第6条第1項第1号ワ"
    :ekyc.method/citation-en         "APTCP Enforcement Regulation Art.6(1)(i)(wa)"
    :ekyc.method/label               "Accredited certification-business electronic certificate + e-signature"
    :ekyc.method/summary             "Customer transmits an electronic certificate (carrying name/address/DOB) issued by a business accredited under Article 4(1) of the Electronic Signatures and Certification Business Act, plus the electronically-signed transaction information it confirms."
    :ekyc.method/required-evidence   [#{:accredited-certifier-certificate} #{:electronic-signature}]
    :ekyc.method/document-fields     {:accredited-certifier-certificate #{:name :address :dob}}
    :ekyc.method/live-capture-required? false
    :ekyc.method/live-capture-kinds  #{}
    :ekyc.method/ial-approx          :ial2-conditional
    :ekyc.method/ial-note            "PKI-based, but the assurance is inherited from the private certifier's OWN identity-proofing rigor at certificate issuance, which this library cannot independently verify -- flagged conditional rather than asserted as IAL2."}

   {:ekyc.method/id                  :yo
    :ekyc.method/jurisdiction        "JPN"
    :ekyc.method/subject             :individual
    :ekyc.method/citation            "犯罪による収益の移転防止に関する法律施行規則 第6条第1項第1号ヨ"
    :ekyc.method/citation-en         "APTCP Enforcement Regulation Art.6(1)(i)(yo)"
    :ekyc.method/label               "Specific recognized private certification-business electronic certificate + e-signature"
    :ekyc.method/summary             "Customer transmits an electronic certificate (carrying name/address/DOB) issued by a private certification business specifically recognized under Public Personal Authentication Act Art.17(1)(v), whose issuance-time identity verification meets Electronic Signature Act Enforcement Regulation Art.5(1), plus the electronically-signed transaction information it confirms."
    :ekyc.method/required-evidence   [#{:specific-certifier-certificate} #{:electronic-signature}]
    :ekyc.method/document-fields     {:specific-certifier-certificate #{:name :address :dob}}
    :ekyc.method/live-capture-required? false
    :ekyc.method/live-capture-kinds  #{}
    :ekyc.method/ial-approx          :ial2-conditional
    :ekyc.method/ial-note            "Same inherited-assurance caveat as wa: the issuance-time proofing is statutorily constrained (stricter than wa), but still not independently verifiable by this library."}

   {:ekyc.method/id                  :corp-ro
    :ekyc.method/jurisdiction        "JPN"
    :ekyc.method/subject             :corporate
    :ekyc.method/citation            "犯罪による収益の移転防止に関する法律施行規則 第6条第1項第3号ロ"
    :ekyc.method/citation-en         "APTCP Enforcement Regulation Art.6(1)(iii)(ro)"
    :ekyc.method/label               "Name/registered-office declaration + registry-information-service lookup"
    :ekyc.method/summary             "Corporate representative declares the entity's name and registered head-office/principal-place-of-business address; the operator receives corroborating registry information from a designated entity under the Act on Provision of Registration Information via Telecommunication Lines (e.g. 登記情報提供サービス)."
    :ekyc.method/required-evidence   [#{:name-and-registered-office-declaration} #{:registry-information-lookup}]
    :ekyc.method/document-fields     {:registry-information-lookup #{:name :registered-office}}
    :ekyc.method/live-capture-required? false
    :ekyc.method/live-capture-kinds  #{}
    :ekyc.method/ial-approx          :not-applicable
    :ekyc.method/ial-note            "NIST 800-63A-4's IAL framework is defined for natural-person subscribers; it has no defined mapping for a legal-entity customer."}

   {:ekyc.method/id                  :corp-ho
    :ekyc.method/jurisdiction        "JPN"
    :ekyc.method/subject             :corporate
    :ekyc.method/citation            "犯罪による収益の移転防止に関する法律施行規則 第6条第1項第3号ホ"
    :ekyc.method/citation-en         "APTCP Enforcement Regulation Art.6(1)(iii)(ho)"
    :ekyc.method/label               "Electronic Certification Registry Office (登記官) electronic certificate + e-signature"
    :ekyc.method/summary             "Corporate representative transmits an electronic certificate issued by a registrar under Commercial Registration Act Art.12-2(1)/(3) (carrying the entity's name and registered head office), plus the electronically-signed transaction information it confirms."
    :ekyc.method/required-evidence   [#{:registrar-issued-certificate} #{:electronic-signature}]
    :ekyc.method/document-fields     {:registrar-issued-certificate #{:name :registered-office}}
    :ekyc.method/live-capture-required? false
    :ekyc.method/live-capture-kinds  #{}
    :ekyc.method/ial-approx          :not-applicable
    :ekyc.method/ial-note            "Same not-applicable rationale as corp-ro: IAL is a natural-person framework."}])

(def ^:private method-by-id
  (into {} (map (juxt :ekyc.method/id identity)) method-catalog))

(defn method
  "Look up a catalog entry by its statute-letter id (:ho :he :to-1 :to-2 :ru
  :ka :wa :yo :corp-ro :corp-ho). Returns nil for anything not in the real
  enumerated catalog -- callers MUST treat nil as \"not a legally recognized
  method\", never fabricate a fallback."
  [id]
  (get method-by-id id))

(defn legally-recognized-method?
  "True when id names a method actually enumerated in method-catalog."
  [id]
  (contains? method-by-id id))

(defn methods-for
  "Catalog entries applicable to subject (:individual or :corporate)."
  [subject]
  (filterv #(= subject (:ekyc.method/subject %)) method-catalog))

;; ---------------------------------------------------------------------------
;; NIST SP 800-63A-4 (July 2025) Identity Assurance Level reference data --
;; informational only (used by the operator console); this library does not
;; itself perform proofing, only cites the framework each method is being
;; approximately compared against.
;; ---------------------------------------------------------------------------

(def ial-levels
  {:ial1 {:ekyc.ial/id         :ial1
          :ekyc.ial/citation   "NIST SP 800-63A-4 Sec. 4.1"
          :ekyc.ial/evidence   "One piece of FAIR (digitally validated, or with a facial portrait/biometric), STRONG, or SUPERIOR evidence."
          :ekyc.ial/biometric  "Optional (biometric matching not required)."
          :ekyc.ial/proofing   "Any proofing type: remote unattended, remote attended, on-site unattended, or on-site attended."}
   :ial2 {:ekyc.ial/id         :ial2
          :ekyc.ial/citation   "NIST SP 800-63A-4 Sec. 4.2"
          :ekyc.ial/evidence   "One FAIR + one STRONG piece, or two STRONG pieces, or one SUPERIOR piece."
          :ekyc.ial/biometric  "Optional; three verification pathways (Non-Biometric, Biometric, Digital Evidence)."
          :ekyc.ial/proofing   "Any proofing type: remote unattended, remote attended, on-site unattended, or on-site attended."}
   :ial3 {:ekyc.ial/id         :ial3
          :ekyc.ial/citation   "NIST SP 800-63A-4 Sec. 4.3"
          :ekyc.ial/evidence   "Same evidence-strength combinations as IAL2 (1 FAIR+1 STRONG, or 2 STRONG, or 1 SUPERIOR)."
          :ekyc.ial/biometric  "Mandatory -- a biometric sample SHALL be collected and retained at proofing time."
          :ekyc.ial/proofing   "On-site attended only (co-located or CSP-controlled kiosk/device with a trained proofing agent)."}})

;; ---------------------------------------------------------------------------
;; Evidence -- opaque references to submitted artifacts, never real biometric
;; data or document images. A real vendor integration populates :ekyc.evidence/ref
;; with its own opaque handle (a vendor session id, a custody-adapter ref, an
;; object-store key, ...); this library only reasons about which fields that
;; artifact is asserted to expose and whether it was captured live.
;; ---------------------------------------------------------------------------

(defn evidence
  "Construct an evidence-item record. kind is one of the evidence-kind
  keywords used in method-catalog (:id-document-image :facial-image
  :ic-chip-read :other-operator-record-inquiry :own-account-transfer-confirmation
  :card-substitute-electromagnetic-record :public-personal-auth-certificate
  :accredited-certifier-certificate :specific-certifier-certificate
  :electronic-signature :name-and-registered-office-declaration
  :registry-information-lookup :registrar-issued-certificate). ref is an
  opaque reference string/id -- never a real image or biometric payload."
  [kind ref & {:keys [fields-confirmed captured-live? source]}]
  {:ekyc.evidence/kind             kind
   :ekyc.evidence/ref              ref
   :ekyc.evidence/fields-confirmed (set fields-confirmed)
   :ekyc.evidence/captured-live?   (boolean captured-live?)
   :ekyc.evidence/source           source})

;; ---------------------------------------------------------------------------
;; Subject claim -- the customer's asserted core attributes (犯収法's 氏名/
;; 住居/生年月日; NIST 800-63A-4's core attributes: name, DOB, address).
;; ---------------------------------------------------------------------------

(defn subject-claim
  [name address dob & {:keys [subject-type]}]
  {:ekyc.subject/name    name
   :ekyc.subject/address address
   :ekyc.subject/dob     dob
   :ekyc.subject/type    (or subject-type :individual)})

(defn- missing-subject-fields
  "Core-attribute completeness. dob is a natural-person-only 犯収法/NIST core
  attribute (法人 has no birth date) -- required for :individual, not
  required for :corporate."
  [subject-claim]
  (let [required (cond-> [:ekyc.subject/name :ekyc.subject/address]
                    (not= :corporate (:ekyc.subject/type subject-claim))
                    (conj :ekyc.subject/dob))]
    (set (remove #(some? (get subject-claim %)) required))))

;; ---------------------------------------------------------------------------
;; Verification record -- subject claim + chosen method + submitted evidence.
;; ---------------------------------------------------------------------------

(defn verification
  "Construct a verification record: id, the catalog method-id the customer
  is claiming to satisfy, their subject-claim, and the evidence items
  submitted so far (possibly incomplete -- validate reports what's missing)."
  [id method-id subject-claim evidence & {:keys [submitted-at]}]
  {:ekyc.verification/id             id
   :ekyc.verification/method         method-id
   :ekyc.verification/subject-claim  subject-claim
   :ekyc.verification/evidence       (vec evidence)
   :ekyc.verification/submitted-at   submitted-at})

(defn- evidence-by-kind [verification]
  (reduce (fn [acc e] (assoc acc (:ekyc.evidence/kind e) e))
          {}
          (:ekyc.verification/evidence verification)))

(defn missing-evidence-groups
  "Vector of unsatisfied required-evidence OR-groups (each a set of kind
  options, any one of which would have satisfied that slot). Empty when
  every group has at least one matching evidence item present. nil when the
  method itself is not recognized."
  [verification]
  (when-let [m (method (:ekyc.verification/method verification))]
    (let [present (set (keys (evidence-by-kind verification)))]
      (vec (remove #(seq (set/intersection % present))
                    (:ekyc.method/required-evidence m))))))

(defn missing-document-fields
  "Map of evidence-kind -> set of required fields not yet confirmed, for
  every evidence item actually present whose declared fields-confirmed
  falls short of what the method requires that kind to expose. nil when the
  method is not recognized."
  [verification]
  (when-let [m (method (:ekyc.verification/method verification))]
    (let [by-kind (evidence-by-kind verification)]
      (reduce
        (fn [acc [kind required-fields]]
          (if-let [e (get by-kind kind)]
            (let [missing (set/difference required-fields (:ekyc.evidence/fields-confirmed e #{}))]
              (cond-> acc (seq missing) (assoc kind missing)))
            acc))
        {}
        (:ekyc.method/document-fields m)))))

(defn missing-liveness-signal
  "Set of evidence kinds that the method requires to be a live/dynamic
  capture (per 犯収法's \"特定事業者が提供するソフトウェアを使用して...撮影
  をさせた\" -- captured in real time via the operator's software, not a
  pre-existing still upload) but whose submitted evidence item is not
  flagged :ekyc.evidence/captured-live? true. nil when the method is not
  recognized."
  [verification]
  (when-let [m (method (:ekyc.verification/method verification))]
    (when (:ekyc.method/live-capture-required? m)
      (let [by-kind (evidence-by-kind verification)]
        (set (keep (fn [kind]
                      (let [e (get by-kind kind)]
                        (when (and e (not (:ekyc.evidence/captured-live? e)))
                          kind)))
                    (:ekyc.method/live-capture-kinds m)))))))

(defn evidence-complete?
  "True when every required-evidence group is satisfied, every present
  document field requirement is met, and every required liveness signal is
  present. False (never nil) for an unrecognized method."
  [verification]
  (boolean
    (when (legally-recognized-method? (:ekyc.verification/method verification))
      (and (empty? (missing-evidence-groups verification))
           (empty? (missing-document-fields verification))
           (empty? (missing-liveness-signal verification))))))

(defn assurance-level
  "The catalog method's approximate NIST SP 800-63A-4 IAL mapping (see
  ial-levels), or nil for an unrecognized method. Returned regardless of
  evidence-completeness -- callers should gate on validate's :disposition,
  not read an assurance level out of an incomplete record as if it were
  achieved."
  [verification]
  (:ekyc.method/ial-approx (method (:ekyc.verification/method verification))))

(defn validate
  "The governor-facing entry point. Returns a disposition record a
  PolicyGovernor can check before accepting a client/debtor's identity
  verification:

    :ekyc.result/disposition        :pass | :incomplete | :fail
    :ekyc.result/reasons            vector of keyword reason codes
    :ekyc.result/missing-evidence   unsatisfied required-evidence OR-groups
    :ekyc.result/missing-document-fields  per-kind missing field sets
    :ekyc.result/missing-liveness-signal  kinds missing a live-capture flag
    :ekyc.result/missing-subject-fields   missing core-attribute fields
    :ekyc.result/assurance-level     approximate NIST IAL, or nil
    :ekyc.result/assurance-note      honest caveat text for that mapping

  :fail means the claimed method is not one of the real enumerated
  犯収法施行規則 6条1項 methods -- reject outright, never invent a fallback.
  :incomplete means the method is real but the record is missing required
  evidence, fields, a liveness signal, or subject-claim attributes.
  :pass means the record is structurally complete for its claimed method
  (this is NOT a biometric-match or document-authenticity pass -- see
  README's scope note)."
  [verification]
  (let [method-id (:ekyc.verification/method verification)]
    (if-not (legally-recognized-method? method-id)
      {:ekyc.result/verification (:ekyc.verification/id verification)
       :ekyc.result/method       method-id
       :ekyc.result/disposition  :fail
       :ekyc.result/reasons      [:unrecognized-method]}
      (let [missing-ev     (missing-evidence-groups verification)
            missing-fields (missing-document-fields verification)
            missing-live   (missing-liveness-signal verification)
            missing-subj   (missing-subject-fields (:ekyc.verification/subject-claim verification))
            reasons        (cond-> []
                              (seq missing-ev)     (conj :missing-evidence)
                              (seq missing-fields) (conj :missing-document-fields)
                              (seq missing-live)   (conj :missing-liveness-signal)
                              (seq missing-subj)   (conj :missing-subject-fields))
            m               (method method-id)]
        {:ekyc.result/verification            (:ekyc.verification/id verification)
         :ekyc.result/method                  method-id
         :ekyc.result/disposition             (if (seq reasons) :incomplete :pass)
         :ekyc.result/reasons                 reasons
         :ekyc.result/missing-evidence        missing-ev
         :ekyc.result/missing-document-fields missing-fields
         :ekyc.result/missing-liveness-signal missing-live
         :ekyc.result/missing-subject-fields  missing-subj
         :ekyc.result/assurance-level         (:ekyc.method/ial-approx m)
         :ekyc.result/assurance-note          (:ekyc.method/ial-note m)}))))
