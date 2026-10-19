(ns kotoba.edn.option-keys
  "option-keys -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(def option-keys
  "Every option `read-string`/`read-all` accept. An unknown key is refused
  rather than ignored: a typo in an option map is silently the strict default,
  which is the failure this namespace spends its whole design avoiding."
  #{:readers :default :eof})
