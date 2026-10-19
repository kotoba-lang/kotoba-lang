(ns kotoba.edn.literal-controls
  "literal-controls -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(def literal-controls
  "The C0 controls text keeps literal: tab, newline, carriage return.

  Not because `pr-str` escapes them inside strings — it does — but because
  this function takes TEXT, and the whitespace between forms in a
  pretty-printed artefact is raw code 9/10/13 that no writer touched.
  Escaping it destroys the document."
  #{9 10 13})
