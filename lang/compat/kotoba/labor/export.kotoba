(ns kotoba.labor.export
  "Operator-facing export for a domestic-employment actor.

  Renders contracts, timesheets and payroll to CSV and JSON for payroll audit
  and downstream reporting. Pure data → text: no network."
  (:require [clojure.string :as str]
            [kotoba.labor :as labor]))

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

(defn contracts->csv [contracts]
  (str/join "\n"
    (cons (csv-row ["contract_id" "worker" "role" "wage_type" "rate" "currency"])
          (for [c contracts]
            (csv-row [(:contract/id c)
                      (:contract/worker c)
                      (:contract/role c)
                      (name (:contract/wage-type c))
                      (:contract/rate c)
                      (:contract/currency c)])))))

(defn timesheets->csv [timesheets]
  (str/join "\n"
    (cons (csv-row ["worker" "date" "hours" "break"])
          (for [t timesheets]
            (csv-row [(:ts/worker t)
                      (:ts/date t)
                      (:ts/hours t)
                      (or (:ts/break t) "")])))))

(defn payrolls->csv [payrolls]
  (str/join "\n"
    (cons (csv-row ["payroll_id" "worker" "period" "gross" "deductions" "net" "currency"])
          (for [p payrolls]
            (csv-row [(:payroll/id p)
                      (:payroll/worker p)
                      (:payroll/period p)
                      (:payroll/gross p)
                      (:payroll/deductions p)
                      (:payroll/net p)
                      (:payroll/currency p)])))))

(defn contracts->json [contracts]
  (str "["
       (str/join ","
                 (for [c contracts]
                   (str "{\"contract_id\":\"" (json-str (:contract/id c)) "\","
                        "\"worker\":\"" (json-str (:contract/worker c)) "\","
                        "\"role\":\"" (json-str (:contract/role c)) "\","
                        "\"wage_type\":\"" (name (:contract/wage-type c)) "\","
                        "\"rate\":" (or (:contract/rate c) 0) ","
                        "\"currency\":\"" (or (:contract/currency c) "USD") "\"}")))
       "]"))

(defn payrolls->json [payrolls]
  (str "["
       (str/join ","
                 (for [p payrolls]
                   (str "{\"payroll_id\":\"" (json-str (:payroll/id p)) "\","
                        "\"worker\":\"" (json-str (:payroll/worker p)) "\","
                        "\"period\":\"" (json-str (:payroll/period p)) "\","
                        "\"gross\":" (or (:payroll/gross p) 0) ","
                        "\"deductions\":" (or (:payroll/deductions p) 0) ","
                        "\"net\":" (or (:payroll/net p) 0) "}")))
       "]"))
