# ADR — kotoba-lang language profile artifacts

- **Status**: Accepted
- **Date**: 2026-06-29
- **Artifacts**: `lang/profile.edn`, `lang/conformance/`,
  `lang/version-policy.edn`, `lang/docs-release.edn`
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

## Addendum (2026-07-08): profile v3 reinstates `.cljs`

Profile v2 (2026-07-02, commit `a11b7eb9`) retired `.cljs` as a dedicated
source extension on a thin equivalence argument alone (`.cljc` +
`#?(:cljs ...)` already covers what a dedicated `.cljs` file would), with no
documented analysis of the porting friction that imposes on people who
already have `.cljs` code and want to try Kotoba without renaming files
first. Profile v3 reverses that: `.kotoba`, `.cljc`, `.clj`, `.cljs` are all
accepted source extensions again.

This is a widening, not a return to the pre-v2 shape:

- `.cljs` is added as a single-target compatibility extension with its own
  `:reader-branches ["cljs" "default"]` — the same shape `.clj` already has
  (`:reader-branches ["clj" "default"]`) — not the fully portable `.kotoba`
  branch chain `.cljc` gets. A `.cljs` file cannot carry `#?(:kotoba ...)`
  branches, mirroring how a `.clj` file cannot either.
- `:cljs` was never removed as a reader *target*: `.cljc`'s
  `:reader-targets`/`namespace-extension-priority` already listed `:cljs`
  throughout v2 (a `.cljc` file was always readable under
  `--reader-target cljs`). This addendum only widens which file *extension*
  can carry that target directly, without going through `.cljc`.
- `namespace-extension-priority` gains `"cljs"` in all three reader-target
  entries: appended last for `:kotoba` and `:clj` (least relevant to those
  targets), inserted second — right after `"cljc"` — for `:cljs`'s own list,
  mirroring where `"clj"` sits in `:clj`'s own list.

Downstream: `kotoba-lang/kotoba-core-contracts`
(`resources/kotoba/lang/source_contract.edn`) gained a `:cljs` source-kind
mirroring `:clj`'s shape, and `kotoba-lang/kotoba` bumped its
`kotoba-core-contracts` pin and added `src/demo.cljs` as a genuine
end-to-end positive fixture (a bare `.cljs` file, accepted directly,
defaulting to the `:cljs` reader target with no `--reader-target` flag
needed).

## Addendum (2026-08-11): profile 6 is release-bound in v0.7.0

Profile maturity (M0–M6) and documentation maturity (D0–D6) are separate
axes. M6 means that a machine-readable profile has compatibility policy and
executable gates. It does not by itself mean that public documentation names a
shipped implementation and independently verifiable artifact. The latter is
D5, and requires an exact release binding.

Profile 6 is the first Kotoba profile accepted under that D5 release contract.
`lang/version-policy.edn` fixes profile 6 and package contract 1 as the public
callable contract. `lang/docs-release.edn` records the promoted platform and
binds the documentation to all of the following evidence:

- release `v0.7.0` and implementation commit
  `6d2ad543f48391b91bec63b50a7fdb7ba8fe8828`;
- implementation tree
  `d78ec08ff0e7d16f8b774b9aa7f2c8ff6a7c431e`;
- darwin-arm64 native binary SHA-256
  `7f294d0c63695d921643cf87435b12c9f8b9cc329a3084605c2ef6432c0368da`;
- archive SHA-256
  `e9d8186c4e54aa95e53e56877a794dcd890c6b296a6e5bd2bfd9cccc8ce0638c`;
- evidence JSON SHA-256
  `71ee59b5c6ab2704cc9e26632dc5350b285f4a992e63fc7cb34bf47da30b7079`;
- signed envelope SHA-256
  `66f6368dabfea6b6a842fb6fa10d261e4e3545a3667ec227740c26a0433b4f2e`;
- release signer
  `did:key:z6MkgtjFR4xwQtb4ZqGg5N8NCpT8fNd4HfjznLamf9oZhmRs`;
- a green release result of 536 tests, 8,580 assertions, zero failures, and
  zero errors.

The release was downloaded again from the public release, its checksum and
signature were verified, the executable was confirmed as Mach-O arm64, the
self-host suite passed 17/17, and the smoke program returned 42. Public docs
were generated from the binding and deployed with content SHA-256
`bf85d618782d411c4d91801bbffa6f4a374fca357b0bfd5a0301046092e04c64`.
The superproject pins for `kotoba`, `kotoba-lang`, and `release` were verified
as default-branch-reachable fast-forwards and reconciled into fleet-db; the
resulting projection is clean at root merge commit
`16fc5bf2b4665be824a158949f505c900bf3d893`.

This closes D5 only for profile 6 / package contract 1 / darwin-arm64. Linux,
Windows, and darwin-amd64 remain unbound and must not inherit the released
claim. D6 also remains open until at least three external users complete the
documented tasks and their failures and completion evidence are recorded.
Kotoba therefore has a release-grade evidence path, but does not yet claim the
ecosystem breadth, multi-platform release coverage, independent-user history,
or long-term compatibility record of Rust, Go, or Deno.

Implementation and publication landed through `kotoba-lang/kotoba-lang` PRs
#414 and #418, `kotoba-lang/kotoba` PRs #459, #461, #463, and #464,
`kotoba-lang/release` PR #1, and `com-junkawasaki/root` PR #1974.
