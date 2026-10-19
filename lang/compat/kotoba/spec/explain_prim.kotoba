(ns kotoba.spec.explain-prim
  "explain-prim -- addressed on its own.

  Split out of kotoba.lang.spec on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.spec.int-like :refer [int-like?]]
            [kotoba.spec.problem :refer [problem]]))

(defn explain-prim [spec x path in]
  (let [t (:type spec)]
    (case t
      :any      '()
      :string   (if (string? x) '() (list (problem path in x :type/string)))
      :keyword  (if (keyword? x) '() (list (problem path in x :type/keyword)))
      :boolean  (if (boolean? x) '() (list (problem path in x :type/boolean)))
      :int      (if (int-like? x) '() (list (problem path in x :type/int)))
      :double   (if (and (number? x) (not (boolean? x)))
                  '() (list (problem path in x :type/double)))
      :fn       (if ((:pred spec) x) '() (list (problem path in x :fn/predicate)))
      ;; composite or unknown handled below
      nil)))
