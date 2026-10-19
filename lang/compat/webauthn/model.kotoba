(ns webauthn.model)

(def ceremonies #{:registration :authentication :prf})
(def transports #{:usb :nfc :ble :internal :hybrid})

(defn challenge [id ceremony opts]
  {:webauthn.challenge/id id
   :webauthn.challenge/ceremony ceremony
   :webauthn.challenge/rp-id (:rp-id opts)
   :webauthn.challenge/user-handle (:user-handle opts)
   :webauthn.challenge/challenge (:challenge opts)
   :webauthn.challenge/created-at (:created-at opts)
   :webauthn.challenge/expires-at (:expires-at opts)})

(defn credential [id opts]
  {:webauthn.credential/id id
   :webauthn.credential/rp-id (:rp-id opts)
   :webauthn.credential/user-handle (:user-handle opts)
   :webauthn.credential/public-key-ref (:public-key-ref opts)
   :webauthn.credential/aaguid (:aaguid opts)
   :webauthn.credential/transports (set (:transports opts))
   :webauthn.credential/discoverable? (boolean (:discoverable? opts))
   :webauthn.credential/created-at (:created-at opts)})

(defn assertion [challenge credential-id ok? opts]
  {:webauthn.assertion/challenge-id (:webauthn.challenge/id challenge)
   :webauthn.assertion/credential-id credential-id
   :webauthn.assertion/ok? (boolean ok?)
   :webauthn.assertion/sign-count (:sign-count opts)
   :webauthn.assertion/user-present? (boolean (:user-present? opts))
   :webauthn.assertion/user-verified? (boolean (:user-verified? opts))
   :webauthn.assertion/evidence-ref (:evidence-ref opts)
   :webauthn.assertion/attested-at (:attested-at opts)})

(defn prf-envelope [credential-id opts]
  {:webauthn.prf/credential-id credential-id
   :webauthn.prf/rp-id (:rp-id opts)
   :webauthn.prf/salt-ref (:salt-ref opts)
   :webauthn.prf/wrapped-ref (:wrapped-ref opts)
   :webauthn.prf/created-at (:created-at opts)})
