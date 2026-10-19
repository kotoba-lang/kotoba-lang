(ns inga.quorum
  "What counts as a quorum — as a predicate, so there is exactly one answer.

  ADR-2607993000 gave this system head-count quorum (n=3f+1, threshold 2f+1).
  ADR-2607994000 then made witness admission PERMISSIONLESS and said plainly
  why head-counting stops being safe there: a Sybil can split a small total
  bond across many identities and buy votes cheaply, so 'true economic safety
  must be stake-weighted'.

  `inga.stake/stake-qc` implements that. Nothing else did. The pacemaker
  counted heads, `inga.sync` counted heads, and `inga.attest` counted heads —
  so the security model the ADR decided on existed in one function while the
  consensus path ran on the model it replaced. Two notions of quorum in one
  system is not a redundancy, it is a question about which one is in force,
  and the answer was the weaker one everywhere it mattered.

  So quorum becomes a PREDICATE over the set of witnesses that voted, passed
  in. Head-count and stake-weighted both implement it, callers cannot silently
  get a different one than they think, and a third rule later is a third
  implementation rather than a third place to edit.

  ## A bare number still works, and means head count

  Passing an integer is accepted and read as 'at least this many distinct
  witnesses' — a THRESHOLD, which is what `at-least` builds. `for-set-size`
  takes n instead and derives the threshold from it, and the two are named
  apart because they were not: `(head-count 4)` required three votes while
  `(->predicate 4)` required four, one line apart in the file written to stop
  a number from meaning two things.

  Head counting is the wrong default under permissionless admission, which is
  why `for-set-size` says so in its own docstring rather than leaving the
  reader to find the ADR."
  (:require [inga.consensus :as c]
            [inga.stake :as stake]))

(defn at-least
  "Quorum at a THRESHOLD: this many distinct witnesses, or more.

  Takes the number of votes required, not the size of the validator set."
  [threshold]
  (fn [witnesses] (>= (count witnesses) threshold)))

(defn for-set-size
  "Head-count quorum for a validator set of `n`: `inga.consensus/quorum-size`
  votes, the smallest number that guarantees two quorums share an honest
  witness.

  Takes n, the size of the SET — not the threshold. `at-least` takes the
  threshold. The distinction is in the names because it was not, and the two
  read identically at a call site: `(head-count 4)` required three votes
  while `(->predicate 4)` required four, one line apart in this namespace, in
  the file written to end exactly that kind of local disagreement about what
  a number means.

  Safe ONLY where the validator set is managed — where somebody fixes n and
  admission is not open. Under permissionless admission this is exactly the
  rule a Sybil defeats, by splitting a small bond across many identities to
  buy votes it did not pay for."
  [n]
  (at-least (c/quorum-size n)))

(def profiles
  "What a quorum rule actually resists, declared rather than inferred.

  Modelled on `kotobase.storage.core/ref-profiles`, and for the same reason
  it gives: the failure mode of guessing is SILENT. A head count and a
  stake-weighted rule are both \"a quorum\", they are both correct code, and
  the difference between them is whether an adversary who can mint identities
  gets a free supermajority.

  - `:head-count` — n-of-m by identity. Correct for a MANAGED set, where who
    may hold a key is decided outside the protocol. **No Sybil resistance**:
    an adversary who can register witnesses can register a quorum.
  - `:stake-weighted` — more than 2/3 of bonded stake. Splitting a bond
    across identities changes the head count and not the stake, so it buys
    nothing. Requires a bond source."
  #{:head-count :stake-weighted})

(defn stake-weighted
  "Quorum by bonded stake: more than 2/3 of the epoch's total bond.

  The rule ADR-2607994000 decided on. Splitting a bond across identities
  changes the head count and not the stake, so it buys nothing."
  [bonds witness-set]
  (with-meta
    (fn [witnesses] (stake/stake-quorum-met? witnesses bonds witness-set))
    {::profile :stake-weighted}))

(defn profile
  "Which of `profiles` `q` is.

  A bare integer is `:head-count` — not because that is a lesser choice, but
  because it is a choice, and a deployment that believes it has Sybil
  resistance while counting heads has the belief and not the property."
  [q]
  (cond
    (integer? q) :head-count
    (fn? q) (or (::profile (meta q)) :head-count)
    :else nil))

(defn ->predicate
  "Coerce `q` to a quorum predicate. An integer means head count; a function
  is used as-is.

  An integer is a THRESHOLD — `at-least` — and not a set size. Use
  `for-set-size` when you have n; the difference is three votes versus four
  on the same numeral.

  Exists so every consumer takes 'a quorum' rather than each deciding what a
  number means — the drift this namespace was written to end started as
  exactly that kind of local decision."
  [q]
  (cond
    (fn? q) q
    (integer? q) (at-least q)
    :else (throw (ex-info "inga.quorum: not a quorum" {:q q}))))

(defn one-honest
  "The smallest number of DISTINCT witnesses that must contain at least one
  honest one: f+1, where n = 3f+1.

  Different from a quorum and used for a different job. A quorum decides what
  is agreed; this decides what is BELIEVABLE — if f+1 witnesses say they have
  moved on, at least one of them is honest and really has, so following them
  cannot be a lie told by the faulty alone.

  Head count only. The stake-weighted analogue is more than one third of the
  bonded total, and it is not implemented: under permissionless admission a
  Sybil defeats this the same way it defeats a head-counted quorum, so a
  deployment with open admission must not rely on it. Stated rather than
  silently inherited from `head-count`."
  [n]
  (inc (c/byzantine-tolerance n)))

(defn met?
  [q witnesses]
  (boolean ((->predicate q) witnesses)))
