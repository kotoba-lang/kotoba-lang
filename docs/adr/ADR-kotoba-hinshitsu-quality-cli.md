# ADR: `hinshitsu` quality checks as a CLI contract command

**Status**: accepted
**Date**: 2026-07-01
**Deciders**: Jun Kawasaki

## Context

`kotoba-shell`'s Rust implementation hand-rolled a family of ad-hoc structs for
its dev-time checks: `SdkCheckStatus` (`Passed`/`Skipped`/`Failed`),
`CoverageAssessment`, `EvidenceCheckReport`, `EvidenceEntry`. Every check
(`sdk-check`, `runtime-check`, `doctor-check`, `release-check`,
`signing-check`, `submission-check`, `evidence-check`, `adapter-check`,
`coverage`, `coverage-check`, ...) reinvented the same "did this pass, what
was checked, what's the detail" shape in Rust-specific types, and none of them
covered visual/rendered-output regression — only structural/build/config
checks.

`kotoba-lang/hinshitsu` generalizes that ad-hoc shape into one portable
`.cljc` schema (`hinshitsu.evidence.v0`: `evidence` / `gate` / `coverage`) plus
a visual-testing module (`hinshitsu.mokushi`: capture + ImageMagick
`compare -metric RMSE` against a committed baseline). It has already been
validated against real device output — manimani's iOS simulator screenshots —
correctly distinguishing an identical frame (distortion 0.0, pass) from a
genuine UI state change (distortion 0.056, correctly fails a 0.02 threshold).

The public `kotoba` CLI (`lang/cli.edn`) is the data-first command contract
host adapters implement; `src/kotoba/cli.cljc`'s `required-commands` is a
closed set validated 1:1 against the contract (`validate-contract` fails if
the contract's command set and the CLJC authority's implemented set diverge —
see `test/kotoba/cli_test.cljc`). Quality/evidence/visual checks have no
command surface here yet; each host tool that wants them (kotoba-shell today,
future adapters) would otherwise re-invent its own ad-hoc flags.

## Decision

Add `:hinshitsu` as a seventh `:m1` command to `lang/cli.edn`, following the
exact shape of `:db`/`:git`/`:rad`/`:deploy` (a `:subcommands` vector mirrored
by an `--op` enum option, for adapters without nested-command support):

- **Subcommands**: `:evidence` (record/inspect a single evidence entry),
  `:gate` (aggregate evidence into one pass/fail decision, optionally
  requiring named checks), `:coverage` (implemented/partial/missing maturity
  assessment), `:mokushi` (visual/snapshot check: capture + baseline compare).
- **Options**: `--file` (EDN evidence/gate/coverage spec), `--op` (mirrors the
  subcommand), `--baseline` / `--candidate` / `--threshold` (mokushi visual
  comparison), `--required-check` (repeatable, feeds `gate`'s
  `:required-checks`), `--json` (matches every other command).

`src/kotoba/cli.cljc`'s `required-commands` gains `:hinshitsu`; like `:run`,
`:db`, `:git`, `:rad`, `:deploy`, its `command-result` falls through to the
generic `:command/planned` / `:adapter-required` branch — the CLJC authority
owns the contract and argv shaping, not the actual check execution (that
stays a host-adapter concern, e.g. kotoba-shell calling into
`kotoba-lang/hinshitsu` directly).

## Consequences

- `kotoba hinshitsu --op mokushi --baseline b.png --candidate c.png
  --threshold 0.02 --json` becomes a documented, contract-validated command
  shape any adapter (kotoba-shell's native launcher, a future Node/JVM
  adapter) can implement against, instead of each host inventing its own
  flags for the same evidence/gate/coverage/visual-diff operations.
- `test/kotoba/cli_test.cljc`'s hard-coded expectations move from 6 to 7
  commands and 33 to 40 options (`:hinshitsu` contributes 7: `input`,
  `operation`, `baseline`, `candidate`, `threshold`, `required-check`,
  `json`) — verified passing (`clojure -M:test`, 6 tests / 27 assertions).
- kotoba-shell's existing Rust check structs are not migrated by this ADR
  alone; that migration (replacing `SdkCheckStatus`/`EvidenceCheckReport`
  with `hinshitsu.core`/`hinshitsu.mokushi` calls, and wiring
  `kotoba shell` subcommands to the new `kotoba hinshitsu` contract) is
  follow-up work, not required for the contract to exist.

## References

- `kotoba-lang/hinshitsu`: `src/hinshitsu/core.cljc`, `src/hinshitsu/mokushi.cljc`.
- `lang/cli.edn`: the `:hinshitsu` command entry.
- `src/kotoba/cli.cljc`: `required-commands`, `command-result`.
- `docs/ADR-kotoba-shell-aiueos-safe-kotoba.md` (kotoba-lang/kotoba): the
  Rust check structs this generalizes (`SdkCheckStatus`, `CoverageAssessment`,
  `EvidenceCheckReport`, `EvidenceEntry`).
