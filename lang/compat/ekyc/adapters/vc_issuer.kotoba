(ns ekyc.adapters.vc-issuer
  (:require [ekyc.core :as core]))

(defprotocol IVcIssuer
  (issue-credential! [issuer payload opts]))

(defn credential-subject [session completion evidence]
  {:id (:ekyc/subject session)
   :ekyc/session-id (:ekyc/id session)
   :ekyc/status (:ekyc/status completion)
   :ekyc/verified-checks (:ekyc/verified-checks completion)
   :ekyc/evidence (mapv (fn [e]
                          {:check (:ekyc/check e)
                           :evidence-ref (:ekyc/evidence-ref e)
                           :source (:ekyc/source e)
                           :observed-at (:ekyc/observed-at e)})
                        evidence)})

(defn credential-payload [session completion evidence opts]
  {:type ["VerifiableCredential" "EkycCredential"]
   :issuer (:issuer opts)
   :issuanceDate (:issued-at opts)
   :credentialSubject (credential-subject session completion evidence)
   :non-adjudicating true})

(defn issue-completion-credential!
  ([issuer session evidence] (issue-completion-credential! issuer session evidence {}))
  ([issuer session evidence opts]
   (let [completion (core/completion session evidence)]
     (when-not (:ekyc/complete? completion)
       (throw (ex-info "eKYC completion is not verified"
                       {:ekyc/completion completion})))
     (let [payload (credential-payload session completion evidence opts)
           issued (issue-credential! issuer payload opts)]
       {:ekyc/completion completion
        :vc/payload payload
        :vc/result issued
        :vc/ref (or (:vc/ref issued) (:credential-ref issued))}))))

(defn static-issuer
  ([] (static-issuer (atom [])))
  ([issued]
   (reify IVcIssuer
     (issue-credential! [_ payload opts]
       (let [ref (or (:credential-ref opts)
                     (str "kagi://vc/ekyc/" (get-in payload [:credentialSubject :ekyc/session-id])))
             result {:vc/ref ref
                     :issuer (:issuer payload)
                     :payload payload}]
         (swap! issued conj result)
         result)))))
