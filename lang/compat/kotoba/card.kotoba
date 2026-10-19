(ns kotoba.card
  "Payment cards, ISO 8583 messages and authorization — pure data contracts.

  A kotoba-lang capability library for the cloud-itonami-6619 (card
  transaction processing and settlement) open business. No network, no I/O.
  Models the records a card-processing operator keeps: PAN (ISO/IEC 7812)
  account numbers with Luhn (ISO/IEC 7812-1) checksum, ISO 8583 message
  headers and data elements, and an authorization/decision contract.

  The library models records, not the wire bitmap/packed-BCD format. ISO
  8583 on the wire uses a 4-byte message indicator, a 16-bit primary
  bitmap and BCD-packed fields; here messages are EDN so a governor or
  test harness can reason structurally without a codec.

  Amounts are plain numbers in the smallest unit of the transaction
  currency (e.g. cents) — no BigDecimal assumption, keeping the library
  portable. PANs are kept whole only for the structural model; a separate
  tokenization layer must replace them in any real system.

  Portable (.cljc) across JVM / ClojureScript / SCI / GraalVM."
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; PAN — Primary Account Number (ISO/IEC 7812), Luhn checksum (ISO/IEC 7812-1)
;;   shape: IIN(6..8) + account(<=12) + check-digit(1)  total 12..19 digits
;; ---------------------------------------------------------------------------

(defn- digits-only [s]
  (when (string? s) (str/replace s #"\D" "")))

(defn luhn-valid?
  "True when s passes the Luhn mod-10 checksum (ISO/IEC 7812-1). Accepts
  digit-only strings of any length >= 2; structural validity (length,
  IIN range) is checked by pan-valid?."
  [s]
  (let [d (digits-only s)]
    (when (and d (>= (count d) 2))
      (let [char->digit (fn [c] (- (int c) (int \0)))
            sum (->> d
                     (reverse)
                     (map char->digit)
                     (map-indexed (fn [i dg]
                                    (if (even? i)
                                      dg
                                      (let [d2 (* 2 dg)]
                                        (if (> d2 9) (- d2 9) d2)))))
                     (reduce +))]
        (zero? (mod sum 10))))))

(def iin-ranges
  "A representative set of Issuer Identification Number prefixes (first
  digit / leading digits) used to classify the card network. Not
  exhaustive — sufficient to assign a network to a well-formed PAN."
  [{:network :amex      :prefix "34"  :lengths #{15}}
   {:network :amex      :prefix "37"  :lengths #{15}}
   {:network :visa      :prefix "4"   :lengths #{13 16 19}}
   {:network :mastercard :prefix "51" :lengths #{16}}
   {:network :mastercard :prefix "52" :lengths #{16}}
   {:network :mastercard :prefix "53" :lengths #{16}}
   {:network :mastercard :prefix "54" :lengths #{16}}
   {:network :mastercard :prefix "55" :lengths #{16}}
   {:network :discover  :prefix "6011" :lengths #{16 19}}
   {:network :discover  :prefix "65"  :lengths #{16 19}}])

(defn- network-of
  "Return the card network keyword for a digit string, or nil when no
  known IIN prefix matches AND the digit string's length is one of that
  network's declared valid lengths."
  [d]
  (some (fn [{:keys [prefix lengths] :as r}]
          (when (and (str/starts-with? d prefix)
                     (contains? lengths (count d)))
            (:network r)))
        (sort-by (comp count :prefix) iin-ranges)))

(defn pan-valid?
  "True when s is a 12..19 digit string whose Luhn checksum is valid and
  whose IIN prefix maps to a known card network."
  [s]
  (let [d (digits-only s)]
    (and (string? s)
         (re-matches #"\d{12,19}" d)
         (luhn-valid? d)
         (network-of d))))

(defn parse-pan
  "Decompose a PAN into {:card/iin :card/account :card/check-digit
  :card/network}. Returns nil when malformed (does not require a valid
  Luhn checksum; see pan-valid?)."
  [s]
  (let [d (digits-only s)]
    (when (and d (re-matches #"\d{12,19}" d))
      {:card/iin         (subs d 0 6)
       :card/account     (subs d 6 (dec (count d)))
       :card/check-digit (subs d (dec (count d)))
       :card/network     (network-of d)})))

;; ---------------------------------------------------------------------------
;; ISO 8583 message — header (MTI) + data elements
;; ---------------------------------------------------------------------------

(def mti-classes
  "ISO 8583 Message Type Indicator — first digit is the class."
  {"01" "Authorization Message"
   "02" "Financial Message"
   "04" "Reversal Message"
   "08" "Network Management Message"
   "11" "Authorization Advice"})

(defn mti-valid?
  "True when mti is a 4-character string whose first two digits name a
  declared message class."
  [mti]
  (and (string? mti)
       (re-matches #"\d{4}" mti)
       (contains? mti-classes (subs mti 0 2))))

(defn message
  "Construct an ISO 8583 message record. mti is a 4-digit Message Type
  Indicator. de is a map of data-element number (1..128 int) to value.
  Returns nil when the MTI is invalid."
  [mti de]
  (when (mti-valid? mti)
    {:card/mti      mti
     :card/class    (mti-classes (subs mti 0 2))
     :card/de       de
     :card/bitmap   (set (keys de))}))

;; ---------------------------------------------------------------------------
;; Authorization / decision contract
;; ---------------------------------------------------------------------------

(def auth-actions
  "Permitted authorization actions a card-processing governor may take."
  #{:approve :decline :refer :partial-approve :capture})

(def ^:private approving-actions
  "Actions that actually grant funds — the only actions whose :card/approved
  should default to the full requested amount. A :decline/:refer/:capture
  record must default to 0 approved (nothing was granted) unless the caller
  explicitly overrides it via :approved."
  #{:approve :partial-approve})

(defn authorization
  "Construct an authorization decision record. action is one of
  :approve/:decline/:refer/:partial-approve/:capture. Returns nil for an
  unknown action. amount is the smallest-unit amount; the optional
  :approved amount lets a partial approval record the granted sum."
  [pan amount action & {:keys [currency approved rrn reason] :as opts}]
  (when (and (pan-valid? pan) (contains? auth-actions action))
    (merge {:card/pan      pan
            :card/amount   amount
            :card/currency (or currency "USD")
            :card/action   action
            :card/approved (or approved (when (contains? approving-actions action) amount) 0)
            :card/rrn      rrn
            :card/reason   reason}
           (dissoc opts :currency :approved :rrn :reason))))

(defn approved?
  "True when an authorization record approved (full or partial)."
  [auth]
  (contains? approving-actions (:card/action auth)))

(defn authorized-amount
  "Return the amount a governor actually approved, or 0 for a decline/refer."
  [auth]
  (if (approved? auth) (:card/approved auth) 0))

;; ---------------------------------------------------------------------------
;; Validation
;; ---------------------------------------------------------------------------

(defn validate-pan
  "Return a validation result for a candidate PAN."
  [s]
  (let [d (digits-only s)]
    (cond
      (not (string? s))          {:card/valid? false :card/error :not-a-string}
      (not (re-matches #"\d{12,19}" d))
      {:card/valid? false :card/error :wrong-length}
      (not (luhn-valid? d))       {:card/valid? false :card/error :bad-checksum}
      (not (network-of d))       {:card/valid? false :card/error :unknown-network}
      :else                      {:card/valid? true :card/parsed (parse-pan s)})))

(defn validate-message
  "Return a validation result for an ISO 8583 message record."
  [m]
  (cond
    (not (map? m))                {:card/valid? false :card/error :not-a-map}
    (not (:card/mti m))           {:card/valid? false :card/error :missing-mti}
    (not (mti-valid? (:card/mti m)))
    {:card/valid? false :card/error :invalid-mti}
    :else                        {:card/valid? true :card/mti (:card/mti m)}))
