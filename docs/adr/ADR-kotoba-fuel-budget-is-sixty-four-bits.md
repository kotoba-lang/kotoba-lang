# ADR: a fuel budget is sixty-four bits, bounded by the counter that counts it

- Status: Accepted
- Date: 2026-09-03
- Amends: `lang/surface-status.edn` `:bounded-admission`
- Related: `docs/lang/fuel-model.md`, `docs/adr/ADR-reliability-t72-fuel-model.md`

## Context

`:bounded-admission` is an `:intentional-security-constraint` on the
`:resource-bounds` shielding axis. Widening one requires a named invariant,
fail-closed enforcement, and an ADR. This is that ADR.

Until 2026-09-03 the entry did not state a fuel ceiling **at all** — its
`:limits` map covers functions, parameters, bindings, symbol chars, expression
nodes, list items, source bytes and reader depth, and stops. The number that
acted as the ceiling was an accident of encoding two repositories away:
`kotoba.native.elf64` wrote a kernel object's per-call budget with

```
49 c7 41 08 <imm32>      mov qword [r9+8], imm32
```

whose sign-extended immediate tops out at **2,147,483,647**. Nobody chose that;
it is the width of a field. The context slot is a `uint64_t`, the charge is
`cmp qword [r9+8],0` / `dec qword [r9+8]`, and both image packagers have always
written the budget as eight data bytes.

It became an argument twice regardless: aiueos ADR-0142 sized the
`sha256-region` window from it, and aiueos ADR-0175 concluded `evaluate_token`
cannot be one Kotoba object because the output projection alone wants thirteen
times it — which is why the Qwen forward pass still has C orchestrating it.

Separately, `kotoba.verifier` and amu's JVM-free driver each admitted at most
**2^20**, four hundred million times tighter than tiers the object route was
already shipping (250,000,000 and 2,147,483,647). They never met because the
object route does not read the sealed budget.

## Decision

### The named invariant

> **A fuel budget is exactly counted by every runtime that counts it.**

The ceiling follows from the invariant rather than from a preference, and it is
different per backend because the counters are:

| surface | ceiling | why |
|---|---|---|
| native (`kotoba.kir/max-fuel`) | **2^53−1** = 9,007,199,254,740,991 | `charge!` is `(vswap! fuel dec)` on a plain host number — a `Long` on the JVM, a **double** on Node, and the JVM-free route is the one Q9 makes normative |
| wasm (`kotoba.wasm/max-fuel`) | 2^62−1 | the counter is an i64 global throughout, with no double in the path — unchanged by this ADR |

Measured on Node, 2026-09-03:

```
     9007199254740991 -> 9007199254740990   exact   (2^53-1)
     9007199254740992 -> 9007199254740991   exact   (2^53)
     9007199254740994 -> 9007199254740992   step 2
     9007199254740996 -> 9007199254740996   STUCK   (2^53+4)
    18014398509481984 -> 18014398509481984  STUCK   (2^54, and every value above)
  4611686018427388000 -> 4611686018427388000 STUCK  (the wasm ceiling)
```

A budget above 2^53−1 is one the oracle would never see reach zero: it would
answer `:ok` for a program that does not terminate, which is the single answer a
fuel bound exists to prevent. Not 2^63−1, which the qword and the widened
immediate can both carry — the ceiling is set where **both** counters are still
exact, not where the wider of them stops.

The number is not invented. `kotoba.compiler.nbb.cli/native-fuel!` was already
enforcing it through `js/Number.isSafeInteger`, beside a `max-native-fuel` test
that hid it.

**This authority file cannot state the wasm ceiling as a number.** The cljs EDN
reader returns `4611686018427388000` for `4611686018427387903`, so
`:max-fuel-wasm` is recorded as a string, with the reason beside it. That is the
same fact one layer up.

### Fail-closed enforcement

Five refusals, each by a reason literal, each on a route that is actually taken:

| layer | refusal |
|---|---|
| `kotoba.kir/execute` | `:fuel-outside-admitted-range` |
| `kotoba.verifier` | `native fuel budget is not admitted` |
| `kotoba.compiler.nbb.cli/native-fuel!` | same, and `Number.isSafeInteger` |
| `kotoba.native.elf64/replenish-bytes` | `:object-fuel-tier-outside-admitted-range` |
| `kotoba.native.interrupt-abi/entry-bytes` | `:isr-entry-fuel-exceeds-imm32` |

The last is a *refusal to widen*: the interrupt entry's frame size is
load-bearing (`entry-stride` 128, offsets hand-counted), so it stays imm32 — and
says so, because `le32` is `(mod n 2^32)` and a fuel of exactly 2^32 wrote four
zero bytes, which would have made every interrupt on the machine take vector 6.

Each was shown red on a deliberately broken input with the reason pinned, and
green unchanged.

## What "bounded" now means, said plainly

Fuel bounds execution so a guest cannot spin forever. A 64-bit budget still
bounds it; what changes is how long "bounded" is. At the rate measured on real
silicon-hosted emulation — **58,367,824 charges/second**, QEMU TCG on an Apple
M4 (aiueos ADR 0195):

| budget | at 58.4M/s | at 1e9/s |
|---|---|---|
| 2^31−1 (the old ceiling) | 36.8 s | 2.1 s |
| **2^53−1 (this ceiling)** | **4.9 years** | **104 days** |
| 2^62−1 (wasm) | 2,503 years | 146 years |
| 2^63−1 (the machine) | 5,007 years | 292 years |

**The ceiling is what the mechanism may carry. It is not a recommendation.** A
tier near it, on an object a kernel calls with interrupts disabled, is a hang
and not a bound; that judgement belongs to the per-object tier, which stays
measured by execution with a stated margin, and no shipped object is within six
orders of magnitude of the ceiling.

## Evidence

- Both immediate forms, byte for byte, verified against `clang -masm=intel`;
  the wide form emitted by the **portable** twin on nbb as well as by the JVM
  one (kotoba-native ADR 0078).
- **No shipped object's bytes moved**: SHA-256 of all 108 packaged kernel
  objects taken at the commit before the change and re-derived after.
- A budget past 2^31 carried rather than truncated, on both runtimes, with the
  low word as the discriminator (kotoba-kir ADR 0268).
- **2,200,000,005 fuel spent on a CPU** under QEMU, 52,516,357 past 2^31, with
  a same-bytes-but-the-budget control that stops where the arithmetic says
  (aiueos ADR 0195).

## Consequence for `evaluate_token`

aiueos ADR-0175's ≈2.8×10^10 for the output projection is four orders of
magnitude inside this ceiling. **The road is open; the object does not exist,
and no part of this work built it.** The tier such an object would need is the
whole-token cost, which has not been measured — the projection is a lower bound
on it, not an estimate.
