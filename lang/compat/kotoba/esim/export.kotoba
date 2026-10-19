(ns kotoba.esim.export
  "Operator-facing export for eSIM profile, notification and transfer records.

  Renders to CSV and JSON for audit and downstream reporting. Pure data → text:
  no network.

  Identifiers are MASKED BY DEFAULT. EID and ICCID are device-bound quasi-PII
  (ADR-2607300300 D7), and an export is precisely where they leak: a CSV lands
  in a spreadsheet, a mail attachment, a ticket. So every function here emits
  only the last four digits unless the caller passes :reveal-identifiers? true,
  which an operator is expected to justify and record rather than set by habit.
  This is a deliberate departure from the sibling exports in kotoba-lang/rcs and
  kotoba-lang/phone, whose E.164 endpoints are not device-bound the way an EID
  is.

  Portable (.cljc) across JVM / ClojureScript / SCI / GraalVM."
  (:require [clojure.string :as str]
            [kotoba.esim :as esim]))

;; The CSV and JSON escaping primitives below mirror kotoba-lang/rcs's export,
;; which in turn mirrors kotoba-lang/phone's -- where the RFC 4180 bare-\r case
;; and the RFC 8259 control-character case were each verified against Python's
;; csv and strict json modules. Kept local to match the family's house style
;; rather than introducing a shared dependency for two functions.

(defn- csv-cell [v]
  (let [s (str (if (nil? v) "" v))]
    ;; RFC 4180 requires quoting a field containing a comma, a double quote, OR
    ;; a line break -- \r alone is also a line break.
    (if (re-find #"[\",\n\r]" s)
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(defn- csv-row [vals] (str/join "," (map csv-cell vals)))

(def ^:private json-hex-digits "0123456789abcdef")

(defn- json-hex4
  "4-digit hex for a JSON \\uXXXX escape (portable: bit ops + a lookup table,
  no Long/Integer interop that would only work on :clj)."
  [n]
  (apply str (for [shift [12 8 4 0]]
               (nth json-hex-digits (bit-and (bit-shift-right n shift) 0xf)))))

(def ^:private json-string-escapes
  "RFC 8259 section 7: EVERY control character U+0000-U+001F must be escaped in
  a JSON string, not just \\ \" and \\n."
  (into {\" "\\\"" \\ "\\\\"}
        (for [i (range 0x20)]
          [(char i) (case i
                      8 "\\b" 9 "\\t" 10 "\\n" 12 "\\f" 13 "\\r"
                      (str "\\u" (json-hex4 i)))])))

(defn- json-str [v]
  (str/escape (str (if (nil? v) "" v)) json-string-escapes))

;; ---------------------------------------------------------------------------
;; Identifier masking
;; ---------------------------------------------------------------------------

(defn mask-identifier
  "Render an EID or ICCID as its last four digits behind an ellipsis, or the
  empty string when there is nothing to mask. Never partially reveals a short
  identifier: anything under 8 digits masks entirely, because the last four of a
  six-digit value is most of it."
  [s]
  (let [d (str (or s ""))]
    (cond
      (str/blank? d)   ""
      (< (count d) 8)  (apply str (repeat (count d) "*"))
      :else            (str "..." (subs d (- (count d) 4))))))

(defn- ident [s reveal?]
  (if reveal? (str (or s "")) (mask-identifier s)))

;; ---------------------------------------------------------------------------
;; CSV
;; ---------------------------------------------------------------------------

(defn profiles->csv
  "Render kotoba.esim/profile records to CSV. Identifiers masked unless
  :reveal-identifiers? is true."
  [profiles & {:keys [reveal-identifiers?]}]
  (str/join "\n"
    (cons (csv-row ["iccid" "state" "class" "msisdn" "provider" "iccid_valid"])
          (for [p profiles]
            (csv-row [(ident (:esim/iccid p) reveal-identifiers?)
                      (some-> (:esim/state p) name)
                      (some-> (:esim/class p) name)
                      (or (:esim/msisdn p) "")
                      (or (:esim/provider-name p) "")
                      (if (esim/iccid-valid? (:esim/iccid p)) "yes" "no")])))))

(defn notifications->csv
  "Render kotoba.esim/notification records to CSV."
  [notifications & {:keys [reveal-identifiers?]}]
  (str/join "\n"
    (cons (csv-row ["eid" "iccid" "kind" "seq" "success" "occurred"])
          (for [n notifications]
            (csv-row [(ident (:esim/eid n) reveal-identifiers?)
                      (ident (:esim/iccid n) reveal-identifiers?)
                      (some-> (:esim/kind n) name)
                      (:esim/seq-number n)
                      (if (:esim/success? n) "yes" "no")
                      (or (:esim/occurred n) "")])))))

(defn transfers->csv
  "Render kotoba.esim/ownership-transfer records to CSV. Subject identifiers are
  NOT masked: they are the accountable parties an audit exists to name."
  [transfers & {:keys [reveal-identifiers?]}]
  (str/join "\n"
    (cons (csv-row ["iccid" "from_subject" "to_subject" "reason" "approved_by"])
          (for [t transfers]
            (csv-row [(ident (:esim/iccid t) reveal-identifiers?)
                      (or (:esim/from-subject t) "")
                      (or (:esim/to-subject t) "")
                      (or (:esim/reason t) "")
                      (or (:esim/approved-by t) "")])))))

;; ---------------------------------------------------------------------------
;; JSON
;; ---------------------------------------------------------------------------

(defn profiles->json
  "Render kotoba.esim/profile records to a JSON array."
  [profiles & {:keys [reveal-identifiers?]}]
  (str "["
       (str/join ","
                 (for [p profiles]
                   (str "{\"iccid\":\"" (json-str (ident (:esim/iccid p) reveal-identifiers?)) "\","
                        "\"state\":" (if-let [s (:esim/state p)] (str "\"" (name s) "\"") "null") ","
                        "\"class\":" (if-let [c (:esim/class p)] (str "\"" (name c) "\"") "null") ","
                        "\"msisdn\":\"" (json-str (:esim/msisdn p)) "\","
                        "\"provider\":\"" (json-str (:esim/provider-name p)) "\","
                        "\"iccid_valid\":" (if (esim/iccid-valid? (:esim/iccid p)) "true" "false") "}")))
       "]"))

(defn notifications->json
  "Render kotoba.esim/notification records to a JSON array."
  [notifications & {:keys [reveal-identifiers?]}]
  (str "["
       (str/join ","
                 (for [n notifications]
                   (str "{\"eid\":\"" (json-str (ident (:esim/eid n) reveal-identifiers?)) "\","
                        "\"iccid\":\"" (json-str (ident (:esim/iccid n) reveal-identifiers?)) "\","
                        "\"kind\":" (if-let [k (:esim/kind n)] (str "\"" (name k) "\"") "null") ","
                        "\"seq\":" (if-let [s (:esim/seq-number n)] (str s) "null") ","
                        "\"success\":" (if (:esim/success? n) "true" "false") "}")))
       "]"))

(defn transfers->json
  "Render kotoba.esim/ownership-transfer records to a JSON array."
  [transfers & {:keys [reveal-identifiers?]}]
  (str "["
       (str/join ","
                 (for [t transfers]
                   (str "{\"iccid\":\"" (json-str (ident (:esim/iccid t) reveal-identifiers?)) "\","
                        "\"from_subject\":\"" (json-str (:esim/from-subject t)) "\","
                        "\"to_subject\":\"" (json-str (:esim/to-subject t)) "\","
                        "\"reason\":\"" (json-str (:esim/reason t)) "\","
                        "\"approved_by\":\"" (json-str (:esim/approved-by t)) "\"}")))
       "]"))
