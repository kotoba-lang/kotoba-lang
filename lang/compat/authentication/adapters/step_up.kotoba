(ns authentication.adapters.step-up
  (:require [authentication.core :as core]
            [authentication.model :as m]))

(defn- required-factor-types [request opts]
  (let [level (:authn.request/required-level request)
        risk (:risk opts)]
    (case level
      :phishing-resistant [:webauthn]
      :multi-factor (if (#{:high :critical} risk)
                      [:password :webauthn]
                      [:password :totp])
      :single-factor (if (#{:high :critical} risk)
                       [:webauthn]
                       [:password])
      [])))

(defn- challenge-ref [request factor-type]
  (str "authn://challenge/" (:authn.request/id request) "/" (name factor-type)))

(defn factor-requests [request opts]
  (mapv (fn [factor-type idx]
          (m/factor-request
           (str (:authn.request/id request) ":" (name factor-type) ":" idx)
           factor-type
           {:subject (:authn.request/subject request)
            :challenge-ref (challenge-ref request factor-type)
            :purpose (:authn.request/purpose request)
            :created-at (:created-at opts)}))
        (required-factor-types request opts)
        (range)))

(defn step-up-plan [request existing-factors opts]
  (let [decision (core/decide request existing-factors)]
    (if (= :authenticated (:authn.decision/decision decision))
      {:authn.step-up/request-id (:authn.request/id request)
       :authn.step-up/needed? false
       :authn.step-up/decision decision
       :authn.step-up/factor-requests []}
      {:authn.step-up/request-id (:authn.request/id request)
       :authn.step-up/needed? true
       :authn.step-up/decision decision
       :authn.step-up/factor-requests (factor-requests request opts)
       :authn.step-up/risk (:risk opts)})))
