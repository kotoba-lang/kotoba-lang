(ns kotoba.esim
  "GSMA Remote SIM Provisioning (eSIM) identifiers, profiles and event
  records — pure data contracts.

  A kotoba-lang capability library modeling the records an eSIM provisioning
  operator keeps: eUICC identifiers (EID), profile identifiers (ICCID),
  installed profile descriptors and their state, activation codes an LPA
  consumes, lifecycle notifications, SM-DS event registrations and
  subject-level ownership transfers. No network, no I/O.

  Two RSP variants are distinguished, because their manager roles differ:
  `:consumer` (GSMA SGP.22, an LPA on the device driving SM-DP+ / SM-DS) and
  `:m2m` (GSMA SGP.02, an SM-SR / SM-DP pair driving the eUICC with no local
  assistant). This library records which variant a record belongs to; it does
  not implement either interface.

  Scope, stated plainly: this models *records*, not wire format — the same
  posture kotoba-lang/card takes toward ISO 8583 and kotoba-lang/rcs takes
  toward SIP/MSRP. Explicitly out of scope and not implemented here: ES2+/ES9+
  /ES10x message framing, BPP (Bound Profile Package) construction, SCP03t
  secure channel and certificate/GSMA-CI trust chain verification, and any
  profile content. This library never carries profile key material.

  Delegated authorities, not re-implemented here:
    - ICCID check digit -> [[kotoba.card/luhn-valid?]] (ISO/IEC 7812-1 mod-10,
      the same checksum ITU-T E.118 specifies for ICCID).
    - MSISDN validity -> [[kotoba.phone/e164-valid?]] (numbering plan).

  On specification citations: this namespace cites the specification *documents*
  (SGP.22 Consumer, SGP.02 M2M, ITU-T E.118) and the structural rules verified
  against records in this workspace, and deliberately does NOT cite individual
  clause or annex numbers, because those were not checked against the
  specification text in this build. Where a rule is a deliberately conservative
  reading rather than a quoted requirement, the docstring says so.

  Portable (.cljc) across JVM / ClojureScript / SCI / GraalVM."
  (:require [clojure.string :as str]
            [kotoba.card :as card]
            [kotoba.phone :as phone]))

(def variants
  "RSP variants this library records. :consumer is GSMA SGP.22 (LPA-driven,
  SM-DP+ / SM-DS); :m2m is GSMA SGP.02 (SM-SR / SM-DP driven)."
  #{:consumer :m2m})

;; ---------------------------------------------------------------------------
;; Identifiers
;; ---------------------------------------------------------------------------

(defn- digits-only
  "Return s stripped of spaces and hyphens when the remainder is all digits,
  otherwise nil. Mirrors kotoba.card's own reading of grouped identifiers."
  [s]
  (when (string? s)
    (let [d (str/replace s #"[\s-]" "")]
      (when (and (seq d) (re-matches #"\d+" d)) d))))

(def telecom-mii
  "Major Industry Identifier that both EID and ICCID carry: 89, the
  telecommunications MII of ITU-T E.118 / ISO/IEC 7812."
  "89")

(defn eid-valid?
  "True when s is a structurally valid eUICC Identifier: exactly 32 decimal
  digits beginning with the telecom MII 89.

  The EID check digits are NOT verified. GSMA specifies a checksum over the
  EID, but this build did not verify which algorithm against the
  specification text, and guessing one would reject valid EIDs. Callers that
  need checksum enforcement must supply it at the boundary that has the
  specification in hand — this predicate deliberately answers the narrower
  structural question rather than a wrong stronger one."
  [s]
  (boolean
   (when-let [d (digits-only s)]
     (and (= 32 (count d))
          (str/starts-with? d telecom-mii)))))

(defn iccid-valid?
  "True when s is a structurally valid ICCID: 19 or 20 decimal digits
  beginning with the telecom MII 89 and passing the ITU-T E.118 mod-10 check
  digit, delegated to [[kotoba.card/luhn-valid?]]."
  [s]
  (boolean
   (when-let [d (digits-only s)]
     (and (<= 19 (count d) 20)
          (str/starts-with? d telecom-mii)
          (card/luhn-valid? d)))))

(defn parse-iccid
  "Split a valid ICCID into its ITU-T E.118 parts. Returns nil when s is not a
  valid ICCID, so a caller can never read fields off a malformed identifier.

  The issuer identifier length is country-specific and not fixed by E.118, so
  no country/issuer split is asserted here: :esim/issuer-identifier carries the
  digits between the MII and the check digit undivided."
  [s]
  (when (iccid-valid? s)
    (let [d (digits-only s)]
      {:esim/iccid              d
       :esim/mii                (subs d 0 2)
       :esim/issuer-identifier  (subs d 2 (dec (count d)))
       :esim/check-digit        (subs d (dec (count d)))
       :esim/digits             (count d)})))

;; ---------------------------------------------------------------------------
;; eUICC
;; ---------------------------------------------------------------------------

(defn euicc
  "Construct an eUICC record. eid must be a structurally valid EID; returns
  nil otherwise. variant defaults to :consumer and must be a member of
  [[variants]].

  eid is recorded here in cleartext because this is the operator's own
  structural record. An EID is device-bound quasi-PII: a store that persists
  these across subjects is expected to hash them at its own boundary (see
  ADR-2607300300 D7), which this library neither performs nor prevents."
  [eid & {:keys [variant free-space-bytes manufacturer]
          :or   {variant :consumer}}]
  (when (and (eid-valid? eid) (contains? variants variant))
    {:esim/eid              (digits-only eid)
     :esim/variant          variant
     :esim/free-space-bytes free-space-bytes
     :esim/manufacturer     manufacturer}))

;; ---------------------------------------------------------------------------
;; Profile
;; ---------------------------------------------------------------------------

(def profile-classes
  "Profile classes an eUICC distinguishes. :operational carries a subscription;
  :provisioning exists to bootstrap connectivity for profile management and is
  an SGP.02 (M2M) concern; :test is for type approval."
  #{:operational :provisioning :test})

(defn profile
  "Construct an installed-profile descriptor. iccid must be a valid ICCID and
  state must be a member of kotoba.esim.lifecycle/states; returns nil
  otherwise.

  msisdn, when given, is validated AND canonicalized by kotoba.phone: the
  record stores [[kotoba.phone/normalize-e164]]'s +<digits> form, not the
  string the caller passed. kotoba.phone deliberately accepts a bare national
  digit string as normalizable, so storing the input verbatim would leave two
  records for one line -- delegating the numbering plan means delegating its
  canonical form too, not just its yes/no.

  state is carried as data rather than mutated in place: transitions are
  computed by kotoba.esim.lifecycle, which owns the reachability rules."
  [iccid state & {:keys [class nickname msisdn imsi smdp-fqdn provider-name]
                  :or   {class :operational}}]
  (when (and (iccid-valid? iccid)
             (contains? profile-classes class)
             (contains? #{:absent :disabled :enabled :deleted} state)
             (or (nil? msisdn) (phone/e164-valid? msisdn)))
    {:esim/iccid         (digits-only iccid)
     :esim/state         state
     :esim/class         class
     :esim/nickname      nickname
     :esim/msisdn        (some-> msisdn phone/normalize-e164)
     :esim/imsi          imsi
     :esim/smdp-fqdn     smdp-fqdn
     :esim/provider-name provider-name}))

;; ---------------------------------------------------------------------------
;; Activation code (the string an LPA is handed, SGP.22 Consumer)
;; ---------------------------------------------------------------------------

(defn activation-code
  "Render the SGP.22 Consumer activation-code string an LPA consumes:

    LPA:1$<smdp-fqdn>$<matching-id>[$<oid>][$<confirmation-code-required>]

  Returns nil when smdp-fqdn or matching-id is blank, or when either contains
  the $ delimiter (which would silently produce a different code than the
  caller intended rather than a detectable error)."
  [smdp-fqdn matching-id & {:keys [oid confirmation-code-required?]}]
  (when (and (string? smdp-fqdn) (string? matching-id)
             (not (str/blank? smdp-fqdn)) (not (str/blank? matching-id))
             (not (str/includes? smdp-fqdn "$"))
             (not (str/includes? matching-id "$")))
    (let [tail (cond
                 confirmation-code-required? [(or oid "") "1"]
                 (some? oid)                 [oid]
                 :else                       [])]
      (str/join "$" (into ["LPA:1" smdp-fqdn matching-id] tail)))))

(defn parse-activation-code
  "Parse an SGP.22 Consumer activation-code string into its parts. Returns nil
  unless the code carries the LPA:1 prefix and both required fields."
  [code]
  (when (string? code)
    (let [parts (str/split code #"\$" -1)]
      (when (and (= "LPA:1" (first parts))
                 (>= (count parts) 3)
                 (not (str/blank? (nth parts 1)))
                 (not (str/blank? (nth parts 2))))
        {:esim/smdp-fqdn                  (nth parts 1)
         :esim/matching-id                (nth parts 2)
         :esim/oid                        (let [o (nth parts 3 nil)]
                                            (when-not (str/blank? o) o))
         :esim/confirmation-code-required? (= "1" (nth parts 4 nil))}))))

;; ---------------------------------------------------------------------------
;; Notifications and SM-DS events
;; ---------------------------------------------------------------------------

(def notification-kinds
  "Lifecycle notification kinds an eUICC reports to the operator that prepared
  the profile."
  #{:install :enable :disable :delete})

(defn notification
  "Construct a lifecycle notification record: the eUICC reporting that a
  profile operation completed. seq-number is the eUICC's own notification
  counter. Returns nil for an unknown kind or an invalid identifier."
  [eid iccid kind seq-number & {:keys [smdp-fqdn occurred success?]
                                :or   {success? true}}]
  (when (and (eid-valid? eid) (iccid-valid? iccid)
             (contains? notification-kinds kind))
    {:esim/eid         (digits-only eid)
     :esim/iccid       (digits-only iccid)
     :esim/kind        kind
     :esim/seq-number  seq-number
     :esim/smdp-fqdn   smdp-fqdn
     :esim/occurred    occurred
     :esim/success?    success?}))

(defn event-registration
  "Construct an SM-DS event registration: a pending profile-download event an
  eUICC will discover by polling the discovery server. Consumer (SGP.22) only —
  returns nil for any other variant, because SGP.02 has no discovery server."
  [eid event-id smdp-fqdn & {:keys [variant registered]
                             :or   {variant :consumer}}]
  (when (and (= :consumer variant)
             (eid-valid? eid)
             (string? event-id) (not (str/blank? event-id))
             (string? smdp-fqdn) (not (str/blank? smdp-fqdn)))
    {:esim/eid        (digits-only eid)
     :esim/event-id   event-id
     :esim/smdp-fqdn  smdp-fqdn
     :esim/variant    :consumer
     :esim/registered registered}))

;; ---------------------------------------------------------------------------
;; Ownership transfer
;; ---------------------------------------------------------------------------

(defn ownership-transfer
  "Construct a subject-level ownership-transfer record: a profile moving from
  one accountable subject to another (an operator-to-operator port, or a device
  changing hands).

  This record exists because the transfer is the security event, not a routine
  lifecycle step: it is the primary SIM-swap fraud path, so a consumer of this
  library is expected to treat it as always requiring human escalation and to
  let it move the subject's other authorities (ADR-2607300300 D4/D6). This
  library records the fact; it does not decide it.

  from-subject and to-subject are opaque subject identifiers (a did:key in this
  workspace). Returns nil when they are absent or identical."
  [iccid from-subject to-subject & {:keys [reason requested approved-by]}]
  (when (and (iccid-valid? iccid)
             (some? from-subject) (some? to-subject)
             (not= from-subject to-subject))
    {:esim/iccid        (digits-only iccid)
     :esim/from-subject from-subject
     :esim/to-subject   to-subject
     :esim/reason       reason
     :esim/requested    requested
     :esim/approved-by  approved-by}))

;; ---------------------------------------------------------------------------
;; Validation (issue seqs, in the shape kotoba.card/validate-pan returns)
;; ---------------------------------------------------------------------------

(defn validate-eid
  "Return a seq of structural issues with eid, empty when acceptable."
  [eid]
  (cond
    (not (string? eid))      [{:esim/issue :eid/not-a-string}]
    (nil? (digits-only eid)) [{:esim/issue :eid/not-digits}]
    :else
    (let [d (digits-only eid)]
      (cond-> []
        (not= 32 (count d))
        (conj {:esim/issue :eid/wrong-length :esim/digits (count d)})
        (not (str/starts-with? d telecom-mii))
        (conj {:esim/issue :eid/not-telecom-mii :esim/mii (subs d 0 (min 2 (count d)))})))))

(defn validate-iccid
  "Return a seq of structural issues with iccid, empty when acceptable."
  [iccid]
  (cond
    (not (string? iccid))      [{:esim/issue :iccid/not-a-string}]
    (nil? (digits-only iccid)) [{:esim/issue :iccid/not-digits}]
    :else
    (let [d (digits-only iccid)]
      (cond-> []
        (not (<= 19 (count d) 20))
        (conj {:esim/issue :iccid/wrong-length :esim/digits (count d)})
        (not (str/starts-with? d telecom-mii))
        (conj {:esim/issue :iccid/not-telecom-mii :esim/mii (subs d 0 (min 2 (count d)))})
        (not (card/luhn-valid? d))
        (conj {:esim/issue :iccid/check-digit-failed})))))
