(ns kura.order
  "Whether a node should do a piece of work, and be paid for having done it.

  An order is a capability: the coordinator signs a statement saying *this*
  node may perform *this* action on *this* shard, up to *this* many bytes,
  until *this* time. A node that checks the signature and nothing else will
  serve an order addressed to someone else, or one that expired last month, or
  one that authorises a read when the request is a write. Each of those is a
  way to be used as somebody else's storage, for free.

  The shape is lifted deliberately from `storj.node.orders/admit`, already in
  this workspace — including the two judgement calls that make it good, which
  are worth restating because they are easy to undo by accident:

  - **Every reason, not the first.** A caller wanting yes/no reads `:ok?`. A
    caller debugging a coordinator integration wants all of them at once, and
    stopping at the first failure turns that into a guessing game.
  - **The signature check is last, and is skipped when the order is already
    rejected on its contents.** That is not an optimisation. It means a node
    under a flood of malformed orders does no asymmetric crypto on any of
    them.

  Orders are also the unit of payment (ADR-2607299200 section 6d): a node
  accumulates the orders it honoured and presents them at epoch close, where
  they are folded into a `merkle-sum` tree whose root goes on chain. So an
  order that `admit` accepted is a claim on money, and the checks here are the
  only thing standing between a coordinator's signature and an invoice.

  ## Where the rules live

  The admission decisions are not computed here. The closed action set, the two
  clock comparisons, the running-total limit, the settlement leaf id, and the
  two structural rules inside `admit` convert their arguments into the guest
  ABI, run `kotoba/order_core.kotoba` — compiled, shipped as
  `resources/kura/oracle/order.kir.edn`, executed by `kura.kotoba-oracle` — and
  convert the answer back. What stays here is everything that is not a
  decision: reading a map, naming a key, and collecting *every* reason rather
  than the first (see above — that property is a vector of heterogeneous maps
  and does not cross this profile, which is exactly why it stayed).

  ## `signing-bytes` did NOT move, and the reason is measured

  It is the sharpest thing in this namespace and it is still computed here.
  Two facts, both measured 2026-08-12 and both about the pins in `deps.edn`
  rather than about the port:

  1. The guest builds the statement with `string-from-i64`, which the compiler
     lowers to `(string-substring \"0123456789\" n (+ n 1))`. At the pinned
     interpreter, `utf8-substring!` guards with `(integer? start)` — and under
     ClojureScript an `:i64` is a `js/BigInt`, for which that is false. So a
     delegated `signing-bytes` throws `\"string substring indexes are out of
     bounds\"` on ClojureScript and works on the JVM. `kura.cljs-runner`
     caught it; the JVM suite was green throughout.
  2. The interpreter that fixes this can only be taken together with a compiler
     that rejects this repo's own source (`string=?` returns `:bool` there and
     `:i64` here), so the fix is on the far side of a language migration of all
     three cores and their parity tests.

  Delegating it on the JVM only would put the rule back in two places on
  purpose, which is the thing this seam exists to remove. So it stays whole,
  on the host, under `kura.kotoba-decision-parity-test`, until that migration
  lands. **The known divergence it carries is pinned by
  `kura.kotoba-oracle-test` rather than left to be rediscovered**: the host
  length-delimits in UTF-16 units (a JVM character count, a ClojureScript
  code-unit count) while the port counts UTF-8 bytes, so a non-ASCII node-id,
  shard-id or nonce frames differently on every host. ASCII — every id
  `kura.hash` will key on — agrees.

  ## The boundary, stated

  `order_core.kotoba` types the time-and-size fields `:i64`. This namespace has
  always accepted an order with a `nil` or malformed limit — `admit` exists to
  REJECT such an order, so it must be able to hold one. Delegating
  unconditionally would turn a rejection into a crash, so it is conditional:
  when every field a call needs is an integer the shipped core answers, and
  when it is not the host path answers with exactly the behaviour it had
  before. Which path ran is decided by the data, never by whether the artifact
  loaded — a missing artifact throws rather than falling back.

  ## ClojureScript hosts must register the KIR

  There is no classpath to read the artifact from, so a ClojureScript host has
  to `kura.kotoba-oracle/register-kir!` before `admit`, `within-limit?` or
  `settlement-leaf` will answer; without it they throw. This is a real
  narrowing on that runtime, and it is the price of the rules having one home."
  (:require [kura.hash :as h]
            [kura.kotoba-oracle :as oracle]))

(def actions
  "What an order can authorise. `:put` and `:get` are client traffic; `:repair-get`
  is a peer reading a shard to rebuild another, and is separated because it is
  paid differently and must not be usable to serve a client read.

  This set is the PUBLISHED form of the closed set, for callers that want to
  enumerate it; `admit` does not consult it for a keyword action — it asks
  `known-action?` in the shipped core. The two are held equal by
  `kura.kotoba-oracle-test`, which is the shape ADR-2608120200 asks for when a
  constant exists on both sides of a port."
  #{:put :get :delete :repair-get :audit-get})

(def default-clock-skew-seconds
  "How far past its expiry an order is still honoured.

  Nodes and coordinators do not share a clock, and a node that trusts its own
  to the second rejects work it was legitimately given. It cuts one way only:
  an order is never honoured *before* it exists."
  0)

(defn order
  "Construct an unsigned order. `:nonce` makes two otherwise identical orders
  distinguishable, so that replaying a settled order is detectable at
  settlement rather than only at admission."
  [{:keys [node-id action shard-id max-bytes expires-at issued-at nonce]}]
  {:node-id node-id :action action :shard-id shard-id
   :max-bytes max-bytes :expires-at expires-at
   :issued-at issued-at :nonce nonce})

(defn signing-bytes
  "The canonical string an order's signature covers.

  Field order is fixed here and is part of the wire contract — a verifier that
  reassembles it differently verifies a different statement. Every component is
  length-delimited so that no two distinct orders can serialise the same way by
  shifting a delimiter into a field.

  **This is the one decision in this namespace that did not move to the shipped
  core, and the reason is a measured property of the pins rather than of the
  port — see the namespace docstring.** `kotoba/order_core.kotoba` states the
  same contract and `kura.kotoba-decision-parity-test` holds the two byte-equal
  for ASCII; `kura.kotoba-oracle-test` pins where they do NOT agree."
  [{:keys [node-id action shard-id max-bytes expires-at issued-at nonce]}]
  (let [part (fn [x] (let [s (str x)] (str (count s) ":" s)))]
    (apply str "kura-order-v1|"
           (map part [node-id (name (or action :nil)) shard-id
                      max-bytes expires-at issued-at nonce]))))

(defn- known-action?
  "Whether `action` is in the closed set the shipped core declares.

  A non-keyword action never reaches the guest — `(name \"put\")` is \"put\",
  so a string would be admitted where `contains?` on `actions` refuses it, and
  the host set is the one that has always answered that question."
  [action]
  (if (keyword? action)
    (oracle/truth (oracle/call :order 'known-action? [(name action)]))
    (contains? actions action)))

(defn- expired? [expires-at now skew]
  (if (and (oracle/fits-i64? expires-at) (oracle/fits-i64? now) (oracle/fits-i64? skew))
    (oracle/truth (oracle/call :order 'expired?
                               [(oracle/i64 expires-at) (oracle/i64 now) (oracle/i64 skew)]))
    ;; Presence is the host's question — this profile has no nil. A field that
    ;; is present but not an integer still compares here, and still throws
    ;; here, exactly as it did.
    (and (some? expires-at) (some? now) (> now (+ expires-at skew)))))

(defn- not-yet-valid? [issued-at now]
  (if (and (oracle/fits-i64? issued-at) (oracle/fits-i64? now))
    (oracle/truth (oracle/call :order 'not-yet-valid?
                               [(oracle/i64 issued-at) (oracle/i64 now)]))
    (and (some? issued-at) (some? now) (< now issued-at))))

(defn admit
  "Decide whether `ord` may be acted on. Returns
  `{:ok? :reasons :action :max-bytes :shard-id}`.

  `opts`: `:node-id` (this node's own id — `is this addressed to me` is the
  check most worth not skipping), `:action` (what the request is actually
  asking for), `:now` (seconds), `:verify-fn` (`(fn [message signature] bool)`,
  injected so this namespace holds no crypto), `:signature`, `:skew-seconds`,
  and `:seen-nonce?` (`(fn [nonce] bool)` for replay)."
  [ord {:keys [node-id action now verify-fn signature skew-seconds seen-nonce?]
        :or {skew-seconds default-clock-skew-seconds}}]
  (let [content
        (cond-> []
          (nil? (:node-id ord))
          (conj {:reason :missing-node-id})

          (nil? node-id)
          (conj {:reason :no-node-id-configured})

          (and (some? (:node-id ord)) (some? node-id) (not= (:node-id ord) node-id))
          (conj {:reason :addressed-to-another-node
                 :expected node-id :found (:node-id ord)})

          (nil? (:shard-id ord))
          (conj {:reason :missing-shard-id})

          (nil? (:action ord))
          (conj {:reason :missing-action})

          (and (some? (:action ord)) (not (known-action? (:action ord))))
          (conj {:reason :unknown-action :value (:action ord)})

          (and (some? action) (some? (:action ord)) (not= action (:action ord)))
          (conj {:reason :action-mismatch :requested action :authorised (:action ord)})

          (or (nil? (:max-bytes ord)) (not (integer? (:max-bytes ord)))
              (neg? (:max-bytes ord)))
          (conj {:reason :missing-or-invalid-limit :value (:max-bytes ord)})

          (expired? (:expires-at ord) now skew-seconds)
          (conj {:reason :order-expired :expired-at (:expires-at ord) :now now})

          (not-yet-valid? (:issued-at ord) now)
          (conj {:reason :order-not-yet-valid :issued-at (:issued-at ord) :now now})

          (nil? (:nonce ord))
          (conj {:reason :missing-nonce})

          (and (some? seen-nonce?) (some? (:nonce ord)) (seen-nonce? (:nonce ord)))
          (conj {:reason :replayed-nonce :nonce (:nonce ord)})

          (nil? signature)
          (conj {:reason :missing-signature}))

        ;; The two structural decisions, both from the shipped core.
        ;;
        ;; `signature-check-needed?` is why the crypto comes last and is
        ;; skipped when the order is already rejected on its contents — see the
        ;; namespace docstring. Asking the guest is what makes that property a
        ;; rule with one home rather than an `if` a refactor can quietly
        ;; reorder: if it ever answered yes for an incoherent order, the line
        ;; below would run asymmetric crypto on every malformed order that
        ;; arrives.
        verifier-present (if (some? verify-fn) 1 0)
        check-signature? (oracle/truth
                          (oracle/call :order 'signature-check-needed?
                                       [(oracle/i64 (count content))
                                        (oracle/i64 verifier-present)]))
        signature-valid (if (and check-signature?
                                 (verify-fn (signing-bytes ord) signature))
                          1 0)

        ;; The reasons stay here: a vector of heterogeneous maps, one per
        ;; failure, each carrying the offending value. That is the "every
        ;; reason, not the first" property, and it is not a decision.
        problems
        (cond
          (seq content) content
          (zero? verifier-present) [{:reason :no-verifier-configured}]
          (zero? signature-valid) [{:reason :bad-coordinator-signature}]
          :else [])]
    {:ok? (oracle/truth (oracle/call :order 'admit-ok?
                                     [(oracle/i64 (count content))
                                      (oracle/i64 verifier-present)
                                      (oracle/i64 signature-valid)]))
     :reasons problems
     :action (:action ord)
     :max-bytes (:max-bytes ord)
     :shard-id (:shard-id ord)}))

(defn within-limit?
  "Whether transferring `n` more bytes stays inside what the order allows.

  A node that checks the limit once at the start and then streams has not
  checked it: the client controls how much it sends."
  [{:keys [max-bytes]} transferred n]
  (if (and (oracle/fits-i64? max-bytes) (oracle/fits-i64? transferred) (oracle/fits-i64? n))
    (oracle/truth (oracle/call :order 'within-limit?
                               [(oracle/i64 max-bytes) (oracle/i64 transferred) (oracle/i64 n)]))
    (and (some? max-bytes) (<= (+ transferred n) max-bytes))))

;; --- settlement ------------------------------------------------------------

(defn settlement-leaf
  "One honoured order as a `merkle-sum` leaf: `:sum` is the bytes actually
  transferred, so an epoch's root sums to the total volume the coordinator
  owes for.

  This is what makes the payout auditable (ADR-2607299200 section 6d). The
  coordinator cannot understate the epoch total without contradicting some
  node's inclusion proof, and cannot inflate a node's share without
  contradicting the root it published on chain."
  [ord bytes-transferred order-hash]
  (assert (and (integer? bytes-transferred) (not (neg? bytes-transferred)))
          "bytes-transferred must be a non-negative integer")
  ;; `:id` is what settlement dedupes and orders on, so the node claiming and
  ;; the coordinator paying have to build it the same way — which is why it
  ;; comes from the shipped core and the leaf map around it does not (`:hash`
  ;; is an externally supplied digest).
  {:id (oracle/call :order 'settlement-leaf-id
                    [(str (:node-id ord)) (str (:nonce ord))])
   :hash order-hash
   :sum bytes-transferred})

(defn order-id
  "Stable identifier of an order, for dedupe and settlement leaf ordering."
  [ord]
  (h/key32 (signing-bytes ord)))
