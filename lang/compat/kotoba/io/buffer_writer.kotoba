(ns kotoba.io.buffer-writer
  "buffer-writer -- addressed on its own.

  Split out of kotoba.lang.io on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.io.writer :refer [Writer write!]]
            [kotoba.io.put :refer [put]])
)

(defn buffer-writer
  "Adapt `buf` as an Writer: each write appends the byte array to the buffer."
  [buf]
  (reify Writer
    (write! [_ chunk] (put buf chunk))))
