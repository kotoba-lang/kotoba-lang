(ns aml.adapters.schema-conformance
  (:require [aml.core :as core]))

(def required-result-keys
  #{:aml/id :aml/route :aml/level :aml/non-adjudicating})

(defn conform-result! [result]
  (let [missing (remove #(contains? result %) required-result-keys)
        problems (concat (map (fn [k] {:aml.problem/code :schema/missing-key :key k}) missing)
                         (core/problems result))]
    (when-let [ps (seq problems)]
      (throw (ex-info "AML result schema conformance failed" {:aml/problems (vec ps)})))
    result))

(defn conform-screening! [screening]
  (doseq [result (:aml/results screening)]
    (conform-result! result))
  screening)
