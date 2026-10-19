(ns kotoba.banking
  "Banking accounts, ledger and clearing — pure data contracts.

  A kotoba-lang capability library for the cloud-itonami-6419 (community
  monetary intermediation) open business. No network, no I/O. Models the
  records a banking operator keeps: IBAN (ISO 13616) identification, account
  records, a double-entry ledger with balanced-posting validation, and a
  clearing batch contract for settlement.

  Amounts are plain numbers in the smallest unit of the account currency
  (e.g. cents) — no BigDecimal assumption, keeping the library portable.
  Portable (.cljc) across JVM / ClojureScript / SCI / GraalVM."
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; IBAN — International Bank Account Number (ISO 13616)
;;   shape: CCkk BBAN  country(2 ISO-3166) check(2) BBAN(<=30 alnum)
;;   checksum: move first 4 chars to end, A..Z -> 10..35, mod 97 == 1
;; ---------------------------------------------------------------------------

(defn- iban-normalize [s]
  (when (string? s)
    (str/upper-case (str/replace s #"\s+" ""))))

(defn- digit-seq
  "Rearrange IBAN (first 4 chars moved to the end) and expand letters to two
  decimal digits (A->10 ... Z->35), returning a lazy sequence of decimal
  digits suitable for streaming mod-97."
  [s]
  (let [rearranged (str (subs s 4) (subs s 0 4))]
    (eduction
      (mapcat (fn [c]
                (let [n (int c)]
                  (cond
                    (<= (int \A) n (int \Z))
                    (let [v (+ 10 (- n (int \A)))]
                      [(quot v 10) (rem v 10)])
                    (<= (int \0) n (int \9))
                    [(- n (int \0))]
                    :else nil))))
      rearranged)))

(defn- mod-97 [digits]
  (reduce (fn [acc d] (mod (+ (* acc 10) d) 97)) 0 digits))

(defn iban-valid?
  "True when s is a well-formed IBAN with a valid mod-97 checksum (== 1)."
  [s]
  (let [n (iban-normalize s)]
    (when (and n (re-matches #"[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}" n))
      (= 1 (mod-97 (digit-seq n))))))

(defn parse-iban
  "Decompose an IBAN into {:iban/country :iban/check-digits :iban/bban
  :iban/normalized}. Returns nil when malformed (does not require a valid
  checksum; see iban-valid?)."
  [s]
  (let [n (iban-normalize s)]
    (when (and n (re-matches #"[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}" n))
      {:iban/country      (subs n 0 2)
       :iban/check-digits (subs n 2 4)
       :iban/bban         (subs n 4)
       :iban/normalized   n})))

;; ---------------------------------------------------------------------------
;; Account
;; ---------------------------------------------------------------------------

(defn account
  "Construct an account record. Amounts are in the smallest unit of currency."
  [id currency & {:keys [holder type]}]
  {:account/id      id
   :account/currency currency
   :account/holder  holder
   :account/type    (or type :checking)
   :account/balance 0})

;; ---------------------------------------------------------------------------
;; Double-entry ledger
;; ---------------------------------------------------------------------------

(defn entry
  "Construct a ledger entry. side is :debit or :credit. Returns nil for an
  unknown side."
  [account-id side amount currency & {:keys [ref]}]
  (when (contains? #{:debit :credit} side)
    {:ledger/account  account-id
     :ledger/side     side
     :ledger/amount   amount
     :ledger/currency currency
     :ledger/ref      ref}))

(defn balanced?
  "True when debit and credit totals of entries match, per currency."
  [entries]
  (let [per-currency (group-by :ledger/currency entries)]
    (every? (fn [[_ ents]]
              (let [sum (fn [side]
                          (reduce + (map :ledger/amount
                                         (filter #(= side (:ledger/side %)) ents))))]
                (= (sum :debit) (sum :credit))))
            per-currency)))

(defn posting
  "Construct a posting (a group of entries that should balance). Sets
  :ledger/balanced? and, when unbalanced, :ledger/unbalanced true so a
  governor can reject the posting before it reaches the ledger."
  [id entries & {:keys [memo]}]
  (let [ok? (balanced? entries)]
    (cond-> {:ledger/posting   id
             :ledger/entries   entries
             :ledger/balanced? ok?}
      memo          (assoc :ledger/memo memo)
      (not ok?)     (assoc :ledger/unbalanced true))))

;; ---------------------------------------------------------------------------
;; Clearing batch
;; ---------------------------------------------------------------------------

(defn clearing-batch
  "Construct a clearing batch of postings for settlement."
  [id postings & {:keys [settlement-currency]}]
  {:clearing/id       id
   :clearing/postings postings
   :clearing/currency (or settlement-currency "XXX")
   :clearing/status   :pending})
