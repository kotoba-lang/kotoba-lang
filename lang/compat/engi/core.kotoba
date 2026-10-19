(ns engi.core
  "ENGI/EN mutual-credit ledger — pure, no-I/O core (ADR-2607101100).

  Everything in this namespace is a pure function of data: no crypto calls
  (@noble/curves lives in `engi.crypto`, cljs-only — see that ns), no
  kotobase.net I/O (that's `engi.store`), no wall-clock reads (`:ts` is
  always caller-supplied so tests are deterministic). This is what makes it
  a genuine `.cljc` — it compiles and runs identically under JVM `clojure
  -M:test` and cljs `shadow-cljs`, with zero platform requires.

  ── schema (ADR-2607101100, exact keywords) ──────────────────────────────

  genesis (once per agent graph):
    {:db/id \"engi/genesis\" :engi/kind \"genesis\"
     :engi/credit-limit <negative int> :engi/created-at <ms>}

  transfer entry (written to ONE party's own graph only):
    {:db/id \"engi/tx/<uuid>\" :engi/kind \"debit\"|\"credit\"
     :engi/seq <monotonic int, 0-based> :engi/prev-hash <string, or \"genesis\">
     :engi/counterparty <did:key> :engi/amount <int> :engi/memo <string, optional>
     :engi/transfer-id <CID of the canonical TransferBody>
     :engi/self-sig <Ed25519 sig> :engi/counter-sig <Ed25519 sig or nil>
     :engi/ts <ms>}

  warrant (fork/violation evidence):
    {:db/id \"engi/warrant/<uuid>\" :engi/kind \"warrant\"
     :engi/evidence-tx-a <eid> :engi/evidence-tx-b <eid>
     :engi/detector <did:key> :engi/detector-sig <Ed25519 sig> :engi/ts <ms>}

  Graph naming: `kotobase/db/<did:key>/engi` (genko's
  `kotobase/db/<did:key>/genko` convention, ADR-2607101100 \"Decision #1\").")

;; ── canonical serialization (for signing / CID — no crypto here) ─────────

(defn canonical-transfer-body
  "Deterministic string serialization of a TransferBody for signing and CID
  computation. Field-by-field concatenation (same style as kotobase.cacao's
  `cacao-siwe-message` — plain string building, no JSON/EDN printer
  dependency, so JVM and cljs produce byte-identical output for the same
  map). `body` is `{:spender :receiver :amount :spender-prev :nonce :ts}`
  — every field required and stringified via `str` (nil is written as the
  literal string \"nil\", which is intentionally distinguishable from any
  real value so a caller can't accidentally omit a field and produce a
  colliding canonical form)."
  [{:keys [spender receiver amount spender-prev nonce ts]}]
  (str "engi/transfer-body\n"
       "spender=" spender "\n"
       "receiver=" receiver "\n"
       "amount=" amount "\n"
       "spender-prev=" spender-prev "\n"
       "nonce=" nonce "\n"
       "ts=" ts "\n"))

(defn canonical-entry
  "Deterministic string serialization of one already-built engi/tx entry, for
  hashing into the NEXT entry's `:engi/prev-hash`. Includes both signatures
  (a chain link commits to the fully-countersigned entry, not just the
  proposal) but not `:db/id` (an entity id is a storage detail, not part of
  the ledger content)."
  [{:keys [engi/kind engi/seq engi/prev-hash engi/counterparty engi/amount
           engi/memo engi/transfer-id engi/self-sig engi/counter-sig engi/ts]}]
  (str "engi/entry\n"
       "kind=" kind "\n"
       "seq=" seq "\n"
       "prev-hash=" prev-hash "\n"
       "counterparty=" counterparty "\n"
       "amount=" amount "\n"
       "memo=" memo "\n"
       "transfer-id=" transfer-id "\n"
       "self-sig=" self-sig "\n"
       "counter-sig=" counter-sig "\n"
       "ts=" ts "\n"))

;; ── genesis / entry construction ──────────────────────────────────────────

(defn genesis
  "Build the once-per-graph genesis entity. `credit-limit` must be a negative
  int (or zero — a zero-credit-limit agent can only ever receive, never
  spend first) — the ADR's \"マイナス方向への許容量\"."
  [{:keys [credit-limit created-at]}]
  (when (pos? credit-limit)
    (throw (ex-info "engi/credit-limit must be <= 0" {:credit-limit credit-limit})))
  {:db/id "engi/genesis"
   :engi/kind "genesis"
   :engi/credit-limit credit-limit
   :engi/created-at created-at})

(defn genesis? [entity] (= "genesis" (:engi/kind entity)))
(defn tx-entry? [entity] (contains? #{"debit" "credit"} (:engi/kind entity)))
(defn warrant? [entity] (= "warrant" (:engi/kind entity)))
(defn debit? [entity] (= "debit" (:engi/kind entity)))
(defn credit? [entity] (= "credit" (:engi/kind entity)))

(defn current-head
  "Given ALL entities of one agent's own graph (any order, genesis included
  or not), returns `{:seq :last-entry :credit-limit}` — `:seq` is -1 with no
  tx entries yet (genesis only), `:last-entry` is that highest-`:engi/seq`
  tx entry (nil at the empty chain), `:credit-limit` is the genesis value
  (nil if genesis hasn't been written — caller should treat that as
  \"ungenesis'd, refuse all activity\")."
  [entities]
  (let [genesis-entity (first (filter genesis? entities))
        tx-entries (filter tx-entry? entities)
        last-entry (when (seq tx-entries) (apply max-key :engi/seq tx-entries))]
    {:seq (if last-entry (:engi/seq last-entry) -1)
     :last-entry last-entry
     :credit-limit (:engi/credit-limit genesis-entity)}))

(defn next-entry
  "Given the agent's current head (`current-head` shape, but only `:seq` is
  read — `prev-hash` is passed explicitly since computing the hash of
  `:last-entry` needs `engi.crypto`) and the fields of the transfer being
  recorded, build the next debit or credit entry. Does NOT sign it (that's
  `engi.crypto/sign-transfer`, layered on top) — `:engi/self-sig` /
  `:engi/counter-sig` may be passed in `opts` when already known, else they
  default to nil and the caller assocs them in later."
  [{:keys [seq]} prev-hash
   {:keys [id kind counterparty amount memo transfer-id ts self-sig counter-sig]}]
  (when-not (contains? #{"debit" "credit"} kind)
    (throw (ex-info "engi/kind must be \"debit\" or \"credit\"" {:kind kind})))
  {:db/id (or id (str "engi/tx/" (random-uuid)))
   :engi/kind kind
   :engi/seq (inc seq)
   :engi/prev-hash (or prev-hash "genesis")
   :engi/counterparty counterparty
   :engi/amount amount
   :engi/memo memo
   :engi/transfer-id transfer-id
   :engi/self-sig self-sig
   :engi/counter-sig counter-sig
   :engi/ts ts})

(defn warrant
  [{:keys [id evidence-tx-a evidence-tx-b detector detector-sig ts]}]
  {:db/id (or id (str "engi/warrant/" (random-uuid)))
   :engi/kind "warrant"
   :engi/evidence-tx-a evidence-tx-a
   :engi/evidence-tx-b evidence-tx-b
   :engi/detector detector
   :engi/detector-sig detector-sig
   :engi/ts ts})

;; ── balance replay ─────────────────────────────────────────────────────────

(defn- delta [entry]
  (case (:engi/kind entry)
    "credit" (:engi/amount entry)
    "debit" (- (:engi/amount entry))
    0))

(defn fold-balance
  "Replay one agent's tx entries (any order — sorted here by `:engi/seq`) and
  compute the running balance, checking every invariant along the way.
  NEVER throws — every violation found is returned as data.

  `entities` — the agent's full entity set (genesis + debit/credit entries;
  warrants are ignored here, `detect-fork` covers fork evidence separately).

  opts:
    :hash-fn      (entry -> hash-string), used to verify the prev-hash chain
                   is actually continuous (needs `engi.crypto/entry-hash` —
                   omit to skip this specific check, e.g. in pure-core unit
                   tests that don't want a crypto dependency).
    :pending-transfer-ids  a set of `:engi/transfer-id` values known to be
                   unconfirmed credits (the counterparty's matching debit
                   hasn't landed yet, per `pending-unconfirmed?` /
                   `engi.protocol`) — these contribute their full amount to
                   `:balance` (the raw ledger replay) but ZERO to
                   `:spendable-balance` (ADR #2 step 5: an unconfirmed
                   credit must not count toward spendable balance).

  → {:balance N :spendable-balance N :violations [...] :credit-limit N-or-nil
     :entries [...sorted...]}."
  ([entities] (fold-balance entities {}))
  ([entities {:keys [hash-fn pending-transfer-ids]
              :or {pending-transfer-ids #{}}}]
   (let [genesis-entity (first (filter genesis? entities))
         credit-limit (:engi/credit-limit genesis-entity)
         tx-entries (sort-by :engi/seq (filter tx-entry? entities))
         violations (atom [])
         note! (fn [v] (swap! violations conj v))]
     (when-not genesis-entity
       (note! {:type :missing-genesis}))
     ;; seq monotonic-from-0, no gaps, no dupes
     (reduce
      (fn [expected entry]
        (let [got (:engi/seq entry)]
          (cond
            (< got expected) (note! {:type :seq-duplicate-or-regression
                                      :expected expected :got got :entry (:db/id entry)})
            (> got expected) (note! {:type :seq-gap :expected expected :got got
                                      :entry (:db/id entry)}))
          (max expected (inc got))))
      0 tx-entries)
     ;; prev-hash chain continuity (only checkable with a hash-fn)
     (when hash-fn
       (reduce
        (fn [expected-prev entry]
          (when (not= expected-prev (:engi/prev-hash entry))
            (note! {:type :prev-hash-mismatch :entry (:db/id entry)
                    :expected expected-prev :got (:engi/prev-hash entry)}))
          (hash-fn entry))
        "genesis" tx-entries))
     ;; replay balance + credit-limit floor at every step
     (let [{:keys [balance spendable]}
           (reduce
            (fn [{:keys [balance spendable]} entry]
              (let [d (delta entry)
                    pending? (and (credit? entry)
                                  (contains? pending-transfer-ids (:engi/transfer-id entry)))
                    balance' (+ balance d)]
                (when (and credit-limit (< balance' credit-limit))
                  (note! {:type :credit-limit-breach :entry (:db/id entry)
                          :balance balance' :credit-limit credit-limit}))
                {:balance balance'
                 :spendable (+ spendable (if pending? 0 d))}))
            {:balance 0 :spendable 0} tx-entries)]
       {:balance balance
        :spendable-balance spendable
        :violations @violations
        :credit-limit credit-limit
        :entries tx-entries}))))

;; ── fork detection ─────────────────────────────────────────────────────────

(defn- fork-content [entry]
  (dissoc entry :db/id :engi/self-sig :engi/counter-sig :engi/ts))

(defn- forks-by [entries key-fn tag]
  (->> entries
       (group-by key-fn)
       (mapcat (fn [[k group]]
                 (let [distinct-contents (distinct (map fork-content group))]
                   (when (> (count distinct-contents) 1)
                     [{:kind tag :key k :entries (vec group)}]))))
       vec))

(defn detect-fork
  "Given a set of tx entries for ONE agent, find every pair (or larger group)
  sharing the same `:engi/seq` OR the same `:engi/prev-hash` with genuinely
  DIFFERENT content (the double-spend fingerprint — a spender can only ever
  sign ONE transfer off a given chain head; two different transfers signed
  off the same head is the fork). A duplicate re-assertion of the identical
  entry (same content, e.g. a retried write — kotobase's append-only log has
  no dedup) is NOT a fork. Returns every fork found, never just the first —
  `[]` when the chain is clean."
  [entities]
  (let [tx-entries (filter tx-entry? entities)]
    (vec (concat (forks-by tx-entries :engi/seq :seq-fork)
                 (forks-by tx-entries :engi/prev-hash :prev-hash-fork)))))

;; ── bilateral confirmation ─────────────────────────────────────────────────

(defn unconfirmed-credit?
  "Pure predicate: is `credit-entry` (from the RECEIVER's own graph) still
  unconfirmed — i.e. does `counterparty-entities` (the SPENDER's full entity
  set, already fetched by the caller) contain no matching debit? A credit
  with no matching counterparty debit must NOT count toward spendable
  balance (ADR #2 step 5, bilateral confirmation). Returns true = pending /
  not spendable, false = confirmed."
  [credit-entry counterparty-entities]
  (when-not (credit? credit-entry)
    (throw (ex-info "unconfirmed-credit? expects a credit entry" {:entry credit-entry})))
  (let [transfer-id (:engi/transfer-id credit-entry)]
    (not (some (fn [e] (and (debit? e) (= transfer-id (:engi/transfer-id e))))
               counterparty-entities))))

(defn pending-transfer-ids
  "Convenience: given a receiver's own entities and a lookup fn
  `(fn [counterparty-did] -> counterparty-entities)` that returns ALREADY
  FETCHED data (pure — no I/O happens here; `engi.protocol` does the actual
  async fetch and calls this), return the set of `:engi/transfer-id` values
  among the receiver's credit entries that are still unconfirmed. Feed this
  straight into `fold-balance`'s `:pending-transfer-ids` opt."
  [own-entities counterparty-entities-fn]
  (->> own-entities
       (filter credit?)
       (filter (fn [c] (unconfirmed-credit? c (counterparty-entities-fn (:engi/counterparty c)))))
       (map :engi/transfer-id)
       set))
