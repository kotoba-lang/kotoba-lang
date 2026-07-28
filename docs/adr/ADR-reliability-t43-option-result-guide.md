# ADR: Reliability T4.3 — option/result usage guide

- Status: Accepted
- Date: 2026-07-28
- WBS: T4.3

## Context

`if-some` / Product Value ABI v1 and stdlib prelude option/result helpers exist,
but contributors still reinvent sentinels. T4.3 asks for a **usage guide + golden**
pointing at the landed helpers (not new runtime APIs).

## Decision

1. Publish `docs/lang/option-result-guide.md` distinguishing:
   - language `[:option T]` + sugar (product pure path)
   - stdlib prelude `Some`/`Ok` helpers (prelude path / T4.1 names)
2. Add `examples/option-result/guide_golden.kotoba` mini golden.
3. Keep PVA `claim_sub.kotoba` as the CI-authoritative golden.

## Non-claims

- No new guest ops
- Does not implement T4.2 string-join/split
- Does not flip T1.3 full matrix

## Evidence

- guide + example + this ADR
- existing compiler PVA / pure-product tests

## Related

- ADR-product-value-abi-v1
- T4.1 stdlib manifest
