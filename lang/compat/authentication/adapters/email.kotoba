(ns authentication.adapters.email
  "Provider-neutral email verification, OTP and magic-link records. The host
  supplies random tokens/digests and sends mail through SES/Resend/Postmark."
  (:require [authentication.identity :as identity]))

(defn challenge
  [{:keys [id purpose email token-digest attempts expires-at created-at]}]
  (let [email (identity/normalize-email email)]
    (when-not (and email (#{:verify-email :login :reset-password} purpose)
                   (string? token-digest) (<= 32 (count token-digest)))
      (throw (ex-info "invalid email challenge" {:purpose purpose})))
    {:identity.email-challenge/id id
     :identity.email-challenge/purpose purpose
     :identity.email-challenge/email email
     :identity.email-challenge/token-digest token-digest
     :identity.email-challenge/attempts (or attempts 0)
     :identity.email-challenge/max-attempts 5
     :identity.email-challenge/created-at created-at
     :identity.email-challenge/expires-at expires-at
     :identity.email-challenge/used? false}))

(defn challenge-usable? [record now]
  (and (not (:identity.email-challenge/used? record))
       (< (:identity.email-challenge/attempts record)
          (:identity.email-challenge/max-attempts record))
       (< now (:identity.email-challenge/expires-at record))))

(defn consume-tx [challenge-id now]
  [{:db/id challenge-id
    :identity.email-challenge/used? true
    :identity.email-challenge/used-at now}])
