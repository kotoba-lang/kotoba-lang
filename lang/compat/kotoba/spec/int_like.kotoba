(ns kotoba.spec.int-like
  "int-like? -- addressed on its own.

  Split out of kotoba.lang.spec on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn int-like?
  "True for an integer-valued number (covers boxed Long/Integer and
  integer-valued doubles, but not booleans — booleans are not numbers in
  Clojure). Portable across JVM/CLJS."
  [x]
  (and (number? x)
       (not (true? x))
       (not (false? x))
       (zero? (mod (double x) 1.0))))
