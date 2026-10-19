(ns kotoba.io.writer
  "IWriter -- addressed on its own.

  Split out of kotoba.lang.io on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defprotocol Writer
  (write! [writer chunk] "Append `chunk` (a byte array). Returns nil."))
