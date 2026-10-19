(ns kotoba.banking.export
  "Operator-facing export for a community banking actor.

  Renders banking records — and Open Banking (Berlin Group XS2A)
  payment-initiation / account-information payloads built by
  kotoba.banking.api — to CSV and JSON for reconciliation, audit export and
  downstream reporting. Pure data → text: no network, no I/O. Exports are
  read-only evidence the audit ledger can append; they never mutate
  accounts, postings or payment-initiation state."
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [kotoba.banking :as bank]))

(defn- csv-cell [v]
  (let [s (str (if (nil? v) "" v))]
    ;; RFC 4180 requires quoting a field containing a comma, a double
    ;; quote, OR a line break -- \r alone is also a line break (a CR-only
    ;; row terminator every standard CSV reader recognizes), but the
    ;; check here only ever covered \n. A field containing a bare \r
    ;; (verified against Python's csv module) silently split into two
    ;; corrupted rows on read-back instead of round-tripping as one.
    (if (re-find #"[\",\n\r]" s)
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(defn- csv-row [vals]
  (str/join "," (map csv-cell vals)))

(def ^:private json-hex-digits "0123456789abcdef")

(defn- json-hex4
  "4-digit hex for a JSON `\\uXXXX` escape (portable: bit ops + a lookup
  table, no Long/Integer interop that would only work on :clj)."
  [n]
  (apply str (for [shift [12 8 4 0]] (nth json-hex-digits (bit-and (bit-shift-right n shift) 0xf)))))

(def ^:private json-string-escapes
  "RFC 8259 §7: EVERY control character U+0000-U+001F must be escaped in
  a JSON string, not just \\ \" and \\n -- a free-text :ledger/memo (or
  any other operator-supplied field) containing a raw \\t, \\r, or other
  control byte would otherwise be copied through raw, producing invalid
  JSON (verified against Python's strict json module)."
  (into {\" "\\\"" \\ "\\\\"}
        (for [i (range 0x20)]
          [(char i) (case i
                      8 "\\b" 9 "\\t" 10 "\\n" 12 "\\f" 13 "\\r"
                      (str "\\u" (json-hex4 i)))])))

(defn- json-str [v]
  (str/escape (str (if (nil? v) "" v)) json-string-escapes))

(defn accounts->csv
  "Export accounts to CSV. Header row + one row per account."
  [accounts]
  (str/join "\n"
    (cons (csv-row ["id" "holder" "type" "balance" "currency"])
          (for [a accounts]
            (csv-row [(:account/id a)
                      (:account/holder a)
                      (name (or (:account/type a) :checking))
                      (:account/balance a)
                      (:account/currency a)])))))

(defn postings->csv
  "Export postings to CSV. Flags unbalanced postings in a `status` column."
  [postings]
  (str/join "\n"
    (cons (csv-row ["posting_id" "entries" "status" "memo"])
          (for [p postings]
            (csv-row [(:ledger/posting p)
                      (count (:ledger/entries p))
                      (if (:ledger/unbalanced p) "unbalanced" "balanced")
                      (or (:ledger/memo p) "")])))))

(defn accounts->json
  "Export accounts to a JSON string."
  [accounts]
  (str "["
       (str/join ","
                 (for [a accounts]
                   (str "{\"id\":\"" (json-str (:account/id a)) "\","
                        "\"holder\":\"" (json-str (:account/holder a)) "\","
                        "\"type\":\"" (name (or (:account/type a) :checking)) "\","
                        "\"balance\":" (or (:account/balance a) 0) ","
                        "\"currency\":\"" (or (:account/currency a) "USD") "\"}")))
       "]"))

(defn postings->json
  "Export postings to a JSON string."
  [postings]
  (str "["
       (str/join ","
                 (for [p postings]
                   (str "{\"posting_id\":\"" (json-str (:ledger/posting p)) "\","
                        "\"entries\":" (count (:ledger/entries p)) ","
                        "\"status\":\"" (if (:ledger/unbalanced p) "unbalanced" "balanced") "\","
                        "\"memo\":\"" (json-str (:ledger/memo p)) "\"}")))
       "]"))

;; ---------------------------------------------------------------------------
;; Open Banking (Berlin Group XS2A) payloads — kotoba.banking.api already
;; builds these with plain string keys mirroring the real JSON field names
;; 1:1, so the JSON exports below are a direct serialization, not a remap.
;; ---------------------------------------------------------------------------

(defn payment-initiations->csv
  "Export Open Banking (Berlin Group XS2A) payment-initiation 201 response
  bodies (kotoba.banking.api/payment-initiation-response maps) to CSV."
  [payment-initiations]
  (str/join "\n"
    (cons (csv-row ["paymentId" "transactionStatus"])
          (for [p payment-initiations]
            (csv-row [(get p "paymentId") (get p "transactionStatus")])))))

(defn payment-initiations->json
  "Export Open Banking (Berlin Group XS2A) payment-initiation 201 response
  bodies to a JSON array."
  [payment-initiations]
  (str "["
       (str/join ","
                 (for [p payment-initiations]
                   (str "{\"paymentId\":\"" (json-str (get p "paymentId")) "\","
                        "\"transactionStatus\":\"" (json-str (get p "transactionStatus")) "\"}")))
       "]"))

(defn account-details->csv
  "Export Open Banking (Berlin Group XS2A) accountDetails maps
  (kotoba.banking.api/account->account-details) to CSV."
  [accounts-details]
  (str/join "\n"
    (cons (csv-row ["resourceId" "iban" "currency" "product" "cashAccountType"])
          (for [a accounts-details]
            (csv-row [(get a "resourceId") (get a "iban") (get a "currency")
                      (get a "product") (get a "cashAccountType")])))))

(defn account-details->json
  "Export Open Banking (Berlin Group XS2A) accountDetails maps to a JSON
  array."
  [accounts-details]
  (str "["
       (str/join ","
                 (for [a accounts-details]
                   (str "{\"resourceId\":\"" (json-str (get a "resourceId")) "\","
                        "\"iban\":\"" (json-str (get a "iban")) "\","
                        "\"currency\":\"" (json-str (get a "currency")) "\","
                        "\"product\":\"" (json-str (get a "product")) "\","
                        "\"cashAccountType\":\"" (json-str (get a "cashAccountType")) "\"}")))
       "]"))
