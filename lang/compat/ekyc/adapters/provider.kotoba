(ns ekyc.adapters.provider
  (:require [ekyc.model :as m]
            [ekyc.ports :as p]))

(defprotocol IEkycProviderClient
  (create-session! [client payload opts])
  (upload-evidence! [client payload opts])
  (retrieve-result! [client payload opts]))

(defprotocol IEvidenceCustody
  (commit-evidence! [custody payload opts]))

(defn- session-payload [session]
  {:id (:ekyc/id session)
   :subject (:ekyc/subject session)
   :purpose (:ekyc/purpose session)
   :required-checks (:ekyc/required-checks session)
   :provider (:ekyc/provider session)
   :created-at (:ekyc/created-at session)
   :expires-at (:ekyc/expires-at session)})

(defn- evidence-payload [session evidence]
  {:session-id (:ekyc/id session)
   :session-ref (:ekyc/session-ref session)
   :check (:ekyc/check evidence)
   :source (:ekyc/source evidence)
   :evidence-ref (:ekyc/evidence-ref evidence)
   :observed-at (:ekyc/observed-at evidence)})

(defn- result-evidence [session item]
  (m/evidence session (:check item) (:status item)
              {:evidence-ref (:evidence-ref item)
               :source (:source item)
               :confidence (:confidence item)
               :observed-at (:observed-at item)}))

(defn provider-port [client custody opts]
  (reify p/IEkyc
    (start-session! [_ session]
      (let [response (create-session! client (session-payload session) opts)]
        {:ekyc/status (or (:status response) :submitted)
         :ekyc/session-ref (:session-ref response)
         :ekyc/provider (or (:provider response) (:ekyc/provider session))
         :ekyc/expires-at (or (:expires-at response) (:ekyc/expires-at session))}))
    (submit-evidence! [_ session evidence]
      (let [custody-ref (when custody
                          (:evidence-ref
                           (commit-evidence! custody (evidence-payload session evidence) opts)))
            response (upload-evidence! client
                                       (assoc (evidence-payload session evidence)
                                              :custody-ref custody-ref)
                                       opts)]
        {:ekyc/status (or (:status response) (:ekyc/status evidence))
         :ekyc/evidence-ref (or (:evidence-ref response) custody-ref (:ekyc/evidence-ref evidence))
         :ekyc/confidence (:confidence response)
         :ekyc/observed-at (or (:observed-at response) (:ekyc/observed-at evidence))}))
    (fetch-result [_ session]
      (let [response (retrieve-result! client {:session-id (:ekyc/id session)
                                               :session-ref (:ekyc/session-ref session)}
                                       opts)]
        {:ekyc/status (or (:status response) :review)
         :ekyc/evidence (mapv #(result-evidence session %) (:evidence response))}))))
