(ns kotoba.io.reader
  "IReader -- addressed on its own.

  Split out of kotoba.lang.io on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defprotocol Reader
  (read! [reader] "Return the next chunk (a byte array) or nil at EOF."))
