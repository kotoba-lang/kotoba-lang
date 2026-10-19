(ns ekyc.model)

(def checks #{:document-authenticity :document-ocr :liveness :face-match :address
              :pep :sanctions :adverse-media :manual-review})
(def statuses #{:created :submitted :verified :rejected :expired :review})

(defn session [id subject opts]
  {:ekyc/id id
   :ekyc/subject subject
   :ekyc/status (get opts :status :created)
   :ekyc/provider (:provider opts)
   :ekyc/purpose (:purpose opts)
   :ekyc/session-ref (:session-ref opts)
   :ekyc/required-checks (set (:required-checks opts))
   :ekyc/created-at (:created-at opts)
   :ekyc/expires-at (:expires-at opts)})

(defn evidence [session check status opts]
  {:ekyc/id (:ekyc/id session)
   :ekyc/check check
   :ekyc/status status
   :ekyc/provider (:ekyc/provider session)
   :ekyc/evidence-ref (:evidence-ref opts)
   :ekyc/source (:source opts)
   :ekyc/confidence (:confidence opts)
   :ekyc/observed-at (:observed-at opts)
   :ekyc/non-adjudicating true})
