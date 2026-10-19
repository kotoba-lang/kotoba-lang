(ns inga.sync
  "Catch-up: what a replica that has been away may accept from a peer.

  `inga.pacemaker` lets a lagging replica jump forward a view at a time on a
  timeout certificate. It does not give it the BLOCKS it missed, and a replica
  without them cannot check that a new proposal extends anything, so it cannot
  safely vote. Catching up is what turns 'I know I am behind' into 'I am not'.

  ## Sync is the easiest place to rewrite history, so it is the strictest

  Everything else in this protocol is fed by the replica's own observations.
  Sync is the one path where a replica takes a sequence of blocks from a
  stranger and adds it to what it believes. If that path is loose, none of the
  safety work upstream matters — an attacker does not need to break the commit
  rule if it can simply hand you a different past.

  So a segment is accepted only when every one of these holds:

  1. It ATTACHES to a block the replica already has. A segment that starts
     from nothing is a fabricated history, however well-formed.
  2. Heights are contiguous and ascending. A gap is a place to hide a block.
  3. Every block is justified by a quorum certificate for ITS OWN PARENT —
     the same `direct-extends?` check `inga.consensus` uses, so sync cannot
     accept a chain the commit rule would reject.
  4. Every certificate carries a quorum of DISTINCT witnesses, and — when a
     verifier is supplied — a quorum that actually SIGNED. Counting names
     alone was a real hole: a certificate inside a block from a stranger was
     never seen by this replica as votes, so `inga.consensus/qc`'s
     \"already verified by the caller\" contract could not hold here, and a
     peer could list three witnesses who never voted.
  5. The segment is bounded. An unbounded one is a memory attack that needs no
     invalid data at all.

  A segment that fails any of them is rejected WHOLE. Adopting the valid
  prefix of a bad segment would let a peer choose where the replica's history
  ends by appending garbage.

  ## What this namespace does not do

  It does not fetch anything, and it does not own the crypto: `inga.attest`
  holds the payload and the check, and the verifier itself is injected — a
  browser that cannot re-verify a certificate is not a verifier. It decides
  what to ask for and what may be believed."
  (:require [inga.consensus :as c]
            [inga.attest :as att]
            [inga.quorum :as q]
            [inga.wire :as wire]))

(def default-params
  {;; the most blocks a peer may hand over at once
   :max-batch 256})

(def reasons
  "Every way a segment can be refused. Closed set, for the same reason
  `torihiki.api`'s is: a free-text reason becomes a string somebody parses."
  #{:empty-segment
    :too-large
    :does-not-attach
    :non-contiguous
    :uncertified
    :does-not-link
    :below-quorum
    :wrong-proposer
    ;; A segment that would replace a COMMITTED block. Distinct from
    ;; `:does-not-attach` on purpose: that one means "I cannot use this", this
    ;; one means "a quorum told me something that contradicts what a quorum
    ;; already finalised", which is a safety alarm and not a sync problem.
    :below-commit})

;; ── what to ask for ─────────────────────────────────────────────────────────

(defn behind?
  [local-height target-height]
  (< local-height target-height))

(defn request
  "The range a replica should ask for, or nil when it is not behind.

  Bounded by `max-batch`, so a replica that is a million blocks behind asks
  for a window and comes back rather than requesting a million — a request
  whose answer does not fit in memory is a request that never completes."
  [local-height target-height {:keys [max-batch] :as _params}]
  (when (behind? local-height target-height)
    {:from (inc local-height)
     :to (min target-height (+ local-height max-batch))}))

;; ── what may be believed ────────────────────────────────────────────────────

(defn- quorum-ok?
  "Quorum-many witnesses, and — when a verifier is supplied — quorum-many that
  actually SIGNED.

  Counting names alone was the hole this closes: a certificate inside a block
  from a stranger was never seen by this replica as votes, so
  `inga.consensus/qc`'s \"already verified by the caller\" contract could not
  hold there. A peer could list three witnesses who never voted.

  The verifier is optional so a replica replaying its own already-checked
  history does not re-verify it — the same distinction `apply-block` draws in
  torihiki between live application and replay."
  [qc quorum chain-id verify-fn admitted?]
  (or
   ;; Genesis is exempt. The first block of any history is justified by the
   ;; certificate `inga.replica/start` fabricates so that a first proposal has
   ;; something to point at — one witness, no signatures, because nobody voted
   ;; for genesis: every replica has it by construction.
   ;;
   ;; Refusing it refused the whole segment, and a segment is refused WHOLE by
   ;; design — so a replica that had fallen behind could never adopt anything
   ;; at all. On the deployed chain one validator sat at genesis while the
   ;; others reached forty-five, and the three that remained had exactly
   ;; quorum with no margin, which is the fault-tolerance gap. The two were
   ;; the same wound: no margin because one is stuck, stuck because the
   ;; segment that would free it starts at height one.
   ;;
   ;; `inga.replica/handle-new-view` already made this exception for the same
   ;; certificate. This is the other place that needed it.
   (zero? (:inga.qc/height qc -1))
   (and (q/met? quorum (:inga.qc/witnesses qc #{}))
        (or (nil? verify-fn)
            (nil? (att/verify-certificate qc chain-id quorum verify-fn admitted?))))))

(defn validate-segment
  "nil when `blocks` (ascending, contiguous) may be adopted on top of the block
  whose hash is `anchor-hash`, otherwise a keyword from `reasons`.

  `anchor` is the block the replica already holds at the height just below the
  segment. Passing it — rather than just its hash — is what lets the first
  block be checked with exactly the same `direct-extends?` every other step
  uses, instead of a special case that could differ."
  ([hash-fn quorum anchor blocks params]
   (validate-segment hash-fn quorum anchor blocks params nil nil nil))
  ([hash-fn quorum anchor blocks params chain-id verify-fn]
   ;; `:witnesses` is the ORDERED validator list — `inga.consensus/leader-for`
   ;; indexes into it, so it is a vector and its order is the leader rotation.
   ;; Membership is a different question asked of the same list, so it is
   ;; derived here rather than by overloading the key: passing a SET as
   ;; `:witnesses` to get membership silently breaks leader election, and
   ;; passing the ordered list to get leader election silently turns on the
   ;; proposer check. Two questions, one source, asked separately.
   (validate-segment hash-fn quorum anchor blocks params chain-id verify-fn
                     (when-let [ws (:witnesses params)] (wire/admits ws))))
  ([hash-fn quorum anchor blocks {:keys [max-batch witnesses] :as _params}
    chain-id verify-fn admitted?]
  ;; Verifying without a validator set is not a weaker check, it is no check:
  ;; a certificate names its own witnesses, so signatures that all verify
  ;; prove only that SOMEBODY holds SOME keys. This throws rather than
  ;; rejecting because it is a caller-construction error, not untrusted input
  ;; -- the same line `inga.ref/ref-store` draws when a seam is missing.
  ;; Superproject ADR-2608047000 follow-up 4.
  (when (and verify-fn (nil? admitted?))
    (throw (ex-info "inga.sync: a verifier needs the validator set to verify against"
                    {:type :inga.sync/no-validator-set})))
  (cond
    (empty? blocks) :empty-segment
    (> (count blocks) max-batch) :too-large
    :else
    (let [heights (map :inga.block/height blocks)]
      (cond
        (not= heights (range (first heights) (+ (first heights) (count blocks))))
        :non-contiguous

        (not= (inc (:inga.block/height anchor)) (first heights))
        :does-not-attach

        :else
        (loop [prev anchor [b & more] blocks]
          (cond
            (nil? b) nil
            (not (quorum-ok? (:inga.block/justify b) quorum chain-id verify-fn admitted?))
            :below-quorum
            ;; `direct-extends?` is TWO checks: the parent hash links, and
            ;; the justify certifies that same parent. Answering `:uncertified`
            ;; for both names one of them and sends the reader at the
            ;; certificates when the problem is the link.
            ;;
            ;; Measured on four replicas: one sat seventy blocks behind
            ;; reporting `{offered 71, from 147, adopted 0, reason
            ;; uncertified}`, which reads as "my peers are sending me blocks
            ;; nobody signed" — while the fact was that its own block 146 was
            ;; not the block they had. Two failures under one name is the
            ;; shape of instrument this workspace has now paid for four times.
            (not= (hash-fn prev) (:inga.block/parent-hash b)) :does-not-link

            (not (c/direct-extends? hash-fn prev b)) :uncertified

            ;; Who proposed it. `handle-proposal` is not the only way a block
            ;; enters a replica and it was the only one that could refuse —
            ;; and it could not refuse this, because a sync response never
            ;; reaches it. The harness forger sends its block as a
            ;; `:sync-response`, and three of four replicas adopted it as
            ;; their tip once delivery was delayed.
            ;;
            ;; The quorum check above does not catch it either: the forged
            ;; block is justified by a GENESIS certificate, and genesis
            ;; certificates are exempt from verification here because the
            ;; bootstrap one is unsigned and refusing it left laggards unable
            ;; to catch up at all. That exemption is what the forgery rides,
            ;; and its signatures are the literal strings "x" "y" "z".
            ;;
            ;; `witnesses` is nil for callers that have not been updated,
            ;; and then this does not fire — the same shape as `verify-fn`
            ;; here. A caller that wants the check passes the set.
            ;; Compared through `wire/wire-id`, not `str`. A proposer crosses
            ;; the wire as "w1" and a witness set is held as :w1, and `str`
            ;; makes those ":w1" and "w1" — so the check refused EVERY
            ;; segment, including the genuine ones, which looks exactly like
            ;; a fix when the thing you are measuring is a forgery getting in.
            ;; The SAME predicate the live proposal path uses. Keyed by the
            ;; round the block carries, not by its height — see
            ;; `inga.consensus/proposed-by-its-leader?` for what it cost to
            ;; have this written twice.
            ;; MONOTONE, not exact. A block in a segment carries a quorum
            ;; certificate, and the quorum that produced it ran the strict
            ;; entitlement check live — a certificate IS that check's record.
            ;; Re-deriving it here would refuse every legitimately skipped
            ;; round, which is exactly how a lagging replica stopped being
            ;; able to catch up once leadership moved off the height.
            (and witnesses
                 (or (not (integer? (:inga.block/round b)))
                     (<= (:inga.block/round b) (:inga.block/round prev -1))
                     (not= (wire/wire-id (:inga.block/proposer b))
                           (wire/wire-id (c/led-by witnesses (:inga.block/round b))))))
            :wrong-proposer
            :else (recur b more))))))))

(defn first-uncertified
  "The first block in `blocks` whose justify does not satisfy `quorum-ok?`,
  or nil. **Diagnostics only** — `validate-segment` is the decision.

  It exists because the refusal it explains was misread twice. A caller
  reporting `:below-quorum` naturally reaches for `(first segment)`, and the
  first block is exactly the one that is EXEMPT (its justify is the genesis
  certificate), so the report always showed a passing block and pointed the
  reader at a genesis problem that was not there. Two iterations of this
  workspace's gap loop went looking for a missing genesis exception that had
  been in `quorum-ok?` all along.

  A diagnostic that names the wrong block is worse than none: it is a wrong
  answer with evidence attached."
  ([quorum blocks] (first-uncertified quorum blocks nil nil nil))
  ([quorum blocks chain-id verify-fn] (first-uncertified quorum blocks chain-id verify-fn nil))
  ([quorum blocks chain-id verify-fn admitted?]
   ;; A diagnostic must answer the SAME question the decision asked, so it
   ;; takes the validator set too. Without it this reported every block as
   ;; uncertified the moment membership started being checked -- naming the
   ;; wrong block again, which is the exact failure this docstring is about.
   (first (filter #(not (quorum-ok? (:inga.block/justify %) quorum chain-id verify-fn admitted?))
                  blocks))))

(defn adopt
  "Append a validated segment. Returns the new chain vector.

  Deliberately takes the chain rather than mutating one: a replica that
  adopted in place could end up half-updated if validation and application
  were ever separated, and the whole point of validating first is that they
  are not."
  [chain segment]
  (into (vec chain) segment))

;; ── rewinding onto a branch that replaces an uncertified suffix ─────────────
;;
;; `sync-step` anchored at `(last chain)` and nowhere else, so a segment could
;; only ever be APPENDED. That is right for a replica that is merely behind,
;; and it cannot resolve a fork: a replica holding a losing branch is not
;; behind, it is beside. Its tip is at the same height as its peers' and a
;; different block, so every segment they can offer starts at or below that
;; height and is refused `:does-not-attach` — the one answer that describes the
;; situation correctly and does nothing about it.
;;
;; Measured in `inga.replica-test`: a 2/2 partition leaves two branches, and
;; after healing all four replicas sit at one height on two tips with
;; `last-sync {:offered 1 :adopted 0 :reason :does-not-attach}`, forever. It is
;; the same shape ADR-2800004800 §1d recorded on the deployed devnet at heights
;; 1030/1031, where one replica reported `no-parent` for a block the other side
;; had built on.

(defn chain-through
  "The prefix of `chain` up to and including height `h`, or nil when the chain
  does not reach that height."
  [chain h]
  (let [prefix (vec (take-while #(<= (:inga.block/height %) h) chain))]
    (when (and (seq prefix) (= h (:inga.block/height (peek prefix))))
      prefix)))

(defn conflicts-with-chain?
  "Does `segment` name a DIFFERENT block than `chain` holds at some height they
  both cover?

  This is what separates a fork from an echo. A peer re-offering blocks the
  replica already has overlaps its chain completely and disagrees with none of
  it; rewinding onto that would discard a suffix in order to put back the
  identical blocks, and — because adopting is what triggers a vote — would cast
  a second vote at a height already voted at. Which is equivocation, produced
  by the machinery meant to repair a fork, against a peer doing nothing wrong.

  Caught by `catching-up-does-not-vote-twice`, which existed for exactly this
  and was the only thing standing between the rewind and a self-inflicted
  slashable offence."
  [hash-fn chain segment]
  (let [by-height (into {} (map (juxt :inga.block/height identity)) chain)]
    (boolean (some (fn [b]
                     (when-let [mine (get by-height (:inga.block/height b))]
                       (not= (hash-fn mine) (hash-fn b))))
                   segment))))

(defn rewind-target
  "Where a segment starting at `start` would attach, given `chain` and the
  height at or below which nothing may be discarded (`floor`).

  Returns `{:prefix [...] :anchor b}` or a refusal keyword. The floor is the
  caller's committed height: committed blocks are final under the 3-chain
  rule, so a segment that would replace one is not a fork to be resolved but a
  safety violation to be refused — and refusing it by NAME is what keeps that
  distinction readable in the one place it will ever matter."
  [chain start floor]
  (cond
    (<= start floor) :below-commit
    :else (if-let [prefix (chain-through chain (dec start))]
            {:prefix prefix :anchor (peek prefix)}
            :does-not-attach)))

(defn- sync-step-append
  "The original append-only body of `sync-step`, unchanged."
  [hash-fn quorum chain segment params chain-id verify-fn]
   (let [anchor (last chain)]
     (if-let [reason (validate-segment hash-fn quorum anchor segment params
                                       chain-id verify-fn)]
       (if (not= :below-quorum reason)
         ;; Structural: the segment is not what it claims to be. Whole refusal
         ;; is the only safe answer, and the argument this function was
         ;; written with applies unchanged.
         {:chain chain :adopted 0 :reason reason}
         ;; Certificates: find where they stop and keep what is behind it.
         (let [;; `admitted?` is a PREDICATE built from the witness list, not the
               ;; list. Passing the list itself threw "Key must be integer" out
               ;; of the certificate verifier -- the same mistake the
               ;; `first-uncertified` docstring warns about, one layer down.
               bad (first-uncertified quorum segment chain-id verify-fn
                                      (when-let [ws (:witnesses params)]
                                        (wire/admits ws)))
               idx (when bad
                     (first (keep-indexed
                             #(when (= (:inga.block/height %2)
                                       (:inga.block/height bad)) %1)
                             segment)))
               prefix (if idx (subvec (vec segment) 0 idx) [])]
           (if (empty? prefix)
             {:chain chain :adopted 0 :reason reason}
             ;; Re-validated, not assumed: the prefix has to stand on its own
             ;; against the same anchor, or this would be trusting the shape
             ;; of a segment that already failed once.
             (if-let [r2 (validate-segment hash-fn quorum anchor prefix params
                                           chain-id verify-fn)]
               {:chain chain :adopted 0 :reason r2}
               {:chain (adopt chain prefix) :adopted (count prefix)
                :reason :uncertified-tail-only}))))
       {:chain (adopt chain segment) :adopted (count segment)})))

(defn sync-step
  "One round of catching up. Returns
  `{:chain c :adopted n}` on success or `{:chain c :adopted 0 :reason r}`.

  ## The certified prefix, not the whole segment

  This refused a segment whole, on the argument that adopting the valid prefix
  of a bad one lets a peer decide where the replica's history ends by
  appending garbage to a good answer. The argument is right about garbage and
  wrong about the ordinary case, because of how chained HotStuff carries
  certificates: **a block's certificate travels in the block AFTER it**, so the
  last block of ANY segment is uncertified by construction. A peer offering
  everything it has therefore always offers an uncertified tail — and the
  whole thing, certified prefix included, was refused.

  Measured on two deployments: replicas that restarted at checkpoints a
  hundred blocks apart were offered the blocks between and adopted zero,
  reason `:below-quorum`, forever. Nothing after the split could ever be
  certified, because certifying needed the quorum the split had broken. Both
  chains stalled until every replica was reset by hand.

  And the whole refusal never bought what it claimed. A peer can always send
  the certified prefix ALONE — truncating its own answer is free — so
  appending garbage to a good answer gains an attacker nothing that honesty
  would not. What has to hold is only that the garbage is never adopted.

  So: refuse the segment whole for any STRUCTURAL defect — a broken hash
  chain, a proposer that does not lead its height, a bad signature — and for
  an UNCERTIFIED TAIL, adopt the certified prefix. The prefix is precisely
  what a quorum has already agreed to, which is the one thing a peer cannot
  fabricate. Garbage appended to a good answer still buys nothing: it is
  uncertified, so it is exactly what gets dropped.

  Returns `:reason :uncertified-tail-only` when it adopted a prefix, so a
  caller can tell a partial catch-up from a complete one."
  ([hash-fn quorum chain segment params]
   (sync-step hash-fn quorum chain segment params nil nil))
  ([hash-fn quorum chain segment params chain-id verify-fn]
   (let [;; `:floor` is the caller's committed height. Absent, it defaults to
         ;; the tip, which reduces this to the append-only behaviour it had
         ;; before rewinding existed — an existing caller that has not been
         ;; taught about forks keeps exactly its old semantics rather than
         ;; silently gaining the ability to discard its own history.
         tip-h (:inga.block/height (last chain) -1)
         floor (:floor params tip-h)
         start (:inga.block/height (first segment))
         rewind? (and (seq segment) (integer? start) (<= start tip-h)
                      (conflicts-with-chain? hash-fn chain segment))
         target (when rewind? (rewind-target chain start floor))]
     (cond
       (keyword? target)
       {:chain chain :adopted 0 :reason target}

       rewind?
       (let [{:keys [prefix anchor]} target]
         (if-let [reason (validate-segment hash-fn quorum anchor segment params
                                           chain-id verify-fn)]
           ;; No certified-prefix salvage on the rewind path. Replacing a
           ;; suffix is only justified by the incoming branch being one a
           ;; quorum certified; a segment we had to trim to make valid is not
           ;; that, and trimming it here would let a peer choose how far back
           ;; we go.
           {:chain chain :adopted 0 :reason reason}
           {:chain (adopt prefix segment) :adopted (count segment)
            :reason :rewound
            :discarded (- tip-h (dec start))}))

       :else
       (sync-step-append hash-fn quorum chain segment params chain-id verify-fn)))))
