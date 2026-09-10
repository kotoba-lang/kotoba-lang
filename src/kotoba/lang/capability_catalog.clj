(ns kotoba.lang.capability-catalog
  (:require [kotoba.lang.edn :as edn]
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
    (doseq [[semantic entry] entries]
      (let [{:keys [source-operation effect compiler-wire-id]} entry]
        (when-not (and (qualified-keyword? semantic)
                       (qualified-symbol? source-operation)
                       (qualified-keyword? effect)
                       (integer? compiler-wire-id)
                       (<= 1 compiler-wire-id 255)
                       ;; Root ADR-2607280100 D5: classification is part of the
                       ;; effect DESCRIPTOR, so that a caller cannot omit it.
                       ;; An entry added here without one is refused at compile
                       ;; time by `kotoba.compiler.effect-classification` in
                       ;; amu; refusing it in the authority as well means the
                       ;; omission is caught where it is made.
                       ;;
                       ;; Presence and keyword-ness only. WHICH labels are
                       ;; legal is `kotoba.security.information-flow/ranks` and
                       ;; is not restated here -- D1 of the same ADR exists
                       ;; because that rank map had two copies, and this
                       ;; repository does not depend on `security`. amu ranks
                       ;; it; this file only refuses to leave it unsaid.
                       (keyword? (:kotoba.security/classification entry)))
          (throw (ex-info "invalid semantic capability entry"
                          {:semantic semantic :entry entry})))))
    (when-not (= (count ids) (count (distinct ids)))
      (throw (ex-info "compiler wire IDs must be unique" {:ids ids})))
    catalog))
