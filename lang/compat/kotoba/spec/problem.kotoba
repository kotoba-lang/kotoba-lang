(ns kotoba.spec.problem
  "problem -- addressed on its own.

  Split out of kotoba.lang.spec on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn problem
  ([path in val reason]
   {:path path :in in :val val :reason reason})
  ([path in val reason spec]
   (assoc (problem path in val reason) :spec spec)))
