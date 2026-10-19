(ns kotoba.coll.project
  "project -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn project
  "Return a relation (set of maps) built from `xrel` by keeping only the
  keys in `ks` of each map. Mirrors clojure.set/project. Maps that become
  identical after projecting (because the dropped keys were the only
  difference) collapse into one entry, since the result is a set."
  [xrel ks]
  (set (map #(select-keys % ks) xrel)))
