# ADR: Reliability T6.1 — primary standalone run = wasmtime on wasm32

- Status: Accepted
- Date: 2026-07-28
- WBS: T6.1

## Decision

1. **Primary** pure standalone path: **wasm32-kotoba-v1 artifact + wasmtime**
   (or equivalent engine with the same import/fuel surface).  
2. **Secondary** native path: **kexe + kexe-loader** for measured freestanding.  
3. Documented in `docs/lang/standalone-run.md`.  
4. Does **not** make native the pure-product default until T1.4 lands.

## Related

- T6.3 tool vs runtime  
- T1.3/T1.5 pilot + goldens  
- compiler native-aot-baseline
