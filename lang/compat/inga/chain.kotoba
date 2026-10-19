(ns inga.chain
  "What inga orders: the ADVANCE of an agent's own source chain.

  ADR-2608038000 H1 states the design in one line — *inga が順序づけるのは
  transaction ではなく、agent chain head の前進である* — and then names the
  gap: `inga.consensus`'s `:inga.block/proposals` is a vector of opaque ids
  and nothing turned a committed one into state. This namespace is that
  bridge, and it is deliberately the only place where a proposal stops being
  opaque.

  ## Why this is the shape that lets Holochain and BFT meet

  Holochain's constraint is that an agent may only author its own source
  chain. BFT's contribution is a total order. Put the transaction bodies in
  the blocks and the two fight: the order has to be over things every replica
  can validate, and validating a transfer means knowing what a transfer is.
  Order the chain HEADS instead and neither side has to move — a head advance
  is `{author, seq, prev, entry}`, `entry` is an id this namespace never
  opens, and the whole of what consensus decides is *which agent moved from
  which head to which, in what order*.

  `inga.state` enforces the rest structurally: `:actor-advance` is a
  self-write op, so an advance that names someone else's address is refused
  by the same rule that refuses writing their record.

  ## The three answers, and who gives them

  | question | answered by |
  |---|---|
  | may this agent advance? | `inga.state`'s `:authority` — self-write |
  | is this the next link? | `inga.state`'s `:actor-advance` — `prev` is the committed head, `seq` is `nonce+1` |
  | is the entry itself valid? | the APPLICATION, before it votes |

  The third is not here and cannot be: an entry's validity is what an
  application means by a transaction, and a consensus layer that imports one
  application becomes that application's consensus layer.

  ## Unresolvable proposals throw, and that is the correct halt

  `decode-block` throws when `resolve-fn` cannot produce the advance a
  committed proposal names. A replica that cannot see what it is applying
  must not invent a state — it must sync. Applying \"nothing\" instead would
  be worse than stopping: it produces a root that disagrees with every
  replica that COULD see the proposal, silently.

  This is only reachable if a quorum voted for a proposal nobody can resolve,
  which is what validating BEFORE voting is for. `valid-advance?` is exported
  so the vote path and the apply path use ONE predicate rather than two that
  can drift.

  ## A fork is a query, not a rumour

  The design this inherits from (ADR-2607101200, and `engi.core/warrant`
  before it) had a neighbourhood of validators pushing fork evidence at each
  other, because without a total order nobody else would ever see it. That
  layer was lost with the Rust workspace and never came back, which is the
  standing gap \"warrants are recorded and do not gossip\".

  Ordering the heads dissolves it rather than closing it. A fork is two
  advances from one head, so the SECOND one is refused by `inga.state` in
  every replica at once, and the evidence is written into the state root
  (`inga.state`'s fork section for why that one refusal is named rather than
  only counted). `forks` reads it back. There is nothing left to propagate:
  the fork never landed, and the record of it is in the root."
  (:require [kotoba.lang.text :as str]
            [inga.state :as state]))

(defn valid-advance?
  "Shape check for a chain advance. Not a validity check on the entry — see
  the ns docstring's third row.

  Used by the apply path here AND intended for the vote path, so a proposal
  that would halt `decode-block` is one a correct replica never voted for."
  [{:keys [author seq prev entry]}]
  (boolean
   (and (string? author) (not (str/blank? author))
        (nat-int? seq)
        (or (nil? prev) (string? prev))
        (string? entry)
        ;; genesis is seq 0 from no parent; anything else needs a parent
        (if (zero? seq) (nil? prev) (some? prev)))))

(defn advance-op
  "One advance -> the `:actor-advance` op `inga.state` applies.

  `:caller` is the author. An advance is BY DEFINITION self-authored, so this
  is not a claim being passed through — it is the same value in the position
  `:authority` checks, which makes \"an agent may only advance its own chain\"
  hold by construction rather than by the resolver's good behaviour."
  [{:keys [author seq prev entry]}]
  {:op :actor-advance
   :address author
   :caller author
   :seq seq
   :prev-entry prev
   :entry entry})

(defn decode-block
  "Build a `:decode-block` for blocks whose proposals are chain advances.

  `resolve-fn` is `(fn [proposal-id] -> advance-or-nil)`: the deployment's own
  read of the body behind an id, which is where application knowledge lives.
  It MUST be deterministic across replicas — two replicas that resolve one id
  differently produce different actor trees from one block, and the roots say
  so, which is the failure being made visible rather than prevented.

  `proposals-fn` defaults to `:inga.block/proposals`, the key
  `inga.consensus` already puts them under."
  ([resolve-fn] (decode-block resolve-fn :inga.block/proposals))
  ([resolve-fn proposals-fn]
   (when-not (ifn? resolve-fn)
     (throw (ex-info "inga.chain/decode-block: resolve-fn is not callable"
                     {:type :inga.chain/invalid-seam})))
   (fn [block]
     (mapv (fn [id]
             (let [advance (resolve-fn id)]
               (when-not (valid-advance? advance)
                 ;; See the ns docstring: stopping is the correct answer, and
                 ;; the reason this is reachable at all is a vote path that
                 ;; did not use `valid-advance?`.
                 (throw (ex-info "inga.chain: a committed proposal did not resolve to an advance"
                                 {:type :inga.chain/unresolved-proposal
                                  :proposal id
                                  :resolved advance})))
               (advance-op advance)))
           (proposals-fn block)))))

(defn forks
  "Every fork committed state has recorded against `author`, earliest first.

  `[{:height h :head entry :claimed-prev entry :attempt entry :seq n} …]`,
  empty when the chain is clean. The record reads as one statement: *this
  author offered `attempt` as the child of `claimed-prev` at `seq`, while the
  committed chain was at `head`* — `engi.core/warrant`'s two pieces of
  evidence plus what connects them, derived by every replica from the same
  block rather than asserted by a detector.

  `inga.state`'s fork section says what this does not prove: a duplicate of an
  old advance is indistinguishable from a fork at this layer, which keeps the
  head and not the history.

  Takes the hydrated state (what `inga.state/hydrate-fn` returns), because
  this reads the datom root while `head` reads the actor root. Two readings of
  one commit, and neither is a second copy of the other."
  [state author]
  (->> (state/query state
                    {:find '[?h ?head ?claimed ?attempt ?seq]
                     ;; The author is bound by building the clause rather than
                     ;; through `:in`, because `inga.state/query` is the two
                     ;; -arity reading of a hydrated state and widening it for
                     ;; one caller would be the wrong place to spend an arity.
                     :where [['?e "inga.fork/address" author]
                             '[?e "inga.fork/height" ?h]
                             '[?e "inga.fork/head" ?head]
                             '[?e "inga.fork/claimed-prev" ?claimed]
                             '[?e "inga.fork/attempt" ?attempt]
                             '[?e "inga.fork/seq" ?seq]]})
       (map (fn [[h head claimed attempt seq]]
              {:height h :head head :claimed-prev claimed
               :attempt attempt :seq seq}))
       (sort-by :height)
       vec))

(defn forked?
  "Has `author`'s chain ever forked, according to committed state?

  The question a counterparty asks before extending credit, and the reason
  the evidence is worth having in the root: it is answerable from state alone,
  with no neighbourhood to ask and no detector to trust."
  [state author] (boolean (seq (forks state author))))

(defn head
  "The committed head of an agent's chain: `{:entry cid :seq n}`, or nil.

  The actor record is where a head lives — `:state` is the entry, `:nonce` is
  its sequence — so this is a reading of the committed state rather than a
  second copy of it. A caller building the NEXT advance needs exactly this
  pair, and getting it from anywhere else is how two sources of truth start."
  [actors author]
  (when-let [record (get actors author)]
    (when (:state record)
      {:entry (:state record) :seq (:nonce record)})))
