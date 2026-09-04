# Kotoba language semantics — single source of truth (SSoT)

**Status:** accepted skeleton (R1 / T1.1)  
**Authority:** this document + executable fixtures under `lang/conformance/**`  
**Profiles:** `lang/surface-status.edn`, `lang/pure-product-profile.edn`  
**WBS:** [`docs/kotoba-reliability-parity-wbs.md`](../kotoba-reliability-parity-wbs.md)

This is the **prose SSoT** for “what a Kotoba program means.”  
When prose and a named conformance case disagree, **fix the case**, then amend this file
in the same change. Do not invent a second informal dialect in product PRs.

---

## 1. Scope

### In scope

| Topic | Summary |
|---|---|
| Values | Closed set of admitted value types (typed profile) |
| Evaluation | Call-by-value; pure expressions; explicit effects only via capabilities |
| Fuel | Finite fuel; non-replenishable default budgets |
| Errors | Fail-closed admission; stable `:kotoba.error/code` + source span |
| Capability call | Named `cap-call` only when declared + granted |
| Incidence | Immutable role relation; CID integrity is not authority |

### Out of scope (intentional non-Clojure)

- Ambient `require` / `eval` / interop / macros  
- Unbounded threads, agents, STM  
- Recursive value types as general data (use form A call-graph or flat ui-v1 trees)  
- Claiming network/secret “ready” without signed providers  

See `lang/surface-status.edn` and Grade-A malicious corpus for the deny list.

---

## 2. Programs and modules

1. A **compilation unit** is a string of Kotoba/EDN forms (typically one `.kotoba` file).  
2. At most one `(ns …)` form is admitted.  
3. Top-level forms are a closed set: `ns`, `def`, `defn` / `defn-`, and a small set of
   closed-world multimethod forms where the profile allows them.  
4. `(ns name …)` may declare:
   - `:export [sym …]` — host-visible exports (no ambient authority)
   - `:capabilities #{:ns/op …}` — closed set of named capabilities for `cap-call`
   - `:schemas {…}` — closed schema map (where admitted)
5. **Import / require clauses are rejected** (fail closed). Linkage is project-mode or
   host-owned, not ambient classpath discovery inside guest source.

---

## 3. Value model

### 3.1 Scalars (typed profile)

| Type | Notes |
|---|---|
| `:i64` | Primary integer; default untyped path is also i64-shaped |
| `:bool` | Boolean; may wire as i64 0/1 at some boundaries |
| `:string` | UTF-8 bytes; length ops use **byte** length unless a named code-point op is used |
| `:keyword` | Admitted keyword values |
| `:f32` / `:f64` | IEEE-754; target-restricted (see [3.4 Floating-point policy](#34-floating-point-policy)) |

### 3.2 Structured (typed / parametric)

| Type | Boundedness |
|---|---|
| `[:option T]` | Exactly none or some(T) |
| `[:result T E]` | ok/err tagged |
| `[:record id fields…]` | Field count bounded |
| `[:variant id cases…]` | Case count bounded |
| Heterogeneous vector / typed map / set | Item/entry caps enforced at admission |

**Non-goal:** general recursive values as first-class nested trees. Prefer:

- **Form A:** pure functions returning `:string` (SSR / HTML assembly)  
- **Form B:** flat node sets with parent pointers (ui-v1)

### 3.3 Product Value ABI v1 (host ↔ pure guest)

Authoritative table: `docs/adr/ADR-product-value-abi-v1.md`.

| Guest | Host bridge |
|---|---|
| `:i64` | long |
| `:string` | String |
| `:bool` | boolean / 0\|1 |
| `[:option T]` | `nil` ↔ none; value ↔ some (or tagged wire form) |

**Forbidden product patterns** (when option/string work): `has-*` i64 sentinels for
optional strings; `ttl -1` for missing i64; public base-N packs when a record export
exists.

### 3.4 Floating-point policy

Decided by [ADR-kotoba-floating-point-on-native](../adr/ADR-kotoba-floating-point-on-native.md).
Machine form: `lang/guest-grammar.edn` `:floating-point`; classification:
`lang/surface-status.edn` `:native-binary32-arithmetic`.

**Widths.** `:f32` is IEEE-754 binary32, `:f64` is binary64. There is no
implicit conversion between them, and none between either and `:i64`. Every
conversion is a named operation.

**Rounding is round-to-nearest, ties-to-even**, for arithmetic and for every
conversion. Emitted code assumes the host's default rounding state (x86 MXCSR,
AArch64 FPCR); no admitted operation lets a guest change it.

**No contraction, no fast-math.** A backend may not fuse a multiply and an add
into an FMA — that changes the result. No reassociation, no reciprocal
substitution for division, no flush-to-zero, no denormals-are-zero. Subnormals
are computed.

**NaN, ±Infinity and −0.0 are ordinary values in computation.** They are
produced, carried, compared and distinguished; `f32-unordered` / `f64-unordered`
is the operation that observes NaN, and `f32-neg` of `+0.0` is `−0.0`, a
different bit pattern. This is *not* the same boundary as the wire:
`lang/value-codec.edn` still rejects all four as transportable values, and
`:f32` remains a binder-level annotation rather than a wire type. A value a
program may compute is not thereby a value the codec may carry.

**NaN payloads are unspecified.** A program may observe *that* a value is NaN;
the language does not promise which quiet-NaN payload an operation produces, and
two backends may differ there and both conform.

**Spelling.** The operations are named heads — `f32-add`, `f64-sqrt`,
`f32-lt` — never `+` or `<`. The `f32+` family in `:admitted-builtins` is the
legacy wasm emitter's builtin vocabulary (its neighbours are `"i64+"`,
`"alloc"`, `"str-ptr"`), not a second name for these; the canonical spelling of
*integer* addition in the same file is plain `+`.

**Literals are exact or refused.** A decimal literal reaches the compiler as a
host binary64, so decimal → binary32 cannot be rounded in one step, and
decimal → binary64 → binary32 is not always the same value. A literal is
admitted in an `:f32` context only when its binary64 round-trips exactly through
binary32. `1.5` and `2.0` are admitted; `0.1` is refused, because the value the
reader hands over is not the float the author wrote. Write the narrowing
explicitly — `(f64-to-f32-rounded 0.1)` — or the pattern —
`(f32-from-bits 0x3DCCCCCD)`.

**Target restriction** (this is what "target-restricted" in §3.1 means):

| Target | f64 | f32 |
|---|---|---|
| KIR oracle (`kotoba.kir/execute`) | full | full |
| `wasm32-kotoba-v1`, `js-kotoba-v1` | full | full |
| native x86_64 / aarch64 | arithmetic, comparison, bits | arithmetic, comparison, bits, and four conversions |

On native, `min`/`max`, the `-checked` conversions and the truncating
float → int conversions are refused, each for a stated reason — see the ADR.
Neither `:f32` nor `:f64` is a native function-boundary type at either width;
floats cross a native module boundary as their bit pattern, and
`f32-from-bits` / `f32-to-bits` cost nothing.

---

## 4. Evaluation order

1. Forms are **desugared** into a core expression language (let/if/do/call/literals…).  
2. Evaluation is **left-to-right, call-by-value**.  
3. `let` bindings are sequential.  
4. A `let` **body is an implicit `do`**: every form is evaluated, in source
   order, and the value is the last one. The core `let` takes exactly ONE body
   expression, so a multi-form source body is collapsed into a `do` during
   desugaring — never into nested `let`s, which would make each non-final form
   an unused binding that a later pass is entitled to drop. `when`, `when-not`
   and `when-let` reach the same rule through their own desugaring (measured).
   `defn`, `fn` and `loop` take one body expression and refuse more.
   `when-some`, `doseq` and `dotimes` are listed as **unmeasured** in
   `lang/guest-grammar.edn` `:implicit-body-forms` — do not read them as
   either.  
5. Core `if` is exactly ternary — test, then, else. Any other arity is refused.  
6. `if` / `if-some` / `cond` / `case` evaluate only the taken branch.  
7. `if-some` binds the **payload** of `[:option T]` when some; else else-branch.  
   Binding type is the option’s **T**, not hardcoded `:i64` (Product Value ABI fix).  
8. There is **no ambient mutable heap** in pure guest code. Host state is capability I/O.

---

## 5. Effects and capabilities

1. Default is **deny-by-default**.  
2. Effects appear only through admitted operations (primarily `cap-call` and kit surface).  
3. Namespace `:capabilities` must **exactly match** used capability names (declare-then-check).  
4. Compilation admission intersects guest effects with a **policy allow-set**.  
5. Runtime host must still enforce grants; compile-time admission is not a substitute for
   runtime deny fixtures.

### Pure-product profile

`lang/pure-product-profile.edn` defines the surface pure product oracles may use.

- **No** `:capabilities` / `cap-call`  
- **Empty** effect set after analysis  
- Control sugar + string ops listed in the profile must execute on product KIR
  (`:wasm32-kotoba-v1` → `kotoba.kir/execute`)  
- Compiler flag: `:language-profile :pure-product` (see compiler admission)

---

## 6. Content-addressed incidence

Durable distributed and organizational state is expressed as immutable,
role-labelled n-ary relations. The canonical contract is
`lang/incidence.edn`; the reference semantics are documented in
[`incidence.md`](./incidence.md).

1. An incidence CID identifies canonical relation content, never authority.
2. Person, agent, organization, and system use the same constitution shape.
3. Assertions and CID-targeted retractions append parent-linked incidences;
   current state and observations are verified projections rather than
   mutation of an identity object.
4. DID, VC, UCAN, and ZCAP terms belong at explicit interoperability adapters.
5. An external delegation remains data-only until verification, local-policy
   intersection, and concrete capability minting succeed.
6. EDN and tagged literals never mint capabilities. Authority is a runtime
   lexical/affine value; Clojure-shaped syntax does not expose ambient JVM
   authority or effectful macro expansion.

## 7. Fuel and termination

1. Default compile artifacts carry a **finite fuel** budget (commonly 512 units;
   target-specific).  
2. Fuel is **not replenishable** unless a profile explicitly says otherwise.  
3. Deep recursion without true tail support may exhaust fuel or stack — authors must
   prefer bounded loops / iterative designs (see WBS T7).  
4. Fuel charge rules are detailed in [`docs/lang/fuel-model.md`](./fuel-model.md) (T7.2):
   **1 unit per function entry**, default budget **512**, non-replenishable, trap
   `:fuel-exhausted`. Small pure functions succeed; adversarial depth fails closed.

---

## 8. Errors and diagnostics

### 8.1 Phases

Compiler `ex-info` data includes `:phase` among:

| Phase | Meaning |
|---|---|
| `:read` | Reader / form limits |
| `:subset` | Language surface rejection |
| `:admission` | Capability / policy deny |
| `:target` | Backend cannot express feature |
| `:ir` / lowering | Internal lowering failure |
| others | see `kotoba.compiler.diagnostic/phase-codes` |

### 8.2 Stable codes (T3.1+)

Prefer machine-readable:

```clojure
{:kotoba.error/code :kotoba.error/…   ;; stable keyword
 :phase :subset
 :span {:line L :column C …}          ;; when form meta available
 :form …}                             ;; optional, for tests
```

`kotoba.compiler.diagnostic/from-error` maps phase → coarse code and **preserves**
`:kotoba.error/code` when present. New `reject!` sites must supply a specific code;
legacy sites may still use the coarse phase code until burned down.

### 8.3 Contributor bar

A new contributor should fix a type/cap error **without reading compiler source**,
using message + span + code only (WBS T3 exit).

---

## 9. Multi-backend meaning

“Same program” reliability means **shared HIR/KIR meaning** across backends that claim
a case class:

| Backend | Role |
|---|---|
| KIR (`kotoba.kir/execute`) | Product pure oracle runner (murakumo pattern) |
| `wasm32-kotoba-v1` | Portable wasm bytes + same KIR semantics |
| `js-kotoba-v1` / `cljs-kotoba-v1` | Web/script targets for typed subset |
| native x86_64 / aarch64 | Pure i64/string/option subset (expanding) |

**Gate (R1 exit):** a pure-product case that fails any **required** backend fails the
language gate — no silent “works on KIR only” for the pure-product surface (T1.3).

Conformance ownership: `lang/conformance/manifest.edn` **v2** — required-backends matrix
per case class (T1.2 landed). Dual-backend execution runner is T1.3.

---

## 10. String operations (product surface)

| Op | Meaning |
|---|---|
| `string-byte-length` / `string-length` | UTF-8 **byte** length (aliases) |
| `string-substring` | Byte-index range; must stay on code-unit boundaries as implemented |
| `string-concat` | Bounded concatenation |
| `string=?` | Equality (do not use `=` on strings) |
| `string-from-i64` | Signed decimal text |
| `string-join` | Separator + ≤8 parts → nested `string-concat` (T4.2) |
| `string-contains?` / `string-fold-case` / `string-upper` / `string-code-point-at` | As admitted |

---

## 11. Equality

- `=` is for admitted scalar/identity types in the safe profile.  
- Strings → `string=?`  
- Floats → the width's own head, `f32-eq` / `f64-eq`, or bit conversion — rejection
  messages must name the alternative. `=` between floats is not admitted: NaN and
  −0.0 do not behave under integer equality on a word the way IEEE-754 says they
  must. See §3.4.

---

## 11. Linking executable truth

| Artifact | Role |
|---|---|
| `lang/conformance/**` | Positive/negative fixtures |
| `lang/malicious-source/**` | Security regression corpus |
| `examples/product-value-abi-v1/` | Living pure-product golden |
| `test/` in **compiler** | Executable semantics (analyze / compile / KIR) |
| `lang/pure-product-profile.edn` | Writeable ⊆ executable for pure oracles |

---

## 12. Change control

1. Semantic changes require: this doc touch **or** an ADR **and** a conformance/test case.  
2. Surface admission changes update `lang/surface-status.edn` / pure-product profile.  
3. Do not “fix” semantics only in product host bridges — host bridges project ABI;
   guest meaning stays here.

---

## 13. Open items (tracked in WBS, not undefined)

| Item | WBS |
|---|---|
| Full multi-backend required matrix | **T1.2 landed**; **T1.3 pilot expanded** (compiler#412+#414, 7 dual-green); T1.4–T1.5 native/golden |
| Fuel model detail | T7.2 |
| True `loop`/`recur` tail | T7.1 |
| Record args vs arity-5 packs | **T5.1 policy landed**; T5.2–T5.3 host bridge + pilot |
| Standalone run without Clojure host | T6 |

---

*Skeleton accepted for R1 so parallel agents share one semantic vocabulary.*
