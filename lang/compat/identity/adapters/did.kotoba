(ns identity.adapters.did
  (:require [identity.model :as m]))

(defprotocol IDidResolver
  (resolve-did! [resolver did opts]))

(defn did->subject [did did-document opts]
  (m/subject did
             (or (:subject-type opts) :person)
             {:did did
              :labels (or (:labels opts) #{:did})
              :source (or (:controller did-document) (:id did-document) did)}))

(defn resolve-subject!
  ([resolver did] (resolve-subject! resolver did {}))
  ([resolver did opts]
   (let [doc (resolve-did! resolver did opts)]
     (when (:error doc)
       (throw (ex-info "DID resolution failed" {:did did :did/document doc})))
     {:identity/subject (did->subject did doc opts)
      :did/document doc})))

(defn static-resolver [documents]
  (reify IDidResolver
    (resolve-did! [_ did _opts]
      (or (get documents did)
          {:error :did/not-found
           :did did}))))
