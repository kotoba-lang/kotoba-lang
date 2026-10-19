(ns authorization.adapters.cacao-policy
  (:require [authorization.model :as m]
            [authorization.ports :as p]))

(defprotocol IPolicyEngine
  (evaluate-policy! [engine request opts]))

(defprotocol ICacaoCapabilityVerifier
  (verify-capability! [verifier capability-ref opts]))

(defn cacao-policy-port
  ([policy-engine capability-verifier] (cacao-policy-port policy-engine capability-verifier {}))
  ([policy-engine capability-verifier opts]
   (reify p/IAuthorization
     (decide! [_ request]
       (let [capability (when-let [ref (:authz.request/capability-ref request)]
                          (verify-capability! capability-verifier ref opts))
             policy (evaluate-policy! policy-engine
                                      (assoc-in request [:authz.request/context :capability] capability)
                                      opts)
             allowed? (and (not= false (:ok? capability))
                           (true? (:allow? policy)))]
         (m/decision request
                     (if allowed? :allow :deny)
                     {:by :cacao-policy
                      :reason (or (:reason policy)
                                  (when-not allowed? :capability-or-policy-denied))
                      :policy-ref (:policy-ref policy)
                      :policy-version (:policy-version policy)
                      :effect-trace (:effect-trace policy)
                      :obligations (:obligations policy)
                      :issued-at (:issued-at policy)}))))))

(defn static-policy-engine [result]
  (reify IPolicyEngine
    (evaluate-policy! [_ _request _opts] result)))

(defn static-capability-verifier [result]
  (reify ICacaoCapabilityVerifier
    (verify-capability! [_ _capability-ref _opts] result)))
