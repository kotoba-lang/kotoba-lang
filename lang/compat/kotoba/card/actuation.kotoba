(ns kotoba.card.actuation
  "What happens AFTER a decision. Deliberately separate from kotoba.card.ports.

  `kotoba.card.ports` is propose-only, and says so: a host that actuates inside
  one of those methods has moved a licensed act behind an interface that reads as
  a query. That rule leaves a hole -- once a governor has cleared a proposal and a
  licensed operator has approved it, SOMETHING has to actually issue the card. This
  namespace is that something, and it is a different protocol on purpose so the
  difference is visible at every call site: `ports/issue-card` drafts, and
  `actuation/issue-card!` does.

  Three properties every implementation must have, and which a caller may rely on:

  1. NOTHING HERE RUNS WITHOUT AN APPROVAL. Each function takes an `approval`
     map carrying at minimum `{:by <who> :reference <what was approved>}`, and an
     implementation MUST refuse a call whose approval is absent or unnamed. An
     actuation nobody is named for cannot be audited, which is most of the reason
     the gate exists.

  2. EVERY CALL IS IDEMPOTENT ON A CALLER-SUPPLIED KEY. `:idempotency-key` is
     required, not optional. A retried issue-card! must not produce a second live
     card -- this is the same discipline kotoba-lang/stripe-ops states for payment
     links, and the failure mode here is worse.

  3. REFUSALS ARE DATA. Implementations return
     {:card/ok? false :card/refusal {...}} rather than throwing, so a governed
     actor can record what the provider said. A thrown exception loses the reason
     somewhere up the stack.

  The state vocabulary is kotoba.card.lifecycle's, which mirrors the issuer side's
  own. A provider whose upstream has FEWER states than that (Stripe Issuing has
  three where the lifecycle has five) must publish its mapping rather than absorb
  the difference silently -- see `state-mapping-complete?`.

  No credential, endpoint or SDK lives in this library. Portable (.cljc)."
  (:require [kotoba.card.lifecycle :as lifecycle]))

(def required-approval-keys
  "An approval must name who granted it and what they were looking at. Both, not
  either: a reference with no approver cannot be audited, and an approver with no
  reference cannot be tied to what they saw."
  #{:by :reference})

(defn approval-issues
  "Return a seq of reasons this approval cannot authorise an actuation, empty when
  it can. Pure, so an implementation's refusal is testable without a provider."
  [approval]
  (cond
    (not (map? approval))
    [{:card/issue :approval/absent}]

    :else
    (vec (for [k required-approval-keys
               :let [v (get approval k)]
               :when (or (nil? v) (and (string? v) (empty? v)))]
           {:card/issue :approval/incomplete :card/missing k}))))

(defn authorised?
  "True when this approval may authorise an actuation."
  [approval]
  (empty? (approval-issues approval)))

(defn idempotency-issues
  "Return a seq of reasons this key cannot be used, empty when it can.

  A blank or missing key is refused rather than generated for the caller. A
  provider-generated key defeats the point: the caller is the only party that
  knows whether this is a retry of something it already sent."
  [k]
  (if (and (string? k) (seq k))
    []
    [{:card/issue :idempotency-key/missing}]))

(defprotocol ICardActuation
  "The live issuer-side operations, each gated on an approval.

  An implementation is expected to check `authorised?` and `idempotency-issues`
  FIRST and return a refusal, before any outbound call. A provider that reaches
  the network and then discovers the approval was unnamed has already acted."
  (issue-card! [this cardholder program approval idempotency-key]
    "Issue a real card. Returns {:card/ok? true :card/reference <provider id>
     :card/state <lifecycle state> :card/provider-record m} or a refusal.")
  (set-card-state! [this reference event approval idempotency-key]
    "Apply a lifecycle event to a live card. `event` is one of
     kotoba.card.lifecycle/events. Returns the resulting lifecycle state, or a
     refusal -- including when the provider cannot express the requested state
     (see `state-mapping-complete?`).")
  (card-state [this reference]
    "The live card's current lifecycle state, or nil when unknown. Read-only and
     ungated: learning a state is not actuating."))

(defprotocol ICardholderActuation
  "Cardholder provisioning, separate because it is a different authority: a
  cardholder record can exist with no card, and a caller that only needs to
  register a person should not hold a port that can also issue."
  (create-cardholder! [this application approval idempotency-key]
    "Create the provider-side cardholder. Returns
     {:card/ok? true :card/cardholder-id s} or a refusal.")
  (cardholder [this cardholder-id]
    "The provider-side cardholder record, or nil."))

;; ---------------------------------------------------------------------------
;; Provider state mapping
;; ---------------------------------------------------------------------------

(defn state-mapping-complete?
  "True when `mapping` gives every lifecycle state a provider-side representation.

  A provider whose upstream has fewer states than the lifecycle MUST say which
  lifecycle states it cannot represent, by mapping them to nil, rather than
  quietly folding two into one. Folding is how `:issued` and `:active` become the
  same thing and a card that was never activated starts working."
  [mapping]
  (= (set (keys mapping)) lifecycle/states))

(defn unrepresentable-states
  "The lifecycle states this provider cannot represent. Empty is the happy case;
  a non-empty result is a fact a caller needs, not an error."
  [mapping]
  (into #{} (for [[k v] mapping :when (nil? v)] k)))

(defn refusal
  "The standard refusal shape, so every provider says no the same way."
  [rule detail]
  {:card/ok? false
   :card/refusal (cond-> {:rule rule} detail (assoc :detail detail))})

(defn precheck
  "The guard every implementation should run before any outbound call. Returns nil
  when the call may proceed, or the refusal to return."
  [approval idempotency-key]
  (let [ai (approval-issues approval)
        ki (idempotency-issues idempotency-key)]
    (cond
      (seq ai) (refusal :approval-invalid {:issues (mapv :card/issue ai)})
      (seq ki) (refusal :idempotency-key-missing
                        {:detail "呼び出し側が冪等キーを供給しなければなりません"})
      :else nil)))
