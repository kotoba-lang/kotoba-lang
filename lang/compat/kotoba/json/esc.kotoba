(ns kotoba.json.esc
  "esc -- addressed on its own.

  Split out of json.core on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.json.char-code :refer [char-code]]
            [kotoba.json.hex4 :refer [hex4]])
)

(defn esc [s]
  (apply str
         (map (fn [ch]
                (case ch
                  \" "\\\""
                  \\ "\\\\"
                  \backspace "\\b"
                  \formfeed "\\f"
                  \newline "\\n"
                  \return "\\r"
                  \tab "\\t"
                  ;; RFC 8259 §7: EVERY control character U+0000-U+001F must
                  ;; be escaped, not just the 7 named above -- the fallback
                  ;; case used to pass the rest through raw, producing a
                  ;; JSON string literal with a literal control byte
                  ;; embedded in it (invalid per both python's json and jq).
                  (if (< (char-code ch) 0x20)
                    (str "\\u" (hex4 (char-code ch)))
                    (str ch))))
              (str s))))
