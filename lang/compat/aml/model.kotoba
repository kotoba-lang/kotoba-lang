(ns aml.model)

(def routes [:yabai :malak])
(def levels #{:clear :monitor :challenge :deny :review})

(defn request [id subject opts]
  {:aml/id id
   :aml/subject subject
   :aml/purpose (:purpose opts)
   :aml/case-ref (:case-ref opts)
   :aml/routes (vec (or (:routes opts) routes))
   :aml/requested-at (:requested-at opts)})

(defn result [request route level opts]
  {:aml/id (:aml/id request)
   :aml/route route
   :aml/level level
   :aml/score (:score opts)
   :aml/categories (set (:categories opts))
   :aml/evidence-ref (:evidence-ref opts)
   :aml/asserter (:asserter opts)
   :aml/observed-at (:observed-at opts)
   :aml/non-adjudicating true})
