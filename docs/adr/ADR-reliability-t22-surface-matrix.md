# ADR: Reliability T2.2 — generated surface matrix

- Status: Accepted
- Date: 2026-07-28
- WBS: T2.2

## Decision

Generate `docs/lang/surface-matrix.md` from `lang/surface-status.edn` via
`kotoba.lang.surface-matrix` (`clojure -M -m kotoba.lang.surface-matrix`).
`--check` fails CI-style when the markdown is stale.

## Related

- `lang/surface-status.edn`
- WBS T2.2 / T10.3 (changelog discipline uses the same source)
