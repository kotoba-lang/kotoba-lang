(ns kotoba.json.ws
  "ws? -- addressed on its own.

  Split out of json.core on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn ws? [ch] (contains? #{\space \tab \newline \return} ch))
