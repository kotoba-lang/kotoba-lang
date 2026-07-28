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
| com-cloudflare | blocked-by-provider | path inventory landed; pure request/parse candidates; live token host |
| com-cloudflare-compat | host-mechanism | adapter keep (pure routes optional later) |
| kotoba-script | blocked-by-language | KIR→mjs backend; ops nbb until kbb gaps close |
| kami-engine-script-runtime | host-mechanism | adapter |

## Guest product evidence already on `.kotoba`

| path | class | ADR |
|---|---|---|
| `compiler/examples/w5-linear-let-move.kotoba` | portable-effectful | 0137 |
| `compiler/examples/w5-object-put-get-product.kotoba` | portable-effectful | 0140–0142 |

## Next actions

1. **Path-level murakumo inventory** — **landed**: [`docs/w6-murakumo-path-inventory.md`](w6-murakumo-path-inventory.md).
2. **Murakumo pure-planner oracles** — high-priority complete (gate#37 … engine#42).
3. **Cloudflare path inventory** — **landed**: [`docs/w6-cloudflare-path-inventory.md`](w6-cloudflare-path-inventory.md) / `lang/w6-cloudflare-path-inventory.edn`.
4. **kbb ability gap list** — **landed**: [`docs/w6-kbb-ability-gap.md`](w6-kbb-ability-gap.md) / `lang/w6-kbb-ability-gap.edn`.
5. **Cloudflare pure-request oracle** — high-priority string cores **complete** (#1 stream, #2 analytics+paths).
6. **Qualify process + scoped-fs kits** — **OS transports landed** (provider#24+#25); cljs remains.
7. **Secret-custody** — **contract first slice** (provider#26 id 21); ops CLI cutover remains.

## Migration process (unchanged)

Per plan W6: oracle → Kotoba path → same fixtures → shadow → compare → canary → soak → remove oracle only with immutable evidence.
