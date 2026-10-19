(ns authorization.datom)

(defn request-datoms [request]
  [{:db/id (:authz.request/id request)
    :authz.request/principal (:authz.request/principal request)
    :authz.request/action (:authz.request/action request)
    :authz.request/resource (:authz.request/resource request)
    :authz.request/context (:authz.request/context request)
    :authz.request/capability-ref (:authz.request/capability-ref request)}])

(defn decision-datoms [decision]
  [{:db/id (:authz.decision/request-id decision)
    :authz.decision/decision (:authz.decision/decision decision)
    :authz.decision/by (:authz.decision/by decision)
    :authz.decision/reason (:authz.decision/reason decision)
    :authz.decision/policy-ref (:authz.decision/policy-ref decision)
    :authz.decision/policy-version (:authz.decision/policy-version decision)
    :authz.decision/effect-trace (:authz.decision/effect-trace decision)
    :authz.decision/obligations (:authz.decision/obligations decision)
    :authz.decision/issued-at (:authz.decision/issued-at decision)}])
