(ns kotoba.json.hex-val
  "hex-val -- addressed on its own.

  Split out of json.core on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.json.char-code :refer [char-code]])
)

(defn hex-val
  "One hex digit's value.

  The bounds are numeric literals rather than `(int \\0)` and friends,
  because that spelling was wrong on ClojureScript in a way that decoded
  rather than threw: every `\\uXXXX` escape containing a-f or A-F silently
  became a different character. `\\u65e5` decoded as U+6505 instead of
  U+65E5 -- 攅 where 日 was written."
  [ch]
  (let [i (char-code ch)]
    (cond
      (<= 48 i 57) (- i 48)                ; 0-9
      (<= 97 i 102) (+ 10 (- i 97))        ; a-f
      (<= 65 i 70) (+ 10 (- i 65))         ; A-F
      :else (throw (ex-info "invalid JSON unicode escape" {:char ch})))))
