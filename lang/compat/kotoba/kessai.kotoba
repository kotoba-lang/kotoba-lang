(ns kotoba.kessai
  "決済 (settlement/payment) — rail-agnostic payment-gateway abstraction, pure
  data contracts.

  A kotoba-lang capability library that gives any ISIC-vertical actor (credit,
  securities, insurance, ...) a single authorize/capture/refund/void port
  instead of a bespoke payment integration per actor. No network, no I/O.

  A PaymentRef is rail-agnostic: {:kessai/ref :kessai/rail :kessai/status
  :kessai/amount :kessai/currency :kessai/instrument}, :kessai/rail is
  :card | :wire | :redirect, :kessai/status is one of :authorized | :declined
  | :failed | :captured | :refunded | :partially-refunded | :voided.

  Card-rail requests are built by kotoba.kessai.card (ISO 8583, via
  kotoba.card); wire-rail requests are built by kotoba.kessai.wire (ISO 20022
  pain.001 + BIC, via kotoba.banking). This namespace only knows the
  rail-agnostic port and the ledger tie-in (via kotoba.banking's double-entry
  postings) — it never touches ISO 8583/ISO 20022 shapes directly.

  **IPaymentPort does not cover every rail, and that is deliberate.**
  kotoba.kessai.redirect (`:redirect`) models PSP-hosted checkout — Stripe
  Checkout, PayPal, Adyen HPP — where the merchant never holds the
  instrument, never issues an authorization and cannot capture. It has its
  own session lifecycle and projects onto a PaymentRef only once settled
  (`redirect/->payment-ref`), so the ledger tie-in below still works. Do not
  widen `:card` to swallow it: authorize/capture is a card-present model and
  a redirect flow does not have those steps to implement.

  Amounts are plain numbers in the smallest unit of the transaction currency
  (e.g. cents) — no BigDecimal assumption, keeping the library portable
  .cljc across JVM / ClojureScript / SCI / GraalVM."
  (:require [kotoba.banking :as banking]))

;; ---------------------------------------------------------------------------
;; PaymentRef
;; ---------------------------------------------------------------------------

(defn payment-ref
  "Construct a rail-agnostic PaymentRef. rail is :card or :wire."
  [rail status amount currency & {:keys [ref instrument]}]
  {:kessai/ref        (or ref (str (name rail) "_" (hash [rail status amount currency])))
   :kessai/rail       rail
   :kessai/status     status
   :kessai/amount     amount
   :kessai/currency   currency
   :kessai/instrument instrument})

(def ^:private terminal-approved #{:authorized :captured :refunded :partially-refunded})

(defn approved? [payment-ref] (contains? terminal-approved (:kessai/status payment-ref)))
(defn authorized? [payment-ref] (= :authorized (:kessai/status payment-ref)))
(defn captured? [payment-ref] (= :captured (:kessai/status payment-ref)))
(defn declined? [payment-ref] (= :declined (:kessai/status payment-ref)))
(defn failed? [payment-ref] (= :failed (:kessai/status payment-ref)))
(defn voided? [payment-ref] (= :voided (:kessai/status payment-ref)))
(defn refunded? [payment-ref]
  (contains? #{:refunded :partially-refunded} (:kessai/status payment-ref)))

;; ---------------------------------------------------------------------------
;; IPaymentPort — rail-agnostic authorize/capture/refund/void
;; ---------------------------------------------------------------------------

(defprotocol IPaymentPort
  (authorize [this request]
    "Reserve funds for `request` (a map produced by kotoba.kessai.card or
    kotoba.kessai.wire, carrying at least :kessai/rail :kessai/amount
    :kessai/currency). Returns a PaymentRef with :kessai/status one of
    :authorized | :declined | :failed. Pure (mock) or side-effectful (real
    adapter — wrap in host effect; none is implemented here, see README).")
  (capture [this payment-ref]
    "Settle a previously :authorized PaymentRef. Returns an updated PaymentRef
    with :kessai/status :captured (unchanged if not currently :authorized).")
  (refund [this payment-ref amount]
    "Return `amount` (this call's increment, not a running total) to the
    payer. Callable repeatedly on a :captured OR already-:partially-refunded
    PaymentRef -- :kessai/refunded-amount accumulates across calls, and
    :kessai/status becomes :refunded once the accumulated total reaches the
    PaymentRef's :kessai/amount, :partially-refunded otherwise. A no-op on
    any other status (:authorized/:declined/:failed/:voided/:refunded).
    `amount` is clamped to whatever remains refundable -- :kessai/refunded-
    amount can never exceed :kessai/amount, even if a caller requests more
    than the remaining balance (retry, duplicate request, operator error).")
  (void [this payment-ref]
    "Cancel an :authorized-but-not-yet-captured PaymentRef. Returns an updated
    PaymentRef with :kessai/status :voided."))

;; ---------------------------------------------------------------------------
;; Mock adapter — always approves/captures. Real Stripe/SWIFT/interbank
;; network adapters are a follow-up (see README "Follow-ups").
;; ---------------------------------------------------------------------------

(defrecord MockPaymentPort []
  IPaymentPort
  (authorize [_ request]
    (payment-ref (:kessai/rail request) :authorized
                 (:kessai/amount request) (:kessai/currency request)
                 :instrument (:kessai/instrument request)))
  (capture [_ payment-ref]
    (if (authorized? payment-ref)
      (assoc payment-ref :kessai/status :captured)
      payment-ref))
  (refund [_ payment-ref amount]
    ;; :captured for the FIRST refund, :partially-refunded for any refund
    ;; after that -- a bare `(captured? payment-ref)` guard meant every
    ;; refund past the first silently no-op'd (status had already moved off
    ;; :captured), with no error to signal it. :kessai/refunded-amount
    ;; accumulates across calls so a chain of partial refunds correctly
    ;; reaches :refunded once the total covers :kessai/amount -- but the
    ;; per-call `amount` is clamped to whatever remains refundable first, so
    ;; an over-refund (retry, duplicate request, operator error) can never
    ;; push :kessai/refunded-amount past :kessai/amount.
    (if (or (captured? payment-ref)
            (= :partially-refunded (:kessai/status payment-ref)))
      (let [already   (:kessai/refunded-amount payment-ref 0)
            remaining (- (:kessai/amount payment-ref) already)
            applied   (max 0 (min amount remaining))
            total     (+ already applied)]
        (assoc payment-ref
               :kessai/status (if (>= total (:kessai/amount payment-ref))
                                :refunded
                                :partially-refunded)
               :kessai/refunded-amount total))
      payment-ref))
  (void [_ payment-ref]
    (if (authorized? payment-ref)
      (assoc payment-ref :kessai/status :voided)
      payment-ref)))

(defn mock-payment-port [] (->MockPaymentPort))

;; ---------------------------------------------------------------------------
;; Ledger tie-in — turn a :captured PaymentRef into a balanced kotoba.banking
;; double-entry posting (debit the clearing account, credit the merchant
;; account).
;; ---------------------------------------------------------------------------

(defn settlement-entries
  "Given a :captured PaymentRef, produce a balanced pair of kotoba.banking
  ledger entries. Returns nil when payment-ref is not :captured."
  [payment-ref clearing-account-id merchant-account-id]
  (when (captured? payment-ref)
    [(banking/entry clearing-account-id :debit
                     (:kessai/amount payment-ref) (:kessai/currency payment-ref)
                     :ref (:kessai/ref payment-ref))
     (banking/entry merchant-account-id :credit
                     (:kessai/amount payment-ref) (:kessai/currency payment-ref)
                     :ref (:kessai/ref payment-ref))]))

(defn settlement-posting
  "Given a :captured PaymentRef, produce a kotoba.banking posting. Returns nil
  when payment-ref is not :captured."
  [id payment-ref clearing-account-id merchant-account-id]
  (when-let [entries (settlement-entries payment-ref clearing-account-id merchant-account-id)]
    (banking/posting id entries :memo (str "kessai settlement " (:kessai/ref payment-ref)))))
