# ADR: T8.3 ops capability catalog — wire ids 19–23

- Status: Accepted
- Date: 2026-08-01
- Depends: provider kits ADR 0143–0151 (ids 19–23); guest host surfaces
  provider ADR 0260–0267; semantic catalog W2 (`lang/capability-catalog.edn`)

## Context

Ops kits have long used stable numeric wire ids on the provider side:

| Kit | Semantic name | Wire id |
|-----|---------------|---------|
| scoped-fs | `:fs/transact` | 19 |
| process | `:process/spawn` | 20 |
| secret | `:secret/get` | 21 |
| git | `:git/run` | 22 |
| entropy | `:entropy/draw` | 23 |

Guest packages (provider ADR 0260–0267) call those ids via
`(typed-cap-call <int> …)`. The language authority catalog only listed wire
ids **1–18**, so named forms such as `(cap-call :secret/get req)` were
rejected as unregistered. Provider ADR 0265/0267 explicitly deferred
**named catalog registration** to `kotoba-lang` authority.

## Decision

1. Extend `lang/capability-catalog.edn` (and the classpath copy under
   `resources/kotoba/lang/`) with entries 19–23 using the provider kit
   semantic names and matching wire ids.
2. Keep wire ids **contiguous and append-only** (tripwire test count 18 → 23).
3. Mark each entry `:source-status :friendly-qualified` (same pattern as
   `:http/post` / ingress). Host authority (spawn/fetch/store/git/CSPRNG)
   remains open by design — this registration does **not** flip
   `:wasm-aot :implemented`.
4. Compiler must vendor the same catalog + update the CLJS fallback map in
   `frontend.cljc` so JVM resource load and CLJS agree (separate PR on
   `kotoba-lang/compiler`).

## Non-goals

- Component-model WIT / `component-model-v1.edn` inventory expansion
  (still 1–18 for CM packaging; ops remain host-injected typed-cap-call).
- Flipping kit readiness `:wasm-aot` or production AOT claims.

## Evidence

- `test/kotoba/lang/capability_catalog_test.clj` — count 23, contiguous
  ids, ops name→id + source-operation assertions
- `catalog/validate!` uniqueness + shape checks

## Related

- Provider ADR 0260–0267 (guest host surfaces)
- `docs/agent-handoff-kotoba-refactor.md` T8.3 residual
- Compiler vendoring of `resources/kotoba/lang/capability-catalog.edn`
