(ns kotoba.card.ports
  "Host-injected ports for the issuer side of a card program.

  This namespace defines the protocols; the host -- a governed actor such as
  cloud-itonami/cloud-itonami-card-issuing -- supplies concrete implementations
  backed by a real card scheme and processor, or by fixtures offline. Same
  posture as kotoba-lang/koe's voice ports and kotoba.esim.ports.

  No SDK, endpoint, scheme membership, HSM handle or credential lives in this
  library -- only the shapes an issuer-side host is built from. Operation names
  match the vocabulary the issuer side already uses (`:bin/sponsor`,
  `:cardholder/intake`, `:card/issue`, `:card/lifecycle`,
  `:authorization/decide`, `:dispute/initiate`), so an implementation does not
  have to translate at the boundary.

  EVERY OPERATION HERE IS PROPOSE-ONLY. An implementation returns a proposal
  record for a governor and a human to decide on; it does not sponsor a real
  BIN, issue a real card, move real funds or file a real chargeback. That is not
  a limitation of the protocol -- it is the containment the issuer-side actor is
  built around, made structural here so a caller cannot mistake a port for an
  actuator. A host that actuates inside one of these methods has moved a
  licensed act behind an interface that reads as a query.

  On card numbers: this library never persists raw PANs (see the kotoba.card
  namespace docstring). A host is expected to pass whatever card reference it
  actually keeps -- for the issuer-side actor that is a synthetic reference, not
  a network-issued PAN -- and to mask it before it leaves the boundary.

  Implementations report refusal as data rather than throwing, matching
  kotoba.card.lifecycle/apply-event.

  Portable (.cljc) across JVM / ClojureScript / SCI / GraalVM.")

(defprotocol ICardholderProvisioning
  "Cardholder account intake, the issuer-side subject record a card hangs off."
  (intake-cardholder [this application]
    "Draft a cardholder record from an application (`:cardholder/intake`).
     Returns a proposal, not an opened account.")
  (cardholder [this cardholder-id]
    "Return the cardholder record, or nil when unknown."))

(defprotocol ICardProgram
  "BIN / range sponsorship at the program level -- the issuer's relationship
  with a card scheme, distinct from any individual card.

  Separate from ICardIssuance because sponsorship is a licensing-and-membership
  act performed once per program, gated on the issuer's own supervisory regime,
  whereas issuance happens per cardholder. A caller holding an issuance port has
  no business proposing a BIN sponsorship."
  (assess-bin [this request]
    "Draft an assessment of whether a BIN/range sponsorship is supportable in a
     jurisdiction (`:bin/assess`), citing the issuer's spec basis.")
  (sponsor-bin [this request]
    "Draft a BIN/range sponsorship proposal (`:bin/sponsor`). Always
     human-gated; never a live scheme registration."))

(defprotocol ICardIssuance
  "Issuance and lifecycle of individual cards.

  Reachability of a lifecycle event is the caller's responsibility: an
  implementation is entitled to assume kotoba.card.lifecycle already admitted
  the event, and is entitled to refuse again if its own view of the card
  disagrees."
  (issue-card [this cardholder-id program]
    "Draft a card-issuance proposal for a cardholder under a sponsored program
     (`:card/issue`).")
  (card [this card-reference]
    "Return the card record including its lifecycle state, or nil when unknown.")
  (apply-lifecycle [this card-reference event]
    "Draft a lifecycle transition (`:card/lifecycle`) for one of
     #{:activate :block :reissue :close}. A :reissue is legal only from
     :blocked, lands in :active, and MINTS A NEW CARD REFERENCE in the same
     operation -- see kotoba.card.lifecycle, which mirrors the issuer side's
     own allowlist rather than restating it."))

(defprotocol IAuthorizationDecision
  "Issuer-side real-time authorization decisions.

  Deliberately a separate protocol rather than a method on ICardIssuance: this
  is the only hot path in the issuer side -- per transaction, latency-bound, and
  available independently of whether anyone can issue a card right now. A host
  may well implement it against different infrastructure, and a caller that only
  needs to decide authorizations should not have to hold a port that can also
  propose issuing cards."
  (decide-authorization [this request]
    "Decide an authorization request, returning a kotoba.card/authorization
     record (`:authorization/decide`). The decision is a record of what the
     issuer's policy concluded -- a caller must not read an :approve as funds
     having moved."))

(defprotocol IDisputeInitiation
  "Issuer-side dispute / chargeback initiation.

  Separate from the acquirer side on purpose. This library's ports cover the
  ISSUER only: raising a dispute on a cardholder's behalf. Acquirer-side
  chargeback hold release and settlement finalization belong to
  cloud-itonami/cloud-itonami-isic-6619 and are deliberately absent here -- one
  actor holding both sides of a dispute is the conflict of interest the split
  exists to prevent."
  (initiate-dispute [this card-reference transaction reason]
    "Draft an issuer-side dispute/chargeback initiation (`:dispute/initiate`).")
  (dispute-status [this dispute-id]
    "Return the recorded dispute, or nil when unknown."))
