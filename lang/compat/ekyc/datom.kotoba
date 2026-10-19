(ns ekyc.datom)

(defn session-datoms [s]
  [{:db/id (:ekyc/id s)
    :ekyc/subject (:ekyc/subject s)
    :ekyc/status (:ekyc/status s)
    :ekyc/provider (:ekyc/provider s)
    :ekyc/purpose (:ekyc/purpose s)
    :ekyc/session-ref (:ekyc/session-ref s)
    :ekyc/required-checks (:ekyc/required-checks s)
    :ekyc/created-at (:ekyc/created-at s)
    :ekyc/expires-at (:ekyc/expires-at s)}])

(defn evidence-datoms [e]
  [{:db/id (str (:ekyc/id e) ":" (name (:ekyc/check e)))
    :ekyc/id (:ekyc/id e)
    :ekyc/check (:ekyc/check e)
    :ekyc/status (:ekyc/status e)
    :ekyc/provider (:ekyc/provider e)
    :ekyc/evidence-ref (:ekyc/evidence-ref e)
    :ekyc/source (:ekyc/source e)
    :ekyc/confidence (:ekyc/confidence e)
    :ekyc/observed-at (:ekyc/observed-at e)
    :ekyc/non-adjudicating (:ekyc/non-adjudicating e)}])

(defn decision-datoms [d]
  [{:db/id (:ekyc/id d)
    :ekyc/complete? (:ekyc/complete? d)
    :ekyc/missing-checks (:ekyc/missing-checks d)
    :ekyc/verified-checks (:ekyc/verified-checks d)
    :ekyc/status (:ekyc/status d)}])
