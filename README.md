# Kotoba

Kotoba is a small, safety-first Clojure-shaped language profile for compiling
capability-checked programs through checked KIR to WebAssembly, native x86-64/
AArch64 machine code, and restricted JavaScript.

It is designed for components that should be inspectable, portable, and
constrained: AI-generated cells, sandboxed automation, repository policy,
applications, orchestrators, and capability providers whose external effects
are explicit and runtime-granted.

## Purpose and philosophy

Kotoba is designed first for software written, changed, tested, and operated by
AI agents and by people directing them. It supports autonomous coding and
vibe-coding workflows without treating generated code, tool requests, or
observed content as authority. Every program must cross explicit, deterministic
capability, effect, resource, package, and target-admission boundaries.

Safety takes priority over ambient convenience: unsupported behavior fails
closed, and a module can use only the authority it was explicitly granted. The
development loop is meant to stay fast and reproducible through machine-readable
contracts, checked KIR shared across qualified targets, bounded workers,
deterministic caches, target-aware tests, and signed build/execution receipts.
Performance claims are evidence for a named workload and host, never a universal
language ranking.

Cryptographic safety also has a precise scope. CID-pinned inputs, signatures,
trust/revocation policy, artifact seals, and receipts protect integrity and
provenance. Encryption, decryption, key use, and secret access require explicit,
purpose-bound capabilities and qualified providers; the language does not claim
that all source or runtime data is automatically encrypted.

Kotoba follows a **white-hat philosophy**: its intended uses are authorized
construction, defense, verification, repair, and auditable automation—not
bypassing controls or acquiring hidden authority. Intent alone is not a security
boundary; the compiler/runtime capability model remains the enforcement layer.

### Design influences

The influences are aspect-specific, not compatibility claims:

- **Lisp/Clojure**: data-oriented programs, immutable values, a small composable
  language, and interactive development.
- **Rust**: compile-time safety discipline and affine authority handling; Kotoba
  is benchmarked against Rust rather than copying its general ownership system.
- **Deno**: secure-by-default execution with explicit permissions.
- **Unison**: semantic, content-addressed definition identity.
- **Ethereum/CACAO**: signed identity, attenuated delegation, and verifiable
  authorization/receipt boundaries, without implying EVM compatibility.
- **IPFS**: CID-addressed, byte-verified discovery and distribution.
- **Nix**: pinned inputs, declarative environments, reproducible artifacts, and
  deploying the same admitted closure that was tested.

## Why Kotoba?

- **Small source surface**: Kotoba source is a Kotoba/EDN subset with `.kotoba`
  as the canonical extension.
- **Clojure-family shape**: `.clj` is Clojure, `.cljs` is ClojureScript,
  `.cljk` is CLJ Kotoba, and `.cljc` is common source across all three reader
  targets (`:clj`, `:cljs`, and `:kotoba`).
- **Implemented in Clojure ("Clojure on Clojure")**: the compiler, CLI, and
  conformance tooling that process this Clojure-shaped language are themselves
  written in Clojure/ClojureScript (`.cljc`). An earlier Rust implementation
  was fully retired in favor of this CLJC authority (see
  `docs/rust-migration-inventory.md`).
- **Multi-target execution**: [`kotoba-lang/amu`](https://github.com/kotoba-lang/amu)
  admits source once into checked KIR and emits Wasm/Wasm Components, restricted
  JavaScript, or sealed KEXE native artifacts for x86-64 and AArch64. Wasm
  Component remains the primary portable application profile; direct native AOT
  is a supported, explicitly selected backend.
- **Capability-safe tooling**: safe-policy, safe-build, and selfhost-inspect are
  part of the expected user-facing workflow. Safety is *benchmarked against*
  Rust, not copied from it: Kotoba's capability-confinement model
  (deny-by-default, explicit typed capabilities, signed audit receipts) is
  ranked on an explicit safety ladder above ordinary Rust-style ownership/
  borrow safety (see `docs/adr/ADR-safe-capability-language.md`). A general
  Rust-style borrow/lifetime system over every value was deliberately NOT
  built — T1 Memory Safety is already achieved without one. What shipped
  instead is a narrow slice scoped only to capability-typed values
  (deterministic drop, no implicit clone: a capability handle may be
  consumed at most once per execution path — `kotoba.runtime/cap-affine-
  problems` in `kotoba-lang/kotoba`, `:cap-value-reused`), which is what the
  ADR's own safety ladder actually calls for.
- **Verified native execution without a Wasm dependency**: Amu emits machine
  instructions directly without an assembler, LLVM, JVM JIT, or Wasm runtime.
  Its independent verifier regenerates and compares the sealed code;
  [`tender-native`](https://github.com/kotoba-lang/tender-native) verifies signed
  KEXE trust/policy, maps code W^X, executes it in a supervised loader process,
  and produces a signed receipt. The supported native language/ABI slice is
  bounded and fails closed outside its qualified coverage.
- **Conformance-oriented**: the profile is machine-readable and backed by
  fixtures so independent tools can agree on source behavior.

Kotoba is not "any JVM Clojure or ClojureScript program runs." It is a
Clojure-shaped profile with its own compatibility contract.

The migration direction is **source preservation plus safety elaboration**:
ordinary Clojure-shaped values and functions stay readable, while the compiler
adds inferred effects, typed abilities, semantic definition identity, exact
imports, and resource bounds. Numeric capability IDs, WIT details, provider
callbacks, and host objects are not the application programming model. See
[`docs/kotoba-centered-migration-plan.md`](docs/kotoba-centered-migration-plan.md).
Root decision: `ADR-2607279200`.

## Component language, runtime roles, and safety qualification

`.kotoba` means canonical capability-safe **component source**, not
"guest-only" or "pure-only" source. A Kotoba component may implement domain
logic, orchestration, policy, an HTTP client, or a database provider, provided
all transitive effects are declared imports and no ambient authority is
introduced.

**Guest** and **host** are relative runtime roles. A component is a guest of
the runtime or provider supplying its imports; the same component is a host to
a downstream component whose imports it implements. For example, an HTTP
provider written in `.kotoba` can import scoped socket/TLS/clock capabilities
and export `http/get`. It does not gain network access from its extension.

`aiueos` decides scoped grants. `kototama` is the component tender/linker: it
admits already-emitted Wasm components, links declared imports and exports,
binds granted capabilities, and enforces resource limits. A minimal native TCB
still owns the Wasm engine, raw syscall bindings, root secret custody, and
grant-verification roots.

The normative role separation and provider examples are in
[`lang/component-role-model.edn`](lang/component-role-model.edn).
The portable platform ABI is WIT plus the WebAssembly Component Model with a
WASI 0.3 baseline; see
[`lang/wasm-component-platform.edn`](lang/wasm-component-platform.edn) and the
corresponding platform ADR. Async functions, futures, and streams are explicit
bounded effects, never ambient authority.

The normative terminology, end-to-end capability invariant, Deno/wasmCloud
comparison boundaries, and the reverse-topological qualification plan that
must precede fleet-wide CLJC migration live in
[`lang/safety-qualification.edn`](lang/safety-qualification.edn). In
particular, `kototama` is the runtime/tender analogue, not the whole wasmCloud
control plane; the accepted wasmCloud/wadm control-plane analogue is the
`murakumo` family.

Q1 safety claims and Q2 executable capability semantics are recorded in
[`lang/safety-claims.edn`](lang/safety-claims.edn) and
[`lang/capability-semantics.edn`](lang/capability-semantics.edn). Run
`bb scripts/check-safety-qualification.bb` to reject missing evidence,
and `clojure -M:grammar-authority` (or `nbb scripts/check-grammar-authority.cljs`)
to reject guest-grammar / surface-status / vendor drift (W0),
capability-catalog drift, or production wildcard authority.

Q1-Q8 now pass for the bounded reference slice recorded in
`../kotoba/qualification/q8-report.edn`, including a CLJC-shadowed pure port,
a denied/allowed capability port, and guarded native OS-isolation conformance.
This is not fleet production maturity: fleet-wide migration remains
unauthorized and the CLJC oracle is retained.

Q9 is now authorized only for bounded Wave 1 tranches; Waves 2-5 and production
deployment remain unauthorized. The live 5-org path inventory, dependency
waves, per-repository gate, soak/rollback policy, and current status are in
[`lang/q9-migration.edn`](lang/q9-migration.edn) and
[`lang/q9-inventory.edn`](lang/q9-inventory.edn). Run
`bb scripts/check-q9-migration.bb` before changing a tranche. Inventory drift,
missing paths, duplicate paths, dependency-open waves, or weakened rollback
requirements fail closed.

Wave 1 preflight found 863 generated schema-DSL files using the bare
`.kotoba` extension. They are not canonical Kotoba programs and are tracked in
[`lang/q9-kotoba-extension-audit.edn`](lang/q9-kotoba-extension-audit.edn).
Tranche 1 moved ten of them to `.kotoba-schema` and updated their manifest
consumers, leaving 853 collisions. No CLJC consumer cutover is performed until
a bounded component/native-TCB split is extracted and oracle-qualified.

All ten Tranche 1 repositories now extract the same bounded page-limit
decision into a repository-local, zero-import `.kotoba` component. Each CLJC
function remains the oracle; clock/UUID/atom-backed CRUD remains in the
temporary native/compatibility adapter for this tranche. Future tranches may
move HTTP, database and other provider logic to `.kotoba` by declaring their
lower-level imports. Reference Wasm, compiler KIR, and each oracle agree with
no effects. Consumer cutover is still false until three signed green murakumo
receipts and seven days of soak are recorded for every repository.
Published murakumo fleet evidence is stored in
[`lang/q9-wave1-tranche-1-soak.edn`](lang/q9-wave1-tranche-1-soak.edn).
After the pilot revisions are committed and published, run
`bb scripts/collect-q9-soak.bb`; then use `bb scripts/check-q9-soak.bb` as the
fail-closed cutover gate. It requires three distinct successful `main` push
runs per repository, enrolled fleet signers, unchanged qualification Git
blobs, and 604800 elapsed seconds. Local preflight never counts as CI evidence.
Qualification dependencies use published immutable Git SHAs, not sibling
`local/root` paths, so every murakumo fleet gate can reproduce the test from a
standalone checkout. GitHub is only the immutable Git transport for the tested
tree and receipt ledger; GitHub Actions is not an evidence authority.

## Documentation

Start at [`docs/README.md`](docs/README.md). It provides separate routes for
learning the language, using the toolchain, implementing a backend, and
evaluating maturity. [`docs/authority-map.edn`](docs/authority-map.edn) records
which repository owns each normative contract; `nbb scripts/check-docs.cljs`
rejects broken checked links, missing routes, and profile-version drift.

## 30-Second Tour

Inline expressions compile through the same Kotoba-to-Wasm path:

```sh
kotoba -e '(+ 1 2)'
```

Build a Kotoba source file:

```sh
kotoba wasm build examples/hello.kotoba -o hello.wasm
```

Inspect and enforce a capability policy:

```sh
kotoba wasm safe-policy examples/policy-demo.kotoba
kotoba wasm safe-build examples/policy-demo.kotoba --policy policy.edn -o policy-demo.wasm
kotoba wasm selfhost-inspect examples/policy-demo.kotoba --policy policy.edn --json
```

The implementation launcher currently lives in `kotoba-lang/kotoba`. This
repository owns the admitted grammar, language semantics, CLI contract, and
conformance fixtures. `kotoba-lang/kotoba-core-contracts` owns source-file
classification and package/runtime boundary contracts.

## Source Contract

- Accepted extensions: `.kotoba`, `.cljc`, `.cljk`, `.clj`, `.cljs`.
- Canonical Kotoba-only extension: `.kotoba`.
- Portable Clojure-family extension: `.cljc`.
- CLJ Kotoba extension: `.cljk`; it uses the Kotoba/Kototama compiler and is
  not a JVM compilation target.
- Standard extensions: `.clj` (Clojure) and `.cljs` (ClojureScript),
  each single-target with its own reader-branch chain (`["clj" "default"]`
  and `["cljs" "default"]` respectively) — neither carries `#?(:kotoba ...)`
  branches the way `.cljc` does.
- Default reader target: `kotoba`.
- `:kotoba` reader branch fallback order: `:kotoba`, then `:clj`, then
  `:default`.
- Namespace resolution priority for target `kotoba`: `.kotoba`, `.cljc`,
  `.cljk`, `.clj`, `.cljs`.

Example portable source:

```clojure
#?(:kotoba (defn main [x] (+ x 10))
   :clj    (defn main [x] (+ x 1))
   :cljs   (defn main [x] (+ x 2)))
```

New Kotoba-only code should use `.kotoba`. Shared Clojure-family source should
use `.cljc` and place Kotoba-specific behavior behind `#?(:kotoba ...)`.

The machine-readable source-file contract is
[`kotoba-core-contracts/lang/profile.edn`](https://github.com/kotoba-lang/kotoba-core-contracts/blob/main/lang/profile.edn).
The broader admitted grammar and current surface live in
[`lang/guest-grammar.edn`](lang/guest-grammar.edn) and
[`lang/surface-status.edn`](lang/surface-status.edn); conformance fixtures live
under `lang/conformance/`.

## Repository Scope

This repository is split from `kotoba-lang/kotoba` so the language surface can
be reviewed independently from the current compiler, runtime, server, and mesh
implementation.

The language semantics and CLI authority live here. Host implementations
consume this repository and the lower-level core-contract repository as data:

- `lang/guest-grammar.edn`: machine-readable admitted language grammar.
- `lang/surface-status.edn`: implemented, partial, and deliberately excluded
  surface.
- `lang/conformance/`: conformance fixtures for source behavior.
- `kotoba-core-contracts/lang/profile.edn`: source-file classification.
- `kotoba-core-contracts/lang/package.edn`: package reference and lock contract.
- `lang/cli.edn` defines `run`, `check`, `db`, `git`, `rad`, `deploy`, and
  `hinshitsu` (software-quality checks: evidence, gates, coverage, mokushi
  visual regression — backed by `kotoba-lang/hinshitsu`).
- `lang/adapters.edn` defines adapter-owned CLI launchers and keeps native
  implementations outside the default language authority repo.
- `lang/lab.edn` defines the `kotoba-lab` notebook, cell, artifact, evidence,
  and capability vocabulary.
- `src/kotoba/cli.cljc` validates the contract, shapes argv as EDN, and returns
  host-neutral command results.
- `kotoba-core-contracts/src/kotoba/lang/package_contract.cljc` validates
  package manifests and lockfiles.
- `docs/`: audience routes, reference, maturity, language gates, and ADRs.
- `docs/kotoba-syntax-quality-review.md`: evidence-based syntax review and the
  prioritized path for improving ergonomics without weakening static safety.
- `docs/adr/`: extracted language and repository ADRs.
- `examples/`: small source examples for docs and CLI smoke tests.
- Node, JVM, native, or other launchers are adapters. They should not define CLI
  protocol semantics independently.

## Current Status

`kotoba-git` and `kotoba-rad` are hosted by CLJC adapters in
`kotoba-lang/kotoba` (`kotoba.git-adapter`, `kotoba.rad-adapter`) that consume
`kotoba.cli/dispatch` planned results through injected host ports. No
independent native host implementation of these commands remains; the command
shape stays CLJC/EDN owned here.

## Maturity

The bounded contract track is tracked to M6:

- `M0`: constants and docs.
- `M1`: machine-readable profile.
- `M2`: positive conformance fixtures.
- `M3`: negative conformance fixtures.
- `M4`: manifest-driven conformance runner.
- `M5`: external implementations can consume the same suite.
- `M6`: profile-version compatibility policy.

Maturity evidence is recorded in `docs/lang/coverage.edn`; compatibility rules
are recorded in `docs/lang/versioning.md`; CI-facing commands are recorded in
`docs/lang/gates.md`. This contract-stage label is not a claim of Rust/Go/Deno
documentation, ecosystem, adoption, release, or production-SLO maturity; those
axes are separated in [`docs/maturity.md`](docs/maturity.md).

## Verify

```sh
clojure -M:test
nbb scripts/check-docs.cljs
bb scripts/check-cli-contract.bb lang/cli.edn
bb ../kotoba-core-contracts/scripts/check-package-contract.bb
bb scripts/check-capability-values.bb
bb scripts/check-legacy-runtime-absence.bb
```

`clojure -M:test` is the primary CLI and package contract gate. This repository
should not contain `Cargo.toml`, `Cargo.lock`, `.rs`, or Rust toolchain files.
