(ns kotoba-lang-maturity
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [xmile.execute :as execute]
            [xmile.model :as model]
            [xmile.validate :as validate]))

(def evidence-path
  "docs/system-dynamics/kotoba-lang-maturity-evidence.edn")

(def results-path
  "docs/system-dynamics/kotoba-lang-maturity-results.edn")

(defn- repeated-outcomes [{:keys [failures successes]}]
  (vec (concat (repeat failures 0.0) (repeat successes 1.0))))

(defn- piecewise-equation
  "XMILE IF expression for VALUES at consecutive unit-width time intervals."
  [values]
  (let [indexed (vec (map-indexed vector values))]
    (reduce (fn [else-expr [index value]]
              (format "IF TIME < %d THEN %.1f ELSE (%s)"
                      (inc index) (double value) else-expr))
            (format "%.1f" (double (last values)))
            (reverse (butlast indexed)))))

(defn- maturity-model [evidence adjustment-time]
  (let [ci (:ci-history evidence)
        language-outcomes (repeated-outcomes (:kotoba-lang ci))
        language-count (count language-outcomes)
        release-time language-count
        adapter-time (inc release-time)
        stop-time (inc adapter-time)
        prior (get-in evidence [:assumptions :reliability-prior])
        repository (get-in evidence [:repository-maturity :composite])
        verification (get-in evidence [:verification :score])
        boundary (get-in evidence [:dependency-boundary :score])
        outcome (piecewise-equation language-outcomes)]
    (-> (model/model
         "kotoba-lang-engineering-maturity"
         {:xmile/sim-specs
          (model/sim-specs 0.0 (double stop-time)
                           {:xmile/dt 1.0 :xmile/method :euler
                            :xmile/time-units "CI runs"})})
        (model/add-variable
         (model/stock "LanguageReliability" (str prior)
                      {:xmile/inflows #{"LanguageAdjustment"}}))
        (model/add-variable
         (model/flow
          "LanguageAdjustment"
          (format "IF TIME < %d THEN ((%s) - LanguageReliability) / %.1f ELSE 0"
                  language-count outcome adjustment-time)))
        (model/add-variable
         (model/stock "ReleaseReliability" (str prior)
                      {:xmile/inflows #{"ReleaseAdjustment"}}))
        (model/add-variable
         (model/flow
          "ReleaseAdjustment"
          (format "IF TIME >= %d AND TIME < %d THEN (1 - ReleaseReliability) / %.1f ELSE 0"
                  release-time (inc release-time) adjustment-time)))
        (model/add-variable
         (model/stock "AdapterReliability" (str prior)
                      {:xmile/inflows #{"AdapterAdjustment"}}))
        (model/add-variable
         (model/flow
          "AdapterAdjustment"
          (format "IF TIME >= %d AND TIME < %d THEN (1 - AdapterReliability) / %.1f ELSE 0"
                  adapter-time (inc adapter-time) adjustment-time)))
        (model/add-variable
         (model/aux "RepositoryMaturity" (str repository)))
        (model/add-variable
         (model/aux "Verification" (str verification)))
        (model/add-variable
         (model/aux "DependencyBoundary" (str boundary)))
        (model/add-variable
         (model/aux
          "SystemReliability"
          "(LanguageReliability * ReleaseReliability * AdapterReliability) ^ (1 / 3)"))
        (model/add-variable
         (model/aux
          "OverallMaturity"
          "RepositoryMaturity ^ 0.45 * SystemReliability ^ 0.30 * Verification ^ 0.15 * DependencyBoundary ^ 0.10")))))

(defn- final-value [result variable]
  (last (get-in result [:xmile/series variable])))

(defn- calculate-scenario [evidence adjustment-time]
  (let [xmile-model (maturity-model evidence adjustment-time)
        problems (validate/validate xmile-model)]
    (when-not (validate/valid? problems)
      (throw (ex-info "invalid maturity XMILE model" {:problems problems})))
    (let [result (execute/run xmile-model)]
      {:adjustment-time-runs adjustment-time
       :language-reliability (final-value result "LanguageReliability")
       :release-reliability (final-value result "ReleaseReliability")
       :adapter-reliability (final-value result "AdapterReliability")
       :system-reliability (final-value result "SystemReliability")
       :overall-maturity (final-value result "OverallMaturity")})))

(defn calculate [evidence]
  (let [times (get-in evidence [:assumptions :adjustment-times-runs])
        central (get-in evidence [:assumptions :central-adjustment-time-runs])
        scenarios (mapv #(calculate-scenario evidence %) times)]
    {:result/version 1
     :result/as-of (:model/as-of evidence)
     :result/scope (:model/scope evidence)
     :repository-static-maturity
     (get-in evidence [:repository-maturity :composite])
     :verification-score (get-in evidence [:verification :score])
     :dependency-boundary-score (get-in evidence [:dependency-boundary :score])
     :central-adjustment-time-runs central
     :central (first (filter #(= central (:adjustment-time-runs %)) scenarios))
     :sensitivity scenarios
     :result/limits
     {:not-measured (:model/excludes evidence)
      :forecast? false
      :note "XMILE integrates observed CI events. Tau and aggregation weights are sensitivity assumptions, not learned parameters."}}))

(defn -main [& args]
  (let [result (calculate (edn/read-string (slurp evidence-path)))]
    (when (some #{"--check"} args)
      (let [checked (edn/read-string (slurp results-path))]
        (when-not (= checked result)
          (throw (ex-info "checked maturity result is stale"
                          {:expected result :checked checked})))))
    (pprint/pprint result)))
