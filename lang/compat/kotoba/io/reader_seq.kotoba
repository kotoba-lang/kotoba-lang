(ns kotoba.io.reader-seq
  "reader-seq -- addressed on its own.

  Split out of kotoba.lang.io on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.io.reader :refer [Reader read!]])
)

(defn reader-seq
  "A lazy seq of chunks from `reader` until EOF. Realizes on consumption."
  [reader]
  (lazy-seq
   (when-let [chunk (read! reader)]
     (cons chunk (reader-seq reader)))))
