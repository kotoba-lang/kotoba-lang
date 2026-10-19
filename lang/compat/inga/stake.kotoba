(ns inga.stake
  "Permissionless witness admission via external-collateral bonding, plus
  equivocation-only slashing (ADR-2607994000). Pure: no I/O, no crypto, no
  wall-clock — bond-balance verification and signature checking are both
  injected by the caller, same seam `engi.core/fold-balance` already uses
  for its optional `:hash-fn`.

  Bond asset is EXTERNAL COLLATERAL (e.g. USDC on Base L2 — reusing
  ADR-2607101100 §4's existing off-ramp boundary), deliberately NOT EN
  itself: EN nets to zero across all agents (mutual credit, not a scarce
  token), so bonding EN would give zero real economic disincentive against
  misbehavior. This ns is agnostic to WHICH external asset/chain custodies
  the bond — it just consumes an already-verified `{witness-did ->
  {:amount N :roles #{...}}}` map; wiring that map to a real escrow
  contract is out of scope here (ADR-2607994000 Decision #1).

  Bond records carry a `:roles` set (ADR-2607995000 §5 — the unified
  witness market): `:ordering` (engi/L1 block-consensus voting, this ns's
  own domain) and/or `:recompute` (proof-of-compute sample recompute, a
  different ns/domain entirely — cloud-murakumo's `verify/compute.cljc`).
  ONE bond market, self-selected roles, instead of two separate staking
  markets for two physically-different node populations (Mac-mini-class
  ordering witnesses vs. GPU-class recompute witnesses) — bond, unbond
  delay, slashing, and governance are all shared; only which duty a
  witness is eligible for differs. Role-filtering happens entirely in
  `eligible-witnesses`'s 3-arity and `eligible-for-role` — every other fn
  here (`stake-qc`, `slash`, ...) is role-agnostic: it just operates on
  whatever witness coll/bond-record the caller already filtered by role.

  The bond FLOOR, however, is role-ASYMMETRIC as of 2026-07-25 (see the
  `required-bond` / `eligible-for-role` / `quorum-met?` section below): one
  market and one rulebook, but the amount of collateral a role must post is
  sized to what that role actually guards, and for `:ordering` the protocol
  cannot compute that without pricing EN — which it must not do.

  Admission (`eligible-witnesses`) is permissionless: meeting the bond
  threshold is the ONLY requirement, no existing-witness vote. Quorum
  (`stake-quorum-met?`/`stake-qc`) is STAKE-weighted, not witness-count —
  the same >2/3-of-total-stake rule real PoS BFT systems use, and the thing
  that actually resists Sybil once anyone can mint arbitrarily many witness
  identities by splitting collateral across many small bonds: splitting
  a fixed amount of stake into more identities changes nothing about the
  SUM those identities can vote with (see consensus_test.cljc-style tests
  for the concrete demonstration).

  Slashing is scoped to EQUIVOCATION ONLY — the one fault category that's
  cryptographically unambiguous (two signed votes, same witness key, same
  height, different block-hash). Liveness/censorship faults are
  deliberately NOT slashable here (`liveness-drop` only removes a witness
  from the next epoch's active set, no bond forfeiture) because they
  cannot be distinguished from honest network failure without a subjective
  judgment call — and avoiding that judgment call is what lets this design
  skip building a slashing-dispute/appeal system entirely (ADR-2607994000
  Decision #5).")

;; ── bond-record accessors ────────────────────────────────────────────────────

(defn bond-amount
  "The bonded amount for `did` in `bonds` ({witness-did -> {:amount N :roles
  #{...}}}) — 0 if `did` has no bond record at all."
  [bonds did]
  (get-in bonds [did :amount] 0))

(defn bond-roles
  "The role set for `did` in `bonds` — #{} if `did` has no bond record or no
  `:roles` key."
  [bonds did]
  (get-in bonds [did :roles] #{}))

;; ── admission (permissionless) ───────────────────────────────────────────────

(defn eligible-witnesses
  "Given `bonds` ({witness-did -> {:amount N :roles #{...}}}) and `min-bond`,
  return the set of DIDs eligible to be witnesses THIS epoch. No
  existing-witness approval is consulted — meeting the bond threshold is
  the only requirement, which is the entire point: com-junkawasaki (or
  anyone else currently bonded) has no protocol-level say over who else may
  bond.

  3-arity: also require `role` to be present in the DID's `:roles` set —
  this is how the single shared bond market (ADR-2607995000 §5) produces a
  per-duty witness set (e.g. `(eligible-witnesses bonds min-bond
  :ordering)` for engi/L1 block consensus, `(eligible-witnesses bonds
  min-bond :recompute)` for proof-of-compute) without needing two separate
  bond maps."
  ([bonds min-bond]
   (into #{} (keep (fn [[did {:keys [amount]}]] (when (>= (or amount 0) min-bond) did))) bonds))
  ([bonds min-bond role]
   (into #{} (filter #(contains? (bond-roles bonds %) role)) (eligible-witnesses bonds min-bond))))

;; ── role-asymmetric bond requirement (2026-07-25) ───────────────────────────
;;
;; ADR-2607995000 §5 unified the two witness markets into ONE bond market with
;; role tags, and that stands. What did NOT hold up is applying the SAME bond
;; FLOOR to both roles, because the two roles do not guard the same value:
;;
;;   :recompute — a buyer paid real USDC for an inference and a witness attests
;;                it was computed honestly. The value at risk is denominated in
;;                the bond asset already, and it exists from the first paid
;;                request. A fixed floor is straightforwardly correct here.
;;
;;   :ordering  — a witness attests the order of EN transfers. EN is non-priced,
;;                non-redeemable and convertible to nothing (ADR-2607995000 §1
;;                membrane rules), so the protocol CANNOT compute what a
;;                successful equivocation is worth without putting an external
;;                price on EN — which is precisely the back door §3 of that same
;;                ADR closed when it repealed per-transfer external-asset fees.
;;
;; Note carefully what this does and does not claim. It is NOT that equivocating
;; on EN ordering is harmless: a counterparty who hands over real goods for
;; double-spent EN loses something real. It is that the loss is the
;; COUNTERPARTY'S OWN valuation of those goods, which is private, unpriced, and
;; unavailable to the protocol. So the ordering floor cannot be derived by
;; formula and is a GOVERNANCE PARAMETER (stake-weighted 2/3, ADR-2607994000
;; Decision #8) — with a bootstrap value of 0 and an objective trigger for
;; revisiting it, rather than a number invented to look prudent.
;;
;; The trigger is now measurable, which it was not before today: the first EN
;; transfer between two agents where neither is the operator. `engi.metrics`
;; reports exactly that (:external-counterparties), so "when do we set a real
;; ordering bond?" has a checkable answer instead of a judgement call.
;;
;; The honest cost of a 0 floor is stated, not buried: an unbonded witness set
;; has NO Sybil resistance. `quorum-met?` below therefore refuses to launder
;; that into something that looks like BFT security — it returns a map that
;; says so.

(def bootstrap-ordering-min-bond
  "Bond floor for `:ordering` during bootstrap: 0.

  Not a claim that ordering is riskless. A claim that (a) the protocol cannot
  price EN without breaking the non-pricing invariant, (b) with zero EN
  transfers between non-operator agents there is currently nothing for an
  equivocation to extract, and (c) a nonzero floor whose only justification is
  that it sounds prudent buys no security and costs every prospective external
  witness their entry — which is the observed state: `docs/witness-recruitment.md`
  quoted 500 USDC-equivalent while no escrow contract existed to accept it, and
  zero external witnesses have ever bonded."
  0)

(def default-bond-policy
  "Per-role bond floors. `:ordering` starts at the bootstrap value above;
  `:recompute` is deliberately left nil rather than given an invented number —
  its floor is owned by whoever custodies the compute payments (cloud-murakumo),
  and this ns will not fabricate one on their behalf. A nil floor for a role
  means that role is NOT admissible here until a policy supplies it, which is
  the fail-closed direction."
  {:ordering bootstrap-ordering-min-bond
   :recompute nil})

(defn required-bond
  "Minimum bond for `role` under `policy` (defaults to `default-bond-policy`).
  Returns nil when the policy has no floor for that role — callers must treat
  nil as 'not admissible', never as 0."
  ([role] (required-bond role default-bond-policy))
  ([role policy] (get policy role)))

(defn eligible-for-role
  "The witnesses admissible for `role`: they declared the role AND meet that
  role's own floor. Empty when the policy supplies no floor for the role.

  This is the role-asymmetric replacement for `(eligible-witnesses bonds
  min-bond role)`. That 3-arity still exists and is unchanged — a caller who
  genuinely wants one floor across every role keeps getting exactly that."
  ([bonds role] (eligible-for-role bonds role default-bond-policy))
  ([bonds role policy]
   (if-let [floor (required-bond role policy)]
     (into #{} (filter #(contains? (bond-roles bonds %) role))
           (eligible-witnesses bonds floor))
     #{})))

;; ── stake-weighted quorum ────────────────────────────────────────────────────

(defn total-stake
  "Sum of `bonds`' `:amount`s across `witnesses` (a coll of DIDs) — 0 for any
  DID not in `bonds`."
  [bonds witnesses]
  (reduce + 0 (map #(bond-amount bonds %) witnesses)))

(defn stake-quorum-met?
  "STAKE-weighted quorum: do the DIDs in `voted` (a set/coll of witness DIDs
  who voted for one block) control more than 2/3 of the TOTAL stake among
  `witnesses` (the current epoch's eligible set)? This replaces
  `inga.consensus/quorum-size`'s witness-COUNT-based 2f+1-of-3f+1 rule —
  that rule silently assumes every witness carries equal weight, which a
  permissionless bond-based admission model breaks (an attacker can always
  mint more low-stake identities). Byzantine safety argument: if
  Byzantine-controlled stake is < 1/3 of total, any two '>2/3 of total'
  quorums intersect in > 1/3 of total stake, so their intersection always
  includes some honest stake — the same argument
  `inga.consensus/quorum-size`'s docstring makes in witness-count terms,
  restated in stake terms."
  [voted bonds witnesses]
  (let [voted-set (set voted)
        total (total-stake bonds witnesses)
        voted-stake (total-stake bonds (filter voted-set witnesses))]
    (and (pos? total) (> (* 3 voted-stake) (* 2 total)))))

(defn quorum-met?
  "Quorum WITH ITS SECURITY BASIS ATTACHED, for the role-asymmetric bond model
  above. Returns a map, never a bare boolean, because the same `true` means two
  very different things depending on whether any collateral is at stake:

    {:met? bool :basis :stake-weighted   :sybil-resistant? true  ...}
    {:met? bool :basis :counted-unbonded :sybil-resistant? false ...}

  When total stake is positive this is exactly `stake-quorum-met?` — unchanged
  semantics, unchanged safety argument. When total stake is ZERO (the bootstrap
  `:ordering` set, where the floor is 0 and nobody has bonded) a stake-weighted
  rule can never be met and the chain would simply halt, so this falls back to a
  >2/3 HEAD COUNT over the enumerated witness set.

  That fallback is not BFT and this fn will not let a caller pretend otherwise:
  head counting has no Sybil resistance whatsoever, since anyone can mint
  arbitrarily many did:keys for free. `:sybil-resistant? false` is the honest
  label (ADR-2607110300's labelling principle: do not call it decentralized
  until it is), and it is returned in-band precisely so a caller cannot get the
  security property wrong by only checking `:met?`.

  The fallback is therefore a LIVENESS arrangement among an enumerated roster,
  not a security guarantee — appropriate exactly while there is nothing to
  steal (zero EN transfers between non-operator agents, which `engi.metrics`
  now measures), and to be retired by the same stake-weighted governance vote
  that sets a real `:ordering` floor once that changes."
  [voted bonds witnesses]
  (let [voted-set (set voted)
        roster (distinct witnesses)
        total (total-stake bonds roster)
        voted-stake (total-stake bonds (filter voted-set roster))
        n (count roster)
        n-voted (count (filter voted-set roster))]
    (if (pos? total)
      {:met? (> (* 3 voted-stake) (* 2 total))
       :basis :stake-weighted
       :sybil-resistant? true
       :total-stake total
       :voted-stake voted-stake}
      {:met? (and (pos? n) (> (* 3 n-voted) (* 2 n)))
       :basis :counted-unbonded
       :sybil-resistant? false
       :witness-count n
       :voted-count n-voted
       :why "no bonded collateral in this witness set, so quorum is a head count
             over an enumerated roster -- a liveness arrangement, not Byzantine
             security. Anyone can mint did:keys for free; do not read :met? true
             here as a safety property."})))

(defn stake-qc
  "Stake-weighted analogue of `inga.consensus/qc`: given `votes` (already
  signature-verified by the caller, same `inga.consensus/make-vote` shape,
  all for the SAME block-hash/height), `bonds`, and `witnesses` (the epoch's
  eligible set), return a QC map (same `:inga.qc/*` shape `inga.consensus`'s
  `direct-extends?`/`three-chain-commits` already consume — a drop-in
  replacement, not a parallel format) if the DISTINCT voting witnesses'
  stake meets `stake-quorum-met?`, else nil."
  ([votes bonds witnesses] (stake-qc votes bonds witnesses nil))
  ([votes bonds witnesses view]
   (when (seq votes)
    (let [{:keys [inga.vote/block-hash inga.vote/height]} (first votes)]
      (when-not (every? #(and (= block-hash (:inga.vote/block-hash %))
                               (= height (:inga.vote/height %)))
                         votes)
        (throw (ex-info "stake-qc: all votes must target the same block-hash/height"
                         {:votes votes})))
      (let [distinct-witnesses (set (map :inga.vote/witness votes))]
        (when (stake-quorum-met? distinct-witnesses bonds witnesses)
          (cond-> {:inga.qc/block-hash block-hash
           :inga.qc/height height
           :inga.qc/witnesses distinct-witnesses
           :inga.qc/stake (total-stake bonds distinct-witnesses)}
          ;; The view, for the same reason inga.consensus/qc records it: the
          ;; pacemaker orders certificates by view and locks on the later one,
          ;; so a certificate without one can never become a lock. A stake
          ;; certificate that could not lock would leave the stake-weighted
          ;; path with the exact bug the head-count path already had.
          view (assoc :inga.qc/view view))))))))

;; ── equivocation detection / verification ────────────────────────────────────

(defn detect-equivocation
  "Given `votes` (a coll of `inga.consensus/make-vote`-shaped maps, each
  carrying an `:inga.vote/sig`, from possibly many witnesses across
  possibly many heights), find every pair sharing the SAME witness, the SAME
  height AND the SAME view but a DIFFERENT block-hash — the objective,
  unambiguous double-vote fingerprint. Returns a vector of evidence maps
  (empty if clean), each `{:inga.evidence/witness :inga.evidence/height
  :inga.evidence/view :inga.evidence/vote-a :inga.evidence/vote-b}`.

  ## The view is part of the key, and leaving it out convicts honest replicas

  This grouped by `[witness height]` alone, which was right while a replica
  voted at most once per HEIGHT. It stopped being right when the voting rule
  became once per VIEW (`inga.replica`'s `voted-view`), and the two have to
  agree about what a double-vote is or the protocol slashes for following
  itself.

  A view change is exactly this shape: a round fails to certify, the next
  leader rebuilds on the highest QC, and an honest replica votes again at the
  same height for a different block in a LATER view. That is not misbehaviour,
  it is the mechanism by which a stalled chain recovers — and under the old
  key every replica that helped a chain recover produced, and its peers
  broadcast, a signed proof that it had equivocated.

  Measured on the deployed devnet 2026-08-15 (root ADR-2608150300): after a
  reset the chain ran clean, `equivocators` climbed 0 -> 1 -> 4 as views
  turned over, and at four the chain stopped — four replicas each alone on
  their own tip with one vote apiece. **The liveness mechanism was
  manufacturing the evidence that disabled it.**

  Within ONE view a witness has one leader and one proposal, so two different
  block-hashes from one witness in one view is still the unambiguous crime it
  always was. Narrowing the key strictly reduces what is convicted; it cannot
  make an equivocator harder to catch than the honest replica it used to."
  [votes]
  (->> votes
       (group-by (juxt :inga.vote/witness :inga.vote/height :inga.vote/view))
       (mapcat (fn [[[witness height view] group]]
                 (let [distinct-hashes (distinct (map :inga.vote/block-hash group))]
                   (when (> (count distinct-hashes) 1)
                     (let [a (first group)
                           b (first (remove #(= (:inga.vote/block-hash %)
                                                 (:inga.vote/block-hash a))
                                             group))]
                       [{:inga.evidence/witness witness
                         :inga.evidence/height height
                         :inga.evidence/view view
                         :inga.evidence/vote-a a
                         :inga.evidence/vote-b b}])))))
       vec))

(defn verify-equivocation-evidence
  "Verify one `evidence` map (as produced by `detect-equivocation`) is
  genuine: both votes must carry a signature that `verify-sig-fn`
  (`(fn [vote] boolean)`, injected — this ns has no crypto of its own,
  same division of labor as `engi.core`'s injected `:hash-fn`) accepts as a
  valid signature BY the claimed witness over that vote's content, and the
  two votes must actually differ in `:inga.vote/block-hash` while sharing
  `:inga.vote/witness`/`:inga.vote/height` (re-checked here, not just
  trusted from `detect-equivocation`, so a caller can also verify evidence
  submitted by someone else without re-running detection)."
  [{:inga.evidence/keys [witness height vote-a vote-b]} verify-sig-fn]
  (boolean
   (and (= witness (:inga.vote/witness vote-a) (:inga.vote/witness vote-b))
        (= height (:inga.vote/height vote-a) (:inga.vote/height vote-b))
        ;; SAME VIEW, and both votes must actually say which one.
        ;;
        ;; This is the half that defends against a peer, and it is the half
        ;; that matters: `detect-equivocation` only governs what THIS replica
        ;; produces, while evidence arrives over the wire from replicas that
        ;; may still be keying by `[witness height]` — and once recorded, a
        ;; conviction is not revisited. A fix that only narrowed detection
        ;; would leave every unfixed peer able to convict this replica of its
        ;; own view changes.
        ;;
        ;; `nil` views fail rather than compare equal. A vote that does not
        ;; say which view it was cast in cannot be shown to conflict with
        ;; another in the same view, and refusing to convict on evidence one
        ;; cannot evaluate is the only safe direction: the cost of declining a
        ;; real equivocation is that it goes unpunished, and the cost of
        ;; accepting a false one is an honest replica removed from the quorum.
        (some? (:inga.vote/view vote-a))
        (= (:inga.vote/view vote-a) (:inga.vote/view vote-b))
        (not= (:inga.vote/block-hash vote-a) (:inga.vote/block-hash vote-b))
        (verify-sig-fn vote-a)
        (verify-sig-fn vote-b))))

;; ── slashing (equivocation only) ─────────────────────────────────────────────

(defn slash
  "Remove `offending-witness`'s ENTIRE bond RECORD (amount + roles) from
  `bonds` and return `{:bonds :burned :rewarded}`. `burn-fraction` +
  `whistleblower-fraction` (default 0.95/0.05, ADR-2607994000's
  illustrative split) must sum to 1.0. `credit-to` (optional — the DID
  that submitted the verified evidence) has `:rewarded` added to its
  `:amount` in the returned `bonds` map (a fresh record with empty
  `:roles` is created if `credit-to` had no prior bond — crediting a
  reward does not implicitly grant witness roles); omit `credit-to` to
  report the numbers without crediting anyone (e.g. when the caller
  settles the reward via a different ledger). Does NOT call
  `verify-equivocation-evidence` itself — a caller must verify before
  slashing; this fn trusts its input, mirroring `engi.core/next-entry`'s
  division of labor (building vs. validating are separate steps)."
  [bonds offending-witness {:keys [burn-fraction whistleblower-fraction credit-to]
                             :or {burn-fraction 0.95 whistleblower-fraction 0.05}}]
  (when-not (== 1.0 (+ burn-fraction whistleblower-fraction))
    (throw (ex-info "slash: burn-fraction + whistleblower-fraction must sum to 1.0"
                     {:burn-fraction burn-fraction :whistleblower-fraction whistleblower-fraction})))
  (let [bond (bond-amount bonds offending-witness)
        reward (* bond whistleblower-fraction)
        burned (* bond burn-fraction)
        bonds' (dissoc bonds offending-witness)]
    {:bonds (if credit-to
              (-> bonds'
                  (update-in [credit-to :amount] (fnil + 0) reward)
                  (update-in [credit-to :roles] (fnil identity #{})))
              bonds')
     :burned burned
     :rewarded (if credit-to reward 0)}))

;; ── liveness (non-slashable — active-set removal only) ───────────────────────

(defn liveness-drop
  "Given `last-active-height` ({witness-did -> height last voted at}),
  `current-height`, `witnesses`, and `window`, return the set of witnesses
  who have gone `window`-or-more heights without voting. Callers exclude
  these from the NEXT epoch's active set — no bond is forfeited (silence
  cannot be objectively distinguished from honest network failure, so it's
  not slashable, ADR-2607994000 Decision #5); a witness that resumes voting
  simply requalifies at the next epoch boundary."
  [last-active-height current-height witnesses window]
  (into #{}
        (filter #(>= (- current-height (get last-active-height % 0)) window))
        witnesses))

;; ── unbonding delay ──────────────────────────────────────────────────────────

(defn request-unbond
  "Record `witness`'s unbond request as of `epoch` in `unbond-requests`
  ({witness-did -> epoch requested})."
  [unbond-requests witness epoch]
  (assoc unbond-requests witness epoch))

(defn unbond-available?
  "Has enough time passed (per `delay-epochs`) since `witness` requested
  unbonding, per `unbond-requests`, for the withdrawal to be allowed at
  `current-epoch`? False if no request is on file. The delay exists so
  equivocation evidence about `witness`'s last active epoch can still be
  filed and slashed before the bond can be withdrawn (no escaping with the
  stake right after misbehaving)."
  [unbond-requests witness current-epoch delay-epochs]
  (when-let [requested-epoch (get unbond-requests witness)]
    (>= current-epoch (+ requested-epoch delay-epochs))))
