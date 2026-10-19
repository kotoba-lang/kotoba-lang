(ns kotoba.coll.select
  "select -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn select
  "Return the subset of relation `xrel` (a set of maps) for which `pred` is
  true. Mirrors clojure.set/select. `xrel` need not be a relation -- select
  works over any set -- but is named `xrel` to match clojure.set's own
  parameter name, since that is its primary use."
  [pred xrel]
  (reduce (fn [s k] (if (pred k) s (disj s k))) xrel xrel))
