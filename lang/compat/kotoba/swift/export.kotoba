(ns kotoba.swift.export
  "Operator-facing export for an interbank-messaging actor.

  Renders BIC validation results, real-wire-format SWIFT MT messages and
  real ISO 20022 XML documents to CSV and JSON for compliance/audit export.
  Pure data → text: no network."
  (:require [clojure.string :as str]
            [kotoba.swift :as swift]
            [kotoba.swift.iso20022 :as iso]))

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

(defn- csv-row [vals] (str/join "," (map csv-cell vals)))

(def ^:private json-hex-digits "0123456789abcdef")

(defn- json-hex4
  "4-digit hex for a JSON `\\uXXXX` escape (portable: bit ops + a lookup
  table, no Long/Integer interop that would only work on :clj)."
  [n]
  (apply str (for [shift [12 8 4 0]] (nth json-hex-digits (bit-and (bit-shift-right n shift) 0xf)))))

(def ^:private json-string-escapes
  "RFC 8259 §7: EVERY control character U+0000-U+001F must be escaped in
  a JSON string, not just \\ \" and \\n -- an operator-supplied field
  containing a raw \\t, \\r, or other control byte would otherwise be
  copied through raw, producing invalid JSON (verified against Python's
  strict json module)."
  (into {\" "\\\"" \\ "\\\\"}
        (for [i (range 0x20)]
          [(char i) (case i
                      8 "\\b" 9 "\\t" 10 "\\n" 12 "\\f" 13 "\\r"
                      (str "\\u" (json-hex4 i)))])))

(defn- json-str [v]
  (str/escape (str (if (nil? v) "" v)) json-string-escapes))

(defn bics->csv [bics]
  (str/join "\n"
    (cons (csv-row ["bic" "valid" "country" "bank" "branch"])
          (for [b bics]
            (let [r (swift/validate-bic b)
                  p (:swift/parsed r)]
              (csv-row [b
                        (if (:swift/valid? r) "yes" "no")
                        (or (:swift/country p) "")
                        (or (:swift/bank p) "")
                        (or (:swift/branch p) "")]))))))

(defn bics->json [bics]
  (str "["
       (str/join ","
                 (for [b bics]
                   (let [r (swift/validate-bic b) p (:swift/parsed r)]
                     (str "{\"bic\":\"" (json-str b) "\","
                          "\"valid\":" (if (:swift/valid? r) "true" "false") ","
                          "\"country\":\"" (json-str (:swift/country p)) "\","
                          "\"bank\":\"" (json-str (:swift/bank p)) "\"}"))))
       "]"))

;; ---------------------------------------------------------------------------
;; SWIFT MT messages — real-wire-format records (mt103 / mt202 / parse-mt-wire)
;; ---------------------------------------------------------------------------

(defn mt-messages->csv
  "CSV export of MT message records, including the real wire string (RFC
  4180-quoted; it contains embedded CRLF)."
  [messages]
  (str/join "\n"
    (cons (csv-row ["mt" "valid" "sender" "receiver" "reference" "wire"])
          (for [m messages]
            (let [r (swift/validate-mt m)]
              (csv-row [(or (get-in m [:swift/block-2 :swift/message-type]) "")
                        (if (:swift/valid? r) "yes" "no")
                        (or (get-in m [:swift/block-1 :swift/lt-address]) "")
                        (or (get-in m [:swift/block-2 :swift/destination-address]) "")
                        (or (swift/mt-field (:swift/block-4 m) "20") "")
                        (swift/mt->wire m)]))))))

(defn mt-messages->json
  [messages]
  (str "["
       (str/join ","
                 (for [m messages]
                   (let [r (swift/validate-mt m)]
                     (str "{\"mt\":\"" (json-str (get-in m [:swift/block-2 :swift/message-type])) "\","
                          "\"valid\":" (if (:swift/valid? r) "true" "false") ","
                          "\"sender\":\"" (json-str (get-in m [:swift/block-1 :swift/lt-address])) "\","
                          "\"receiver\":\"" (json-str (get-in m [:swift/block-2 :swift/destination-address])) "\","
                          "\"reference\":\"" (json-str (swift/mt-field (:swift/block-4 m) "20")) "\","
                          "\"wire\":\"" (json-str (swift/mt->wire m)) "\"}"))))
       "]"))

;; ---------------------------------------------------------------------------
;; ISO 20022 documents — real XML (pain001-doc / pacs008-doc / parse-xml)
;; ---------------------------------------------------------------------------

(defn iso20022-docs->csv
  "CSV export of ISO 20022 documents, including the real generated XML
  (RFC 4180-quoted; it contains embedded newlines)."
  [docs]
  (str/join "\n"
    (cons (csv-row ["message-type" "valid" "msg-id" "xml"])
          (for [d docs]
            (let [r (iso/validate-iso20022 d)
                  grp-hdr (or (some-> d (iso/xml-find :CstmrCdtTrfInitn) (iso/xml-find :GrpHdr))
                              (some-> d (iso/xml-find :FIToFICstmrCdtTrf) (iso/xml-find :GrpHdr)))
                  msg-id (some-> grp-hdr (iso/xml-find :MsgId) iso/xml-text)]
              (csv-row [(or (:swift/message-type r) "")
                        (if (:swift/valid? r) "yes" "no")
                        (or msg-id "")
                        (iso/xml->str d)]))))))

(defn iso20022-docs->json
  [docs]
  (str "["
       (str/join ","
                 (for [d docs]
                   (let [r (iso/validate-iso20022 d)
                         grp-hdr (or (some-> d (iso/xml-find :CstmrCdtTrfInitn) (iso/xml-find :GrpHdr))
                                     (some-> d (iso/xml-find :FIToFICstmrCdtTrf) (iso/xml-find :GrpHdr)))
                         msg-id (some-> grp-hdr (iso/xml-find :MsgId) iso/xml-text)]
                     (str "{\"messageType\":\"" (json-str (:swift/message-type r)) "\","
                          "\"valid\":" (if (:swift/valid? r) "true" "false") ","
                          "\"msgId\":\"" (json-str msg-id) "\","
                          "\"xml\":\"" (json-str (iso/xml->str d)) "\"}"))))
       "]"))
