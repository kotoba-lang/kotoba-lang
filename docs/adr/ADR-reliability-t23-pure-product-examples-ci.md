# ADR-reliability-t23: pure-product examples CI (T2.3 living contract)

- Status: Accepted
- Date: 2026-07-31
- WBS: T2.3

## Context

ADR 0188 closed the **conformance runner half** of T2.3 (pure-product profile
enforced on labelled T1.3 cases). The remaining half is a CI job that treats
`examples/` as a living contract: every pure-product example must compile under
`:language-profile :pure-product` and KIR-execute expected cases.

## Decision

1. **Manifest** `examples/pure-product-examples.edn` lists pure-product
   example paths and KIR cases (`:export` / `:args` / `:expect`).
2. **Runner** `kotoba.lang.pure-product-examples` compiles each source with
   `compile-source` + `{:language-profile :pure-product}`, then
   `kotoba.kir/execute` for each case.
3. **Alias** `clojure -M:pure-product-examples` (requires sibling
   `../compiler` checkout).
4. **CI** job `pure-product-examples` on ubuntu-24.04 clones compiler tip and
   runs the alias (fail closed).

## Evidence

```
KOTOBA_LANG_ROOT=. clojure -M:pure-product-examples
# T2.3 pure-product examples: 4 / 4 passed
```

Covered: `hello`, `policy-demo`, `option-result/guide_golden`,
`product-value-abi-v1/claim_sub` (10 KIR cases total).

## Non-claims

- Not wasm32 dual-backend in this job (KIR only; wasm remains T1.3 suite)
- Not automatic discovery of every `.kotoba` under `examples/` (manifest is
  the allowlist so non-pure demos stay out)
- Does not replace compiler pure-product unit tests

## Related

- Reliability WBS T2.3
- compiler ADR 0188 (runner half)
- `lang/pure-product-profile.edn`
