(ns kotoba.json.emit
  "emit -- addressed on its own.

  Split out of json.core on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.lang.text :as str]
            [kotoba.json.esc :refer [esc]]
            [kotoba.json.kstr :refer [kstr]]
            [kotoba.json.numstr :refer [numstr]])
)

(defn emit [x ind pretty?]
  (let [pad  (apply str (repeat ind "  "))
        pad+ (str pad "  ")
        sep  (if pretty? ",\n" ",")
        kv   (if pretty? ": " ":")]
    (cond
      (nil? x)                      "null"
      (boolean? x)                  (str x)
      (number? x)                   (if pretty? (str x) (numstr x))
      (or (string? x) (keyword? x)) (str \" (esc (kstr x)) \")
      (map? x) (if (empty? x) "{}"
                 (let [entries (if pretty? x (sort-by (comp kstr key) x))]
                   (str "{"
                        (when pretty? "\n")
                        (str/join sep
                                  (for [[k v] entries]
                                    (str (when pretty? pad+)
                                         \" (esc (kstr k)) \" kv
                                         (emit v (inc ind) pretty?))))
                        (when pretty? (str "\n" pad))
                        "}")))
      (sequential? x) (if (empty? x) "[]"
                        (str "["
                             (when pretty? "\n")
                             (str/join sep
                                       (for [v x]
                                         (str (when pretty? pad+)
                                              (emit v (inc ind) pretty?))))
                             (when pretty? (str "\n" pad))
                             "]"))
      :else (str \" (esc (str x)) \"))))
