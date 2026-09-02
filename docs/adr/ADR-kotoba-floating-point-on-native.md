# ADR — floating point on native: binary32 reaches the machine-code backends

- **Status**: Accepted
- **Date**: 2026-09-02
- **Scope**: `kotoba-lang`, `kotoba-kir`, `kotoba-sema`, `kotoba-native`, `amu`
- **Related**: `ADR-kotoba-compiler-native-boundary-v1.md`,
  `ADR-kotoba-canonical-value-codec.md`,
  `ADR-kotoba-language-surface-status.md`,
  superproject `adr-2608030300` (f64 on native),
  superproject `adr-2607279200` (Kotoba-shaped safety elaboration migration)

## Context

`docs/lang/semantics-ssot.md:63` has said `` `:f32` / `:f64` | IEEE-754;
target-restricted (see floating-point policy) `` for as long as the document has
existed. **There is no floating-point policy section.** Nothing in `docs/lang/`
says which targets restrict it, what rounding is guaranteed, whether NaN is a
value a program may hold, or how a float literal is read. The nearest existing
statements are `lang/value-codec.edn:138` (`:f32` is a binder-level annotation,
not a wire type) and `lang/q9-wave1-tranche-2.edn:25-68` (floating point out of
scope for that cohort), and neither is a policy.

Meanwhile the implementation had gone in three directions at once, measured
2026-09-02 against the SHAs in the Evidence section:

- **kotoba-kir** implements the whole f32 family in the reference interpreter
  (`src/kotoba/kir.cljc:2279-2332`) and the whole value layer
  (`src/kotoba/kir/value.cljc:99-246`), and then refuses every one of them at
  the native admission gate with the line *"f32 is deliberately absent: neither
  backend implements it"*.
- **kotoba-sema** already types the whole f32 family — arity
  (`f32-operations`), operand typing (`contextual-f32-argument-indexes`),
  result typing, `:f32` in `value-types`, `closure-flat-result-types` and
  `schema.cljc` binder types. Nothing about f32 was missing there except the
  literal.
- **kotoba-native** implements f64 on both ISAs and no f32 at all, and **amu**
  throws `"f32 values require the kotoba-script or Wasm target"`
  (`src/kotoba/compiler/core.clj:512-519`).

So f32 was fully specified, fully interpreted, fully typed — and unreachable.
Qwen inference in Kotoba is the forcing function: a dequantised dot product is
binary32 arithmetic over words loaded from memory, and today it cannot be
written.

There is also a **spelling conflict** the authority never resolved.
`lang/guest-grammar.edn:382-383` lists `f32 f32+ f32- f32* f32/ f32div f32sqrt
f32neg f32= f32< f32> f32<= f32>=`, while `lang/q9-wave1-tranche-2.edn:44-47`
records the measured spelling as `f64-add` and "a parallel `f32-*` set".

## Decision

### 1. Numeric semantics

Kotoba floating point is **IEEE-754 binary32 (`:f32`) and binary64 (`:f64`)**,
with these guarantees on every backend that claims the operation:

- **Rounding is round-to-nearest, ties-to-even**, for arithmetic and for every
  conversion. Emitted code assumes the host's default rounding state (x86
  MXCSR, AArch64 FPCR) and **a Kotoba program has no operation that changes
  it**; a host that changes it before entering a Kotoba artifact breaks this
  contract and there is no capability that lets a guest do so.
- **No contraction.** A backend may not fuse a multiply and an add into an FMA,
  because that changes the result. If a fused operation is wanted it must be a
  distinct named operation with its own semantics; none exists today.
- **No fast-math.** No reassociation, no reciprocal substitution for division,
  no flush-to-zero, no denormals-are-zero. Subnormals are computed.
- **NaN, ±Infinity and −0.0 are ordinary values in computation.** They are
  produced (`f32-div` of zero by zero), carried in registers, compared
  (`f32-unordered` is the operation that observes NaN), and distinguished
  (`f32-neg` of `+0.0` is `−0.0`, a different bit pattern).
  This does **not** widen `lang/value-codec.edn:138`'s
  `:rejects [:nan :positive-infinity :negative-infinity :negative-zero]`: that
  rejection is about the **wire**, and it stays exactly as it is. A value a
  program may compute is not thereby a value the codec may transport, and this
  ADR changes nothing about transport. `:f32` also remains a binder-level type
  annotation rather than a wire type.
- **NaN payloads are not specified.** A program may observe *that* a value is
  NaN (via `f32-unordered`, or `f32-to-bits` and a mask) but the language does
  not promise which quiet-NaN payload an arithmetic operation produces. Two
  backends may differ in the payload bits and both are conforming.

### 2. Canonical operation spelling is the `f32-add` family

The canonical spelling of a floating-point operation is
`f32-add f32-sub f32-mul f32-div f32-min f32-max f32-neg f32-abs f32-sqrt`,
`f32-eq f32-lt f32-le f32-gt f32-ge f32-unordered`,
`f32-to-bits f32-from-bits`, and the conversions in §4 — with the parallel
`f64-*` set for binary64.

This is not a preference. It is what the frontend accepts: measured, the head
`f32-add` resolves through `kotoba.compiler.frontend/f32-operations`, and
`f32+` resolves through nothing at all in that path.

**`f32+` and its family in `lang/guest-grammar.edn` `:admitted-builtins` are a
different vocabulary and are left in place.** That set is the *legacy wasm
emitter's* builtin surface — its immediate neighbours are `"i64+"`, `"alloc"`,
`"str-ptr"`, `"memory-grow"` — and the canonical spelling of integer addition
in the same file is plain `+`, in `:arithmetic`, not `"i64+"`. Reading
`:admitted-builtins` as the operation vocabulary is the mistake; the two
surfaces are labelled so the next reader does not make it. Neither is removed:
removing `f32+` would break the legacy emitter, and promoting it would give one
operation two names.

### 3. The native representation: an i64 word holding the sign-extended pattern

On the machine-code backends an `:f32` value occupies **one ordinary integer
register and stack slot, holding its binary32 bit pattern sign-extended from
bit 31**. This is the f64 convention (`kotoba-native
src/kotoba/native/x86_64.cljc:248-254`) at the narrower width, and it is chosen
so that the same two properties hold:

- **`f32-to-bits` emits nothing.** The word already is the pattern, in the
  signed-i32 form the KIR interpreter's `f32-to-i64-bits` produces.
- **`f32-from-bits` sign-extends** (`movsxd` / `sxtw`) — the one member of the
  family that is not an identity, and the difference from f64. It is what makes
  the invariant hold at every producer, and it canonicalises the zero-extended
  u32 that `kernel-load-u32` returns into the same word a signed i32 already
  is.

Zero-extension was the alternative and was rejected: it would make
`f32-to-bits` a sign-extending instruction instead, moving the same three bytes
one operation later while breaking the "from-bits/to-bits are identities"
property f64 established.

**Consequence for a guest reading floats out of memory.** `kernel-load-u32`
zero-extends, so every negative float's pattern is above `Integer/MAX_VALUE`
and the KIR interpreter — the definition — refuses it
(`"f32 bit pattern is not signed i32"`). The composition a guest must write is
`(f32-from-bits (i32-wrap (kernel-load-u32 base len index)))`. Native is
permissive where the interpreter refuses, and permissive in the canonicalising
direction: it computes the float the interpreter would have computed for the
wrapped word, never a different one.

**`:f32` is NOT a native function-boundary type, and neither is `:f64`.**
`kotoba-kir`'s `native-word-value-type?` admits neither, and this ADR does not
change that. The kexe export ABI passes i64 words; there is no representation
for a host float crossing it, and inventing one would mean widening
`kotoba.verifier`'s independently-derived boundary set in another repository at
the same time. Floats cross a native module boundary as their bit pattern, and
`f32-from-bits` / `f32-to-bits` cost nothing. **This is a named gap, not a
silence**: it applies equally to f64, which has been on native since
`adr-2608030300`, and closing it is a separate increment.

### 4. What is admitted on native, and what is not

Admitted:

| Family | Operations |
|---|---|
| arithmetic | `f32-add` `f32-sub` `f32-mul` `f32-div` |
| unary | `f32-neg` `f32-abs` `f32-sqrt` |
| comparison | `f32-eq` `f32-lt` `f32-le` `f32-gt` `f32-ge` `f32-unordered` |
| reinterpretation | `f32-from-bits` `f32-to-bits` |
| widening / narrowing | `f32-to-f64-exact` `f64-to-f32-rounded` |
| int → float | `i64-to-f32-rounded` `i64-to-f64-rounded` |

Refused on native, each for its own reason:

- **`f32-min` / `f32-max`.** x86 `MINSS`/`MAXSS` return the **second** operand
  when either input is NaN; AArch64 `FMIN`/`FMAX` return the NaN; and the KIR
  interpreter — the definition — uses `Math/min`, which also returns the NaN.
  **The f64 line already admits `f64-min`/`f64-max` and therefore already
  carries this disagreement on x86.** That is a pre-existing defect, recorded
  here and not repaired in this change because repairing it moves f64 goldens.
  This slice declines to duplicate it into a second width.
- **The `-checked` conversions** (`i64-to-f32-checked`, `f32-to-i64-checked`
  and their f64 twins). They **trap** in the interpreter on inexactness, and
  neither backend emits that check. Admitting them would let a program that
  must trap compute an answer instead.
- **The truncating float → int conversions** (`f32-to-i64-truncating`,
  `f64-to-i64-truncating`). On an out-of-domain input there are three different
  answers: x86 `CVTTSS2SI` yields the integer-indefinite value (`INT64_MIN`),
  AArch64 `FCVTZS` **saturates**, and the interpreter traps. Making them agree
  needs an emitted domain check, which is a separate increment.

The four admitted conversions are the ones on which both ISAs and the
interpreter agree for **every** input: widening is exact and total, narrowing
is RNE with overflow to ±Infinity, and every i64 has a defined RNE image at
both widths.

### 5. Float literals: exact or refused

A decimal literal in source is read as a host binary64 before the compiler sees
it. There is therefore no way to round decimal → binary32 correctly in one
step, and decimal → binary64 → binary32 is not always the same value.

**A decimal literal in an `:f32` context is admitted only when the binary64 the
reader produced round-trips exactly through binary32**, and refused otherwise
with a named reason. `1.5`, `0.5`, `2.0`, `16777216.0` are admitted; `0.1` is
refused, because the literal a reader hands over is not the float the author
wrote.

The explicit spelling for the rounding is `(f64-to-f32-rounded 0.1)`, which
says in the source that a narrowing happened. `(f32-from-bits 0x3DCCCCCD)`
says the same thing exactly.

This follows the discipline `lang/value-codec.edn:256-260` already applies to
the wire (*"fail-closed, never two encodings for one source program"*) rather
than inventing a second one. It is not a claim that double rounding is
frequent — it is the refusal to make the rare case silent.

### 6. Register convention, and what it means for the SIMD wave

The scalar convention is: **values live in general-purpose registers; the SSE /
NEON bank is borrowed for the duration of one operation and is not part of any
value's lifetime.** x86 bounces through `xmm0`/`xmm1`, AArch64 through
`s0`/`s1`; nothing is live in a vector register across an instruction boundary,
and no vector register is callee-saved by anything this backend emits.

This is the right convention for scalar code and **the wrong one for packed
SIMD**, which is the next wave. A packed kernel's whole benefit is keeping 4 or
8 lanes resident in a vector register across a loop; a bounce per operation
would spend more than the parallelism earns. That wave therefore needs XMM/YMM
**register allocation** (which `kotoba-mir` does not have) and cannot be built
by widening these encodings. Nothing here forecloses it: the scalar path stays
correct beside a packed path, and the two share the value representation, since
a packed lane's pattern is the same binary32 pattern this ADR pins.

## Consequences

- Qwen-shaped f32 kernels become writable in `.kotoba` for the native targets:
  load a word, `i32-wrap`, `f32-from-bits`, multiply-accumulate, `f32-to-bits`,
  store. No C matvec, no host callback.
- `docs/lang/semantics-ssot.md:63` stops pointing at a section that does not
  exist.
- Two divergences are now written down instead of being latent: the x86
  min/max NaN defect on the existing f64 path, and the absence of a float
  function-boundary type at both widths.
- `surface-status` gains `:native-binary32-arithmetic` with disposition
  `:implemented-partial` and implementation `#{:compiler :kotoba-kir}` — the
  native backends and the oracle. It deliberately does not claim the portable
  backend set: f32 on `wasm32-kotoba-v1` and the script targets is older,
  separate, and not measured by this change.

## Evidence

Measured 2026-09-02 against `kotoba-lang@aa9b49f`, `kotoba-kir@b6bfe23`,
`kotoba-sema@74ac0d7`, `kotoba-native@4a4c4c3`, `amu@b1fdaad2`.

- `kotoba-kir` `test/kotoba/kir_f32_native_admission_test.clj` — 10 tests, 97
  assertions: the admitted slice and the refused set pinned in **both**
  directions, plus golden binary32 bit patterns
  (`(f32-add 0.1f 0.2f) = 0x3E99999A`, which is the pattern that distinguishes
  `ADDSS` from `ADDSD`; two round-to-nearest-even ties;
  `(i64-to-f32-rounded 16777217) = 0x4B800000`; NaN ≠ NaN on every ordered
  comparison). Shown to fail on a deliberately broken gate — one op swapped
  between the admitted and refused sets — with exactly four failures, two in
  each direction, and to pass unchanged.
- The spelling claim is a measurement, not a reading: `f32-add` resolves
  through `kotoba.compiler.frontend/f32-operations`; `f32+` resolves nowhere in
  that path.
