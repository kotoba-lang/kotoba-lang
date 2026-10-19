(ns kotoba.walk.walk-depth-exceeded
  "walk-depth-exceeded! -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.walk.walk :refer [walk]])
)

(defn walk-depth-exceeded! [limit]
  (throw (ex-info "coll walk exceeds bounded depth limit"
                   {:kotoba.lang.coll/reason :walk/depth-exceeded :limit limit})))
