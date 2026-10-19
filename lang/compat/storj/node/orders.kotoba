(ns storj.node.orders
  "Whether a storage node should honour an order limit.

  An `OrderLimit` is a capability: the satellite signs a statement saying
  *this* node may perform *this* action on *this* piece, up to *this* many
  bytes, until *this* time. A node that checks the signature and nothing else
  will happily serve a limit addressed to a different node, or one that
  expired last month, or one whose action says GET when the request is a PUT.
  Each of those is a real way to be used as someone else's storage.

  So `admit` returns every reason a limit is unacceptable, not the first.
  A caller that only wants a yes/no reads `:ok?`; a caller diagnosing a
  satellite integration wants all of them at once, and stopping at the first
  failure turns that into a guessing game.

  The signature check is last, and is skipped when the limit is already
  rejected on its own contents. That ordering is not an optimisation — it
  means a node under a flood of malformed limits does no asymmetric crypto on
  any of them."
  (:require [storj.node.bytes :as b]
            [storj.node.pb :as pb]
            [storj.node.protocols :as p]))

(def default-clock-skew-seconds
  "How far past its expiry an order limit is still honoured.

  Storage nodes and satellites do not share a clock, and a node that trusts
  its own to the second will reject work it was legitimately given. Storj's
  own storagenode config exposes this as a knob for the same reason. It cuts
  one way only: a limit is never honoured *before* it exists."
  0)

(defn- expired? [expiry now skew]
  (and (some? expiry) (> now (+ expiry skew))))

(defn admit
  "Decide whether `limit` (a decoded `OrderLimit`) may be acted on.

  Returns `{:ok? bool :reasons [...] :action kw :limit n :piece-id bytes}`.

  `opts` takes `:node-id` (this node's 32 bytes — required, since `is this
  addressed to me` is the check most worth not skipping), `:action` (the
  action the request is actually asking for), `:satellite-key` and
  `:algorithm` for the signature, and `:skew-seconds`."
  [limit {:keys [node-id action satellite-key algorithm clock verifier
                 skew-seconds]
          :or   {skew-seconds default-clock-skew-seconds}}]
  (let [sig        (pb/get-bytes limit pb/order-limit :satellite-signature)
        target     (pb/get-bytes limit pb/order-limit :storage-node-id)
        limit-act  (pb/get-enum limit pb/order-limit :action pb/piece-action)
        max-bytes  (pb/get-varint limit pb/order-limit :limit)
        piece-id   (pb/get-bytes limit pb/order-limit :piece-id)
        order-exp  (pb/timestamp-seconds (pb/get-field limit pb/order-limit :order-expiration))
        now        (some-> clock p/-now-seconds)
        content-problems
        (cond-> []
          (nil? target)
          (conj {:reason :missing-storage-node-id})

          (and (some? target) (some? node-id) (not (b/equal? target node-id)))
          (conj {:reason :addressed-to-another-node
                 :expected (b/hex node-id) :found (b/hex target)})

          (nil? node-id)
          (conj {:reason :no-node-id-configured})

          (nil? piece-id)
          (conj {:reason :missing-piece-id})

          (or (nil? limit-act) (= :invalid limit-act))
          (conj {:reason :missing-action})

          (and (some? action) (some? limit-act) (not= action limit-act))
          (conj {:reason :action-mismatch :requested action :authorised limit-act})

          (and (some? limit-act) (vector? limit-act))
          (conj {:reason :unknown-action :value (second limit-act)})

          (nil? max-bytes)
          (conj {:reason :missing-limit})

          (and (some? now) (expired? order-exp now skew-seconds))
          (conj {:reason :order-expired :expired-at order-exp :now now})

          (nil? sig)
          (conj {:reason :missing-satellite-signature}))

        ;; Only reached when the limit is internally coherent — see the
        ;; namespace docstring on why the crypto comes last.
        problems
        (if (seq content-problems)
          content-problems
          (cond-> []
            (or (nil? verifier) (nil? satellite-key))
            (conj {:reason :no-verifier-configured})

            (and (some? verifier) (some? satellite-key)
                 (not (p/-verify verifier algorithm satellite-key
                                 (pb/encode-order-limit-for-signing limit)
                                 sig)))
            (conj {:reason :bad-satellite-signature})))]
    {:ok?      (empty? problems)
     :reasons  problems
     :action   limit-act
     :limit    max-bytes
     :piece-id piece-id}))

(defn within-limit?
  "Whether transferring `n` more bytes stays inside what the limit allows.

  A node that checks the limit once at the start and then streams has not
  checked it: the uplink controls how much it sends."
  [{:keys [limit]} transferred n]
  (and (some? limit) (<= (+ transferred n) limit)))
