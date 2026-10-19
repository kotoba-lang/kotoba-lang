(ns kotoba.kessai.redirect
  "決済 — hosted-redirect rail (`:redirect`): the shape a PSP-hosted checkout
  actually has (Stripe Checkout, PayPal, Adyen HPP, Alipay/WeChat web).

  **This rail exists because authorize/capture does not describe it.** The
  `:card` rail is ISO 8583 and the `:wire` rail is ISO 20022 pain.001; both
  assume the merchant holds the instrument and drives a two-step reserve →
  settle. In a hosted redirect the merchant never sees the instrument, never
  issues an authorization, and cannot capture: it *creates a session*, sends
  the payer away, and later *learns* what happened. Forcing that into
  authorize/capture is what made every consumer bypass this library and call
  the PSP directly (com-junkawasaki/root ADR-2608040100).

  Pure data contracts — no network, no clock. The caller supplies `now` and
  the webhook payload; every function is deterministic.

  ## The property this rail exists to enforce

  **A payer returning to your success URL is not evidence that they paid.**
  The return is a browser navigation the payer controls: they can edit the
  URL, they can arrive without paying, and — much more commonly — they can
  pay and then close the tab so the return never happens at all. The only
  authoritative signal is the PSP's server-to-server callback.

  So `observe-return` **cannot** reach `:succeeded`, by construction. There
  is no argument you can pass it that will settle a session. `confirm` is
  the only door, and it re-checks the amount and currency against the
  session before opening it.

  Amounts are integers in the smallest unit of the transaction currency, as
  everywhere else in kessai."
  (:require [clojure.string :as str]
            [kotoba.kessai :as kessai]))

;; ---------------------------------------------------------------------------
;; Lifecycle
;; ---------------------------------------------------------------------------

(def statuses
  "`:created`    session made, payer not yet sent anywhere
   `:redirected` payer has been sent to the PSP
   `:succeeded`  PSP confirmed payment, amount verified — terminal
   `:failed`     PSP confirmed failure — terminal
   `:expired`    the session's window passed unpaid — terminal
   `:cancelled`  payer or merchant abandoned it — terminal"
  #{:created :redirected :succeeded :failed :expired :cancelled})

(def transitions
  "**`:succeeded` is reachable only from `:redirected`.** A session nobody was
  ever sent to cannot have been paid, and the terminal states have no exits —
  a late webhook for an already-expired session does not resurrect it."
  {:created    #{:redirected :cancelled :expired}
   :redirected #{:succeeded :failed :cancelled :expired}
   :succeeded  #{}
   :failed     #{}
   :expired    #{}
   :cancelled  #{}})

(defn- terminal? [status] (empty? (get transitions status #{})))

(defn can-transition? [from to]
  (contains? (get transitions from #{}) to))

;; ---------------------------------------------------------------------------
;; Session
;; ---------------------------------------------------------------------------

(defn session
  "Construct a redirect session. Returns nil when the id is blank, the amount
  is not a positive integer, or the currency is blank.

  `expires-at` is an opaque comparable supplied by the host (epoch millis,
  ISO string, whatever it also passes to `expire-if-due`) — this namespace
  never reads a clock, so \"expired\" is always the caller's judgement made
  explicit rather than something that happens behind its back.

  A **zero amount is rejected**: a redirect session for nothing is either a
  bug in the caller's totalling or an attempt to use this rail for card
  setup, which is a different flow with different consent requirements."
  [id amount currency & {:keys [return-url cancel-url expires-at metadata]}]
  (when (and (string? id) (not (str/blank? id))
             (integer? amount) (pos? amount)
             (string? currency) (not (str/blank? currency)))
    {:redirect/id         id
     :redirect/status     :created
     :redirect/amount     amount
     :redirect/currency   currency
     :redirect/return-url return-url
     :redirect/cancel-url cancel-url
     :redirect/expires-at expires-at
     :redirect/metadata   (or metadata {})
     ;; 適用済み webhook event id。PSP は同じ event を再送する仕様なので、
     ;; 「起きるかもしれない」ではなく必ず起きる。
     :redirect/applied-events #{}}))

(defn status [s] (:redirect/status s))
(defn succeeded? [s] (= :succeeded (status s)))
(defn settled? [s] (succeeded? s))
(defn open? [s] (not (terminal? (status s))))

(defn mark-redirected
  "Record that the payer was sent to `url`. Returns the updated session, or
  nil when the session is not in a state that can be redirected."
  [s url]
  (when (can-transition? (status s) :redirected)
    (assoc s :redirect/status :redirected :redirect/psp-url url)))

(defn cancel
  "Payer or merchant abandoned the session."
  [s]
  (when (can-transition? (status s) :cancelled)
    (assoc s :redirect/status :cancelled)))

(defn expire-if-due
  "Move an open session to `:expired` when `now` has reached its
  `:redirect/expires-at`. `compare-fn` defaults to `compare`, which is right
  for epoch millis and for ISO-8601 strings; pass your own for anything else.

  Returns the session unchanged when it is not due or has no expiry — this
  is a sweep helper, so a no-op is the common case and must not be nil."
  ([s now] (expire-if-due s now compare))
  ([s now compare-fn]
   (let [due (:redirect/expires-at s)]
     (if (and due (open? s) (>= (compare-fn now due) 0))
       (assoc s :redirect/status :expired)
       s))))

;; ---------------------------------------------------------------------------
;; The return URL is not evidence
;; ---------------------------------------------------------------------------

(defn observe-return
  "Record that the payer came back to the merchant's return or cancel URL.

  **This can never settle a session.** `:cancel` moves an open session to
  `:cancelled`; anything else leaves the status exactly as it was and only
  notes that the payer was seen. That is the whole point of the function
  existing: hosts want somewhere to put the `?status=success` handler, and
  if this namespace did not offer one that refuses to settle, the handler
  would be written against `mark-*` and would settle.

  The status is still whatever the webhook has (or has not) made it, so a
  UI reading this should say \"confirming your payment\" and not \"paid\"."
  [s outcome-hint]
  (let [s' (assoc s :redirect/payer-returned true
                    :redirect/return-hint outcome-hint)]
    (if (and (= :cancel outcome-hint) (can-transition? (status s) :cancelled))
      (assoc s' :redirect/status :cancelled)
      s')))

;; ---------------------------------------------------------------------------
;; Webhook — the only authoritative signal
;; ---------------------------------------------------------------------------

(defn confirm
  "Apply a PSP callback to a session. Returns `{:ok session}` or
  `{:error <reason> ...}` — never throws, because a webhook handler that
  throws returns 5xx and the PSP simply resends the same bad event forever.

  `event` is `{:event/id :event/session :event/outcome :event/amount
  :event/currency}` where outcome is `:succeeded` or `:failed`.

  Refusal reasons, and why each one is a refusal rather than a warning:

  - `:unknown-session`  the event names a different session. Applying it
                        would settle the wrong order.
  - `:already-applied`  this event id was already applied. **PSPs resend by
                        design**; without this a retry double-settles.
  - `:not-transitionable` the session is terminal or was never redirected.
                        A late `succeeded` for an expired session is not a
                        payment you can honour — the goods were released or
                        the price has moved.
  - `:amount-mismatch`  the PSP charged an amount or currency that is not
                        what this session is for. This is the check that
                        catches a tampered client, a reused session id, and
                        a currency-conversion surprise, and it is the one
                        most often skipped because the happy path never
                        exercises it."
  [s event]
  (let [{:event/keys [id session outcome amount currency]} event]
    (cond
      (not= session (:redirect/id s))
      {:error :unknown-session :expected (:redirect/id s) :got session}

      (contains? (:redirect/applied-events s) id)
      {:error :already-applied :event id :status (status s)}

      (not (contains? #{:succeeded :failed} outcome))
      {:error :unknown-outcome :got outcome}

      (not (can-transition? (status s) outcome))
      {:error :not-transitionable :from (status s) :to outcome}

      (and (= :succeeded outcome)
           (or (not= amount (:redirect/amount s))
               (not= currency (:redirect/currency s))))
      {:error :amount-mismatch
       :expected [(:redirect/amount s) (:redirect/currency s)]
       :got [amount currency]}

      :else
      {:ok (-> s
               (assoc :redirect/status outcome)
               (update :redirect/applied-events conj id))})))

;; ---------------------------------------------------------------------------
;; Bridge back to the rail-agnostic PaymentRef
;; ---------------------------------------------------------------------------

(defn ->payment-ref
  "Project a settled session onto a `kessai/payment-ref` so the existing
  ledger tie-in (`kessai/settlement-entries`) works unchanged. Returns nil
  for any session that has not succeeded.

  The status is `:captured`, not `:authorized`: in a hosted redirect the
  funds are already settled when the PSP says succeeded, and there is no
  second step for anyone to call. Mapping it to `:authorized` would leave a
  `capture` that no adapter can implement and that the ledger would wait
  for forever."
  [s]
  (when (succeeded? s)
    (kessai/payment-ref :redirect :captured
                        (:redirect/amount s) (:redirect/currency s)
                        :ref (:redirect/id s)
                        :instrument :hosted-redirect)))
