(ns kotoba.spec.validate
  "validate -- addressed on its own.

  Split out of kotoba.lang.spec on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.spec.invalid :refer [invalid]]
            [kotoba.spec.valid :refer [valid?]]))

(defn validate
  "Return `x` if it conforms to `spec`, else `:kotoba.lang.spec/invalid`."
  [spec x]
  (if (valid? spec x) x invalid))
