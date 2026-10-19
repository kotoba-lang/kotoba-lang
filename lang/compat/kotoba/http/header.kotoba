(ns kotoba.http.header
  "header -- addressed on its own.

  Split out of kotoba.lang.http on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.lang.text :as str])
)

(defn header
  "Case-insensitive header lookup. Returns the first value for `name`, or
  `default` (nil) if absent."
  ([headers name] (header headers name nil))
  ([headers name default]
   (let [target (str/lower (str name))]
     (reduce (fn [acc [k v]]
               (if (= (str/lower (str k)) target)
                 (reduced v)
                 acc))
             default headers))))
