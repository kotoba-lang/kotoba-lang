(ns authentication.model)

(def factor-types #{:password :webauthn :passkey :faceid :touchid :totp :hotp
                    :cacao :oauth :oidc :saml :recovery})
(def levels #{:none :single-factor :multi-factor :phishing-resistant})
(def decisions #{:authenticated :challenge :deny})

(defn factor [id type ok? opts]
  {:authn.factor/id id
   :authn.factor/type type
   :authn.factor/ok? (boolean ok?)
   :authn.factor/subject (:subject opts)
   :authn.factor/evidence-ref (:evidence-ref opts)
   :authn.factor/assurance (:assurance opts)
   :authn.factor/at (:at opts)})

(defn factor-request [id type opts]
  {:authn.factor-request/id id
   :authn.factor-request/type type
   :authn.factor-request/subject (:subject opts)
   :authn.factor-request/challenge-ref (:challenge-ref opts)
   :authn.factor-request/purpose (:purpose opts)
   :authn.factor-request/created-at (:created-at opts)})

(defn request [id subject opts]
  {:authn.request/id id
   :authn.request/subject subject
   :authn.request/required-level (get opts :required-level :single-factor)
   :authn.request/purpose (:purpose opts)
   :authn.request/created-at (:created-at opts)})

(defn decision [request decision factors opts]
  {:authn.decision/request-id (:authn.request/id request)
   :authn.decision/subject (:authn.request/subject request)
   :authn.decision/decision decision
   :authn.decision/level (:level opts)
   :authn.decision/factors (vec factors)
   :authn.decision/issued-at (:issued-at opts)})
