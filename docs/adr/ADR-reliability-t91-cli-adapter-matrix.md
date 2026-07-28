# ADR: Reliability T9.1 — CLI adapter matrix

- Status: Accepted
- Date: 2026-07-28
- WBS: T9.1

## Decision

1. Keep `lang/cli.edn` as the public 8-command contract.  
2. Record host adapter readiness in `lang/cli-adapter-matrix.edn`.  
3. Validate with `clojure -M:cli-adapter-matrix` / unit tests.  
4. Promote `:check` contract tier to **M2** (compiler adapter + pure-product
   fixtures landed). Other commands remain M1/contract-only until adapters exist.  
5. Compiler extras (`test`, `fuel-estimate`, sign/receipt…) stay outside the
   8-id public set but are listed under `:compiler-extra`.

## Related

- T9.2/T9.3 check/test harness, `scripts/check-cli-contract.bb`
