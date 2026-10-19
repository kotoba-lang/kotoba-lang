(ns identity.startup
  "起動時に鍵を問い、無ければ名乗る — which of three states an agent runtime is in
  when it starts, and whether it may serve anything before that is settled.

  ## What this decides, and what it must not

  This namespace answers ONE question: *given what the machine can see at
  startup, may this runtime act as somebody, and if not, what must be asked?*

  It does NOT decide whether a held authority covers a requested operation.
  That is `authority/chain-authorize` (`kotoba-lang/authority`), the fleet's
  single decision maker for the capability lattice, and ADR-2608180200 is
  explicit that wires may multiply while deciders may not. A delegation
  arrives here already parsed into grants — `biscuit.kotoba/->delegated` is
  the wire that produces them — and leaves for `authority` unchanged.

  ## Three states, not two

  `no key` is not a synonym for `first run`. Collapsing them is the defect
  this namespace exists to prevent: a runtime that mints whenever it finds no
  key gives every machine a different name, and then \"sync\" between two
  devices is not synchronisation at all, because they are two people.

    :key-present  a device key and a live delegation for it -> act
    :mint         no key, and no identity exists anywhere   -> create
    :link         no key, but an identity exists elsewhere  -> be authorised
    :undecidable  no key, and nobody has said which         -> ASK

  The fourth is the honest one. Whether an identity exists elsewhere is not
  a fact this machine holds, so it cannot be derived here — it can only be
  answered by the person, and until it is, the runtime serves its public
  surface and nothing else.

  ## Failure is always toward less authority

  Every path that cannot establish a live delegation returns
  `:identity.startup/serve :public-only`. There is no branch that falls back
  to ambient trust — that fallback is what this design replaces."
  (:require [clojure.string :as str]))

(def states #{:key-present :mint :link :undecidable})

(def ^:private serve-levels {:full 1 :public-only 0})

(defn- blank? [s] (or (nil? s) (and (string? s) (str/blank? s))))

(defn- expired?
  "A grant with no expiry never expires; one with an expiry needs `now` to
  judge it. A missing `now` is not treated as \"not expired\" — an unanswerable
  question must not read as a pass."
  [{:grant/keys [expires]} now]
  (cond
    (blank? expires) false
    (blank? now)     ::unknown
    :else            (neg? (compare (str expires) (str now)))))

(defn live-grants
  "The grants held for `holder` that are usable at `now`.

  -> {:identity.startup/live [...] :identity.startup/dropped [{:grant .. :reason ..}]}

  A grant issued to a different holder is dropped rather than ignored: a
  delegation this device is merely carrying for somebody else confers nothing
  here, and saying so is what keeps the drop visible."
  [grants holder now]
  (reduce
   (fn [acc g]
     (let [gh (:grant/holder g)
           exp (expired? g now)]
       (cond
         (and (not (blank? gh)) (not= gh holder))
         (update acc :identity.startup/dropped conj {:grant g :reason :holder-mismatch})

         (= ::unknown exp)
         (update acc :identity.startup/dropped conj {:grant g :reason :expiry-unknown})

         exp
         (update acc :identity.startup/dropped conj {:grant g :reason :expired})

         (empty? (:grant/scopes g))
         (update acc :identity.startup/dropped conj {:grant g :reason :empty-scope})

         :else
         (update acc :identity.startup/live conj g))))
   {:identity.startup/live [] :identity.startup/dropped []}
   grants))

(defn resolve-state
  "Facts a runtime can gather locally -> the startup decision.

  Facts:
    :device-did        did:key this machine holds a private key for, or nil
    :grants            delegations on disk, already parsed to `:grant/*`
    :now               RFC3339 instant, or nil if the clock is unreadable
    :identity-known?   `true` / `false` when the PERSON has said whether an
                       identity exists elsewhere; `nil` when nobody has been
                       asked. Only this tri-state separates :mint from :link.

  -> {:identity.startup/state   one of `states`
      :identity.startup/serve   :full | :public-only
      :identity.startup/reason  keyword
      :identity.startup/ask     what the person must answer, when anything
      :identity.startup/live    grants usable now
      :identity.startup/dropped grants that were not, and why}"
  [{:keys [device-did grants now identity-known?]}]
  (let [{:identity.startup/keys [live dropped]} (live-grants (or grants []) device-did now)
        base {:identity.startup/live live :identity.startup/dropped dropped}]
    (merge
     base
     (cond
       (blank? device-did)
       (case identity-known?
         true  {:identity.startup/state :link
                :identity.startup/serve :public-only
                :identity.startup/reason :no-device-key-identity-exists
                :identity.startup/ask :authorise-this-device}
         false {:identity.startup/state :mint
                :identity.startup/serve :public-only
                :identity.startup/reason :no-device-key-no-identity
                :identity.startup/ask :confirm-new-identity}
         {:identity.startup/state :undecidable
          :identity.startup/serve :public-only
          :identity.startup/reason :no-device-key-unanswered
          :identity.startup/ask :mint-or-link})

       (seq live)
       {:identity.startup/state :key-present
        :identity.startup/serve :full
        :identity.startup/reason :delegation-live}

       :else
       {:identity.startup/state :link
        :identity.startup/serve :public-only
        :identity.startup/reason (or (some-> (first dropped) :reason
                                             (as-> r (keyword (str "no-live-delegation-" (name r)))))
                                     :no-delegation)
        :identity.startup/ask :authorise-this-device}))))

(defn may-serve?
  "True only for a settled, live identity. Every other state is public-only —
  including the one where the question could not be answered."
  [decision]
  (= :full (:identity.startup/serve decision)))
