# ADR: `:abort` reaches the sealed effect row

- Status: accepted
- Date: 2026-09-03
- Adjudicates: kotoba-kir `984a5073f395974694e0474d39244ca206b4f438` against
  amu ADR-0300 section 4 and its `definition_identity_test`
  `an-unbridgeable-effect-row-is-refused-with-a-marker`.
- Authority: `lang/surface-status.edn` `:invariants :explicit-errors`
  (`:disposition :intentional-security-constraint`,
  `:shielding-axis :control-effect-tracking`,
  `:widening-path {:mechanism :typed-abort-ability}`), and its precondition
  `:effect-row-integration`.
- Superseded by this: amu ADR-0300 section 4 as a *decision*. It stands as a
  measurement of the bridge at kotoba-kir `1e00f830`.

## Context

On 2026-09-02 two commits in two repositories answered the same question in
opposite directions, hours apart, and both landed.

**kotoba-kir `984a507`** widened `effect-row-from-hir` so that a member of a
closed set `control-effects` — today exactly `#{:abort}` — passes through the
bridge unchanged into the sealed row. Everything that is neither
`[:cap/call <id>]` nor a member of that set is still refused, with the same
message it had before.

**amu ADR-0300 section 4** ("`:abort` has no keyword the bridge can seal")
wrote the opposite into a test: a function whose row holds `:abort` gets
`:definition-cid :unbridged-effect`, its callers get
`:dependency-unavailable`, the module yields no cache material, and
`scanned-line` reports `SCANNED\t0/2`.

The consequence was mechanical and visible. amu held its kotoba-kir pin at
`08bdab8b` — the commit *before* `984a507` — with the reason written into
`deps.edn`, because advancing it turns eight assertions in that test red.
Every kotoba-kir change after 2026-09-02 was stranded behind an unmade
decision: the slice-carrier refusal, the alpha-normalization move, two ADR
renumberings and a ClojureScript-safe i64 ordering.

The SLICE-VALUE stream measured this and declined to adjudicate it, which was
correct — it is a language-authority question, not a slice question.

## Decision

**`:abort` reaches the sealed effect row.** kotoba-kir's reading is the
correct one. amu's test is amended to assert the pass-through, and amu's
kotoba-kir pin is free to advance.

### Why, from the authority rather than from preference

1. **`:effect-row-integration` is a named precondition of the sanctioned
   widening path.** `:explicit-errors` does not merely permit the typed abort
   ability; it lists what must hold before the ability may be admitted, and
   effect-row integration is one of three. A row member that cannot reach a
   *definition identity* is not integrated into the row — it is refused at
   the row's boundary. Under the refusal reading, no aborting definition can
   ever be pinned by a package lock or served from a definition-keyed cache,
   which closes the path this entry exists to open. A precondition that can
   only be satisfied by removing the feature is not a precondition.

2. **The shielding axis is `:control-effect-tracking`.** The invariant
   `:explicit-errors` protects is that no control effect is *untracked* — not
   that control flow is unexpressible. `:enforcement :tracked-elaboration`
   already says so in the same entry. The definition identity is the last
   boundary the effect crosses; if the identity cannot carry `:abort`, that
   boundary is exactly where tracking stops.

3. **The soundness argument runs the same way in both readings, and it points
   at a third option nobody took.** A function that can abort has interface
   `[:result T E]`; one that cannot has `T`. The dangerous move is
   *stripping* `:abort` before bridging, which would give two different
   programs one identity and let a lock pinning the pure one admit the
   aborting one. Both landed commits avoid that. kotoba-kir avoids it by
   sealing the keyword; amu avoided it by refusing to seal anything. Sealing
   is the one that also satisfies (1).

### What survives from amu ADR-0300 section 4

Both of its arguments, intact:

- *"A CID is never invented for a hole."* Passing `:abort` through invents
  nothing. There is no catalog lookup to get wrong because there is no wire
  id: the keyword **is** the sealed vocabulary. What section 4 measured was
  that the bridge had no *translation* for it, and it inferred from the
  absence of a translation that there was nothing to seal. The inference is
  the part that was wrong.
- *"A partial identity is not an identity."* Unchanged, and it still has
  work to do: `:definition-cid :unbridged-effect`,
  `:dependency-unavailable`, the `SCANNED n/m` floor and the
  `REFUSED:` listing all remain, for every member that is neither
  `[:cap/call <id>]` nor a member of `control-effects`. The refusal
  machinery ADR-0300 built is not deleted; it is given a correct domain.

Section 4 was a true measurement of a dependency written up as a decision
about the language. That is the general shape to watch for: a test that pins
what the pinned version of another repository happens to do will hold that
version's behaviour still, and will hold the pin still with it.

## The vocabulary is closed, and closed by whom

`kotoba.kir.definition-identity/control-effects` is the authority for which
keywords bridge as themselves. It is `#{:abort}` today. Growing it is a
contract change — a keyword the compiler did not mean as a control effect is
still refused with `effect row member is not a wire capability call`, so the
set cannot grow by a stray keyword arriving.

`lang/surface-status.edn` records the same set under
`:effect-row-integration :adjudication :vocabulary`, and the two must agree.

## The check that stops this recurring

The two repositories may not simply be told to agree; the agreement has to be
mechanical, because the whole failure was that neither noticed the other.

The shape is HYGIENE-1's (kotoba-native ADR-0050, kotoba-verifier ADR-0024):
**the producer exports the set it branches on; the consumer derives its own
and asserts equality across the pin.** amu's
`the-sealed-control-effect-vocabulary-agrees-across-the-pin` compares amu's
own expectation against `kotoba.kir.definition-identity/control-effects`
through the `deps.edn` pin. Neither repository imports the other's answer, so
the comparison is real; and the day one side moves, the pin advance that
carries the move is what goes red.

It is placed in amu rather than in kotoba-verifier — the repository HYGIENE-1
used — because kotoba-verifier has no part in definition identity at all. The
consumer that diverged is the one that must compare.

## Consequences

- amu advances its kotoba-kir pin past `984a507`, and the eight assertions
  are rewritten to the pass-through reading, keeping both directions:
  an aborting and a non-aborting definition get **different** CIDs, and a
  keyword outside `control-effects` is still refused by name.
- amu ADR-0300 section 4 is amended in place, and amu ADR-0326 records the
  adjudication on that side.
- Nothing in this repository's conformance corpus changes. `:abort` was
  already `:met` for the row; what was undecided was whether the row could be
  sealed, and that half is now recorded under `:sealed-identity` and
  `:adjudication`.

## What is not decided here

- Whether `control-effects` should ever hold a second member. Nothing today
  proposes one.
- `:checked-lexical-facet-unwind` remains `:not-met`, and every refusal that
  cites it — throw inside `loop`/`doseq`/`dotimes` bodies, in lazy thunks and
  `fn` literals, and in any function whose row carries a `:dataspace/*`
  operation — stands untouched. This ADR decides where a *tracked* abort may
  be recorded, not where an abort may occur.
