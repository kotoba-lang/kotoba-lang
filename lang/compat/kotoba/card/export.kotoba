(ns kotoba.card.export
  "Operator-facing export for a card-payment-processing actor.

  Renders PAN validation, ISO 8583 messages and authorization decisions to
  CSV and JSON for settlement audit and downstream reporting. PANs are masked
  to the last 4 digits in exports — raw PANs are never persisted. Pure data
  → text: no network."
  (:require [clojure.string :as str]
            [kotoba.card :as card]))

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

(defn- mask [pan]
  (let [d (str (or pan "")) n (count d)]
    (if (>= n 4) (str "•••• " (subs d (- n 4))) d)))

(defn pans->csv [pans]
  (str/join "\n"
    (cons (csv-row ["pan_masked" "valid" "network" "error"])
          (for [p pans]
            (let [r (card/validate-pan p) parsed (:card/parsed r)]
              (csv-row [(mask p)
                        (if (:card/valid? r) "yes" "no")
                        (or (some-> (:card/network parsed) name) "")
                        (name (or (:card/error r) :ok))]))))))

(defn messages->csv [messages]
  (str/join "\n"
    (cons (csv-row ["mti" "valid" "class" "data_elements" "bitmap"])
          (for [m messages]
            (let [r (card/validate-message m)]
              (csv-row [(or (:card/mti m) "")
                        (if (:card/valid? r) "yes" "no")
                        (or (:card/class m) "")
                        (count (keys (:card/de m)))
                        (count (:card/bitmap m))]))))))

(defn authorizations->csv [authorizations]
  (str/join "\n"
    (cons (csv-row ["pan_masked" "amount" "action" "approved" "rrn" "reason"])
          (for [a authorizations]
            (csv-row [(mask (:card/pan a))
                      (:card/amount a)
                      (name (:card/action a))
                      (card/authorized-amount a)
                      (or (:card/rrn a) "")
                      (or (:card/reason a) "")])))))

(defn pans->json [pans]
  (str "["
       (str/join ","
                 (for [p pans]
                   (let [r (card/validate-pan p) parsed (:card/parsed r)]
                     (str "{\"pan_masked\":\"" (json-str (mask p)) "\","
                          "\"valid\":" (if (:card/valid? r) "true" "false") ","
                          "\"network\":\"" (json-str (:card/network parsed)) "\"}"))))
       "]"))

(defn authorizations->json [authorizations]
  (str "["
       (str/join ","
                 (for [a authorizations]
                   (str "{\"pan_masked\":\"" (json-str (mask (:card/pan a))) "\","
                        "\"amount\":" (or (:card/amount a) 0) ","
                        "\"action\":\"" (name (:card/action a)) "\","
                        "\"approved\":" (card/authorized-amount a) ","
                        "\"rrn\":\"" (json-str (:card/rrn a)) "\"}")))
       "]"))
