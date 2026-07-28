# Kotoba reliability parity WBS

**Goal:** Kotoba-as-language reaches **Clojure/ClojureScript-class reliability** on the
axes that matter for daily engineering—**predictability, multi-backend
consistency, diagnostics, stdlib, standalone execution, capability readiness**—
without becoming ambient Clojure.

**Non-goal:** Feature parity with Clojure (macros, interop, unbounded
concurrency, ambient `require`). Those remain intentional security constraints
(`lang/surface-status.edn`).

**Authority cross-links**

| Doc | Role |
|---|---|
| `lang/surface-status.edn` | What is admitted / forbidden / partial |
| `lang/pure-product-profile.edn` | Product pure oracle writeable ⊆ executable |
| `docs/adr/ADR-product-value-abi-v1.md` | Host↔guest option/string ABI |
| `docs/grade-a-version-policy.md` + `lang/version-policy.edn` | SemVer / deprecation |
| `lang/host-parity.edn` | actor:host import matrix |
| `lang/conformance/**` | Existing fixture trees |
| `lang/cli.edn` | Public CLI contract |
| `docs/agent-handoff-kotoba-refactor.md` | Product dual-source migration |

**Tier summary**

| Tier | Name | Outcome |
|---|---|---|
| **R0** | Foundations already present | inventory + version policy + partial conformance |
| **R1** | Semantic trust | writeable = multi-backend runnable; CI enforces surface |
| **R2** | Writeable language | stdlib + records/arity stress + loop/fuel |
| **R3** | Runnable alone | primary path without host Clojure |
| **R4** | Team scale | LSP/CLI polish + LTS + public apps |

---

## Top 10 → work packages

### T1 — Semantic SSoT + multi-backend conformance

**Why:** CLJ reliability is “same program, different host.” Kotoba still has
compiler / KIR / wasm / native / legacy path skew.

| ID | Task | Owner repo(s) | Deliverable | Depends | Estimate |
|---|---|---|---|---|---|
| T1.1 | Author `docs/lang/semantics-ssot.md` (values, evaluation, fuel, errors, capability call) | **kotoba-lang** | Accepted prose SSoT; points at executable suites | — | M |
| T1.2 | Expand `lang/conformance/manifest.edn` to list **required** backends per case class — **landed** (v2 matrix + `conformance-matrix` + tests; ADR-reliability-t12) | **kotoba-lang** | Machine-readable matrix | T1.1 | S |
| T1.3 | Implement/extend runner: each case runs on **KIR + wasm32-kotoba-v1** minimum — **pilot landed** (compiler#412 / ADR 0161; 5 pure-product fixtures dual-green; full matrix progressive) | **compiler**, **kotoba-kir**, **kotoba-wasm** | `clojure -M:conformance` green on both | T1.2 | L |
| T1.4 | Add native (x86_64/aarch64) cases for pure i64/string/option subset | **compiler**, **kotoba-native** | Profile `pure-native-v1` cases | T1.3 | L |
| T1.5 | Golden digests for IR + selected artifact bytes (where policy allows) | **compiler**, **artifact** | CI fails on silent semantic drift | T1.3 | M |

**Exit:** One failing backend fails the language gate; no “works on KIR only” silent pass for pure-product surface.

---

### T2 — Surface / profile CI enforcement

**Why:** Product Value ABI work showed “compiler admits ≠ product path runs.”

| ID | Task | Owner repo(s) | Deliverable | Depends | Estimate |
|---|---|---|---|---|---|
| T2.1 | Promote `lang/pure-product-profile.edn` to **admission check** in frontend | **compiler**, **kotoba-lang** | Compile error if form outside profile when `--profile pure-product` | PVA v1 | M |
| T2.2 | Generate `docs/lang/surface-matrix.md` from `surface-status.edn` in CI | **kotoba-lang** | Generated doc; `--check` mode | — | S |
| T2.3 | CI job: every `pure-product` example under `examples/` compiles + KIR-executes | **kotoba-lang**, **compiler** | Examples as living contract | T2.1 | S |
| T2.4 | Negative corpus: forbidden ambient forms still reject (link `grade-a-malicious-source-corpus.md`) | **compiler**, **kotoba-lang** | Always-on security regression | — | S |

**Exit:** “If it typechecks under pure-product, it runs on product KIR + wasm.”

---

### T3 — Diagnostics quality

**Why:** Teams trust languages that point at the broken line.

| ID | Task | Owner repo(s) | Deliverable | Depends | Estimate |
|---|---|---|---|---|---|
| T3.1 | Error contract: every `reject!` carries source span + stable error code | **compiler** | `{:kotoba.error/code … :line … :column …}` | — | M |
| T3.2 | Capability deny messages name **missing grant / effect / policy** | **compiler**, **provider**, **kototama** | Uniform deny envelope | T3.1 | M |
| T3.3 | KIR trap → source map (function + approximate form) | **kotoba-kir**, **compiler** | Runtime errors cite export name + hint | T3.1 | L |
| T3.4 | CLI pretty-printer for errors (`kotoba check` human mode) | **kotoba** CLI / **compiler** cli | Readable default UX | T3.1, CLI | S |

**Exit:** New contributor can fix a type/cap error without reading compiler source.

---

### T4 — Official bounded stdlib

**Why:** Reliability includes “not reimplementing decimal/string every time.”

| ID | Task | Owner repo(s) | Deliverable | Depends | Estimate |
|---|---|---|---|---|---|
| T4.1 | Freeze `stdlib` module list in `lang/conformance/stdlib/` — **landed** (manifest.edn + core mirror + tests; ADR-reliability-t41) | **kotoba-lang** | Manifest of public names | PVA v1 | S |
| T4.2 | Ship `string` kit: length, from-i64, join (bounded), split (bounded optional) — **partial landed**: join via concat desugar (compiler#413 / ADR 0162); split deferred | **compiler** (desugar/helpers), **kotoba-kir** | Documented ops + tests | T1.3 | M |
| T4.3 | Ship `option`/`result` usage guide + helpers (if-some already fixed) — **landed** (option-result-guide.md + examples/option-result; ADR-reliability-t43) | **kotoba-lang** docs, **compiler** examples | Guide + golden | T2.3 | S |
| T4.4 | Ship `record` + small typed-map cookbook for pure-product | **kotoba-lang**, **compiler** | Replace public base-N packs where possible | T5 | M |
| T4.5 | Collections: document O-costs; add only **bounded** map/filter/reduce over hetero-vector/typed-map | **compiler**, **kotoba-lang** | No silent O(n²) without docs | T1.1 | M |

**Exit:** New pure oracle rarely needs private `nat-str` / digit tables.

---

### T5 — Structural arguments / arity stress

**Why:** `max-parameters 5` forces bit-packs and kills API reliability.

| ID | Task | Owner repo(s) | Deliverable | Depends | Estimate |
|---|---|---|---|---|---|
| T5.1 | ADR: structural args (`record` / typed-map) preferred over arity growth | **kotoba-lang** | Accepted ADR | — | S |
| T5.2 | Product host bridge: map/record ↔ guest record for oracle/call | **murakumo**, **com-cloudflare** (pattern) | `oracle/call-record` or typed args | T5.1, PVA | M |
| T5.3 | Pilot rewrite: rebalance seats pack → record export | **murakumo**, **compiler** | Delete public base-65536 from API | T5.2, T4.4 | L |
| T5.4 | (Optional) raise max-parameters with security ADR **or** keep 5 + record-only | **kotoba-lang**, **compiler** | Decision recorded | T5.1 | S |

**Exit:** New public pure APIs do not introduce `has-*` or base-N packs.

---

### T6 — Production execution less dependent on host Clojure

**Why:** “Language reliability” ≠ “Clojure embedding reliability.”

| ID | Task | Owner repo(s) | Deliverable | Depends | Estimate |
|---|---|---|---|---|---|
| T6.1 | Define **primary standalone run path** (wasmtime **or** kexe loader) | **kotoba**, **compiler**, **aiueos**/loader | Documented `kotoba run` without `clojure -M` for pure apps | T1.3 | L |
| T6.2 | Precompiled KIR (or wasm) as default product artifact (already murakumo pattern) | product repos | Gen in CI; no compiler on prod classpath | — | M |
| T6.3 | Bootstrap plan: compiler remains CLJ **tool**; language runtime is not | **kotoba-lang** ADR | Clear tool vs runtime split | T6.1 | S |
| T6.4 | cljs/browser: execute same pure artifacts (optional oracle load) | **kotoba-kir** cljs, **wasm-webcomponent** | Remove pure mirror where possible | T6.2 | L |

**Exit:** Pure app demo runs in CI on wasmtime/kexe with zero Clojure at runtime.

---

### T7 — Tail calls, loop, fuel predictability

**Why:** Deep recursion + fuel surprises destroy production trust.

| ID | Task | Owner repo(s) | Deliverable | Depends | Estimate |
|---|---|---|---|---|---|
| T7.1 | `loop`/`recur` true tail on KIR + wasm (legacy runtime parity if still used) | **compiler**, **kotoba-kir**, **kotoba-wasm** | Spec + tests | roadmap | L |
| T7.2 | Fuel model doc: charge rules, defaults, per-module budgets | **kotoba-lang**, **kotoba-kir** | `docs/lang/fuel-model.md` | T1.1 | S |
| T7.3 | `kotoba fuel-estimate` or compile-time crude cost attribute (optional) | **compiler** | Best-effort tool | T7.2 | M |
| T7.4 | Conformance: tail recursion 10k iterations within fuel envelope | **compiler** | Regression | T7.1 | S |

**Exit:** Authors can predict stack/fuel for iterative pure code.

---

### T8 — Capability production readiness

**Why:** CLJ interop “just works”; Kotoba must make **denied-by-default effects** equally trustworthy when granted.

| ID | Task | Owner repo(s) | Deliverable | Depends | Estimate |
|---|---|---|---|---|---|
| T8.1 | Define **kit ready checklist** (**landed** provider#35 ADR 0153) (schema, dual-runtime, deny fixtures, quota, audit, 2-host parity) | **kotoba-lang**, **provider** | Checklist ADR | application-profile | S |
| T8.2 | Apply checklist to HTTP / object / secret / process (**first pass** `kit-readiness-v1.edn` provider#35) (gap list) | **provider**, **kotoba-component** | Status table in `lang/` | T8.1 | L |
| T8.3 | Network + secret **signed** reference providers (leave pure allowlist alone) — **partial**: kit EDN receipts provider#36 / ADR 0154; Wasm receipt API provider#37 / ADR 0155 (fixture only); identity inject provider#38 / ADR 0156; package-manifest provider#40 / ADR 0158; **production AOT Component still open** | **capability-***, **kotoba-core-contracts**, **tamaki**, **kototama**, **provider** | contract-only → signed | T8.1 | XL |
| T8.4 | Expand `lang/host-parity.edn` L5 conformance cases for critical imports — **partial landed**: crypto/http/kagi/transport/llm fixtures + resources sync (45 expanded cases); remaining: live host runners in kototama/wasm-webcomponent | **kotoba-lang**, **kototama**, **wasm-webcomponent** | Same guest expectations | T8.2 | M |

**Exit:** “Granted capability” has the same operational confidence as a well-tested CLJ client library.

---

### T9 — Toolchain (CLI / check / test)

**Why:** Reliability is a daily loop.

| ID | Task | Owner repo(s) | Deliverable | Depends | Estimate |
|---|---|---|---|---|---|
| T9.1 | Map `lang/cli.edn` commands to implemented adapters; close M2 gaps | **kotoba**, **compiler** | `check` / `test` / `run` / `compile` | cli.edn | M |
| T9.2 | `kotoba check` = frontend admit + pure-product profile | **compiler** CLI | Seconds-scale feedback | T2.1, T3.4 | M |
| T9.3 | Official test harness for `.kotoba` modules (fixtures in-tree) | **kotoba-lang** or **compiler** | Documented `kotoba test` | T9.1 | M |
| T9.4 | Minimal formatter (or strict style subset) | **kotoba-lang** | `kotoba fmt --check` optional | — | S |
| T9.5 | LSP spike (diagnostics from T3) | new or **compiler** | Experimental but usable | T3.1 | L |

**Exit:** New hire runs check/test/run without memorizing `clojure -M:…` classpaths.

---

### T10 — LTS + compatibility policy (operationalize existing Grade A)

**Why:** Policy exists (`grade-a-version-policy.md`); reliability needs **habit**.

| ID | Task | Owner repo(s) | Deliverable | Depends | Estimate |
|---|---|---|---|---|---|
| T10.1 | Publish **current language profile version** on every release tag | **kotoba-lang**, release workflow | Tag binds profile id | version-policy | S |
| T10.2 | CI compatibility report artifact (`clojure -M:compatibility …`) required on release | **kotoba-lang** | Gate | version-policy | S |
| T10.3 | Changelog discipline: surface-status diffs in release notes | **kotoba-lang** | Process | T2.2 | S |
| T10.4 | Deprecation window drills (one intentional soft deprecation of a sugar) | **compiler**, **kotoba-lang** | Proves policy works | T10.1 | M |

**Exit:** Consumers can pin a language profile for ≥180 days without surprise breaks.

---

## Work packages by repository

### `kotoba-lang` (language authority)

| Package | Tasks | Notes |
|---|---|---|
| Semantics & docs | T1.1, T1.2, T2.2, T4.1, T4.3, T5.1, T6.3, T7.2 | SSoT prose + edn |
| Conformance ownership | T1.2, T2.3, T4.1 | fixtures under `lang/conformance` |
| CLI contract | T9.1 (contract side) | `lang/cli.edn` |
| Version/LTS | T10.* | already has Grade A skeleton |
| Host parity | T8.4 | `lang/host-parity.edn` |
| Handoff | keep agent-handoff in sync with R-tier exits | |

### `compiler`

| Package | Tasks |
|---|---|
| Admission / profiles | T2.1, T2.4, T5.4 |
| Conformance runner hooks | T1.3, T1.4, T1.5 |
| Diagnostics | T3.1, T3.4 |
| Stdlib lowering | T4.2, T4.4, T4.5 |
| Loop/fuel | T7.1, T7.3, T7.4 |
| CLI check | T9.2 |

### `kotoba-kir`

| Package | Tasks |
|---|---|
| Execute parity | T1.3, T3.3, T7.1, T7.2 |
| String/option runtime | T4.2 (with compiler) |
| cljs execute | T6.4 |

### `kotoba-wasm` / `kotoba-native` / `artifact`

| Package | Tasks |
|---|---|
| Backend conformance | T1.3, T1.4, T1.5 |
| Standalone run | T6.1 |

### `provider` / `kotoba-component` / `kototama` / `tamaki` / `kotoba-core-contracts` / `capability-*`

| Package | Tasks |
|---|---|
| Kit readiness | T8.1–T8.3 |
| Host parity L5 | T8.4 |

### Product repos (`murakumo`, `com-cloudflare`, …)

| Package | Tasks |
|---|---|
| Apply PVA + records | T5.2, T5.3, residual has-* |
| Artifact-only prod | T6.2 |
| **Not** language reliability core | keep dual-source; don’t invent language features in product |

### `kotoba` (public CLI tree, if separate from compiler)

| Package | Tasks |
|---|---|
| CLI adapter to `lang/cli.edn` | T9.1, T9.3, T9.4 |

---

## Suggested sequencing (quarters, not calendar promises)

```text
R1 (first):  T1 + T2 + T3.1 + T10.1–2
R2:          T4 + T5 + T7.1–2 + T9.2
R3:          T6 + T8.1–3 + T3.2–3
R4:          T9.4–5 + T10.3–4 + public app proofs
```

**Parallelism**

- **Track L (language):** T1, T2, T3, T4, T7 — compiler/kir/lang  
- **Track C (capability):** T8 — provider/contracts  
- **Track P (product apply):** T5.3, T6.2 — murakumo/CF only after L lands  
- **Track T (toolchain):** T9, T10 — can start early on docs/CI  

Do **not** parallelize “new sugar” with “conformance SSoT” on the same backend without T1.3 green.

---

## Agent assignment guide

| Task shape | Agent can solo? | Prompt must include |
|---|---|---|
| Conformance case + both backends | Yes | pure-product profile; no ambient; PR tests |
| Error code plumbing | Yes | stable error code table |
| Stdlib op | Yes | desugar to existing wasm-safe ops preferred |
| Record pilot (rebalance) | Yes with design note | T5.1 ADR; no new pack public API |
| Standalone wasmtime path | Senior / multi-repo | T6.1 scope lock |
| Signed network/secret | Senior + security | T8.1 checklist; no fake reference-impl |
| LSP | Optional later | T3 first |

**Standing product rules still apply:** sibling worktree; no force-push; no keychain dump; inventory/plan notes on product PRs.

---

## Measurable exit criteria (language-level)

| Metric | Target |
|---|---|
| pure-product examples failing any required backend | **0** |
| New pure API introducing has-*/base-N public packs | **0** (lint or review) |
| `reject!` without source span / error code | **0** new; burn down old |
| Release without compatibility report | **blocked** |
| Pure demo `run` requiring Clojure runtime | **false** on primary path (R3) |
| Kit “ready” without deny fixtures | **not claimed** |

---

## Explicitly out of scope for “CLJ reliability parity”

- Guest `defmacro`, ambient `require`, unrestricted interop  
- Unbounded threads/agents  
- Full HAMT as default (optional later profile only)  
- Claiming network/secret ready without signed providers  
- Rewriting design-system consumers before stdlib/records (product track)

---

## Immediate next three PRs (start here)

| # | Task | Status |
|---|---|---|
| 1 | **kotoba-lang:** `docs/lang/semantics-ssot.md` skeleton + handoff wire (T1.1) | **landed** #295 |
| 2 | **compiler:** pure-product profile admission + examples KIR CI (T2.1 + T2.3) | **landed** #411 |
| 3 | **compiler:** error code + span contract; migrate top reject sites (T3.1) | **landed** #411 |

These three unlock parallel agent work on T4/T5 without reopening “what is the language.”
