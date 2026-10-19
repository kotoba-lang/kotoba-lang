(ns kotoba.kessai.card
  "Card rail for kessai — bridges kotoba.card's ISO 8583 (MTI/data-element)
  and PAN/Luhn data model to kotoba.kessai's rail-agnostic PaymentRef. This
  namespace never decides approve/decline itself (that is IPaymentPort's
  job, mock or real) — it only builds ISO 8583 request/capture messages and
  adapts kotoba.card authorization records into PaymentRefs.

  No network, no I/O — a real card-network adapter reads/writes the ISO 8583
  data elements this namespace builds, but sending them on the wire is a
  follow-up (see README)."
  (:require [kotoba.card :as card]
            [kotoba.kessai :as kessai]))

(defn card-request
  "Build a :card rail authorize request from a PAN/amount/currency. Validates
  the PAN (Luhn + network, ISO/IEC 7812) via kotoba.card and attaches the
  ISO 8583 authorization-request message (MTI 0100; DE2 PAN, DE4 amount,
  DE49 currency) for a real adapter to send on the wire. Throws ex-info with
  the validation map on an invalid PAN."
  [pan amount currency]
  (let [validated (card/validate-pan pan)]
    (when-not (:card/valid? validated)
      (throw (ex-info "invalid PAN" validated)))
    {:kessai/rail       :card
     :kessai/amount     amount
     :kessai/currency   currency
     :kessai/instrument (:card/parsed validated)
     :card/iso8583      (card/message "0100" {2 pan 4 amount 49 currency})}))

(defn authorization->payment-ref
  "Adapt a kotoba.card authorization decision record (kotoba.card/authorization
  — produced by a real network adapter parsing an ISO 8583 response, or by a
  test harness standing in for one) into a rail-agnostic kessai PaymentRef."
  [auth]
  (kessai/payment-ref :card
                       (if (card/approved? auth) :authorized :declined)
                       (card/authorized-amount auth)
                       (:card/currency auth)
                       :ref (:card/rrn auth)
                       :instrument (when-let [pan (:card/pan auth)] (card/parse-pan pan))))

(defn capture-message
  "Build the ISO 8583 financial/capture message (MTI 0200; DE4 amount, DE39
  \"00\" = approved response code) for a :captured PaymentRef."
  [payment-ref]
  (card/message "0200" {4 (:kessai/amount payment-ref) 39 "00"}))
