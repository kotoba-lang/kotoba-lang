# ADR: The authority names every head the frontend admits

- Status: accepted
- Date: 2026-09-03
- Applies: kotoba-native `docs/lang-authority-diff.md` section 1, widened to
  today's measurement.
- Files: `lang/guest-grammar.edn` `:admitted-builtins`,
  `lang/surface-status.edn` `:checked-memory`, and the four vendored byte
  copies.

## Context

Two facts, measured 2026-09-03 against kotoba-sema `1afff23`:

`kotoba.compiler.frontend` — the only thing that decides admission — admits
**114** kernel heads as builtins, with no host import and no capability grant:

| table | count |
|---|---|
| `kernel-memory-operations` | 53 |
| `slice-value-operations` | 8 |
| `kernel-privileged-operations` | 53 |

`lang/guest-grammar.edn` `:admitted-builtins` named **three** of them:
`kernel-load-u8`, `kernel-store-u8`, `kernel-boot-info`.

The understatement is older than any of this week's work. MEMWIDTH measured
it at 46-against-3 and recorded that seven of the missing operations — the
4 KiB and 16 KiB tiers, the u32 pair, `kernel-subregion` and the lock pair —
had shipped on both native ISAs without ever reaching the list. Since then
the memory table grew the 64 KiB tier, u16 and u64 widths, six general
atomics, the f32 dot product and three fused dequantize-and-dot kernels; the
privileged table grew port reads, MSRs, `cpuid`, `xgetbv`/`xsetbv`, CR4,
fences, `rdtsc`, the UEFI call thunks and the ISR entry address. None of them
reached it either.

`:admitted-builtins` has exactly one reader, and it is not the compiler.
`kotoba.grammar/admitted-heads` — in kotoba-lang/kotoba's vendored grammar
loader, `vendor/grammar/src/kotoba/grammar.clj` — unions it into the known-head
set that `strict-problems` checks a guest program against. Nothing in
kotoba-lang, kotoba-sema or amu reads it at all, and nothing anywhere reads it
to decide what the COMPILER admits: `kotoba.compiler.frontend`'s three tables
do that, and they never consult this file.

So the understatement had a consequence, and it was the opposite of a hole:
kotoba reported `kernel-load-u32` and 110 other heads the compiler admits as
`:unknown-form`. Naming them stops that report; the compiler still decides
whether each has a lowering on the target at hand, and on the wasm and
ClojureScript targets it does not, so the same programs are still refused —
by "operation has no admitted lowering" instead of by the grammar pre-check.
Two refusals, one of them accurate.

> **Corrected before landing, 2026-09-03.** The first draft of this ADR and of
> the file's own comment said "`:admitted-builtins` decides nothing", from a
> grep across the four repositories that covered `src test scripts` and not
> `vendor/` — which is where the one reader lives. Measuring a subset and
> reporting it as the whole is the same defect this ADR is about, so it is
> recorded rather than quietly fixed.

### The check that should have caught the second half was measuring nothing

`local-and-sibling-vendors-match-authority` compares this repository's copy
of `lang/guest-grammar.edn` against sibling checkouts at `../amu`,
`../kotoba`, `../kotoba-sema` and `../grammar`. Those paths exist only in the
west monorepo layout. The test guards each with `(when (.isFile ...))`, and
`authority-vendor-drift` reports an absent path as `:missing`, which callers
tolerate.

So in a single-repository clone the test compares **one** copy — this
repository's own — and reports green. Measured today, on main, before any
change here:

| copy | state |
|---|---|
| `kotoba-lang/lang/guest-grammar.edn` (authority) | 601 lines |
| `kotoba-lang/resources/…` (local vendor) | identical |
| `kotoba-sema/resources/…` | identical |
| `amu/resources/…` | **580 lines** — one change behind (local-state slice 1) |
| `kotoba/resources/…` | **401 lines** |
| `kotoba/vendor/grammar/resources/…` | **401 lines** |

Three copies had drifted, on main, and the check written to find drift said
nothing. This is the shape ADR-2608136000 names: *a check that could not run
returns the value of a check that ran and found nothing wrong.*

## Decision

**1. `:admitted-builtins` names all 114 kernel heads.** Grouped and
commented by family, with the measurement and its date in the file. The set
now equals `kernel-memory-operations ∪ slice-value-operations ∪
kernel-privileged-operations` exactly — verified in both directions, not by
inspection.

**2. `:checked-memory :kernel-memory-windows :surface` names all 45 memory
heads** (the 53-entry table minus the eight `slice-*` machine operations,
which `:slice-carrier` claims). It named eight, which read as though the
tiers were a detail of those eight. It also gains
`:two-region-operations`, because "every kernel operation's base is argument
0" was true when the family was written and stopped being true when the dot
products arrived.

**3. `:bounded-admission` is not widened, and no ceiling moves.**
`vector-item-limit` 16384, `:vector-item-capacity` 65536,
`:vector-capacity` 4096 and `:bytes {:max-value-bytes 65536}` are all
untouched. This ADR changes what the authority *says*, not what the compiler
*admits*: every one of the 111 newly named heads was already admitted by the
frontend before this file was edited, and none of them becomes admitted by
being named here.

The one behavioural consequence is named above and is in kotoba: those heads
stop being reported as `:unknown-form` by the grammar pre-check. They are
still refused wherever they have no lowering, by the compiler, which is the
component that knows.

**4. Three of the four vendored copies are resynced in one wave**, and every
vendoring repository gains a test that fails when they drift. **kotoba is
deliberately not resynced**; see below.

## The drift check, and why it is placed where it is

The replacement check compares copies **on a classpath**, where a copy cannot
be absent:

- **amu** sees its own `resources/` copy and kotoba-sema's, across the
  `deps.edn` pin. Two copies, always, and comparing them is what found amu's
  own copy one change behind.
- **kotoba-sema** sees one. Its dependencies carry no copy, so its check is
  the pinned-digest half plus the comparison only it can make — its copy
  against the frontend tables, which live there.
- **kotoba** sees five, and already had the strongest of the three checks:
  `every-guest-grammar-on-the-classpath-is-the-same-bytes` requires all five
  to be byte-identical with no exemption.

### kotoba is not resynced, and that is the finding

Its five copies all agree — at the *previous* authority. Resyncing the two it
ships would make them disagree with the three arriving from its pinned amu,
kotoba-lang and kotoba-sema, which is exactly what its own check refuses, and
refuses rightly: `io/resource` answers with whichever comes first, so
admission would be decided by classpath order. Moving the dependency copies
means advancing its amu pin, **106 commits behind** — a compiler migration,
not a grammar resync.

A first draft of that repository's change did resync both copies and added an
allowlist keyed on the stale dependency pins. Its own suite caught it: a
weaker check landing beside a stronger one that already existed. What landed
instead is a **baseline naming both digests** — the one its copies are at and
the one they owe — with the head count asserted through
`kotoba.grammar/admitted-heads`, which is the one reader of
`:admitted-builtins` anywhere. So the gap is a number in a test rather than a
sentence in a document.

Each of the other checks asserts the sha256 of the copy it holds equals the
authority digest of its wave. That literal appears in four repositories, which
is the point: the next authority edit is a four-repository wave by
construction, and a copy that is edited alone goes red even where there is
nothing to compare it to.

**It worked within the hour.** A parallel stream edited this file the same day
(`904ad31`, the map surface is not keyword-only) and updated the pinned digest
here as part of it — which is the tripwire doing its job. Its three vendored
copies are then owed the same bytes, and that is now a visible, dated
obligation rather than a silent divergence.

Each check prints `COMPARED\t<n>` and refuses `n = 0`, and each names the
differing heads — the symmetric difference of `:admitted-builtins` and of
`:forbidden-heads` — rather than reporting "the files differ".

## What is still not true

The 53 privileged operations are now **named** in the grammar, but they have
no `:checked-memory`-shaped family in `lang/surface-status.edn`, so they fall
to `:default-for-missing` (`:not-yet-implemented`) while shipping on both
native ISAs. That is the same understatement, one file over, and it is
recorded under `:slice-carrier :authority-gap` as open rather than closed.

It is not fixed here because fixing it means claiming a per-ISA disposition
for `kernel-swapgs`, `kernel-uefi-call6`, `kernel-rdtscp` and fifty others,
and this stream did not measure them. Writing the entry from the operation
names would be inventing the answer to the question the entry exists to
record.
