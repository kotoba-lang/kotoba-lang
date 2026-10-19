(ns aml.core
  (:require [aml.model :as m]
            [aml.ports :as p]))

(defn problems [record]
  (cond-> []
    (and (contains? record :aml/case-ref) (nil? (:aml/case-ref record)))
    (conj {:aml.problem/code :missing-case-ref})
    (seq (remove (set m/routes) (:aml/routes record)))
    (conj {:aml.problem/code :unknown-route})
    (and (:aml/route record) (not (contains? (set m/routes) (:aml/route record))))
    (conj {:aml.problem/code :unknown-route})
    (and (:aml/level record) (not (contains? m/levels (:aml/level record))))
    (conj {:aml.problem/code :unknown-level})
    (and (:aml/level record) (false? (:aml/non-adjudicating record)))
    (conj {:aml.problem/code :adjudicating-result})))

(defn- valid! [record]
  (when-let [ps (seq (problems record))]
    (throw (ex-info "invalid AML record" {:aml/problems ps})))
  record)

(defn status [results]
  (let [levels (set (map :aml/level results))]
    (cond
      (contains? levels :deny) :hold
      (some levels [:challenge :monitor :review]) :review
      (seq results) :clear
      :else :not-run)))

(defn screen [port request]
  (valid! request)
  (let [results (mapv #(valid! (p/screen! port request %)) (:aml/routes request))]
    {:aml/status (status results)
     :aml/case-ref (:aml/case-ref request)
     :aml/routes (:aml/routes request)
     :aml/results results}))
