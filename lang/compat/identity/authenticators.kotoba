(ns identity.authenticators
  "did を軸に、認証方法を重ねる — factors bind TO a DID; they are not where it comes from.

  ## The axis

  A `did:key` is this workspace's portable name: it is derived from a key pair,
  survives the machine it was made on, and is verifiable by anyone holding only
  the public half. A passkey, TouchID, a TOTP seed or a CACAO signature are
  **ways of proving a person is present** — each one is bound to a hardware
  authenticator, a browser profile, or a device, and each can be added and
  removed. Deriving the identity FROM one of them makes the name as mortal as
  the credential: replace the phone and you are somebody else.

  So the DID is the subject, and authenticators layer on top of it. Adding or
  revoking one leaves the DID untouched — which is the whole point, and is
  asserted directly by `did-survives-binding-changes` in the test suite.

  ## What this decides, and the three things it must not

  It answers ONE question: *is this authentication decision about THIS did, and
  did it come from an authenticator this did has bound?*

  It does not combine factors into a level — `authentication.core/decide`
  (`kotoba-lang/authentication`) does, and it already understands
  `:authn.request/required-level`. It does not decide whether a held authority
  covers a requested scope — `authority/chain-authorize` does. It does not
  decide whether a runtime may act at all — `identity.startup` does. Four
  questions, four owners; this namespace consumes a decision rather than
  reaching one, which is why it needs no crypto and no factor vocabulary of
  its own.

  ## The closed set is the caller's

  `factor-types` is passed in rather than defined here, for the same reason
  `biscuit.kotoba/->delegated` takes `kinds`: the vocabulary belongs to the
  semantics, and a copy in a second repo is a copy that can disagree. A factor
  outside the given set is REJECTED, never ignored — ignoring one silently
  drops a restriction somebody meant."
  (:require [clojure.string :as str]))

(defn- blank? [s] (or (nil? s) (and (string? s) (str/blank? s))))

(defn binding
  "Register FACTOR-TYPE credential CREDENTIAL-ID as an authenticator for DID."
  [did factor-type credential-id opts]
  {:identity.authenticator/did did
   :identity.authenticator/factor-type factor-type
   :identity.authenticator/credential-id credential-id
   :identity.authenticator/label (:label opts)
   :identity.authenticator/bound-at (:bound-at opts)
   :identity.authenticator/expires (:expires opts)
   :identity.authenticator/revoked? (boolean (:revoked? opts))})

(defn- live-binding
  "The binding covering this factor, or the reason there is none."
  [bindings did factor now]
  (let [ftype (:authn.factor/type factor)
        cred  (:identity.authenticator/credential-id factor)
        match (first (filter #(and (= did (:identity.authenticator/did %))
                                   (= ftype (:identity.authenticator/factor-type %))
                                   (or (blank? cred)
                                       (= cred (:identity.authenticator/credential-id %))))
                             bindings))
        exp (:identity.authenticator/expires match)]
    (cond
      (nil? match)                    {:reason :unbound-authenticator}
      (:identity.authenticator/revoked? match) {:reason :revoked}
      (blank? exp)                    {:binding match}
      (blank? now)                    {:reason :expiry-unknown}
      (neg? (compare (str exp) (str now))) {:reason :expired}
      :else                           {:binding match})))

(defn acceptable
  "May this authentication decision act as DID?

  Takes:
    :did           the did:key that is the axis
    :decision      an `authn.decision` from `authentication.core/decide`
    :bindings      authenticators this did has bound
    :factor-types  the closed vocabulary the caller's semantics owns
    :now           RFC3339 instant, or nil if the clock is unreadable

  -> {:identity.authenticators/acceptable? bool
      :identity.authenticators/reason keyword
      :identity.authenticators/used [factor …]
      :identity.authenticators/rejected [{:factor .. :reason ..}]}

  A rejected factor never contributes. When every factor is rejected the answer
  is no, whatever level the decision reached — a level computed from factors
  this did never bound is a level about somebody else."
  [{:keys [did decision bindings factor-types now]}]
  (let [factors (:authn.decision/factors decision)
        base {:identity.authenticators/used [] :identity.authenticators/rejected []}
        no (fn [reason] (merge base {:identity.authenticators/acceptable? false
                                     :identity.authenticators/reason reason}))]
    (cond
      (blank? did)
      (no :no-did)

      (not= :authenticated (:authn.decision/decision decision))
      (no :not-authenticated)

      (not= did (:authn.decision/subject decision))
      (no :subject-mismatch)

      (empty? factors)
      (no :no-factors)

      :else
      (let [{:keys [used rejected]}
            (reduce (fn [acc f]
                      (cond
                        (not (contains? (set factor-types) (:authn.factor/type f)))
                        (update acc :rejected conj {:factor f :reason :unknown-factor-type})

                        (not (:authn.factor/ok? f))
                        (update acc :rejected conj {:factor f :reason :factor-not-ok})

                        :else
                        (let [{:keys [binding reason]} (live-binding bindings did f now)]
                          (if binding
                            (update acc :used conj f)
                            (update acc :rejected conj {:factor f :reason reason})))))
                    {:used [] :rejected []}
                    factors)]
        {:identity.authenticators/acceptable? (boolean (seq used))
         :identity.authenticators/reason (if (seq used)
                                           :bound-authenticator
                                           (or (-> rejected first :reason) :no-bound-factor))
         :identity.authenticators/used used
         :identity.authenticators/rejected rejected}))))
