(ns authentication.core
  (:require [authentication.model :as m]
            [authentication.ports :as p]))

(defn factor-problems [f]
  (cond-> []
    (not (contains? m/factor-types (:authn.factor/type f)))
    (conj {:authn.problem/code :unknown-factor})))

(def phishing-resistant-types
  "Factor types that are inherently phishing-resistant (public-key /
  platform-biometric ceremonies that can't be replayed against a spoofed
  relying party). Mirrors mfa.core/phishing-resistant-factors."
  #{:webauthn :passkey :faceid :touchid})

(defn achieved-level [factors]
  (let [ok (filter :authn.factor/ok? factors)
        types (set (map :authn.factor/type ok))]
    (cond
      (some types phishing-resistant-types) :phishing-resistant
      (>= (count types) 2) :multi-factor
      (= 1 (count types)) :single-factor
      :else :none)))

(defn level-satisfies? [achieved required]
  (let [rank {:none 0 :single-factor 1 :multi-factor 2 :phishing-resistant 3}]
    (>= (rank achieved 0) (rank required 0))))

(defn decide [request factors]
  (when-let [ps (seq (mapcat factor-problems factors))]
    (throw (ex-info "invalid authentication factor" {:authn/problems ps})))
  (let [level (achieved-level factors)
        ok? (level-satisfies? level (:authn.request/required-level request))]
    (m/decision request (if ok? :authenticated :challenge) factors {:level level})))

(defn factor-request-problems [factor-request]
  (cond-> []
    (not (contains? m/factor-types (:authn.factor-request/type factor-request)))
    (conj {:authn.problem/code :unknown-factor-request-type})))

(defn verify-factor [verifiers factor-request response]
  (when-let [ps (seq (factor-request-problems factor-request))]
    (throw (ex-info "invalid authentication factor request" {:authn/problems ps})))
  (let [factor-type (:authn.factor-request/type factor-request)
        verifier (get verifiers factor-type)]
    (when-not verifier
      (throw (ex-info "missing factor verifier" {:authn.factor/type factor-type})))
    (p/verify-factor! verifier factor-request response)))

(defn authenticate
  "Verify factor-requests through host verifiers, then decide the authentication request."
  [verifiers request factor-requests responses]
  (let [response-by-id (into {} (map (juxt :authn.factor-response/request-id identity) responses))
        factors (mapv (fn [fr]
                        (verify-factor verifiers fr (get response-by-id (:authn.factor-request/id fr))))
                      factor-requests)]
    (decide request factors)))
