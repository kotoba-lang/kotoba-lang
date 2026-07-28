# Fuel model (execution budgets)

**WBS:** T7.2  
**Status:** accepted (documentation of current behavior)  
**Related:** [`semantics-ssot.md`](./semantics-ssot.md) §6, WBS T7.1 (true tail), T7.3 (fuel-estimate tool)

This document is the **charge-rules SSoT** for pure and guest execution fuel.
It describes what engines **actually do today**, not a wish list.

---

## 1. What fuel is

Fuel is a **finite, non-replenishable call budget** attached to a program run.
It prevents unbounded recursion / loops from hanging a host. Fuel is
**interpreter / module bookkeeping** — never a guest `.kotoba` value.

| Property | Value |
|---|---|
| Default initial budget | **512** units |
| Replenishable? | **No** (default profile) |
| Exhaustion trap | `:fuel-exhausted` (fail closed) |
| Max declared budget (wasm) | \(2^{62}-1\) (`kotoba.wasm/max-fuel`) |

---

## 2. Charge unit (current backends)

**Charge = 1 unit per function entry** (not per expression).

| Backend | Mechanism | Default |
|---|---|---|
| **KIR** (`kotoba.kir`) | `charge!` in `invoke-function` decrements a volatile counter | 512 |
| **wasm32-kotoba-v1** | Module-private `mut i64` global; prologue `global.get` / `eqz` → `unreachable` / `sub` | 512 baked into global init |
| **js-kotoba-v1** | `kotoba$fuel` atom; charge helper throws `fuel-exhausted` | 512 |

Evidence comments in tree:

- KIR: “Backends charge once on function entry, not once per expression.”
- Wasm emit: “Every call consumes one unit from a module-private monotonic fuel…”

### What does **not** charge (today)

- Intra-function expressions (`if`, `let`, arithmetic, string ops) — **no per-op charge**
- Capability provider wall time / network — separate provider timeouts / quotas (not this fuel counter)
- Host Clojure / nbb orchestration outside the guest module

### Implications for authors

| Pattern | Fuel cost (units) |
|---|---|
| Straight-line pure fn, no calls | 1 (entry of `main` / export) |
| Recursion depth *N* | ~*N* (each recursive call) |
| Mutual recursion / multi-export helpers | 1 per call edge |
| Deep expression tree, no calls | 1 |

So `fact 5` costs a handful of units; a non-tail `forever` loop exhausts 512 and traps.
Demo: `compiler/examples/fuel.kotoba`.

---

## 3. Defaults and parameterization

| Context | How budget is set |
|---|---|
| Unparameterized compile | Historical **512** (`wasm/default-fuel`, KIR `default-fuel`) |
| Component / compile opts | `:fuel` / `:budgets {:fuel n}` (must be positive integer ≤ max-fuel) |
| CLI | `--fuel`, receipt fields `--fuel-initial` / `--fuel-remaining` |
| Receipts | `{:fuel {:initial … :remaining … :consumed …}}` |

Changing the default would break historical component hashes; keep 512 unless a
versioned profile explicitly bumps it (Grade A / T10).

---

## 4. Sugar / stdlib **inner** fuel helpers (orthogonal)

Some surface desugars use a **second**, helper-local fuel (e.g. collection
transforms bound at **128** — `lang/surface-status.edn`
`:primary-transform-fuel 128`). That is a **compile-time desugar bound** for
eager pair-chain map/filter helpers, **not** a replacement for the runtime
module fuel counter.

| Layer | Bound | Role |
|---|---|---|
| Runtime module fuel | 512 default | Call graph / recursion |
| Transform helper fuel | 128 (typical) | Single desugared collection walk |
| Lazy take/drop | execution fuel of forcing path | See surface-status |

Do not assume “fuel 512 ⇒ 512 map iterations.” Map helpers may stop earlier via
their own bound; recursion still burns module fuel per call.

---

## 5. Termination expectations (product pure)

| Kind of program | Expectation under default 512 |
|---|---|
| Pure-product oracles (murakumo-style, shallow call graph) | Succeed with large margin |
| Bounded loops (`dotimes` small *N*) | OK if desugared call depth ≤ budget |
| Deep recursion without true tail | May exhaust fuel or stack (T7.1 improves tail) |
| Intentional non-termination | Always traps (fuel or host kill) |

**Fail closed:** never “run forever with warning.” Exhaustion is a trap, not a soft log.

---

## 6. Multi-backend agreement

T1.3 dual-backend pilot requires **same observable result** on KIR and
wasm32 for pure-product cases. Fuel traps must agree on exhaustion for the
same call graph under the same initial budget. Per-expression charging must
not be reintroduced on one backend only.

---

## 7. Out of scope (follow-ups)

| Item | WBS |
|---|---|
| Zero-charge `recur` / machine TCO (arbitrary fns) | T7.1 residual |
| Per-op / weighted charging | not planned; would need ADR + dual-backend rewrite |

---

## 8. Implementation pointers

| Repo / path | Role |
|---|---|
| `kotoba-kir` `src/kotoba/kir.cljc` | `default-fuel`, `charge!`, `invoke-function` |
| `kotoba-wasm` `src/kotoba/wasm/core.cljc` | `default-fuel`, `max-fuel`, prologue charge |
| `compiler` `backend/cljs.clj` | JS atom fuel |
| `compiler` `core.clj` | `:limits {:fuel 512 :replenishable? false}` |
| `compiler` `examples/fuel.kotoba` | fact / forever demo |
| `lang/surface-status.edn` | transform helper fuel 128 |

## loop / recur (T7.1 / T7.4)

`loop`/`recur` desugar to a **named helper** (`__kotoba_loop_N`) that self-calls.
Each iteration is a **function entry** and charges **1 fuel unit** (same as any
call). Dual-backend pilot kits prove KIR + wasm agreement.

**T7.4 + T7.1 (landed):** `:loop-deep-kit` runs **10_000** iterations with case
`:fuel 16` (compiler#428). KIR trampolines + **zero-charges** self-tail re-entry
of `__kotoba_loop_N`; wasm omits fuel prologue on those helpers. First helper
entry still costs 1 unit. Arbitrary non-helper TCO still open; hosts must
wall-clock-bound infinite loops.

