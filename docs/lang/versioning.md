# Kotoba Language Profile Versioning

`kotoba-lang` uses two related version numbers:

- `:kotoba.lang/profile-version` in `profile.edn`
- the Rust crate version inherited from the workspace

The profile version is the compatibility contract for source-processing tools.
The crate version is the packaging version.

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

## External Implementations

An implementation conforms to profile version 1 when it can consume
`crates/kotoba-lang/resources/kotoba/lang/conformance/manifest.edn`, run all
`:kind :run` source-file cases and `:kind :compile-expr` inline-expression cases
for the declared target set, and produce the declared errors for all negative
cases relevant to its admission mode.

Implementations may support a subset of targets only if they report unsupported
targets explicitly. Silent fallback to another target is non-conforming.

## Maturity Gate

M6 requires:

- constants and docs (`M0`)
- machine-readable profile (`M1`)
- positive fixtures (`M2`)
- negative fixtures (`M3`)
- manifest-driven runner (`M4`)
- external implementation suite contract (`M5`)
- this version/profile compatibility policy (`M6`)
