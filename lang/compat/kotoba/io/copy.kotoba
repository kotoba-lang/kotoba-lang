(ns kotoba.io.copy
  "copy -- addressed on its own.

  Split out of kotoba.lang.io on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.io.reader :refer [Reader read!]]
            [kotoba.io.writer :refer [Writer write!]])
)

(defn copy
  "Drain `reader` into `writer` until EOF. Pure reduction over read!/write! —
  the host drives the loop (no threads)."
  [reader writer]
  (loop []
    (when-let [chunk (read! reader)]
      (write! writer chunk)
      (recur))))
