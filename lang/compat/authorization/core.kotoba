(ns authorization.core
  (:require [authorization.model :as m]
            [authorization.ports :as p]))

(defn request-problems [request]
  (cond-> []
    (nil? (:authz.request/id request))
    (conj {:authz.problem/code :missing-request-id})
    (nil? (:authz.request/principal request))
    (conj {:authz.problem/code :missing-principal})
    (nil? (:authz.request/action request))
    (conj {:authz.problem/code :missing-action})
    (nil? (:authz.request/resource request))
    (conj {:authz.problem/code :missing-resource})))

(defn decision-problems [request decision]
  (cond-> []
    (not= (:authz.request/id request) (:authz.decision/request-id decision))
    (conj {:authz.problem/code :request-id-mismatch})
    (not (contains? m/decisions (:authz.decision/decision decision)))
    (conj {:authz.problem/code :unknown-decision})
    (and (= :deny (:authz.decision/decision decision))
         (nil? (:authz.decision/reason decision)))
    (conj {:authz.problem/code :deny-without-reason})))

(defn- valid-request! [request]
  (when-let [ps (seq (request-problems request))]
    (throw (ex-info "invalid authorization request" {:authz/problems ps})))
  request)

(defn- valid-decision! [request decision]
  (when-let [ps (seq (decision-problems request decision))]
    (throw (ex-info "invalid authorization decision" {:authz/problems ps})))
  decision)

(defn authorize [port request]
  (valid-request! request)
  (let [d (p/decide! port request)]
    (valid-decision! request d)))
