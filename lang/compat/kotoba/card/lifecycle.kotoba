(ns kotoba.card.lifecycle
  "Card lifecycle as a pure state machine.

  This namespace exists so that -- is this lifecycle transition even reachable
  from the state we have on record? -- is answered by a total, deterministic
  function with no I/O, no model and no policy. It is the intended callee of a
  consent surface's pre-check, which must reject an unreachable transition
  before any human approval is requested. Same posture and shape as
  kotoba.esim.lifecycle.

  IMPORTANT -- this table is NOT this library's invention. It mirrors, exactly,
  the rules the deployed issuer side already enforces:

    cloud-itonami/cloud-itonami-card-issuing
      cardissuing.governor/legal-predecessor   (which state each event is legal from)
      cardissuing.store/lifecycle!             (which state each event lands in)

  The reason to extract them here is NOT that the issuer side lacked them -- it
  has enforced this allowlist all along -- but that a consent surface needs to
  ask the same question BEFORE requesting human approval, and any second copy of
  these rules would drift from the governor's. One table, two readers.

  So: when the issuer-side governor changes `legal-predecessor` or
  `lifecycle!`'s `next-status`, THIS TABLE MUST CHANGE WITH IT. It is a mirror,
  and a mirror that disagrees with its subject is worse than no mirror -- a
  pre-check more permissive than the governor waves work through to a human that
  the governor will then refuse, which is precisely the wasted approval this
  namespace exists to prevent.

  What this namespace decides: structural reachability and terminality. What it
  deliberately does NOT decide: whether an issuer is licensed, whether a
  cardholder consented, whether a block is warranted. Those belong to the
  governor and the consent surface and are not expressible as a state
  transition.

  Portable (.cljc) across JVM / ClojureScript / SCI / GraalVM."
  (:require [clojure.string :as str]))

(def states
  "Cardholder statuses, mirroring the issuer side's own `:status` values.

  :intake is the pre-issuance record. No LIFECYCLE event leaves it -- a card
  gets out of :intake by being issued (`:card/issue`), which is a separate
  operation with its own scrutiny, not a lifecycle transition. Modeling
  issuance as a lifecycle event here would let it be reached through the
  lifecycle gate instead of its own."
  #{:intake :issued :active :blocked :closed})

(def terminal-states
  "States no event leads out of. Only :closed -- note there is no separate
  'reissued' state: a reissue lands the cardholder back in :active under a NEW
  card reference (see `events`)."
  #{:closed})

(def events
  "Lifecycle events: the states each is legal from, and the state it lands in.
  Mirrors cardissuing.governor/legal-predecessor and
  cardissuing.store/lifecycle!'s next-status exactly.

  An allowlist, not a denylist -- a future event defaults to illegal until
  deliberately wired here, the same discipline the issuer-side table states.

  Two consequences worth reading carefully, because both are easy to guess
  wrong:

  - :activate is legal ONLY from :issued. Unblocking a blocked card is NOT an
    activate; the issuer side's recovery path from :blocked is :reissue. A
    blocked card does not simply resume.

  - :reissue is legal ONLY from :blocked, lands in :active, and MINTS A NEW CARD
    REFERENCE (cardissuing.store/lifecycle! calls register-card-issuance with
    the next sequence for the BIN). The successor is created by the same
    operation -- this is the issuer side's deliberate design, not an oversight,
    so `apply-event` reports it rather than pretending the caller must issue the
    replacement separately."
  {:activate {:from #{:issued}                  :to :active}
   :block    {:from #{:active}                  :to :blocked}
   :reissue  {:from #{:blocked}                 :to :active  :mints-successor? true}
   :close    {:from #{:issued :active :blocked} :to :closed}})

(defn terminal?
  "True when state admits no further event."
  [state]
  (contains? terminal-states state))

(defn reachable?
  "True when event is admissible from state. False for any unknown state or
  event -- an unrecognized input is never treated as permissive."
  [state event]
  (boolean
   (when-let [{:keys [from]} (get events event)]
     (and (contains? states state)
          (contains? from state)))))

(defn next-state
  "The state event lands in when applied to state, or nil when the event is not
  reachable from it."
  [state event]
  (when (reachable? state event)
    (get-in events [event :to])))

(defn mints-successor?
  "True when this event creates a NEW card reference as part of the same
  operation. Only :reissue does."
  [event]
  (boolean (get-in events [event :mints-successor?])))

(defn transition-issues
  "Return a seq of reasons event cannot be applied to state, empty when it can.
  Reasons are data so a governor can cite one rather than re-derive it."
  [state event]
  (cond
    (not (contains? events event))
    [{:card/issue :event/unknown :card/event event}]

    (not (contains? states state))
    [{:card/issue :state/unknown :card/state state}]

    (terminal? state)
    [{:card/issue :state/terminal :card/state state :card/event event}]

    (not (reachable? state event))
    [{:card/issue :transition/unreachable
      :card/state state
      :card/event event
      :card/from  (get-in events [event :from])}]

    :else []))

(defn apply-event
  "Apply event to state, returning either

    {:card/ok? true  :card/from s :card/to s'}

  or

    {:card/ok? false :card/issues [...]}

  and never throwing.

  A :reissue additionally reports :card/mints-successor? true (and echoes
  :card/supersedes-reference when given), because the issuer side creates the
  replacement card in the same operation. A caller that treats a reissue as a
  pure state change will miss the new card reference."
  [state event & {:keys [card-reference]}]
  (let [issues (transition-issues state event)]
    (if (seq issues)
      {:card/ok? false :card/issues (vec issues)}
      (cond-> {:card/ok? true
               :card/from state
               :card/to   (next-state state event)}
        (mints-successor? event)
        (assoc :card/mints-successor? true
               :card/supersedes-reference card-reference)))))

(defn describe
  "A one-line human-readable rendering of a transition, for an operator log.
  Pure string building; no formatting library."
  [state event]
  (if-let [to (next-state state event)]
    (str (name event) ": " (name state) " -> " (name to)
         (when (mints-successor? event) " (new card reference)"))
    (str (name event) ": refused from " (name state)
         " (reachable from "
         (str/join ", " (sort (map name (get-in events [event :from] #{}))))
         ")")))
