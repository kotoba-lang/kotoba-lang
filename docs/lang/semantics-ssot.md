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
| `:f32` / `:f64` | IEEE-754; target-restricted (see floating-point policy) |

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

---

## 4. Evaluation order

1. Forms are **desugared** into a core expression language (let/if/call/literals…).  
2. Evaluation is **left-to-right, call-by-value**.  
3. `let` bindings are sequential.  
4. `if` / `if-some` / `cond` / `case` evaluate only the taken branch.  
5. `if-some` binds the **payload** of `[:option T]` when some; else else-branch.  
   Binding type is the option’s **T**, not hardcoded `:i64` (Product Value ABI fix).  
6. There is **no ambient mutable heap** in pure guest code. Host state is capability I/O.

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

## 6. Fuel and termination

1. Default compile artifacts carry a **finite fuel** budget (commonly 512 units;
   target-specific).  
2. Fuel is **not replenishable** unless a profile explicitly says otherwise.  
3. Deep recursion without true tail support may exhaust fuel or stack — authors must
   prefer bounded loops / iterative designs (see WBS T7).  
4. Fuel charge rules (per op class) are detailed in `docs/lang/fuel-model.md` when T7.2
   lands; until then treat fuel as “small pure functions succeed; adversarial depth fails closed.”

---

## 7. Errors and diagnostics

### 7.1 Phases

Compiler `ex-info` data includes `:phase` among:

| Phase | Meaning |
|---|---|
| `:read` | Reader / form limits |
| `:subset` | Language surface rejection |
| `:admission` | Capability / policy deny |
| `:target` | Backend cannot express feature |
| `:ir` / lowering | Internal lowering failure |
| others | see `kotoba.compiler.diagnostic/phase-codes` |

### 7.2 Stable codes (T3.1+)

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

### 7.3 Contributor bar

A new contributor should fix a type/cap error **without reading compiler source**,
using message + span + code only (WBS T3 exit).

---

## 8. Multi-backend meaning

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

## 9. String operations (product surface)

| Op | Meaning |
|---|---|
| `string-byte-length` / `string-length` | UTF-8 **byte** length (aliases) |
| `string-substring` | Byte-index range; must stay on code-unit boundaries as implemented |
| `string-concat` | Bounded concatenation |
| `string=?` | Equality (do not use `=` on strings) |
| `string-from-i64` | Signed decimal text |
| `string-join` | Separator + ≤8 parts → nested `string-concat` (T4.2) |
| `string-contains?` / `string-fold-case` / `string-code-point-at` | As admitted |

---

## 10. Equality

- `=` is for admitted scalar/identity types in the safe profile.  
- Strings → `string=?`  
- f64 → `f64-eq` or bit conversion — rejection messages must name the alternative.

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
