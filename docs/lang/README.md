# Kotoba Language Profile

**Semantics SSoT:** [`semantics-ssot.md`](./semantics-ssot.md) — values, evaluation,
fuel, errors, capabilities, multi-backend meaning (R1 / T1.1).

**Surface matrix (T2.2):** [`surface-matrix.md`](./surface-matrix.md) — generated from
`lang/surface-status.edn` (`clojure -M -m kotoba.lang.surface-matrix --check`).

**Fuel model (T7.2):** [`fuel-model.md`](./fuel-model.md).  
**Records (T4.4):** [`record-cookbook.md`](./record-cookbook.md).  
**Option/result (T4.3):** [`option-result-guide.md`](./option-result-guide.md).  
**String kit (T4.2):** [`string-kit.md`](./string-kit.md).
**Collections costs (T4.5):** [`collections-costs.md`](./collections-costs.md).

Kotoba source is a Kotoba/EDN subset with a capability-safe profile for
untrusted or AI-generated code. `.kotoba` is the canonical Kotoba-only source
extension; portable `.cljc` is for shared Clojure-family source where
Kotoba-specific behavior is selected with reader conditionals:

```clojure
#?(:kotoba (defn main [x] (+ x 10))
   :clj    (defn main [x] (+ x 1))
   :cljs   (defn main [x] (+ x 2)))
```

## Getting Started

For the public implementation CLI, start with the smallest compile-and-run path:

```sh
kotoba -e '(+ 1 2)'
```

Then build a source file and inspect the safe-language policy surface:

```sh
kotoba wasm build examples/hello.kotoba -o hello.wasm
kotoba wasm safe-policy examples/policy-demo.kotoba
kotoba wasm safe-build examples/policy-demo.kotoba --policy policy.edn -o policy-demo.wasm
```

The examples in `examples/` are intentionally small. The authoritative
compatibility examples are the conformance fixtures under `lang/conformance/`.

## Source Contract

- Accepted extensions: `.kotoba`, `.cljc`, `.cljk`, `.clj`, `.cljs`.
- `.cljk` means CLJ Kotoba and selects the `:kotoba` reader/compiler target.
- `.cljc` is common source for `:clj`, `:cljs`, and `:kotoba`.
- Default reader target: `kotoba`.
- `:kotoba` branch fallback order: `:kotoba`, then `:clj`, then `:default`.
- Namespace resolution priority for target `kotoba`: `.kotoba`, `.cljc`,
  `.cljk`, `.clj`, `.cljs`.
- Namespace resolution priority for target `clj`: `.cljc`, `.clj`.
- Namespace resolution priority for target `cljs`: `.cljc`, `.cljs`.
- `.clj` and `.cljs` are single-target compatibility extensions (profile v3
  reinstates `.cljs`, previously retired in v2 — see
  `docs/lang/versioning.md`); each has its own reader-branch chain
  (`["clj" "default"]` / `["cljs" "default"]`) and neither carries
  `#?(:kotoba ...)` branches the way `.cljc` does.

`.clj` keeps Clojure semantics and `.cljs` keeps ClojureScript semantics.
`.kotoba` and `.cljk` use the Kotoba compiler subset; `.cljc` is the only
portable source surface shared by all three reader targets.

Inline expressions are also part of the compiler conformance vocabulary:
`kotoba -e '(+ 1 2)'` wraps the expression as an exported `main`, compiles it
through the same Kotoba -> core Wasm path, and runs `main`. This is
compile-and-run sugar, not runtime `eval`; the lower-level implementation
binary keeps a compatibility `-e` path only for crate-local testing and existing
integrations.

Capability-safe language tooling is exposed through `kotoba wasm`:

```sh
kotoba wasm build cell.kotoba
kotoba wasm build -S src cell.kotoba -o cell.wasm
kotoba wasm safe-policy cell.kotoba
kotoba wasm safe-build cell.kotoba --policy policy.edn -o cell.wasm
kotoba wasm selfhost-inspect cell.kotoba --policy policy.edn --json
```

Namespace source roots are supplied with `-S` / `--source-path` or
`KOTOBA_SOURCE_PATH`; `KOTOBA_CLJ_PATH` is retained only as a compatibility
alias.

Those commands are implemented by the CLJC authority and the launcher in
`kotoba-lang/kotoba`, but keep the user-facing language surface under the
`kotoba` command.

Dynamic authority is modeled as explicit capability values, not as ambient host
access or plain resource strings. The profile semantics are documented in
[`capability-values.md`](capability-values.md).

Source-file classification lives in
[`kotoba-core-contracts/lang/profile.edn`](https://github.com/kotoba-lang/kotoba-core-contracts/blob/main/lang/profile.edn).
The admitted language surface is owned here by `lang/guest-grammar.edn`. The
current classification of intentional safety constraints, deliberate semantic
simplifications, partial features, and ordinary implementation gaps lives at
`lang/surface-status.edn`; its decision record is
`docs/adr/ADR-kotoba-language-surface-status.md`. In particular, map and vector
literals are partial compiler features, while set semantics and the
higher-order `map` function are not yet portable guest features. Compiler
conformance fixtures live under `lang/conformance/`.
Coverage and maturity tracking lives in `docs/lang/coverage.edn`; compatibility
rules live in `docs/lang/versioning.md`; CI-facing commands live in
`docs/lang/gates.md`.

The machine-readable CLI command contract lives at `lang/cli.edn`. It defines
the public `kotoba` command vocabulary for `run`, `check`, `db`, `git`, `rad`,
and `deploy` so host implementations can adapt to CLJC/EDN data instead of
owning the protocol surface.

## Package References

Package and registry work is tracked separately from the language surface. The
machine-readable package contract lives in
[`kotoba-core-contracts/lang/package.edn`](https://github.com/kotoba-lang/kotoba-core-contracts/blob/main/lang/package.edn);
example authoring shapes remain in `examples/package-manifest.edn` and
`examples/kotoba.lock.edn`.

Safe Kotoba package references are content-pinned and authority-checked:

- source trees, package manifests, registry records, and built components are
  pinned by CID;
- package authority comes from repo RID plus signed records, not from CID alone;
- dependencies receive no host capability unless the caller lockfile and policy
  grant it explicitly;
- name plus semver without repo RID, signatures, and CID pins is non-conforming
  for safe execution.

The decision is recorded in `docs/adr/ADR-kotoba-package-cid-lock.md`.
Human-facing authoring rules live in `docs/lang/package-rules.md`.
Executable package-contract fixtures live in `kotoba-core-contracts/lang/package-conformance/`;
`kotoba-core-contracts/scripts/check-package-contract.bb` accepts the positive manifest/lock fixtures
and rejects version-only, unsigned, missing-CID, and over-capability negative
fixtures.

## Wire Protocol

Kotoba-owned app/resource communication uses plain JSON by default:

- media type: `application/json`, optionally `Content-Encoding: gzip`
- authoritative implementation: `kotoba-lang/transit`
- in-memory and file authoring shape: EDN
- package/storage integrity: CID, signed manifests, and lockfiles
- external OpenAPI/GraphQL/XRPC/provider protocols: explicit adapters

The decision is recorded in `docs/adr/ADR-kotoba-json-wire-protocol.md`,
superseding `docs/adr/ADR-kotoba-transit-wire-protocol.md`. Package rules
require JSON wire contract surfaces for Kotoba-internal app APIs that cross a
host or network boundary.

## Capability Values

Safe Kotoba treats resource names and authority as separate concepts. Dynamic
resource access should pass scoped capability values rather than relying on a
string that becomes authority at the host boundary. The profile-level semantics
are documented in `docs/lang/capability-values.md`.

## Self-Hosting Track

The target is for Kotoba's language and admission semantics to move into Kotoba
itself. The measure is the one `ADR-safe-capability-language` §0.1 sets — not
fewer host lines, but more slices whose **safety decision** is authored in
Kotoba.

**Read the state below before citing this section (corrected 2026-08-11).** It
previously described `kotoba-lang/kotoba:crates/kotoba-clj/selfhost/safe_analyzer.kotoba`
as implementing covered effect, minimal-policy, policy-check and admission-check
slices. That file went with the Rust crates and **no longer exists**; what
survived the removal is its classification tables, as
`kotoba-selfhost-contracts:resources/kotoba/selfhost/safe_analyzer_facts.edn`.
For a period they had no reader at all: `kotoba.selfhost.contracts` validates
seed *shape* and never classifies an op, so the CLI gates named here
(`selfhost-inspect`, `safe-policy`, `safe-build`) were checking that seeds are
well-formed, not that a Kotoba analyzer agrees with them. A fact list is not a
decision, and this section read as though it were.

Current evidence, by kind:

| Slice | Where | What it decides |
|---|---|---|
| Op classification | `kotoba-selfhost-contracts:kotoba/safe_analyzer_core.kotoba` | non-executable form / numeric result / effect op / user-call excluded — pure, `effects=#{}`, green on `:jvm-kir`, `:js`, `:wasm` |
| Effect algebra, minimal policy, policy check, admission | `kotoba-selfhost-contracts:kotoba/capability_admission_core.kotoba` | effect union / declaration check / unused-grant lint / minimal policy / effective scope / attenuation / deny ladder — pure, `effects=#{}`, green on `:jvm-kir`, `:js`, `:wasm` |
| Effect **inference** (the AST walk) | host | folding a call graph into a mask is a traversal over a collection. Mechanism, and host until the native gate takes collections |
| Seed shape | `kotoba.selfhost.contracts` (CLJ) | that a seed is well-formed. **Not** self-hosting evidence |

Both Kotoba slices keep the authority split that makes the claim checkable: the
EDN owns *which* ops are in a class, the `.kotoba` owns *what it means* to be in
one, and an authority test refuses to let either move alone. In the admission
slice that binding extends to the bit ORDER — `effect-bit` assigns bit *i* to
index *i* of `:effect-ops`, so reordering that vector is a wire change and not a
formatting change. Cite a slice here only once something equivalent binds it.

Note the row that is deliberately not a Kotoba row. Splitting "effect inference"
into an algebra that moved and a traversal that did not is the honest reading;
recording the slice as done because the algebra landed would be exactly the
error this section was corrected for.

This repository tracks the path under `docs/lang/coverage.edn` `:selfhost`.
Remaining work is explicit: the effect-inference traversal (blocked on the
native gate taking collections, not on a decision), package lock enforcement in
safe-build, registry signature verification, repo RID validation through
kotoba-rad, capability values in the host ABI, and broader compiler semantics
self-hosting.

## Contract maturity

- `M0`: constants and docs.
- `M1`: machine-readable profile.
- `M2`: positive conformance fixtures.
- `M3`: negative conformance fixtures.
- `M4`: manifest-driven conformance runner.
- `M5`: external implementation can consume the same suite.
- `M6`: profile-version compatibility policy.

This scale measures the bounded conformance contract. It does not measure the
documentation product, implementation release, operational reliability,
ecosystem adoption, or production SLOs. See
[`docs/maturity.md`](../maturity.md) for the separated assessment.

## Layering

- `kotoba-lang`: language semantics, admitted grammar, and conformance vocabulary.
- `kotoba-core-contracts`: source classification, package, and runtime-boundary contracts.
- `kotoba-cli`: public compiler surface: `kotoba -e` and `kotoba wasm ...`.
- `kotoba-clj`: compiler implementation crate and compatibility binary for the
  profile.
- `kotoba-runtime`: host/runtime for compiled components.
- `kotoba-datomic` and storage crates: data substrate.

The profile is kept in-repo for now. Split it into a separate repository only
when an independent compiler, runtime, or external conformance suite needs to
consume it outside this workspace.

## Foundational stdlib roadmap

The profile (source contract) is mature at `:m6`, and the **horizontal
foundational stdlib** that the vertical `*-clj` libs (`langchain`, `langgraph`,
`statechart`, `num`, …) assume is now complete: 12 foundational + 3 composite
consumer libraries, all at **v0.1.0** and **M6**, all PUBLIC and CI-green under
the `kotoba-lang` org, in the same zero-dep `.cljc` + host-injected pattern as
`dsl-core` / `statechart` / `num`:

- **Layer 1 — data**: `coll`, `spec`, `json` (P0)
- **Layer 2 — cap/effect**: `wit` (WIT bindings + capability tokens), `async`
  (CSP channels, bounded) (P0)
- **Layer 3 — I/O**: `time`, `fs`, `http`, `io` (capability-tokenized,
  host-injected) (P1)
- **Layer 4 — tooling**: `test` (property), `fmt`, `lsp` (P2)
- **Composite consumers**: `scheduler` (←async/time/coll), `store`
  (←fs/io/wit/coll), `lint` (←fmt/lsp/fs/coll)

Each lib is capability-parameterized (never direct-OS), plugs into the
existing `effects.rs` / `policy.rs` deny-by-default boundary, and carries its
own semver separate from `:kotoba.lang/profile-version` (the profile stays 1).
Per-lib M0–M6 maturity and the full catalog are tracked in
`docs/lang/coverage.edn` under `:stdlib` (track at `:m6`); the semver/compat
policy is in `docs/lang/stdlib-versioning.md`; the gate set is in
`docs/lang/stdlib-gates.md`; the decision and rationale (comparison vs Go /
Python / Rust / Deno / TS) are in
`docs/adr/ADR-kotoba-lang-foundational-stdlib.md`.

**M5 consumer provenance**: every consumable leaf lib has a confirmed
external consumer — `json` ← `http` and `langchain` (a real vertical), `spec`
← `test`, `async`/`time`/`coll` ← `scheduler`, `fs`/`io`/`wit` ← `store`,
`fmt`/`lsp` ← `lint`. `registry` is deferred to the `:packages` CID-lock
track.

**Standalone run (T6.1):** [`standalone-run.md`](./standalone-run.md).
