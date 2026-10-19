(ns webauthn.core
  (:require [webauthn.model :as m]
            [webauthn.ports :as p]))

(defn problems
  ([record] (problems record nil))
  ([record {:keys [stored-sign-count]}]
   (cond-> []
     (and (:webauthn.challenge/ceremony record)
          (not (contains? m/ceremonies (:webauthn.challenge/ceremony record))))
     (conj {:webauthn.problem/code :unknown-ceremony})
     (seq (remove m/transports (:webauthn.credential/transports record)))
     (conj {:webauthn.problem/code :unknown-transport})

     ;; The adapter's underlying signature/RP-ID/origin verification itself
     ;; failed (a forged, replayed, or wrong-origin assertion) -- this MUST
     ;; be its own unconditional problem, independent of the user-present?/
     ;; user-verified?/sign-count checks below, all of which are (correctly)
     ;; gated on ok? being true and so say nothing at all when it's false.
     ;; Confirmed bug this closes: without this clause, a port reporting
     ;; ok?=false produced zero problems, so valid!/authenticate never
     ;; threw -- a cryptographically-failed assertion was returned to the
     ;; caller exactly as if it had succeeded (authentication bypass).
     (and (contains? record :webauthn.assertion/ok?)
          (not (:webauthn.assertion/ok? record)))
     (conj {:webauthn.problem/code :assertion/not-ok})

     (and (contains? record :webauthn.assertion/ok?)
          (:webauthn.assertion/ok? record)
          (not (:webauthn.assertion/user-present? record)))
     (conj {:webauthn.problem/code :assertion/user-not-present})

     (and (contains? record :webauthn.assertion/ok?)
          (:webauthn.assertion/ok? record)
          (not (:webauthn.assertion/user-verified? record)))
     (conj {:webauthn.problem/code :assertion/user-not-verified})

     ;; Cloned-authenticator detection (WebAuthn L2/L3 7.2 step 21): a
     ;; non-increasing sign-count is the spec-mandated replay/clone signal.
     ;; Authenticators that don't support counters report 0 both times --
     ;; that pair is the documented exception and must not be flagged.
     (and (contains? record :webauthn.assertion/ok?)
          (:webauthn.assertion/ok? record)
          stored-sign-count
          (pos? stored-sign-count)
          (:webauthn.assertion/sign-count record)
          (<= (:webauthn.assertion/sign-count record) stored-sign-count))
     (conj {:webauthn.problem/code :assertion/sign-count-not-increased}))))

(defn valid!
  ([record] (valid! record nil))
  ([record opts]
   (when-let [ps (seq (problems record opts))]
     (throw (ex-info "invalid WebAuthn record" {:webauthn/problems ps})))
   record))

(defn register [port challenge attestation]
  (valid! challenge)
  (p/register! port challenge attestation))

(defn authenticate
  ([port challenge assertion] (authenticate port challenge assertion nil))
  ([port challenge assertion stored-sign-count]
   (valid! challenge)
   (valid! (p/authenticate! port challenge assertion) {:stored-sign-count stored-sign-count})))

(defn authenticate-once
  ([port challenge-store challenge assertion]
   (authenticate-once port challenge-store challenge assertion nil))
  ([port challenge-store challenge assertion stored-sign-count]
   (when-not (p/consume-challenge! challenge-store (:webauthn.challenge/id challenge))
     (throw (ex-info "WebAuthn challenge replay" {:webauthn.challenge/id (:webauthn.challenge/id challenge)})))
   (authenticate port challenge assertion stored-sign-count)))
