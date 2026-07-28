# W6 Murakumo path inventory (first slice)

Status: accepted path-level first slice (2026-07-28)  
Machine-readable: [`lang/w6-murakumo-path-inventory.edn`](../lang/w6-murakumo-path-inventory.edn)  
Parent: [`docs/w6-migration-inventory.md`](w6-migration-inventory.md)

## Why murakumo first

W6 inventory v1 marked `orgs/kotoba-lang/murakumo` as **`blocked-by-provider`**
with product verticals **state / LLM / governor / checkpoint**. This document
classifies concrete source paths so cutover picks pure planners first and leaves
SSH/nbb shells on the host.

## Vertical map

| vertical | pure core (cutover candidates) | host / ops shell |
|---|---|---|
| **State / dashboard** | `dash/state.cljc`, `persist.cljc` | `dash.clj`, `ops.cljs` (nbb) |
| **LLM / infer** | `infer/plan.cljc`, `infer/engine.cljc`, schedule/credits/join/gc/moe/relay pure | `infer.clj`, gateway, relay_server/worker, media, orchestrate |
| **Governor** | `kekkai/gate.cljc`, `token.cljc`, `identity.cljc` | `kekkai.clj` |
| **Checkpoint / tasks** | `task/plan.cljc` | `task.cljs`, `task/exec.cljs`, `task/worker.cljs` |
| **Fleet control** | `deploy/plan.cljc`, `reconcile/plan.cljc`, `fleet/inventory.cljc`, `config.cljc` | `core.clj`, `ssh.clj`, `fleet.clj`, overlay drivers |

## First cutover slice (recommended)

**`murakumo-pure-planners-v1`** — oracle parity only, no SSH move:

1. `infer/plan.cljc` + `infer/engine.cljc`
2. `task/plan.cljc`
3. `kekkai/gate.cljc`
4. `dash/state.cljc`
5. `token.cljc` (already dual-runtime pure; shared with cloud-murakumo)

Method: existing cljc tests as oracle → `.kotoba` pure definitions → EDN/plan
fixture equality → shells remain on bb/nbb/JVM host.

## Explicit non-goals (this slice)

- Moving `ssh.clj` / Tailscale transport into Kotoba
- Replacing nbb `ops.cljs` / task workers (blocked on kbb abilities)
- Cloudflare Worker deploy of murakumo control plane
- Production LLM endpoint orchestration inside murakumo (uses provider llm kit)

## Blockers by vertical

| vertical | primary blocker |
|---|---|
| state-dashboard | state kit production + atproto/storage host |
| llm-infer | llm production transport + object weights/artifacts |
| governor-admission | kekkai ledger host + authorization provider surface |
| checkpoint-task-batch | state audit log + object result blobs |
| fleet-control-plane | kbb process/ssh + optional HTTP ingress APIs |

## Progress

- **2026-07-28 murakumo#37:** `kotoba/kekkai_gate_core.kotoba` oracle parity for gate string core.
- **2026-07-28 murakumo#38:** `kotoba/infer_plan_core.kotoba` usable-bytes + choose-strategy parity.
- **2026-07-28 murakumo#39:** `kotoba/dash_state_core.kotoba` short-hosted-cid / health-class / clamp-at / interval-sleep-ms parity.

## Next

1. Remaining pure-planner oracles: `task/plan`, `token`, `infer/engine`.
2. Cloudflare route inventory (sibling W6 next-action).
3. kbb ability gap list for nbb shells (`ops`, `task/*`).
