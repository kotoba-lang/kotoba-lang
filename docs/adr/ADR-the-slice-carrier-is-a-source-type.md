# ADR: The slice carrier is a source type

- Status: accepted
- Date: 2026-09-02

## Context

amu ADR 0285 decided that the GiB-scale bulk carrier must be **addressable
memory, not a bigger vector**, and refused to raise `vector-item-limit`
(16384), `:vector-item-capacity` (65536), `:vector-capacity` (4096) or
`:bytes {:max-value-bytes 65536}`. Its machine half landed as
`slice-{load,store}-u{8,16,32,64}` — element-indexed, ceilinged at 2^40, one
unsigned compare and one scaled load per element, **no context callback**.

This file had no entry for kernel memory at all, so the whole family — the
window operations shipped on both native ISAs since ADR 0042, and the slice
family since MEMWIDTH — fell to `:default-for-missing`
(`:not-yet-implemented`). That is the same shape this file already records
for floating point, and it is a lie in the safe direction: it understates a
shipped surface rather than overstating one, but a reader cannot tell which
way an omission points.

## Decision

A `:checked-memory` family, with two entries.

`:kernel-memory-windows` records the byte-indexed operations: four transfer
widths by four window tiers, `kernel-subregion` and the lock pair, with the
five checks in the order the backends emit them and the **alignment
exemption** written down as an exemption. `kernel-{load,store}-u32` at the 512
tier and the lock pair skip the natural-alignment check because they predate
the rule and retrofitting it would move the bytes of shipped aiueos objects.
That is an asymmetry by date rather than by shape, and recording it is the
difference between a rule with an exception and a rule nobody follows.

`:slice-carrier` records both altitudes of one family:

- the eight machine operations, which take `base length index [value]` as
  separate i64 words;
- the eight **carried** operations — `slice-of-u{8,16,32,64}`,
  `slice-length`, `slice-get`, `slice-set!`, `slice-sub` — which take a
  `[:slice T]` **value**.

`:representation` is `:two-machine-words-erased-in-the-frontend`, because that
is the whole design: `[:slice T]` is a type of kotoba-sema's **source syntax
and of nothing else** (kotoba-sema ADR 0022), erased before HIR, so no IR
below the frontend has a two-word value and no backend needed a register pair.

`:refusals` names the eight `:kotoba.error/` codes that keep it there —
returned, escaped, given to a non-slice parameter, wrong element width,
computed base, exported, past five machine words, unadmitted element type.
They are listed because the boundary is the feature: a carrier that could
leave the frontend would be a two-word value the machine cannot hold.

## `[:slice :f32]` is declared and not admitted

It is the element type the carrier is ultimately **for** — the Qwen weights
are binary32 — and there is no `slice-load-f32` on either native ISA.
Recording it as available before the load exists would be amu ADR 0284's
defect in miniature: an admission that admits what nothing can lower. So it
is refused by name, with `:kotoba.error/slice-element-not-admitted`, and said
out loud here.

## `:bounded-admission` is not widened

Every bound in the new entries is new and fail-closed, and none of the
existing ceilings moves. That is ADR 0285's second decision and it is honoured
literally: the carrier's 2^40 is an **address-space** bound, which is why it
arrives in the emitted code as a `movabs` rather than as a `cmp r64, imm32` —
a window tier fits an imm32 and an address space does not.

## What is NOT changed, and why

`lang/guest-grammar.edn`'s `:admitted-builtins` still names three kernel
memory operations while the frontend admits forty-six. That understatement
predates this ADR by seven operations: the 4 KiB and 16 KiB tiers, the u32
pair, `kernel-subregion` and the lock pair have all been in
`kotoba.compiler.frontend` and in both backends without ever reaching the
list.

It is not widened here because `guest-grammar.edn` is a file **four
repositories carry a byte copy of** — amu, kotoba-sema, kotoba and grammar —
checked by `local-and-sibling-vendors-match-authority`. Widening it is a
four-repository resync, which is kotoba-native `docs/lang-authority-diff.md`
section 1's change and not this one's. The gap is recorded in the
`:slice-carrier` entry's `:authority-gap` rather than left silent.

Measured 2026-09-02 while writing this: `kotoba-lang/main:lang/guest-grammar.edn`
and `amu/main:resources/kotoba/lang/guest-grammar.edn` **already differ**, so
that resync is owed regardless of this entry.

## Evidence

- kotoba-native ADR 0042 — the scale is read out of the x86 SIB byte and the
  AArch64 ADD shift field by byte goldens.
- kotoba-native ADR 0064 — the carrier needed no change in the backend at all.
- amu ADR 0314 — a carried traversal and the same traversal written with the
  machine operations compile to **identical objects** on both ISAs at every
  element width; crossing the widths differs on both, so the comparison can
  tell two programs apart.
- aiueos ADR 0160 — a `[:slice :u8]` passed as a function parameter, filled
  and summed on a real conventional-memory page under q35 + OVMF: console
  `0000082000000410SLC`, exit 33. An overrunning `slice-sub` prints nothing,
  because the emitted `kernel-subregion` check reaches `ud2` first.
