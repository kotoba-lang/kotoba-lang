(ns kotoba.lang.capability-catalog
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn read-authority
  "Read the repository's canonical semantic capability authority."
  []
  (-> (io/file "lang" "capability-catalog.edn") slurp edn/read-string))

(defn validate!
  "Fail closed when semantic names, source operations, effects, or wire IDs drift."
  [catalog]
  (let [entries (:capabilities catalog)
        ids (map :compiler-wire-id (vals entries))]
    (when-not (= 1 (:kotoba.lang.capability-catalog/version catalog))
      (throw (ex-info "unsupported capability catalog version" {:catalog catalog})))
    (when-not (and (map? entries) (seq entries))
      (throw (ex-info "capability catalog must contain entries" {:catalog catalog})))
    (doseq [[semantic {:keys [source-operation effect compiler-wire-id]}] entries]
      (when-not (and (qualified-keyword? semantic)
                     (qualified-symbol? source-operation)
                     (qualified-keyword? effect)
                     (integer? compiler-wire-id)
                     (<= 1 compiler-wire-id 255))
        (throw (ex-info "invalid semantic capability entry"
                        {:semantic semantic :entry (get entries semantic)}))))
    (when-not (= (count ids) (count (distinct ids)))
      (throw (ex-info "compiler wire IDs must be unique" {:ids ids})))
    catalog))
