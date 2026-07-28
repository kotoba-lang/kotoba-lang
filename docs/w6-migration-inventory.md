# W6 Repository Migration Inventory (first slice)

Status: accepted first slice (2026-07-28)  
Machine-readable twin: [`lang/w6-migration-inventory.edn`](../lang/w6-migration-inventory.edn)

## Classification scheme

| class | meaning |
|---|---|
| `portable-pure` | No effects; eligible pure `.kotoba` oracle/cutover |
| `portable-effectful` | Effects via declared capabilities only |
| `host-mechanism` | Runtime/tooling/adapter; not product source of truth |
| `operational-command` | Deploy/ops CLI surface |
| `blocked-by-language` | Needs language/kbb surface not yet qualified |
| `blocked-by-provider` | Needs host kit/provider qualification first |

## Cohort A — Design system (complete)

Dependency order preserved: `css → html → shitsuke → liquid-glass-ui → kotoba-ui`.

Delivery-6 document-layer cutover is **5/5 complete**. Each repo keeps consumer
`.cljc` APIs; form-A oracles remain. Default class: **`portable-pure`** for
document emitters; remaining theme join / pretty-print / dual-render seams stay
**`host-mechanism`**.

## Cohort B — Language platform (not bulk-ported)

| repo | class | note |
|---|---|---|
| compiler | host-mechanism | W5 deepen through ADR 0142; guest examples are evidence |
| kotoba-kir | host-mechanism | IR + linear resource table |
| kotoba-component | host-mechanism | CM packaging / Wasmtime multi-step |
| abi | host-mechanism | Capability catalog |
| kotoba-wasm | host-mechanism | Wasm tooling |
| provider | host-mechanism | W5 kits (object/http/storage/llm/…) |

These are **authority hosts**, not W6 product file ports.

## Cohort C — Next product verticals (inventory only)

| repo | class | blocked on |
|---|---|---|
| murakumo | blocked-by-provider | state/LLM/governor/checkpoint product cutover |
| murakumo-studio | blocked-by-provider | follows murakumo |
| com-cloudflare | blocked-by-provider | HTTP ingress workerd qualification; then route semantics |
| com-cloudflare-compat | host-mechanism | adapter keep |
| kotoba-script | blocked-by-language | kbb fs/process/git/secrets/cloud abilities (keep nbb) |
| kami-engine-script-runtime | host-mechanism | adapter |

## Guest product evidence already on `.kotoba`

| path | class | ADR |
|---|---|---|
| `compiler/examples/w5-linear-let-move.kotoba` | portable-effectful | 0137 |
| `compiler/examples/w5-object-put-get-product.kotoba` | portable-effectful | 0140–0142 |

## Next actions

1. **Path-level murakumo inventory** — **landed**: [`docs/w6-murakumo-path-inventory.md`](w6-murakumo-path-inventory.md) / `lang/w6-murakumo-path-inventory.edn`.
2. **Murakumo pure-planner oracles** — high-priority complete (gate#37 / infer-plan#38 / dash-state#39 / task-plan#40 / token#41 / engine#42).
3. **Cloudflare route inventory** — separate route product from mechanism adapters after ingress soak.
4. **kbb ability gap list** — explicit blockers for kotoba-script ← nbb cutover.

## Migration process (unchanged)

Per plan W6: oracle → Kotoba path → same fixtures → shadow → compare → canary → soak → remove oracle only with immutable evidence.
