# Kotoba-centered migration plan

This is the executable work plan referenced by ADR-2607279200. The ADR is the
decision authority; this file records delivery order, evidence, and exit gates.
No legacy oracle is removed merely because a file was renamed to `.kotoba`.

## Invariants

- `lang/guest-grammar.edn` is the only source-surface authority. Compiler and
  launcher copies must be byte-identical and CI must reject drift.
- Normal source stays Clojure-shaped. Capability wire IDs, WIT imports,
  portable-effect envelopes, and provider callbacks are compiler/host details.
- Definition identity is computed from versioned elaborated typed KIR and
  direct definition-CID dependencies, never from a source-text hash alone.
- Effect rows describe requirements; only a host-bound, scoped ability grants
  authority. Admission and every external call fail closed.
- Existing CLJ/CLJS/CLJC/nbb implementations remain as oracles until every
  gate for their vertical slice is green.

## Delivery ledger

| Delivery | Exit evidence | State |
| --- | --- | --- |
| 1. One grammar authority | byte-identical compiler/launcher resources; normative drift test | implemented |
| 2. Generated capability artifacts | one semantic catalog generates numeric IDs, WIT inputs, and provider manifests; no handwritten fallback catalog | in progress |
| 3. One elaboration pipeline | one versioned receipt covers every stage in `lang/elaboration-pipeline.edn` and binds definition CIDs | in progress |
| 4. Recursive logical values | reader/printer round-trip, structural equality, persistent update, schemas, Document/Style fixtures | pending |
| 5. Host qualification | HTTP ingress, UI, state/storage, then LLM/stream pass Component and browser/workerd matrices | pending |
| 6. Design-system migration | css → html → shitsuke → liquid-glass-ui → kotoba-ui; logical-value and browser/stream render parity | pending |
| 7. Product vertical slices | Cloudflare, murakumo, and browser slices pass all seven gates | pending |
| 8. kbb replacement of nbb | typed fs/process/git/cloud host plus command lifecycle qualified | pending |

“Implemented” in this ledger means its exit evidence is mechanically checked.
Partial backend support must remain “in progress”.

## Vertical-slice gates

Every migration slice owns one evidence record containing:

1. **source fidelity** — ordinary immutable values/functions fit the authority
   grammar without ambient interop;
2. **semantic parity** — reference KIR, restricted ESM, and selected Wasm hosts
   return the same conformance vectors;
3. **safety** — inferred effects, denial, attenuation, quota, deadline,
   revocation, provider revalidation, and receipts are tested;
4. **host parity** — browser/workerd/kototama adapters implement the same typed
   request/result contract;
5. **performance** — time, allocation, and artifact-size budgets are recorded;
6. **release** — compiler/profile/package/definition pins and reproducible
   SPDX/CID/signature receipts are present;
7. **rollout** — shadow/oracle comparison, rollback evidence, and soak results
   are complete.

The signed semantic build/test/deploy receipts in `kotoba-lang/kotoba` satisfy
part of the release gate. They do not by themselves satisfy safety, host
parity, performance, or rollout.

## Immediate work packages

### W1 — authority and drift

- Keep `lang/guest-grammar.edn` canonical.
- Embed exact copies only where a standalone artifact requires one.
- Verify the ADR ID, elaboration contract, and consumer bytes in tests.

### W2 — generated capability catalog

- Extend the semantic catalog from a set of kinds to stable semantic names,
  request/result schemas, effect names, WIT interface/function names, lifecycle
  bounds, and reserved numeric-ID history.
- Generate compiler JVM/CLJS lookup data and provider qualification manifests.
- Reject unknown, reused, or renumbered IDs and remove handwritten fallbacks.

### W3 — elaboration receipt

- Expose a single compiler entry point that returns stage versions, inferred
  types/effects, canonical typed KIR, direct dependency CIDs, and target
  evidence.
- Compute definition CIDs from that receipt’s canonical semantic payload.
- Bind SPDX, build/test/deploy receipts, cache keys, and provider discovery to
  the same elaboration and definition identities.

### W4 onward

Implement Deliveries 4–8 in order. A later package may prototype early, but it
cannot become the application authority before its dependencies and seven
vertical-slice gates are green.
