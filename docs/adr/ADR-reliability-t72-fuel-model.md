# ADR: Reliability T7.2 — fuel model documentation

- Status: Accepted
- Date: 2026-07-28
- WBS: T7.2

## Context

Semantics SSoT §6 deferred charge-rule detail to `docs/lang/fuel-model.md`.
Authors and multi-backend work need a single place for defaults and charge unit.

## Decision

Publish `docs/lang/fuel-model.md` documenting **current** behavior:

1. Default budget **512**, non-replenishable
2. Charge **1 unit per function entry** (KIR / wasm / js-kotoba-v1)
3. Trap `:fuel-exhausted` on exhaustion
4. Transform-helper bounds (e.g. 128) are orthogonal desugar limits
5. T7.1/T7.3/T7.4 remain open for tail/estimate/10k conformance

## Related

- `docs/lang/semantics-ssot.md` §6
- `kotoba-kir` / `kotoba-wasm` default-fuel
