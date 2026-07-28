# ADR: Reliability T1.2 — conformance required-backends matrix

- Status: Accepted
- Date: 2026-07-28
- WBS: T1.2 (`docs/kotoba-reliability-parity-wbs.md`)

## Context

T1.1 landed prose semantics SSoT. R1 exit requires that pure-product cases
fail the language gate if **any required backend** fails — not “works on KIR
only.” Before dual-backend runners (T1.3), the manifest must declare the
matrix machine-readably.

## Decision

Bump `lang/conformance/manifest.edn` to **version 2** with:

| key | role |
|---|---|
| `:backends` | catalog of backend ids (`:kir`, `:wasm32-kotoba-v1`, host, native, admit) |
| `:case-classes` | class → required/optional backends + profile |
| per-case `:class` | class membership |
| per-case `:required-backends` | explicit set (case-level authority) |

### Pure-product bar

Classes `:pure-product-run` and `:compile-expr` require:

```text
#{:kir :wasm32-kotoba-v1}
```

Host-compat classes (`:host-reader-target`, `:host-entry-extension`) are
**separate** — multi-target expect keys are not a pure-product loophole.

Negative cases require `#{:compiler-admit}` only.

### Library

`kotoba.lang.conformance-matrix` loads and validates the matrix. Execution
across backends remains **T1.3**.

## Non-claims

- Does not implement dual-backend runner (T1.3)
- Does not run wasm or native in this PR
- Does not change guest semantics

## Evidence

- `lang/conformance/manifest.edn` v2
- `test/kotoba/lang/conformance_matrix_test.clj`

## Related

- `docs/lang/semantics-ssot.md` §8
- WBS T1.2–T1.3
