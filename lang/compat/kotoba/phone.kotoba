(ns kotoba.phone
  "Telephony numbering, SIP URIs, call records and SMS — pure data contracts.

  A kotoba-lang capability library for the cloud-itonami-6190 (community
  telecommunications access) open business. No network, no I/O. Models the
  records a telecom operator keeps: E.164 (ITU-T) international subscriber
  numbers, SIP (RFC 3261) addressing, call detail records (CDR) and SMS
  message records.

  The library expects international-form input (a leading +, or an
  international access prefix 00 / 011). National trunk prefixes are not
  translated — that is the operator's numbering-plan concern, not the
  contract layer.

  Portable (.cljc) across JVM / ClojureScript / SCI / GraalVM."
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; E.164 — international subscriber numbers (ITU-T E.164)
;;   canonical: +<country-code><national>  total 1..15 digits (>=6 typical)
;; ---------------------------------------------------------------------------

(def country-codes
  "A representative set of ITU-T country-code prefixes (1-3 digits). Not
  exhaustive — sufficient to split an E.164 number into country + national."
  #{"1" "7" "20" "27" "30" "31" "32" "33" "34" "36" "39" "40" "41" "43" "44"
    "45" "46" "47" "48" "49" "51" "52" "53" "54" "55" "56" "57" "58" "60" "61"
    "62" "63" "64" "65" "66" "81" "82" "84" "86" "90" "91" "92" "93" "94" "95"
    "98" "212" "213" "234" "351" "352" "353" "354" "358" "359" "370" "371" "372"
    "374" "375" "380" "381" "385" "386" "420" "421" "880" "886" "962" "963" "966"
    "971" "972" "974" "977" "994" "995" "996" "998" "852" "853" "855" "856" "870"})

(defn- digits-only [s]
  (when (string? s) (str/replace s #"\D" "")))

(defn normalize-e164
  "Normalize a user-entered number to canonical E.164 (+<digits>). Accepts a
  leading +, or an international access prefix (00 / 011), and ignores
  spaces, dashes, dots and parentheses. Returns nil when the result is not
  6..15 digits."
  [s]
  (when-let [d (digits-only s)]
    (let [stripped (cond
                     (str/starts-with? s "+")   d
                     (str/starts-with? d "00")  (subs d 2)
                     (str/starts-with? d "011") (subs d 3)
                     :else d)]
      (when (and (seq stripped) (re-matches #"\d{6,15}" stripped))
        (str "+" stripped)))))

(defn e164-valid?
  "True when s is a canonical or normalizable E.164 number (6..15 digits)."
  [s]
  (boolean (normalize-e164 s)))

(defn- country-code-of
  "Return the longest matching country-code prefix (1-3 digits) for the digit
  string, or nil."
  [digits]
  (or (when (and (>= (count digits) 3) (contains? country-codes (subs digits 0 3)))
        (subs digits 0 3))
      (when (and (>= (count digits) 2) (contains? country-codes (subs digits 0 2)))
        (subs digits 0 2))
      (when (seq digits) (when (contains? country-codes (subs digits 0 1))
                           (subs digits 0 1)))))

(defn parse-e164
  "Decompose an E.164 number into {:phone/country-code :phone/national
  :phone/normalized}. Returns nil when malformed or when no country-code
  prefix matches."
  [s]
  (when-let [n (normalize-e164 s)]
    (let [digits (subs n 1)]
      (when-let [cc (country-code-of digits)]
        {:phone/country-code cc
         :phone/national     (subs digits (count cc))
         :phone/normalized   n}))))

;; ---------------------------------------------------------------------------
;; SIP URI (RFC 3261) — sip:user@host;params?headers
;; ---------------------------------------------------------------------------

(def ^:private sip-pattern #"^sip:([^@;?]+)@([^;?]+)(.*)$")

(defn parse-sip-uri
  "Parse a sip: URI into {:phone/user :phone/host :phone/params :phone/headers}.
  params is the segment after the first ; up to ?. headers is the segment
  after ?. Returns nil when malformed."
  [s]
  (when (and (string? s)
             (str/starts-with? (str/lower-case s) "sip:"))
    (when-let [m (re-matches sip-pattern s)]
      (let [tail (nth m 3 "")
            [params headers] (str/split tail #"\?" 2)]
        {:phone/user    (nth m 1)
         :phone/host    (nth m 2)
         :phone/params  (when (seq params) (subs params 1)) ; drop leading ;
         :phone/headers (when (and headers (seq headers)) headers)}))))

;; ---------------------------------------------------------------------------
;; Call detail record (CDR) and SMS
;; ---------------------------------------------------------------------------

(defn cdr
  "Construct a call detail record. direction is :inbound or :outbound. calling
  and called are E.164 numbers. duration is seconds."
  [id calling called direction duration & {:keys [started]}]
  (when (contains? #{:inbound :outbound} direction)
    {:phone/cdr-id    id
     :phone/calling   calling
     :phone/called    called
     :phone/direction direction
     :phone/duration  duration
     :phone/started   started}))

(defn sms
  "Construct an SMS message record. from and to are E.164 numbers."
  [id from to body & {:keys [sent]}]
  {:phone/sms-id id
   :phone/from   from
   :phone/to     to
   :phone/body   body
   :phone/sent   sent})

;; ---------------------------------------------------------------------------
;; Validation
;; ---------------------------------------------------------------------------

(defn validate-e164
  "Return a validation result for a candidate E.164 number."
  [s]
  (if-let [n (normalize-e164 s)]
    {:phone/valid? true :phone/normalized n}
    {:phone/valid? false :phone/error :malformed-e164}))
