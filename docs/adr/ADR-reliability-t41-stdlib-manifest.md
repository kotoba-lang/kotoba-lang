# ADR: Reliability T4.1 — frozen stdlib public module list

- Status: Accepted
- Date: 2026-07-28
- WBS: T4.1

## Context

Bounded stdlib exists (`lang/stdlib/core.kotoba` + `lang/stdlib.edn`) but the
**public name set** was only implicit in source. T4.1 requires a frozen,
machine-readable module list under `lang/conformance/stdlib/`.

## Decision

1. Add `lang/conformance/stdlib/manifest.edn` with:
   - module `:core` public names (every `defn` in core.kotoba)
   - records `Some`/`None`/`Ok`/`Err`
   - language-builtin string ops (pure-product profile; not prelude)
2. Mirror `core.kotoba` into `lang/conformance/stdlib/core.kotoba` (byte-identical)
   so the portable-source-stdlib conformance case can resolve its prelude path.
3. Tests: name set == source defns; sha256 matches package contract.

## Non-claims

- Does not ship T4.2 string kit expansions
- Does not change guest admission surface
- Does not execute dual-backend on stdlib prelude case (still admission-limited)

## Evidence

- `lang/conformance/stdlib/manifest.edn`
- `test/kotoba/lang/stdlib_manifest_test.clj`

## Related

- WBS T4.1–T4.3
- `lang/stdlib.edn`
