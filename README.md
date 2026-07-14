# Kotoba

Kotoba is a small Clojure-shaped language profile for compiling safe,
capability-checked programs to WebAssembly.

It is designed for code that should be inspectable, portable, and constrained:
AI-generated cells, sandboxed automation, repository policy, and other
untrusted programs where the host decides which capabilities are available.

## Why Kotoba?

- **Small source surface**: Kotoba source is a Kotoba/EDN subset with `.kotoba`
  as the canonical extension.
- **Clojure-family shape**: `.clj`, `.cljc`, and `.cljs` inputs are accepted
  as compatibility source formats, while Kotoba-specific behavior is
  selected with the `:kotoba` reader target — only reachable through
  `.cljc`'s wider branch chain, since `.clj` and `.cljs` are single-target
  compatibility extensions with their own reader-branch chain each (profile
  v3, reinstating `.cljs`; see `docs/lang/versioning.md`).
- **Implemented in Clojure ("Clojure on Clojure")**: the compiler, CLI, and
  conformance tooling that process this Clojure-shaped language are themselves
  written in Clojure/ClojureScript (`.cljc`). An earlier Rust implementation
  was fully retired in favor of this CLJC authority (see
  `docs/rust-migration-inventory.md`).
- **Wasm-first execution**: the public compiler surface is `kotoba -e` and
  `kotoba wasm ...`.
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
- **Conformance-oriented**: the profile is machine-readable and backed by
  fixtures so independent tools can agree on source behavior.

Kotoba is not "any JVM Clojure or ClojureScript program runs." It is a
Clojure-shaped profile with its own compatibility contract.

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
repository owns the language profile, source contract, CLI contract, and
conformance fixtures that the implementation consumes.

## Source Contract

- Accepted extensions: `.kotoba`, `.cljc`, `.clj`, `.cljs`.
- Canonical Kotoba-only extension: `.kotoba`.
- Portable Clojure-family extension: `.cljc`.
- Compatibility extensions: `.clj` (JVM Clojure) and `.cljs` (ClojureScript),
  each single-target with its own reader-branch chain (`["clj" "default"]`
  and `["cljs" "default"]` respectively) — neither carries `#?(:kotoba ...)`
  branches the way `.cljc` does.
- Default reader target: `kotoba`.
- `:kotoba` reader branch fallback order: `:kotoba`, then `:clj`, then
  `:default`.
- Namespace resolution priority for target `kotoba`: `.kotoba`, `.cljc`,
  `.clj`, `.cljs`.

Example portable source:

```clojure
#?(:kotoba (defn main [x] (+ x 10))
   :clj    (defn main [x] (+ x 1))
   :cljs   (defn main [x] (+ x 2)))
```

New Kotoba-only code should use `.kotoba`. Shared Clojure-family source should
use `.cljc` and place Kotoba-specific behavior behind `#?(:kotoba ...)`.

The machine-readable source contract is `lang/profile.edn` (profile version 3);
conformance fixtures live under `lang/conformance/`.

## Repository Scope

This repository is split from `kotoba-lang/kotoba` so the language surface can
be reviewed independently from the current compiler, runtime, server, and mesh
implementation.

The language and CLI authority lives here. Host implementations consume this
repository as data:

- `lang/profile.edn`: machine-readable source profile.
- `lang/conformance/`: conformance fixtures for source behavior.
- `lang/package.edn`: machine-readable package reference and lock contract.
- `lang/cli.edn` defines `run`, `check`, `db`, `git`, `rad`, `deploy`, and
  `hinshitsu` (software-quality checks: evidence, gates, coverage, mokushi
  visual regression — backed by `kotoba-lang/hinshitsu`).
- `lang/adapters.edn` defines adapter-owned CLI launchers and keeps native
  implementations outside the default language authority repo.
- `lang/lab.edn` defines the `kotoba-lab` notebook, cell, artifact, evidence,
  and capability vocabulary.
- `src/kotoba/cli.cljc` validates the contract, shapes argv as EDN, and returns
  host-neutral command results.
- `src/kotoba/lang/package_contract.cljc` validates package manifests and
  lockfiles against `lang/package.edn`.
- `docs/lang/`: profile maturity, gates, and versioning.
- `docs/adr/`: extracted language and repository ADRs.
- `examples/`: small source examples for docs and CLI smoke tests.
- Node, JVM, native, or other launchers are adapters. They should not define CLI
  protocol semantics independently.

**Out of scope, by design**: this repository does not define the "safe
Kotoba" compile-time admission grammar (subset/capability/effect gates —
T1 Memory Safety / T2 Effect Soundness / T3 Capability Confinement). That is
implemented in [`kotoba-lang/compiler`](https://github.com/kotoba-lang/compiler)
(the CLJC-native successor of `kotoba-lang/kotoba`'s historical Rust
`policy.rs`/`subset.rs`/`effects.rs`). `kotoba-lang/kotoba`'s README
previously misattributed that successor to this repository; see
`com-junkawasaki/root` ADR-2607141600.

## Current Status

`kotoba-git` and `kotoba-rad` are hosted by CLJC adapters in
`kotoba-lang/kotoba` (`kotoba.git-adapter`, `kotoba.rad-adapter`) that consume
`kotoba.cli/dispatch` planned results through injected host ports. No
independent native host implementation of these commands remains; the command
shape stays CLJC/EDN owned here.

## Maturity

The profile is tracked to M6:

- `M0`: constants and docs.
- `M1`: machine-readable profile.
- `M2`: positive conformance fixtures.
- `M3`: negative conformance fixtures.
- `M4`: manifest-driven conformance runner.
- `M5`: external implementations can consume the same suite.
- `M6`: profile-version compatibility policy.

Maturity evidence is recorded in `docs/lang/coverage.edn`; compatibility rules
are recorded in `docs/lang/versioning.md`; CI-facing commands are recorded in
`docs/lang/gates.md`.

## Verify

```sh
clojure -M:test
bb scripts/check-cli-contract.bb lang/cli.edn
bb scripts/check-package-contract.bb
bb scripts/check-capability-values.bb
bb scripts/check-legacy-runtime-absence.bb
```

`clojure -M:test` is the primary CLI and package contract gate. This repository
should not contain `Cargo.toml`, `Cargo.lock`, `.rs`, or Rust toolchain files.
