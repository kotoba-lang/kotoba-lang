(ns kotoba.io.put
  "put -- addressed on its own.

  Split out of kotoba.lang.io on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn put
  "Append a byte array `arr` to `buf`. Each byte stored as an unsigned int 0–255."
  [buf ^bytes arr]
  (swap! buf into (map #(bit-and (int %) 0xFF) (seq arr)))
  buf)
