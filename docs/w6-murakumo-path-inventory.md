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
- **2026-07-28 murakumo#40:** `kotoba/task_plan_core.kotoba` slots / failed? / can-retry? / defaults parity.
- **2026-07-28 murakumo#41:** `kotoba/token_core.kotoba` claims/scope/expired/signing-input parity.
- **2026-07-28 murakumo#42:** `kotoba/infer_engine_core.kotoba` rpc/embed cmd assembly + split-mode/endpoint parity.
- **2026-07-28 murakumo#43:** `kotoba/fleet_inventory_core.kotoba` resolve-port / health-url / selector predicates / offline-line parity.
- **2026-07-28 murakumo#44:** `kotoba/infer_schedule_core.kotoba` eligible?/score keys/compare parity (bit-packed flags).
- **2026-07-28 murakumo#45:** `kotoba/infer_join_core.kotoba` + `kotoba/infer_gc_core.kotoba` tier/relay/can/resident + gc policy math parity.
- **2026-07-28 murakumo#46:** `kotoba/config_core.kotoba` + `identity_core.kotoba` + `deploy_plan_core.kotoba` path/seed-preimage/argv parity.
- **2026-07-28 murakumo#47:** `kotoba/infer_credits_core.kotoba` + `reconcile_plan_core.kotoba` integer settle/charge + action-name parity.
- **2026-07-28 murakumo#49:** `persist_core` + `infer_moe_core` + `infer_rebalance_core` + `infer_relay_core` optional pure oracles.
- **2026-07-28 murakumo#51:** `connect_core` + `cloud_plan_core` + `provision_plan_core` low-priority pure oracles.
- **2026-07-28 murakumo#54:** `tunnel_core` + `report_core` ops-shell string oracles (SSH host-forever).
- **2026-07-28 murakumo#56:** `overlay_keyring_core` + `overlay_stream_core` + `overlay_runtime_core` pure oracles.
- **2026-07-28 murakumo#57:** `overlay_peer_core` + `overlay_driver_core` pure oracles.
- **2026-07-28 murakumo#59:** `component_authority_core.kotoba` identifier/epoch/sequence pure oracle.
- **2026-07-28 murakumo#60:** `secret_core.kotoba` name/env constants + env/path-ref policy oracle.
- **2026-07-28 murakumo#61:** `infer_rebalance_core` 3-pool `largest-remainder-3` map-fold oracle (first beyond scalars).

## Next

1. murakumo pure scalars complete; first map-fold (`largest-remainder-3`) landed (#61). Remaining: demand-from-runs / placement moves / crypto/host shells.
2. Cloudflare pure-request + deploy + parse + client cores landed (#1–#12); compat coerce/path oracle (#2).
3. kbb dual-runtime OS transports + git/entropy kits landed; pure capability allowlist reference-impl complete.
