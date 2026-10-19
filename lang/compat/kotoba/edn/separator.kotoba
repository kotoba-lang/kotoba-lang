(ns kotoba.edn.separator
  "separator? -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn separator? [ch]
  (or (= ch \,) (= ch \space) (= ch \tab)
      (= ch \newline) (= ch \return)))
