(ns authorization.adapters.policy
  (:require [authorization.model :as m]
            [authorization.ports :as p]))

(defprotocol IPolicyEngine
  (evaluate! [engine payload opts]))

(defn- payload [request]
  {:request-id (:authz.request/id request)
   :principal (:authz.request/principal request)
   :action (:authz.request/action request)
   :resource (:authz.request/resource request)
   :context (:authz.request/context request)
   :capability-ref (:authz.request/capability-ref request)})

(defn- normalize-decision [request response]
  (m/decision request (:decision response)
              {:by (:by response)
               :reason (:reason response)
               :policy-ref (:policy-ref response)
               :policy-version (:policy-version response)
               :effect-trace (:effect-trace response)
               :obligations (:obligations response)
               :issued-at (:issued-at response)}))

(defn policy-port [engine opts]
  (reify p/IAuthorization
    (decide! [_ request]
      (normalize-decision request (evaluate! engine (payload request) opts)))))
