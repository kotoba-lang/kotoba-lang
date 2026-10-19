(ns kotoba.lang.witness-quorum.orchestrator
  "Witnessed write orchestrator.

  Composes the substrate-pure write primitive, the witness selector, and the
  quorum reducer into a single L1-witnessed write that returns only when:
    - the record was committed to PDS (via `write-fn`), AND
    - >= :quorum-threshold witness cells produced matching accept (or
      reject) attestations.

  CLJ port of this repo's original @etzhayyim/witness-quorum
  src/orchestrator.ts. JVM-only, and synchronous throughout (unlike the TS
  original's async/Promise plumbing) -- see the repo README for the
  adaptation notes below.

  Per kotoba-datomic SPEC S5 + ADR-2605231400 S\"Implementation plan\" #2-#4."
  (:require [kotoba.lang.witness-quorum.attestation :as attestation]
            [kotoba.lang.witness-quorum.quorum :as quorum]
            [kotoba.lang.witness-quorum.reputation :as reputation]
            [kotoba.lang.witness-quorum.outcome :as outcome]
            [kotoba.lang.witness-quorum.selector :as selector]
            [kotoba.lang.witness-quorum.stake :as stake])
  (:import [java.security MessageDigest]
           [java.util.concurrent ConcurrentHashMap LinkedBlockingQueue TimeUnit]
           [java.util.function Function]))

(defn write-with-witnesses
  "One-shot L1-witnessed write. Returns when the quorum state is decided
  (witnessed / rejected / escalated) or the timeout fires.

  `opts`:
    :write-fn        `(fn [write-opts] -> {:uri ... :cid ...})`. Adapts the
                      TS `WriteCapableClient.write` method into a plain
                      function -- idiomatic Clojure prefers a function over
                      a single-method object.
    :write-opts      passed through to `write-fn` (e.g. {:collection ... :record ... :rkey ...}).
    :fleet           seq of fleet-cell maps (see `selector/fleet-cell`).
    :rule            the membrane rule (provides default :quorum-size/:quorum-threshold/:escalation-policy).
    :transport       {:request-attestation (fn [req] ...)
                       :subscribe-attestations (fn [quorum-group] -> poll-fn)}
                      -- see `create-in-memory-witness-transport` below. This
                      adapts the TS `WitnessTransport` interface's
                      `subscribeAttestations` (which returned an
                      AsyncIterable) into a function returning a `poll-fn`
                      compatible with `quorum/collect-quorum`.
    :quorum-options  override; default derived from `rule`.
    :timeout-ms      total wall-clock budget. Default 30000.

  Returns {:uri ... :cid ... :selected-witnesses [...] :state <quorum-state>}."
  [{:keys [write-fn write-opts fleet rule transport quorum-options timeout-ms]}]
  (let [quorum-options (or quorum-options
                            {:quorum-size (:quorum-size rule)
                             :quorum-threshold (:quorum-threshold rule)
                             :escalation-policy (or (:escalation-policy rule) :council)})
        receipt (write-fn write-opts)
        selected (selector/select-witnesses (:cid receipt) fleet (:quorum-size quorum-options (:quorum-size rule)))
        qg (selector/quorum-group (:cid receipt))
        poll-fn ((:subscribe-attestations transport) qg)]
    (doseq [cell selected]
      ((:request-attestation transport) {:cell cell
                                          :record-uri (:uri receipt)
                                          :record-cid (:cid receipt)
                                          :record (:record write-opts)
                                          :rule rule}))
    (let [state (quorum/collect-quorum selected poll-fn (assoc quorum-options :timeout-ms (or timeout-ms 30000)))]
      {:uri (:uri receipt)
       :cid (:cid receipt)
       :selected-witnesses selected
       :state state})))

(defn write-with-witnesses-precommit
  "Pre-commit variant of `write-with-witnesses` (ADR-2607110300 Phase 2):
  the write is proposed to witnesses BEFORE it is applied, and `commit-fn`
  only runs if the quorum reaches `:witnessed`. This is the difference
  between post-hoc cosign (the record already exists when witnesses see it)
  and pre-commit quorum (witnesses gate whether the record is applied at
  all).

  `opts` differs from `write-with-witnesses` in two keys:
    :propose-fn  `(fn [write-opts] -> {:uri ... :cid ...})`. Computes the
                 uri/cid the record WOULD have -- e.g. a local content-hash
                 derivation -- WITHOUT persisting anything. Must be
                 deterministic: the same `write-opts` must yield the same
                 :cid `commit-fn` will later persist, since witnesses sign
                 against this proposed cid.
    :commit-fn   `(fn [write-opts receipt] -> commit-result)`. Called ONLY
                 when the quorum state's :kind is :witnessed. Performs the
                 actual write/append. Not called on :rejected, :escalated,
                 or :pending.
  All other opts (:write-opts :fleet :rule :transport :quorum-options
  :timeout-ms) are unchanged from `write-with-witnesses`.

  Returns {:uri ... :cid ... :selected-witnesses [...] :state <quorum-state>
           :committed? bool :commit-result (present only when :committed? true)}.

  Note (honesty, per ADR-2607110300): this is crash-fault tolerance among
  witnesses operated by a single party, not Byzantine consensus -- there is
  no fork-choice/view-change for conflicting concurrent proposals. Callers
  must not represent this as BFT."
  [{:keys [propose-fn commit-fn write-opts fleet rule transport quorum-options timeout-ms]}]
  (let [quorum-options (or quorum-options
                            {:quorum-size (:quorum-size rule)
                             :quorum-threshold (:quorum-threshold rule)
                             :escalation-policy (or (:escalation-policy rule) :council)})
        receipt (propose-fn write-opts)
        selected (selector/select-witnesses (:cid receipt) fleet (:quorum-size quorum-options (:quorum-size rule)))
        qg (selector/quorum-group (:cid receipt))
        poll-fn ((:subscribe-attestations transport) qg)]
    (doseq [cell selected]
      ((:request-attestation transport) {:cell cell
                                          :record-uri (:uri receipt)
                                          :record-cid (:cid receipt)
                                          :record (:record write-opts)
                                          :rule rule}))
    (let [state (quorum/collect-quorum selected poll-fn (assoc quorum-options :timeout-ms (or timeout-ms 30000)))
          witnessed? (= :witnessed (:kind state))]
      (cond-> {:uri (:uri receipt)
               :cid (:cid receipt)
               :selected-witnesses selected
               :state state
               :committed? witnessed?}
        witnessed? (assoc :commit-result (commit-fn write-opts receipt))))))

(defn write-with-witnesses-precommit-and-record
  "write-with-witnesses-precommit, plus automatically applying the resulting
  quorum state to REPUTATION via outcome/apply-quorum-outcome.

  Renamed from `write-with-witnesses-precommit-and-slash` on 2026-08-05
  (ADR-2608055000 G1), and it no longer slashes. The old version confiscated
  bond from every witness in `:minority` -- which, because
  `quorum/quorum-state` folded `:escalate` into that list, included witnesses
  who only declined to judge. Disagreeing with a majority is not a
  cryptographically settleable fault, so it may not cost anyone property; see
  `outcome`'s docstring for the full argument and `stake/slash!` for where
  bond arithmetic legitimately lives.

  `:stake-ledger` is still accepted and still returned as `:stake-ledger'`,
  now strictly as a PASS-THROUGH: this fn does not modify it under any quorum
  outcome. It is kept in the signature so a caller threading a ledger through
  a batch of rounds does not have to special-case this one, and so the
  returned map still tells you which ledger the round ran against.

  Additional opts beyond write-with-witnesses-precommit:
    :reputation-db  current reputation db. Default reputation/empty-reputation.
    :stake-ledger   current stake ledger. Default stake/empty-ledger.
                    Passed through unchanged.

  `:slash-amount` is accepted and IGNORED (callers pinned to an older
  witness-quorum still pass it); it will be dropped once no caller sends it.

  Returns write-with-witnesses-precommit's result map plus `:reputation-db'`
  (updated), `:stake-ledger'` (unchanged), and `:outcomes` (this round's
  `{:agreed :disagreed :abstained}` cell-key audit trail, replacing the old
  `:slashed` vector of confiscated units). `:pending`/`:escalated` quorum
  states are NOT applied (not a decision -- matches
  reputation/record-quorum-outcomes's own caveat); in that case
  `:reputation-db'` passes through unchanged and `:outcomes` is empty."
  [{:keys [reputation-db stake-ledger]
    :or {reputation-db reputation/empty-reputation
         stake-ledger stake/empty-ledger}
    :as opts}]
  (let [result (write-with-witnesses-precommit opts)
        decided? (contains? #{:witnessed :rejected} (:kind (:state result)))
        {:keys [reputation-db outcomes]}
        (if decided?
          (outcome/apply-quorum-outcome reputation-db (:state result))
          {:reputation-db reputation-db
           :outcomes {:agreed [] :disagreed [] :abstained []}})]
    (assoc result
           :reputation-db' reputation-db
           :stake-ledger' stake-ledger
           :outcomes outcomes)))

(def ^{:deprecated "2026-08-05"
       :doc "Deprecated alias for `write-with-witnesses-precommit-and-record`,
  kept only so a caller pinned to an older witness-quorum keeps resolving
  while west pins move (murakumo's overlay is the one caller). It does NOT
  slash -- the name is the only thing that survived. Callers must migrate:
  the result carries `:outcomes` instead of `:slashed`, and `:stake-ledger'`
  is now always the ledger you passed in. ADR-2608055000 G1."}
  write-with-witnesses-precommit-and-slash
  write-with-witnesses-precommit-and-record)

;; --- In-memory transport (testing + integration smoke) ------------------

(defn- computing-queue ^LinkedBlockingQueue [^ConcurrentHashMap queues key]
  (.computeIfAbsent queues key
                     (reify Function
                       (apply [_ _] (LinkedBlockingQueue.)))))

(defn create-in-memory-witness-transport
  "In-memory WitnessTransport: routes each request-attestation to the
  matching cell handler on a background thread (`future`), and makes the
  produced attestation available to `subscribe-attestations`'s poll-fn via a
  `LinkedBlockingQueue` per quorum-group.

  Unlike the TS original's lazy queue/waiters dance (needed there to avoid
  losing attestations that race ahead of a subscriber showing up), a
  `LinkedBlockingQueue` obtained via `computeIfAbsent` on the *same*
  quorum-group key from both the producer and the consumer side makes the
  ordering irrelevant -- whichever side arrives first creates the queue,
  and blocking put/poll do the rest.

  `opts`:
    :cell-handlers        map from fleet-cell `:key` to a handler function
                           `(fn [request] -> attestation-map-or-nil)`.
                           Unknown cells produce no attestation (simulates a
                           non-responsive cell).
    :attestation-delay-ms  optional delay before the handler's result is
                           queued. Default 0."
  [{:keys [cell-handlers attestation-delay-ms]}]
  (let [queues (ConcurrentHashMap.)]
    {:request-attestation
     (fn [req]
       (future
         (when (and attestation-delay-ms (pos? attestation-delay-ms))
           (Thread/sleep (long attestation-delay-ms)))
         (when-let [handler (get cell-handlers (:key (:cell req)))]
           (when-let [att (handler req)]
             (.put (computing-queue queues (:quorum-group att)) att))))
       nil)

     :subscribe-attestations
     (fn [quorum-group]
       (let [q (computing-queue queues quorum-group)]
         (fn [remaining-ms]
           (let [item (.poll q (long (max 0 remaining-ms)) TimeUnit/MILLISECONDS)]
             (if item
               {:status :value :value item}
               {:status :timeout})))))}))

;; --- Deterministic test signer --------------------------------------------

(defn make-deterministic-test-signer
  "Test-only signer: produces a deterministic 32-byte \"signature\" via
  sha256(canonical-bytes || cell-id). Byte-stable across re-runs, which
  makes integration tests reproducible. Replace with
  `kotoba.lang.witness-quorum.signer/make-ed25519-cell-signer` in production
  cells."
  [cell-id]
  (fn [^bytes canonical-bytes]
    (let [cell-bytes (.getBytes ^String cell-id "UTF-8")
          combined (byte-array (+ (alength canonical-bytes) (alength cell-bytes)))]
      (System/arraycopy canonical-bytes 0 combined 0 (alength canonical-bytes))
      (System/arraycopy cell-bytes 0 combined (alength canonical-bytes) (alength cell-bytes))
      (.digest (MessageDigest/getInstance "SHA-256") combined))))

(defn make-standard-cell-handler
  "Convenience: build a default cell handler that runs the standard
  membrane validation pipeline and produces a signed attestation.

  `opts`: {:cell ... :signer ... :validators ...} (see
  `kotoba.lang.witness-quorum.attestation/produce-attestation`)."
  [{:keys [cell signer validators]}]
  (fn [req]
    (attestation/produce-attestation
     {:record-uri (:record-uri req)
      :record-cid (:record-cid req)
      :record (:record req)
      :cell cell
      :rule (:rule req)
      :validators validators
      :signer signer})))
