# Kotoba Language Profile Versioning

`kotoba-lang` uses three related version numbers:

- `:kotoba.lang/profile-version` in `profile.edn`
- `:kotoba.lang.package/version` in `package.edn`
- package/library semver in package manifests

The profile version is the compatibility contract for source-processing tools.
The package contract version is the compatibility contract for package
manifests, lockfiles, registry records, and dependency safety checks. Package
semver is a library compatibility signal and does not define source-profile
compatibility.

## Compatibility Rules

- Patch-compatible changes may add documentation, fixtures, or tests without
  changing accepted source behavior.
- Minor-compatible changes may add source forms or new positive conformance cases
  when existing valid source keeps the same meaning.
- Major/profile-version changes are required when an existing accepted source
  extension, reader target, reader branch order, namespace priority, or fixture
  expectation changes incompatibly.
- Removing a source extension or reader target is incompatible.
- Reordering reader fallback branches is incompatible.
- Reordering namespace extension priority is incompatible unless guarded by a new
  reader target or a new profile version.

Profile version 2 retires dedicated `.cljs` source files from the accepted
source-extension set. ClojureScript-targeted behavior remains expressible in
portable `.cljc` source through `#?(:cljs ...)` reader branches.

Profile version 3 reinstates `.cljs` as an accepted compatibility source
extension. The retirement in version 2 rested on a thin equivalence
argument (`.cljc` + `#?(:cljs ...)` already covers what a dedicated `.cljs`
file would) with no documented analysis of porting friction for people who
already have `.cljs` code and want to try Kotoba without renaming files
first; that friction is the reason for the reversal. `.cljs` is added as a
single-target compatibility extension mirroring `.clj`'s shape (its own
`:reader-branches ["cljs" "default"]`, not the fully portable `.kotoba`
branch chain), not as a return to full ClojureScript compatibility — see
`:kotoba.lang/authoring-surface` in `profile.edn` and
`docs/adr/ADR-kotoba-lang-profile.md`'s addendum. `:cljs` was never removed
as a reader *target* (`.cljc` could already be read under
`--reader-target cljs`); this only widens which file extension can carry it
directly.

## External Implementations

An implementation conforms to profile version 1 when it can consume
`lang/conformance/manifest.edn`, run all `:kind :run` source-file cases and
`:kind :compile-expr` inline-expression cases for the declared target set, and
produce the declared errors for all negative cases relevant to its admission
mode.

Implementations may support a subset of targets only if they report unsupported
targets explicitly. Silent fallback to another target is non-conforming.

## Package Contract

A package tool conforms to package contract version 1 when it can consume
`lang/package.edn`, accept lock entries with repo RID, manifest CID, tree CID,
signers, and capability grants, and reject version-only dependencies for safe
execution.

Changing package lock required fields, signature authority rules, or capability
grant semantics incompatibly requires a new package contract version. It does
not require a new `:kotoba.lang/profile-version` unless accepted source behavior
also changes.

## Maturity Gate

M6 requires:

- constants and docs (`M0`)
- machine-readable profile (`M1`)
- positive fixtures (`M2`)
- negative fixtures (`M3`)
- manifest-driven runner (`M4`)
- external implementation suite contract (`M5`)
- this version/profile compatibility policy (`M6`)
