(ns kotoba.phone.export
  "Operator-facing export for a telecom-access actor.

  Renders E.164 validation, CDRs and SMS records to CSV and JSON for billing
  audit and downstream reporting. Pure data → text: no network."
  (:require [clojure.string :as str]
            [kotoba.phone :as phone]))

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

(defn numbers->csv [numbers]
  (str/join "\n"
    (cons (csv-row ["number" "valid" "normalized"])
          (for [n numbers]
            (let [r (phone/validate-e164 n)]
              (csv-row [n
                        (if (:phone/valid? r) "yes" "no")
                        (or (:phone/normalized r) "")]))))))

(defn cdrs->csv [cdrs]
  (str/join "\n"
    (cons (csv-row ["cdr_id" "calling" "called" "direction" "duration"])
          (for [c cdrs]
            (csv-row [(:phone/cdr-id c)
                      (or (:phone/calling c) "")
                      (or (:phone/called c) "")
                      (name (:phone/direction c))
                      (:phone/duration c)])))))

(defn sms->csv [messages]
  (str/join "\n"
    (cons (csv-row ["sms_id" "from" "to" "body"])
          (for [m messages]
            (csv-row [(:phone/sms-id m)
                      (or (:phone/from m) "")
                      (or (:phone/to m) "")
                      (:phone/body m)])))))

(defn numbers->json [numbers]
  (str "["
       (str/join ","
                 (for [n numbers]
                   (let [r (phone/validate-e164 n)]
                     (str "{\"number\":\"" (json-str n) "\","
                          "\"valid\":" (if (:phone/valid? r) "true" "false") ","
                          "\"normalized\":\"" (json-str (:phone/normalized r)) "\"}"))))
       "]"))

(defn cdrs->json [cdrs]
  (str "["
       (str/join ","
                 (for [c cdrs]
                   (str "{\"cdr_id\":\"" (json-str (:phone/cdr-id c)) "\","
                        "\"calling\":\"" (json-str (:phone/calling c)) "\","
                        "\"called\":\"" (json-str (:phone/called c)) "\","
                        "\"direction\":\"" (name (:phone/direction c)) "\","
                        "\"duration\":" (or (:phone/duration c) 0) "}")))
       "]"))
