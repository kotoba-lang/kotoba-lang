(ns aml.datom)

(defn result-datoms [r]
  [{:db/id (str (:aml/id r) ":" (name (:aml/route r)))
    :aml/id (:aml/id r)
    :aml/route (:aml/route r)
    :aml/level (:aml/level r)
    :aml/score (:aml/score r)
    :aml/categories (:aml/categories r)
    :aml/evidence-ref (:aml/evidence-ref r)
    :aml/asserter (:aml/asserter r)
    :aml/observed-at (:aml/observed-at r)
    :aml/non-adjudicating (:aml/non-adjudicating r)}])

(defn screening-datoms [s]
  [{:db/id (:aml/id s)
    :aml/status (:aml/status s)
    :aml/case-ref (:aml/case-ref s)
    :aml/routes (:aml/routes s)
    :aml/non-adjudicating true}])
