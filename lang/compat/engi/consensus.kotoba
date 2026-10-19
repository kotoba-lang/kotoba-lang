(ns engi.consensus
  "ENGI/EN L1 — chained HotStuff-style BFT consensus over blocks of transfer
  proposals (ADR-2607993000). Pure, no I/O, no crypto, no wall-clock: like
  `engi.core`, signature verification is injected by the caller rather than
  performed here (the same seam `engi.core/fold-balance` already uses for
  its optional `:hash-fn` — this ns takes already-verified votes and a
  caller-supplied `hash-fn`). Runs identically under JVM `clojure -M:test`
  and cljs, and a whole n-witness validator set can be simulated as plain
  data with no real network, transport, or keys.

  What this ns OWNS: block/QC shape, quorum-size arithmetic (n=3f+1,
  quorum=2f+1), the chained 3-chain commit rule, round-robin leader
  rotation, and — the property this whole ADR is about — that two
  conflicting Quorum Certificates at the same height can never both form
  (see `consensus_test.cljc`'s Byzantine-equivocation scenario).

  What this ns does NOT own (deliberately, per ADR-2607993000 Decision #1):
  wire transport (murakumo/overlay's QUIC, reused unchanged), vote/QC
  signing (kotoba-lang/witness-quorum's signer/attestation, reused
  unchanged), or ENGI's transfer-proposal semantics (engi.core, unchanged —
  a block's :engi.block/proposals are just a vector of TransferBody CIDs
  from that existing schema).")

;; ── quorum arithmetic ───────────────────────────────────────────────────────

(defn quorum-size
  "n = 3f+1 witnesses tolerate f Byzantine. Quorum = 2f+1 (a majority of the
  honest supermajority: any two quorums of this size out of n intersect in
  at least f+1 nodes, i.e. at least one honest node — the safety lemma
  chained HotStuff and PBFT both rely on). f is derived from n, never
  passed separately, so a caller can't supply an inconsistent (n, f) pair."
  [n]
  (let [f (quot (dec n) 3)]
    (inc (* 2 f))))

(defn byzantine-tolerance
  "f for a given n (n=3f+1). Convenience inverse of quorum-size's derivation,
  for tests/callers that want to state \"how many faulty witnesses can this
  validator-set size tolerate\" directly."
  [n]
  (quot (dec n) 3))

;; ── block / vote / QC shape ──────────────────────────────────────────────────

(defn canonical-block
  "Deterministic string serialization of a block, for hashing/signing — same
  style as `engi.core/canonical-entry` (plain string concatenation, no
  JSON/EDN printer dependency, byte-identical across JVM and cljs)."
  [{:keys [engi.block/height engi.block/parent-hash engi.block/proposals
           engi.block/proposer engi.block/ts]}]
  (str "engi/block\n"
       "height=" height "\n"
       "parent-hash=" parent-hash "\n"
       "proposals=" (apply str (interpose "," proposals)) "\n"
       "proposer=" proposer "\n"
       "ts=" ts "\n"))

(defn make-block
  "Build a block. `justify` is the QC (see `qc`) certifying this block's
  immediate parent — nil only for the genesis block. `proposals` is a vector
  of TransferBody CIDs (engi.core/ADR-2607101100's existing proposal shape,
  unchanged here)."
  [{:keys [height parent-hash proposals proposer ts justify]}]
  {:engi.block/height height
   :engi.block/parent-hash (or parent-hash "genesis")
   :engi.block/proposals (vec proposals)
   :engi.block/proposer proposer
   :engi.block/ts ts
   :engi.block/justify justify})

(defn make-vote
  "A witness's vote for a specific block. Unsigned here — signing is
  `witness-quorum`'s job, reused unchanged (ADR-2607993000 Decision #1); a
  real caller attaches `:engi.vote/sig` after this and `qc` never inspects
  signatures itself (verification already happened before votes reach this
  ns, same division of labor as `fold-balance`'s injected `:hash-fn`)."
  [witness block-hash height]
  {:engi.vote/witness witness
   :engi.vote/block-hash block-hash
   :engi.vote/height height})

(defn qc
  "Given `votes` (already signature-verified by the caller) all claiming the
  SAME block-hash/height, and the validator-set size `n`, return a Quorum
  Certificate if the number of DISTINCT witnesses reaches `quorum-size`,
  else nil. Distinct-BY-WITNESS is what matters — a Byzantine witness
  resubmitting (or being credited with) the same vote twice must not count
  twice toward quorum; this is the concrete place equivocation gets
  neutralized. Throws if the votes don't actually agree on block-hash/height
  (a caller bug, not a Byzantine-tolerance case — routing votes for
  different blocks into one `qc` call is a programming error)."
  [votes n]
  (when (seq votes)
    (let [{:keys [engi.vote/block-hash engi.vote/height]} (first votes)]
      (when-not (every? #(and (= block-hash (:engi.vote/block-hash %))
                               (= height (:engi.vote/height %)))
                         votes)
        (throw (ex-info "qc: all votes must target the same block-hash/height"
                         {:votes votes})))
      (let [distinct-witnesses (set (map :engi.vote/witness votes))]
        (when (>= (count distinct-witnesses) (quorum-size n))
          {:engi.qc/block-hash block-hash
           :engi.qc/height height
           :engi.qc/witnesses distinct-witnesses
           :engi.qc/vote-count (count distinct-witnesses)})))))

;; ── chained 3-chain commit rule ───────────────────────────────────────────────

(defn direct-extends?
  "`child` directly extends `parent`: child's :engi.block/parent-hash AND its
  :engi.block/justify QC both point at parent (hash AND height agree). A
  block that merely NAMES a parent hash without a QC actually certifying
  that parent is not a safe direct extension — this double-check (link +
  justify) is what stops a Byzantine proposer from splicing an uncertified
  block into the chain."
  [hash-fn parent child]
  (let [parent-hash (hash-fn parent)
        justify (:engi.block/justify child)]
    (boolean
     (and (= parent-hash (:engi.block/parent-hash child))
          justify
          (= parent-hash (:engi.qc/block-hash justify))
          (= (:engi.block/height parent) (:engi.qc/height justify))))))

(defn three-chain-commits
  "Given `chain` (a vector of blocks in strictly increasing height order,
  each carrying a :engi.block/justify QC for its immediate predecessor —
  the genesis block is the only one that may have justify=nil), return the
  vector of blocks SAFELY COMMITTED under chained HotStuff's 3-chain rule:
  block B commits once B <- B' <- B'' are three CONSECUTIVE direct
  extensions. This is what turns a bare sequence of proposed blocks into
  finalized ones — everything before the last-committed block is authoritative
  for `en.core/finalized-balance` (ADR-2607993000 Decision #4); anything at
  or after the tip is still tentative."
  [hash-fn chain]
  (let [n (count chain)]
    (vec
     (keep (fn [i]
             (let [b0 (nth chain i) b1 (nth chain (inc i)) b2 (nth chain (+ i 2))]
               (when (and (direct-extends? hash-fn b0 b1)
                          (direct-extends? hash-fn b1 b2))
                 b0)))
           (range (max 0 (- n 2)))))))

;; ── leader rotation ──────────────────────────────────────────────────────────

(defn leader-for
  "v1 leader election: plain round-robin over `witnesses` (a vector of
  witness ids) keyed by height. Deliberately simple and PREDICTABLE — an
  adaptive adversary that knows the schedule could target the upcoming
  leader. ADR-2607993000 names this an intentional v1 simplification;
  hardening to VRF-based unpredictable election is deferred until a real
  adversarial (non-single-operator) validator set exists."
  [witnesses height]
  (nth witnesses (mod height (count witnesses))))
