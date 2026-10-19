(ns webauthn.datom)

(defn challenge-datoms [challenge]
  [{:db/id (:webauthn.challenge/id challenge)
    :webauthn.challenge/ceremony (:webauthn.challenge/ceremony challenge)
    :webauthn.challenge/rp-id (:webauthn.challenge/rp-id challenge)
    :webauthn.challenge/user-handle (:webauthn.challenge/user-handle challenge)
    :webauthn.challenge/challenge (:webauthn.challenge/challenge challenge)
    :webauthn.challenge/created-at (:webauthn.challenge/created-at challenge)
    :webauthn.challenge/expires-at (:webauthn.challenge/expires-at challenge)}])

(defn credential-datoms [credential]
  [{:db/id (:webauthn.credential/id credential)
    :webauthn.credential/rp-id (:webauthn.credential/rp-id credential)
    :webauthn.credential/user-handle (:webauthn.credential/user-handle credential)
    :webauthn.credential/public-key-ref (:webauthn.credential/public-key-ref credential)
    :webauthn.credential/aaguid (:webauthn.credential/aaguid credential)
    :webauthn.credential/transports (:webauthn.credential/transports credential)
    :webauthn.credential/discoverable? (:webauthn.credential/discoverable? credential)
    :webauthn.credential/created-at (:webauthn.credential/created-at credential)}])

(defn assertion-datoms [assertion]
  [{:db/id (str (:webauthn.assertion/challenge-id assertion) ":"
                (:webauthn.assertion/credential-id assertion))
    :webauthn.assertion/challenge-id (:webauthn.assertion/challenge-id assertion)
    :webauthn.assertion/credential-id (:webauthn.assertion/credential-id assertion)
    :webauthn.assertion/ok? (:webauthn.assertion/ok? assertion)
    :webauthn.assertion/sign-count (:webauthn.assertion/sign-count assertion)
    :webauthn.assertion/user-present? (:webauthn.assertion/user-present? assertion)
    :webauthn.assertion/user-verified? (:webauthn.assertion/user-verified? assertion)
    :webauthn.assertion/evidence-ref (:webauthn.assertion/evidence-ref assertion)
    :webauthn.assertion/attested-at (:webauthn.assertion/attested-at assertion)}])
