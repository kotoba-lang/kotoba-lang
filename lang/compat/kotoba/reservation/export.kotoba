(ns kotoba.reservation.export
  "Operator-facing export for a reservation-bearing actor.

  Renders inventory, holds and quote lines to CSV and JSON for
  settlement audit and downstream reporting. Pure data → text: no
  network."
  (:require [clojure.string :as str]
            [kotoba.reservation :as res]))

(defn- csv-cell [v]
  (let [s (str (if (nil? v) "" v))]
    ;; RFC 4180: quote a field containing a comma, a double quote, or ANY
    ;; line break — including a bare \r, which is a CR-only row
    ;; terminator every standard CSV reader recognizes and which would
    ;; otherwise split one row into two corrupted ones on read-back.
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
  a JSON string, not just \\ \" and \\n — an operator-supplied label
  containing a raw \\t or \\r would otherwise be copied through raw,
  producing invalid JSON."
  (into {\" "\\\"" \\ "\\\\"}
        (for [i (range 0x20)]
          [(char i) (case i
                      8 "\\b" 9 "\\t" 10 "\\n" 12 "\\f" 13 "\\r"
                      (str "\\u" (json-hex4 i)))])))

(defn- json-str [v]
  (str/escape (str (if (nil? v) "" v)) json-string-escapes))

(defn buckets->csv [buckets]
  (str/join "\n"
            (cons (csv-row ["resource" "date" "class" "capacity" "sold" "held" "overbook" "available" "oversold"])
                  (for [b buckets]
                    (csv-row [(:inv/resource b) (:inv/date b) (name (:inv/class b))
                              (:inv/capacity b) (:inv/sold b) (:inv/held b) (:inv/overbook b)
                              (res/available b)
                              (if (res/oversold? b) "yes" "no")])))))

(defn holds->csv
  "Hold rows as of `now` — expiry is evaluated against the passed time,
  never a clock read here."
  [holds now]
  (str/join "\n"
            (cons (csv-row ["hold_id" "resource" "date" "class" "qty" "expires_at" "status" "expired"])
                  (for [h holds]
                    (csv-row [(:hold/id h) (:hold/resource h) (:hold/date h) (name (:hold/class h))
                              (:hold/qty h) (:hold/expires-at h) (name (:hold/status h))
                              (if (res/expired? h now) "yes" "no")])))))

(defn quote->csv [q]
  (str/join "\n"
            (cons (csv-row ["kind" "date_or_label" "qty" "modifier_bp" "amount" "currency"])
                  (for [l (:quote/lines q)]
                    (csv-row [(name (:line/kind l))
                              (or (:line/date l) (:line/label l))
                              (:line/qty l) (:line/bp l) (:line/amount l)
                              (:quote/currency q)])))))

(defn buckets->json [buckets]
  (str "["
       (str/join ","
                 (for [b buckets]
                   (str "{\"resource\":\"" (json-str (:inv/resource b)) "\","
                        "\"date\":\"" (json-str (:inv/date b)) "\","
                        "\"class\":\"" (name (:inv/class b)) "\","
                        "\"capacity\":" (:inv/capacity b) ","
                        "\"sold\":" (:inv/sold b) ","
                        "\"held\":" (:inv/held b) ","
                        "\"overbook\":" (:inv/overbook b) ","
                        "\"available\":" (res/available b) ","
                        "\"oversold\":" (if (res/oversold? b) "true" "false") "}")))
       "]"))

(defn quote->json [q]
  (str "{\"currency\":\"" (json-str (:quote/currency q)) "\","
       "\"qty\":" (:quote/qty q 0) ","
       "\"subtotal\":" (:quote/subtotal q 0) ","
       "\"tax\":" (:quote/tax q 0) ","
       "\"total\":" (:quote/total q 0) ","
       "\"lines\":["
       (str/join ","
                 (for [l (:quote/lines q)]
                   (str "{\"kind\":\"" (name (:line/kind l)) "\","
                        "\"label\":\"" (json-str (or (:line/date l) (:line/label l))) "\","
                        "\"amount\":" (:line/amount l) "}")))
       "]}"))
