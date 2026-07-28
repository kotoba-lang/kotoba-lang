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
- **2026-07-28 murakumo#62:** `report_core` extended with remaining pure ops lines.
- **2026-07-28 murakumo#61:** `infer_rebalance_core` 3-pool `largest-remainder-3` map-fold oracle (first beyond scalars).
- **2026-07-28 murakumo#63:** `pool-demand-pack` + `seats-from-pool-pack` + `classify-run-flags` map-fold compose.
- **2026-07-28 murakumo#64:** `demand-inc` class-pack reduce + `demand-to-pool-pack` (demand-from-runs fold).
- **2026-07-28 murakumo#65:** placement pure layer — workers/seats-for-online/hysteresis/node-online?/move-needed/reason.
- **2026-07-28 murakumo#66:** seat-order assignment — seat-order-pack/take-end/pipeline-note/reason-detail.
- **2026-07-28 murakumo#67:** `infer_plan_core` plan-lr-3 + fits gates + layer-bytes (partition vertical).
- **2026-07-28 murakumo#68:** `infer_plan_core` partition-layers integer walk — `partition-3-ends` / `advance-hi` / `layer-byte-at` (3-node ring).
- **2026-07-28 murakumo#69:** `plan-fits-3` compose + `ok-mark` + moe `pick-max-idx-3` / `moe-capacity-ok`.
- **2026-07-28 murakumo#70:** `infer_engine_core` mlx-moe/launch/tensor-split-3 string pure oracle.
- **2026-07-28 murakumo#71:** `infer_engine_core` head-cmd-front/middle/tail + rpc-csv (llama-server head assembly).
- **2026-07-28 murakumo#72:** plan report GiB milli/floor + `report_core` pad-right/nodes-header/status-header.
- **2026-07-28 murakumo#73:** schedule pick pure — pick-idx-2-full / pick-idx-3-tournament / warm+score.
- **2026-07-28 murakumo#74:** `report_core` command-help + reconcile-lines pure fragments (title/row/detail/reach/drift).
- **2026-07-28 murakumo#75:** reconcile pick-targets pure — first-of-2/3 + pick-targets-2-pack (load×name).
- **2026-07-28 murakumo#76:** schedule `assign-step-2` + task `task-eligible?` / fill-milli / wave-slot pure assign cores.
- **2026-07-28 murakumo#77:** `infer_plan_core` n≠3 plan maps — partition-1/2-ends + plan-fits-1/2 + asg-row-pack (host attaches node ids).
- **2026-07-28 murakumo#78:** task expand `task-id`/assign-task-step-2 + credits multi-unit/share-floor pure.
- **2026-07-28 murakumo#79:** schedule `assign-step-3` / `assign-pick-3` / `apply-pick-3` pure 3-node job batch step.
- **2026-07-28 murakumo#80:** n>3 host-fold — `partition-step`/`partition-last`/`fits-and` + `pick-fold-step`/`queue-inc-if`.
- **2026-07-28 murakumo#81:** task assign-step-3/summary + dash take-last/cap index pure.
- **2026-07-28 murakumo#82:** host-shell pure — report `nodes-row`/`status-row`/`pad-to` + tunnel `parse-digits`/conn-opts strings.
- **2026-07-28 murakumo#83:** token wire pure — `encode-claims-json` / `wire-token` / `constant-time-eq` (HMAC host remains).
- **2026-07-28 murakumo#85:** identity JWT/op-token templates + `overlay_crypto_core` packaging (alg/nonce/tag/fields/b64 pad); AES-GCM seal/open host.
- **2026-07-28 murakumo#86:** **product-shell oracle authority** (first dual-source cutover) — `murakumo.kekkai.gate` JVM public API delegates pure helpers to precompiled KIR (`resources/murakumo/oracle/kekkai_gate_core.kir.edn`) via `murakumo.kotoba.oracle` + `kotoba-kir`/`ir/execute`. Compiler stays test-only; CI drift gate regenerates with `murakumo.kotoba-oracle-gen`. ADR `ADR-260728-w6-product-shell-oracle-authority`.
- **2026-07-28 murakumo#87:** live HMAC + AES host adapters — token pure wire (`encode-claims-json`/`signing-input`/`wire-token`/`constant-time=`) on live `sign`/`verify`; overlay.crypto packaging gates on `open` (AES-GCM Cipher stays host).
- **2026-07-28 murakumo#88:** **product-shell oracle authority (token)** — `murakumo.token` JVM pure helpers delegate to `resources/murakumo/oracle/token_core.kir.edn` via `:token` catalog; HMAC/b64url host remains.
- **2026-07-28 murakumo#89:** **product-shell oracle authority (report)** — `murakumo.report` JVM pure helpers (headers/pad/rows/`command-help`/reconcile pure builders/constants) delegate to `resources/murakumo/oracle/report_core.kir.edn` via `:report-core` catalog; host remains map projection + CSV joins + reconcile mapcat. ADR `ADR-260728-w6-report-oracle-authority`.
- **2026-07-28 murakumo#91:** **product-shell oracle authority (infer.plan)** — JVM `GiB`/defaults/`usable-bytes`/`choose-strategy` name delegate to `resources/murakumo/oracle/infer_plan_core.kir.edn` via `:infer-plan`; partition walk stays cljc.
- **2026-07-28 murakumo#93:** **product-shell oracle authority (dash.state)** — JVM `short-hosted-cid` / `health-class` / `interval-sleep-ms` / `clamp-at` / `append-capped` start / `recent-alerts` n delegate to `resources/murakumo/oracle/dash_state_core.kir.edn` via `:dash-state`; HTML join, probe parse, map folds stay cljc.
- **2026-07-28 murakumo#94:** **product-shell oracle authority (infer.schedule)** — JVM `eligible?` / `score` / assign queue-inc delegate to `resources/murakumo/oracle/infer_schedule_core.kir.edn` via `:infer-schedule`; set projection + stable sort-by pick stay host.
- **2026-07-28 murakumo#95:** **product-shell oracle authority (task.plan)** — JVM `slots` / `failed?` / `eligible?` flags / `task-id` / retry bounds / wave·slot / percentile idx / summary retried·speedup delegate to `resources/murakumo/oracle/task_plan_core.kir.edn` via `:task-plan`; admit/prepare folds + sort-by stay cljc.
- **2026-07-28 murakumo#96:** **product-shell oracle authority (infer.engine)** — JVM `rpc-server-cmd` / `endpoint` / `head-cmd-*` / mlx/embed fragments delegate to `resources/murakumo/oracle/infer_engine_core.kir.edn` via `:infer-engine`; plan walks + CSV join stay host.
- **2026-07-28 murakumo#98:** **product-shell oracle authority (secret)** — JVM name/env constants + `valid-env-var-name?` + POSIX `valid-path-ref?` delegate to `resources/murakumo/oracle/secret_core.kir.edn` via `:secret`; env/map/kagi fetch + System.getenv stay host.
- **2026-07-28 murakumo#99:** **bulk product-shell catalog** — all 32 `kotoba/*_core.kotoba` ship as `resources/murakumo/oracle/*.kir.edn`; auto-discover gen; host-wired secret + overlay.crypto packaging; remaining hosts wire incrementally.
- **2026-07-28 murakumo#100:** **product-shell oracle authority (tunnel + config)** — JVM `murakumo.tunnel` pure conn-opts/wrap-cmd/parse-rc digits/scp-dest/forward/curl and `murakumo.config` path builders delegate to `tunnel_core.kir.edn` / `config_core.kir.edn`; SSH argv assembly, EDN I/O, env folds stay host. ADR `ADR-260728-w6-tunnel-config-oracle-authority`.
- **2026-07-28 murakumo#101:** **product-shell oracle authority (reconcile.plan)** — JVM `desired` / `deficit` / `action-name` / `watch-sleep-ms` delegate to `reconcile_plan_core.kir.edn`; eligible/observed set algebra + variable pick-targets sort stay host. ADR `ADR-260728-w6-reconcile-oracle-authority`.
- **2026-07-28 murakumo#102:** **product-shell oracle authority (fleet.inventory)** — JVM `node-port` / `health-url` / selector predicates / offline-line delegate to `fleet_inventory_core.kir.edn` via `:fleet-inventory`; enrich/named vector folds stay host. ADR `ADR-260728-w6-fleet-inventory-oracle-authority`.
- **2026-07-28 murakumo#103:** **product-shell oracle authority (identity + credits)** — JVM seed preimages/JWT templates/did helpers via `identity_core.kir.edn`; credits defaults/memory-time-weight/charge-allow? via `infer_credits_core.kir.edn`. SHA-256/b64url + float settle folds stay host. ADR `ADR-260728-w6-identity-credits-oracle-authority`.
- **2026-07-28 murakumo#105:** **product-shell oracle authority (infer.join + infer.gc)** — JVM join tier max-resident/can?/needs-relay?/clamp/eligible via `infer_join_core.kir.edn`; gc GiB/defaults/need/free/target/comfy via `infer_gc_core.kir.edn`. Partition/plan folds stay host. ADR `ADR-260728-w6-join-gc-oracle-authority`.
- **2026-07-28 murakumo#107:** **product-shell oracle authority (moe + rebalance + relay)** — JVM capacity-default/expert-ratio/verdict/resident via `infer_moe_core.kir.edn`; usable-gb + largest-remainder-3 via `infer_rebalance_core.kir.edn`; make-id/lease-expired?/msg kinds via `infer_relay_core.kir.edn`. Custom tiers + pool placement + queue maps stay host. ADR `ADR-260728-w6-moe-rebalance-relay-oracle-authority`.
- **2026-07-28 murakumo#109:** **product-shell oracle authority (persist)** — JVM constants/rkeys/repo-uri/url/write-ok? via `persist_core.kir.edn`; envelope maps + graph-cid stay host. ADR `ADR-260728-w6-persist-oracle-authority`.

## Next

1. murakumo pure+adapter path **#61–#109** — **full KIR catalog shipped** (32 artifacts); most pure hosts wired. Remaining catalog-only: overlay-*/deploy/connect/cloud/provision/component-authority / Delivery 5–8 remaining shells / network·secret caps contract-only / cljs oracle load optional.
2. Cloudflare pure-request + deploy + parse + client cores landed (#1–#12); compat coerce/path oracle (#2).
3. kbb dual-runtime OS transports + git/entropy kits landed; pure capability allowlist reference-impl complete.
4. Full pure-planner cutover still dual-implemented until each core is wired like #86–#98 (or bulk generate); blocked on production compiler (avoid) / kbb in-process load (future optional).
