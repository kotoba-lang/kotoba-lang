# ADR — kotoba-lang language profile artifacts

- **Status**: Accepted
- **Date**: 2026-06-29
- **Artifacts**: `lang/profile.edn`, `lang/conformance/`
- **Related**: `ADR-kotoba-wasm.md`, `ADR-safe-capability-language.md`

## Context

Kotoba source is intentionally Clojure-shaped, but the operational contract is
not "any JVM Clojure / ClojureScript program runs." The supported surface is a
Kotoba/EDN subset with `.kotoba` as the canonical Kotoba source extension, plus
portable `.cljc` for shared Clojure-family source and Kotoba-specific reader
branches selected with `#?(:kotoba ...)`.

Before this ADR, that source contract was documented in `kotoba-clj` and partly
encoded inside compiler compatibility code. That made `kotoba-clj` look like the
owner of both language semantics and compiler implementation.

## Decision

Create language-profile artifacts that are independent of the compiler
implementation and independent of Rust packaging. The language contract is the
EDN profile, conformance fixtures, and docs.

`kotoba-lang` owns:

- accepted source extensions: `.kotoba`, `.cljc`, `.clj`
- reader targets: `kotoba`, `clj`, `cljs`
- `.cljc` reader conditional branch order
- namespace source resolution extension priority

`kotoba-clj` remains the implementation: it compiles the `kotoba-lang` profile's
Kotoba/EDN subset to WebAssembly and applies safe Kotoba admission checks.

## Consequences

- `.kotoba` is the canonical Kotoba-only source extension.
- `.cljc` remains the portable sharing format. Kotoba-specific behavior belongs
  in `#?(:kotoba ...)`.
- Dedicated `.cljs` source files are retired from profile v2; ClojureScript
  reader behavior remains available inside `.cljc` through `#?(:cljs ...)`.
- Language profile constants are no longer duplicated inside `kotoba-clj`.
- The canonical profile and conformance suite are not nested under a Rust crate.
- The profile stays in the monorepo until an independent compiler, runtime, or
  external conformance suite needs to consume it outside this workspace.

## Maturity

The profile is tracked to M6:

- M0: constants and docs.
- M1: machine-readable `profile.edn`.
- M2: positive conformance fixtures.
- M3: negative conformance fixtures.
- M4: manifest-driven conformance runner.
- M5: external implementations can consume the same suite.
- M6: profile-version compatibility policy and CI-facing gates.

The maturity evidence is recorded in `docs/lang/coverage.edn`; versioning rules
are recorded in `docs/lang/versioning.md`; gate commands are recorded in
`docs/lang/gates.md`.
