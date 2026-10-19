(ns kotoba.edn.token-end
  "token-end -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.closing :refer [closing?]]
            [kotoba.edn.opening :refer [opening?]]
            [kotoba.edn.separator :refer [separator?]]))

(defn token-end
  "Index one past the bare token starting at `i`."
  [text i]
  (let [n (count text)]
    (loop [j i]
      (if (or (>= j n)
              (separator? (.charAt ^String text j))
              (opening? (.charAt ^String text j))
              (closing? (.charAt ^String text j))
              (= (.charAt ^String text j) \")
              (= (.charAt ^String text j) \;))
        j
        (recur (inc j))))))
