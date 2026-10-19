(ns kotoba.json.kstr
  "kstr -- addressed on its own.

  Split out of json.core on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn kstr [k] (if (keyword? k) (name k) (str k)))
