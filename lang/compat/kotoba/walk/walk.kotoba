(ns kotoba.walk.walk
  "walk -- addressed on its own.

  Split out of kotoba.lang.coll on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn walk
  "The most general tree walker: recursively applies `inner` to each
  element of `form`, then applies `outer` to the result. Mirrors
  clojure.walk/walk, unbounded (no depth ceiling -- see the section header
  above). `prewalk`/`postwalk` below are the usual entry points; use `walk`
  directly only when you need a different inner/outer split than either."
  [inner outer form]
  (cond
    (list? form) (outer (apply list (map inner form)))
    (map-entry? form) (outer [(inner (key form)) (inner (val form))])
    (seq? form) (outer (doall (map inner form)))
    (record? form) (outer (reduce (fn [r x] (conj r (inner x))) form form))
    (coll? form) (outer (into (empty form) (map inner form)))
    :else (outer form)))
