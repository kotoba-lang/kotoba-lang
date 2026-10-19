(ns authorization.model)

(def decisions #{:allow :deny :not-applicable})

(defn request [id principal action resource opts]
  {:authz.request/id id
   :authz.request/principal principal
   :authz.request/action action
   :authz.request/resource resource
   :authz.request/context (:context opts)
   :authz.request/capability-ref (:capability-ref opts)})

(defn decision [request decision opts]
  {:authz.decision/request-id (:authz.request/id request)
   :authz.decision/decision decision
   :authz.decision/by (:by opts)
   :authz.decision/reason (:reason opts)
   :authz.decision/policy-ref (:policy-ref opts)
   :authz.decision/policy-version (:policy-version opts)
   :authz.decision/effect-trace (:effect-trace opts)
   :authz.decision/obligations (vec (:obligations opts))
   :authz.decision/issued-at (:issued-at opts)})
