# kotoba-lang stdlib Versioning and Compatibility

The kotoba-lang foundational stdlib is a set of independent libraries
(`coll`, `spec`, `json`, `wit`, `async`, `time`, `fs`, `http`, `io`, `test`,
`fmt`, `lsp`) under the `kotoba-lang` org. Each carries its **own** semantic
version, independent of the language profile version.

This document is the **M6** compatibility policy for the stdlib track — the
capstone that makes a library safe to depend on.

## Two version axes (do not confuse them)

- **`:kotoba.lang/profile-version`** (in `lang/profile.edn`) — the *source*
  contract (extensions, reader targets, namespace priority). This is the
  *language*, and its versioning rules live in `docs/lang/versioning.md`. The
  stdlib does **not** bump the profile version.
- **Per-library semver** — each stdlib repo's own `:version` in `deps.edn` and
  a `CHANGELOG.md`. This is the *library* contract. A consumer depends on a
  library by git SHA today; the version tags releases.

## Initial version

Every shipped stdlib library starts at **`0.1.0`** (`:version "0.1.0"` in
`deps.edn`, git tag `v0.1.0`). The `0.x` major means the public surface is still
settling: breaking changes are allowed within `0.x` but must be called out.

## What counts as a breaking change (MAJOR)

For `1.x` and later, a MAJOR bump is required when an existing accepted call
stops working or returns a different shape:

- removing or renaming a public var (anything in the lib's documented surface)
- changing a public function's arity or argument order
- changing the shape of a returned value a consumer could be destructuring
- adding a new required field to a returned map, or removing an existing one
- changing the semantics of an existing function (same args, different result)
- tightening a validation that previously accepted input (a value that
  `valid?`'d before now fails)

For `0.x`, these are allowed but must be recorded in the CHANGELOG and bumped
`0.1 → 0.2 → …`; once a lib reaches `1.0`, the MAJOR rule above binds.

## What counts as MINOR

A MINOR bump adds capability without breaking existing consumers:

- adding a new public var
- adding a new optional argument with a default
- adding a new field to a *returned* map that consumers are expected to ignore
  unknown keys for
- loosening a validation (accepting more input)

## What counts as PATCH

A PATCH bump fixes defects without changing the public contract:

- bug fixes that change output only for cases that were previously wrong
- performance improvements
- documentation, examples, test additions

## Compatibility guarantees

- A consumer that pins a library by git SHA is unaffected by any later version
  (immutable). Semver governs *upgrades*.
- The stdlib libraries depend on each other by **first-party git SHA**, not by
  version range, so an internal change in one lib cannot break a consumer
  unless they explicitly upgrade. Cross-library version drift is a manual
  concern at upgrade time, recorded in each consumer's CHANGELOG.
- `clojure.core` is the platform baseline; stdlib libs do not shadow
  `clojure.core` names except where documented (and the namespace docstring
  notes the `:refer-clojure :exclude`).

## CHANGELOG convention

Each stdlib repo keeps a `CHANGELOG.md` following [Keep a Changelog](https://keepachangelog.com/)
with the four sections: Added / Changed / Deprecated / Removed / Fixed. The
top entry is the unreleased or current-version section.

## Out of scope

- This policy does **not** cover the `:packages` (CID-lock) track — that lives
  in `docs/lang/package-rules.md` and `ADR-kotoba-package-cid-lock.md`.
- It does **not** set a release cadence; releases are cut when a lib's surface
  is stable enough to tag.
