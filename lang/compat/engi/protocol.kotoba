(ns engi.protocol
  "Transfer state machine (ADR-2607101100 Decision #2): propose -> validate
  -> counter-commit -> finalize, plus `audit-agent!`. Pure orchestration —
  every fn here takes an explicit client (or clients) and returns a Promise;
  all the actual invariant logic lives in `engi.core` (pure) and
  `engi.crypto` (signing), this ns just wires them to `engi.store` I/O so
  the whole handshake can be exercised against a FAKE in-memory client in
  tests (see test/engi/protocol_test.cljs) and the REAL kotobase.net client
  identically (test/engi/live_test.cljs) — no branching on client type
  anywhere in this file.

  `client` here means anything `engi.store`'s `fetch-entities!`/
  `write-entity!` accept — either a real `kotobase.client` client, or (in
  tests) a fake with the same `fetch-entities!`/`write-entity!` shape (a
  plain map of dids -> atom of entities works, see the test fake)."
  (:require [engi.core :as core]
            [engi.crypto :as crypto]
            [engi.store :as store]))

;; ── 1. propose (spender side) — no writes ─────────────────────────────────

(defn propose-transfer!
  "Spender computes its next {seq, prev-hash}, builds + self-signs the
  TransferBody, and returns the proposal — nothing is written yet (ADR step
  1). `spender-client` reads the spender's OWN graph (owner-client).

  opts: :nonce :now-ms (deterministic test overrides).
  → Promise<{:transfer-body :transfer-id :self-sig :spender-did :receiver-did
             :amount :memo :spender-prev :spender-seq}>."
  ([spender-client spender-secret-key receiver-did amount]
   (propose-transfer! spender-client spender-secret-key receiver-did amount {}))
  ([spender-client spender-secret-key receiver-did amount
    {:keys [memo nonce now-ms]}]
   (-> (store/fetch-entities! spender-client)
       (.then
        (fn [entities]
          (let [{:keys [seq last-entry]} (core/current-head entities)
                spender-prev (if last-entry (crypto/entry-hash last-entry) "genesis")
                spender-did (:did spender-client)
                ts (or now-ms (js/Date.now))
                body {:spender spender-did :receiver receiver-did :amount amount
                      :spender-prev spender-prev :nonce (or nonce (str (random-uuid))) :ts ts}
                {:keys [transfer-id sig]} (crypto/sign-transfer body spender-secret-key)]
            {:transfer-body body
             :transfer-id transfer-id
             :self-sig sig
             :spender-did spender-did
             :receiver-did receiver-did
             :amount amount
             :memo memo
             :spender-prev spender-prev
             :spender-seq seq
             :ts ts}))))))

;; ── 2. validate (receiver side) — reads only ──────────────────────────────

(defn validate-proposal!
  "Receiver re-derives the spender's current state from the spender's OWN
  graph (never trusts the proposal's claims) and checks every invariant.
  `spender-reader-client` is a `store/reader-client` for the spender's did
  (or the spender's own client works too, for tests). `known-validators` —
  optional seq of reader-clients whose graphs may carry already-filed
  warrant entities naming this spender (opt-in registry check on top of the
  direct, always-on fork re-check — see ns docstring / README).

  ── real limitation, live-probed 2026-07-09 (see README \"Known
  limitations\") ── against PRODUCTION kotobase.net, `store/reader-client`
  (unauthenticated `:public-reads?`) gets 401 Unauthorized reading a graph
  it doesn't own — the apex requires a CACAO on every call, and only the
  graph owner's own key can mint a satisfying one. So today a genuine
  cross-agent call (the receiver independently reading the SPENDER's graph)
  only works once the spender's graph is registered \"Public\" server-side,
  or once a delegated-read-CACAO handshake exists (neither is implemented
  here). Until then, `spender-reader-client` in practice has to be the
  spender's OWN owner-client (a weaker, self-attested check) — this fn's
  LOGIC is unaffected either way; only which client can successfully fetch
  changes. `engi.protocol-test` exercises the full cross-agent design
  against the in-memory fake, where this restriction doesn't apply.

  → Promise<{:valid? bool :reason (when invalid) ...diagnostics}>."
  ([spender-reader-client proposal] (validate-proposal! spender-reader-client proposal {}))
  ([spender-reader-client proposal {:keys [known-validators] :or {known-validators []}}]
   (-> (js/Promise.all
        (clj->js (cons (store/fetch-entities! spender-reader-client)
                       (map store/fetch-entities! known-validators))))
       (.then
        (fn [^js results]
          (let [[spender-entities & validator-entity-sets] (array-seq results)
                validator-warrants (mapcat #(filter core/warrant? %) validator-entity-sets)
                {:keys [balance credit-limit violations]} (core/fold-balance spender-entities)
                {:keys [last-entry]} (core/current-head spender-entities)
                actual-prev (if last-entry (crypto/entry-hash last-entry) "genesis")
                claimed-prev (get-in proposal [:transfer-body :spender-prev])
                sig-ok? (crypto/verify-transfer-id-sig (:transfer-id proposal) (:self-sig proposal)
                                                        (:spender-did proposal))
                forks (core/detect-fork spender-entities)
                spender-entity-ids (set (map :db/id spender-entities))
                live-warrants (filter (fn [w] (or (contains? spender-entity-ids (:engi/evidence-tx-a w))
                                                   (contains? spender-entity-ids (:engi/evidence-tx-b w))))
                                       validator-warrants)
                new-balance (- balance (:amount proposal))]
            (cond
              (seq violations)
              {:valid? false :reason :spender-chain-invalid :violations violations}

              (nil? credit-limit)
              {:valid? false :reason :spender-no-genesis}

              (not= actual-prev claimed-prev)
              {:valid? false :reason :stale-prev :expected actual-prev :got claimed-prev}

              (not sig-ok?)
              {:valid? false :reason :bad-signature}

              (seq forks)
              {:valid? false :reason :spender-forked :forks forks}

              (seq live-warrants)
              {:valid? false :reason :spender-under-warrant :warrants live-warrants}

              (< new-balance credit-limit)
              {:valid? false :reason :credit-limit-exceeded
               :balance balance :new-balance new-balance :credit-limit credit-limit}

              :else
              {:valid? true :balance balance :new-balance new-balance :credit-limit credit-limit})))))))

;; ── 3. counter-commit (receiver side) — one write, to the receiver's OWN graph

(defn counter-commit!
  "If `validate-proposal!` said `:valid? true`, the receiver writes a credit
  entry to its OWN graph and returns its counter-signature (over the SAME
  transfer-id the spender signed) for the spender to finalize with.
  `receiver-client` is the receiver's OWN owner-client.

  → Promise<{:counter-sig :credit-entry}>."
  [receiver-client receiver-secret-key proposal]
  (-> (store/fetch-entities! receiver-client)
      (.then
       (fn [entities]
         (let [{:keys [seq last-entry]} (core/current-head entities)
               prev-hash (if last-entry (crypto/entry-hash last-entry) "genesis")
               counter-sig (crypto/sign-transfer-id (:transfer-id proposal) receiver-secret-key)
               entry (core/next-entry
                      {:seq seq} prev-hash
                      {:kind "credit"
                       :counterparty (:spender-did proposal)
                       :amount (:amount proposal)
                       :memo (:memo proposal)
                       :transfer-id (:transfer-id proposal)
                       :ts (:ts proposal)
                       :self-sig counter-sig
                       :counter-sig (:self-sig proposal)})]
           (-> (store/write-entry! receiver-client entry)
               (.then (fn [_] {:counter-sig counter-sig :credit-entry entry}))))))))

;; ── 4. finalize (spender side) — one write, to the spender's OWN graph ────

(defn finalize!
  "Upon receiving the receiver's counter-sig, the spender writes its debit
  entry. Re-checks the chain hasn't moved since `propose-transfer!` (a
  concurrent finalize would otherwise silently fork the chain) — refuses
  (does not write) rather than risk it; the caller should re-propose.

  `_spender-secret-key` is accepted (unused) for signature symmetry with
  `propose-transfer!`/`counter-commit!` — finalize reuses the self-sig
  already computed at propose time rather than re-signing.

  → Promise<{:finalized? bool :debit-entry (when finalized) :reason (when not)}>."
  [spender-client _spender-secret-key proposal counter-sig]
  (-> (store/fetch-entities! spender-client)
      (.then
       (fn [entities]
         (let [{:keys [seq last-entry]} (core/current-head entities)
               actual-prev (if last-entry (crypto/entry-hash last-entry) "genesis")]
           (if (not= actual-prev (:spender-prev proposal))
             (js/Promise.resolve {:finalized? false :reason :stale-prev-at-finalize
                                   :expected actual-prev :got (:spender-prev proposal)})
             (let [entry (core/next-entry
                          {:seq seq} actual-prev
                          {:kind "debit"
                           :counterparty (:receiver-did proposal)
                           :amount (:amount proposal)
                           :memo (:memo proposal)
                           :transfer-id (:transfer-id proposal)
                           :ts (:ts proposal)
                           :self-sig (:self-sig proposal)
                           :counter-sig counter-sig})]
               (-> (store/write-entry! spender-client entry)
                   (.then (fn [_] {:finalized? true :debit-entry entry}))))))))))

;; ── bilateral confirmation (async orchestration over engi.core's pure check)

(defn pending-unconfirmed!
  "Async version of `engi.core/unconfirmed-credit?` — fetches the
  counterparty's entities via `counterparty-reader-client` (an
  `engi.store/reader-client`, or fake-equivalent) then delegates to the pure
  check. → Promise<bool>."
  [credit-entry counterparty-reader-client]
  (-> (store/fetch-entities! counterparty-reader-client)
      (.then (fn [counterparty-entities] (core/unconfirmed-credit? credit-entry counterparty-entities)))))

(defn- fetch-pending-ids
  "own-entities + a did->entities map (already fetched) -> the set of
  :engi/transfer-id values among own-entities' credits that are still
  unconfirmed. Split out from `spendable-balance!` so it's independently
  testable without Promise plumbing."
  [own-entities counterparty-entities-by-did]
  (->> own-entities
       (filter core/credit?)
       (filter (fn [c] (core/unconfirmed-credit? c (get counterparty-entities-by-did (:engi/counterparty c)))))
       (map :engi/transfer-id)
       set))

(defn- fold-with-pending [own-entities credit-dids ^js entity-sets]
  (let [by-did (zipmap credit-dids (array-seq entity-sets))
        pending-ids (fetch-pending-ids own-entities by-did)]
    (core/fold-balance own-entities {:hash-fn crypto/entry-hash
                                      :pending-transfer-ids pending-ids})))

(defn spendable-balance!
  "Full projection for one agent, INCLUDING bilateral-confirmation filtering:
  fetches the agent's own entities, then every distinct counterparty
  appearing in a credit entry (via `counterparty-client-fn`, `(fn [did] ->
  reader-client)`), computes which credits are still unconfirmed, and folds
  with `:pending-transfer-ids` set so those don't count toward
  `:spendable-balance` (ADR #2 step 5). → Promise<fold-balance result>."
  [own-client counterparty-client-fn]
  (-> (store/fetch-entities! own-client)
      (.then (fn [own-entities]
               (let [credit-dids (->> own-entities (filter core/credit?) (map :engi/counterparty) distinct)
                     fetches (map #(store/fetch-entities! (counterparty-client-fn %)) credit-dids)]
                 (-> (js/Promise.all (clj->js fetches))
                     (.then #(fold-with-pending own-entities credit-dids %))))))))

;; ── audit ──────────────────────────────────────────────────────────────────

(defn audit-agent!
  "Full-history audit of one agent's graph: fold (with prev-hash chain
  verification) + fork detection. Mirrors the original Rust design's
  `audit_peer_chain`/`Engi::audit_solvency` spirit. → Promise<{:balance
  :spendable-balance :violations :forks :credit-limit :entries}>."
  [reader-client]
  (-> (store/fetch-entities! reader-client)
      (.then
       (fn [entities]
         (let [fold-result (core/fold-balance entities {:hash-fn crypto/entry-hash})
               forks (core/detect-fork entities)]
           (assoc fold-result :forks forks))))))
