(ns kotoba.io.byte-buffer
  "byte-buffer -- addressed on its own.

  Split out of kotoba.lang.io on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn byte-buffer
  "A pure growable byte buffer backed by a persistent vector of unsigned byte
  values (ints 0–255). Portable to WASM (no ByteArrayOutputStream)."
  []
  (atom []))
