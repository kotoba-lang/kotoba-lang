(ns kura.node.accounting
  "Enforcing an order's byte limit while the bytes are moving, and keeping the
  ledger that becomes an invoice.

  `kura.order/admit` decides whether an order may be acted on **once**.
  Everything after that is this namespace's problem, and the reason it exists
  is in `kura.order/within-limit?`'s docstring: a node that checks the limit at
  the start and then streams has not checked it, because the client controls
  how much it sends. So a transfer is a small state machine — open, then a
  sequence of chunks each of which can be refused — rather than a boolean
  decision followed by a pipe.

  **Refusal is not an error.** Hitting the limit is the ordinary end of a
  transfer for a client that asked for more than it paid for. `feed` returns
  `:refused` with what would have been exceeded, and the caller stops sending;
  nothing throws, because a node that throws on the expected case will have
  that path exercised by every over-eager client on the network.

  The ledger is what `kura.order/settlement-leaf` turns into a `merkle-sum`
  leaf at epoch close, so a byte counted wrong here is money."
  (:require [kura.order :as order]))

(defn open
  "Begin a transfer under an admitted order.

  Takes the admission result rather than the raw order, so that a transfer
  cannot be opened against something `kura.order/admit` never accepted."
  [ord admission]
  (assert (:ok? admission) "cannot open a transfer on a rejected order")
  {:order ord
   :order-id (order/order-id ord)
   :action (:action admission)
   :max-bytes (:max-bytes admission)
   :transferred 0
   :chunks 0
   :closed? false})

(defn feed
  "Account for `n` more bytes.

  Returns `{:transfer :allowed? :remaining}` where `:allowed?` is false when
  the chunk would exceed the order. A refused chunk does NOT advance the
  counter — the bytes were not sent, so charging for them would be charging
  for a refusal."
  [{:keys [max-bytes transferred closed?] :as t} n]
  (assert (not (neg? n)) "chunk size must be non-negative")
  (cond
    closed?
    {:transfer t :allowed? false :reason :transfer-closed :remaining 0}

    (not (order/within-limit? {:max-bytes max-bytes} transferred n))
    {:transfer t :allowed? false :reason :limit-exceeded
     :remaining (- max-bytes transferred)
     :would-have-been (+ transferred n)}

    :else
    (let [t' (-> t (update :transferred + n) (update :chunks inc))]
      {:transfer t' :allowed? true :remaining (- max-bytes (:transferred t'))})))

(defn close
  "End a transfer. Idempotent — a caller that closes twice on an error path
  should not get a different ledger than one that closes once."
  [t]
  (assoc t :closed? true))

(defn ledger-entry
  "The honoured order, as the coordinator will be asked to pay for it.

  `order-hash` is injected: the settlement tree commits to whatever the domain
  decides an order's canonical digest is, and this namespace holds no crypto."
  [{:keys [order transferred]} order-hash]
  (order/settlement-leaf order transferred order-hash))

(defn epoch-ledger
  "Every closed transfer of an epoch, as settlement leaves.

  Open transfers are excluded, not silently closed: a transfer still running
  at epoch boundary belongs to the next epoch, and folding it in early would
  bill for bytes that may yet be refused."
  [transfers order-hash]
  (into []
        (comp (filter :closed?)
              (map #(ledger-entry % order-hash)))
        transfers))

(defn epoch-totals
  "What the node will claim for an epoch, and what it actually did.

  `:claimed-bytes` is the sum the settlement tree will commit to.
  `:refused-chunks` is not billable and is reported anyway — a node seeing
  many refusals is being asked for more than its orders authorise, which is
  either a client bug or a coordinator issuing limits too small to be useful."
  [transfers]
  {:transfers (count transfers)
   :closed (count (filter :closed? transfers))
   :open (count (remove :closed? transfers))
   :claimed-bytes (reduce + 0 (map :transferred (filter :closed? transfers)))
   :chunks (reduce + 0 (map :chunks transfers))})
