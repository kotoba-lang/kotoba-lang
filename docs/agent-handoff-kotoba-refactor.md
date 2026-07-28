# Agent handoff: Kotoba refactor (`.kotoba` migration)

**Authoritative superproject ADR:**  
`com-junkawasaki/root` → `90-docs/adr/2607289500-kotoba-refactor-agent-handoff-and-capability-repo-cid.edn`  
(policy root: `ADR-2607279200` Clojure-shaped + safety elaboration)

**Living plan:** [`docs/kotoba-centered-migration-plan.md`](./kotoba-centered-migration-plan.md)

This file is the **runbook** so a fresh agent can continue without chat history.

---

## 0. Read order (do not skip)

1. Superproject `ADR-2607279200` — source stays Clojure-shaped; no new app DSL
2. Superproject `ADR-2607289500` — handoff + capability CID boundary
3. This runbook + `docs/kotoba-centered-migration-plan.md` (especially **Next**)
4. Relevant inventory EDN under `lang/w6-*.edn`
5. If touching capabilities: `kotoba-core-contracts` README + `capability_repository.cljc`

---

## 1. Where we are (2026-07-28)

### Done (high level)

| Area | Status |
|---|---|
| W0–W1 grammar / named-op elaboration | done |
| Design-system form-A + Delivery-6 document cutover | **5/5** (css→…→kotoba-ui) |
| W5 kits dual-runtime + production transports | broadly done |
| W5 get-stream, linear resources, CM packaging | compiler ADR **0120–0136** |
| W5 guest move typing | **0137–0139** |
| W5 product verticals (put/get/CAS/conditional) | **0140–0142** |
| W6 inventories | murakumo / cloudflare / kbb gap |
| W6 murakumo pure-planners **high** | **6/6** (#37–#42) |
| W6 murakumo pure-planners **medium+** | **complete through #99** (#61–#99: **all 32 kotoba cores as KIR catalog** + host-wired high-traffic verticals + live HMAC/AES) |
| W6 cloudflare pure-request / deploy oracles | client / stream / analytics / deploy / pages (com-cloudflare) |
| W6 kbb ability gaps | **COMPLETE** (process / scoped-fs / secret / git / entropy / cloud-deploy; SSH host-forever) |
| W6 process / scoped-fs / secret / git / entropy kits | provider ADR **0143–0151** (ids 19–23; cljs OS transports landed) |
| Pure capability allowlist reference-impl | **COMPLETE** (sin / cos / sha256 / cbor / json / clock / random / now-days) |
| Network / secret capability packages | still **contract-only** (no production signed providers yet) |

### Plan Next (priority order)

1. **Incremental host wiring** of catalog-only cores (fleet/config/credits/overlay/tunnel/…) against shipped KIR (#99)
2. **Delivery 5–8 remaining shells** — continue wiring pure oracles into real entrypoints (not new DSL)
3. **Production signed providers** for network / secret capabilities (leave pure allowlist alone; signed content-addressed Wasm)
4. **wasm-aot packaging claims** — packaging / AOT path honesty vs remaining host shells

**Landed 2026-07-28:** product-shell KIR for all cores (#99 bulk catalog) + host-wired kekkai/token/report/plan/dash/schedule/task/engine/secret/overlay-crypto; live HMAC/AES (#87).

### Do not

- Invent a new application DSL
- Use legacy `kotoba wasm emit` as the language ceiling (use `kotoba compile`)
- Hardcode numeric capability IDs in app source
- Treat GitHub repo name or Radicle RID as import **identity**
- Ambient PATH / CWD / home for process/fs transports
- Secret/list/enumerate/keychain dump

---

## 2. How to cut over product pure cores (oracle pattern)

```text
inventory → kotoba/*_core.kotoba → cljc parity tests → leave host in cljc
         → update lang/w6-*-inventory.edn + migration plan Next
```

1. Classify path as `portable-pure` vs `host-mechanism` (inventory).
2. Port **only** pure string/integer/bitmask helpers to `.kotoba`.
3. Keep HTTP/SSH/spawn/DOM/crypto host on cljc/nbb.
4. Prove parity with deterministic fixtures (counts go in the PR).
5. Work in a **sibling worktree** outside the superproject; pin one west entry at a time.

Examples already landed:

- murakumo: `kotoba/kekkai_gate_core.kotoba`, `infer_plan_core`, `dash_state_core`, …
- com-cloudflare: `kotoba/stream_core.kotoba`, `analytics_core`, `workers_path_core`

---

## 3. Capability repos + semantic CID (remote, merged)

### Pins

| Repo | PR | Merge SHA |
|---|---|---|
| [kotoba-core-contracts](https://github.com/kotoba-lang/kotoba-core-contracts) | [#21](https://github.com/kotoba-lang/kotoba-core-contracts/pull/21) | `2e615d17406ef4b6311401167294becceb5af2a1` |
| [tamaki](https://github.com/kotoba-lang/tamaki) | [#10](https://github.com/kotoba-lang/tamaki/pull/10) | `660e70bfbb08423d1f498616a68d4d01ae2a39e5` |
| [kototama](https://github.com/kotoba-lang/kototama) | [#95](https://github.com/kotoba-lang/kototama/pull/95) | `74146a896dfae72b8eb407acf6f5717e0d2fd2e1` |

### Contract (do not weaken)

| Rule | Detail |
|---|---|
| 1 cap = 1 public repo | `capability-<id-with-dashes>` (e.g. `capability-http-fetch`) |
| Import identity | `:capability/definition-cid` = CIDv1 over canonical DAG-CBOR definition block |
| Discovery only | GitHub name + Radicle RID are **aliases** |
| Hash rules pinned | `:capability/hash-contract-cid` |
| No ambient authority | Knowing a CID ≠ grant; Tamaki request + Kototama admit + policy |
| Scaffold | `clojure -M -m scaffold-capability-repos /abs/out [--update]` from core-contracts |
| Generated status | `contract-only` until signed content-addressed Wasm component exists |

Definition block **includes:** schema, version, ABI ns/version, sorted imports/effects, defaultPolicy, artifactFormat, hashContract link.  
Definition block **excludes:** human name, repo path, RID, provider availability.

Tamaki emits repository refs with definition + hash-contract CIDs on the execution envelope.  
Kototama **rejects** definition-CID drift / substitution before HostCaps admission.

### Three layers (do not conflate)

| Layer | What | Example |
|---|---|---|
| A. Semantic definition CID | Meaning identity | `bafyrei…` on capability package |
| B. actor:host wire numeric ids | Host import table in `capability_contract.edn` | 201+ |
| C. Compiler/provider kit ids | Application capability kits | 1–23 (scoped-fs=19, process=20, secret=21, git=22, entropy=23) |

App source should use **named operations / semantic abilities**, not raw numbers.

### Adding or changing a capability

1. Edit **kotoba-core-contracts** catalog (imports, effects, host surface).
2. Recompute definition CIDs; run scaffold (`--update` if regenerating).
3. Bump Tamaki + Kototama pins to the new contracts SHA.
4. Fix admit fixtures if CIDs changed (expected: meaning change ⇒ new CID).
5. Do **not** rename a repo and keep the old CID while changing effects.

---

## 4. Provider kit frontier (W6 kbb-related)

| Kit | id | Status |
|---|---|---|
| scoped-fs | 19 | pure resolve-path + mem-store + **os-store** (`:roots` required); cljs/nbb OS transport landed (provider#28 / ADR 0147) |
| process | 20 | pure validate-spawn + echo-transport + **os-spawn** (`:binaries` required); cljs/nbb OS transport landed (provider#28 / ADR 0147) |
| secret | 21 | get-only allowlist + map-fetch / env-fetch (no dump); ops first-cutover (murakumo#48/#50) |
| git | 22 | pure validate-run + echo-transport + **os-run** dual-runtime (ADR 0148–0150; provider#29+#31+#32); murakumo#55 deploy pin |
| entropy | 23 | CSPRNG draw + dual-runtime **os-draw** (provider#33 / ADR 0151); clock-and-random gap closed |

cljs OS transports for process / scoped-fs / git are **done**. SSH is **host-forever** (no `provider.ssh` kit; decision recorded). Residual: remaining ops CLI ambient sites beyond first secret/git cutovers.

---

## 5. Suggested first commands for a new agent

```bash
# Plan + inventories
gh api repos/kotoba-lang/kotoba-lang/contents/docs/kotoba-centered-migration-plan.md --jq .content | base64 -d | tail -n 40
gh api repos/kotoba-lang/kotoba-lang/contents/lang/w6-kbb-ability-gap.edn --jq .content | base64 -d | head -80

# Capability contract pin
gh api repos/kotoba-lang/kotoba-core-contracts/commits/main --jq .sha
# expect 2e615d17… or newer if advanced deliberately

# Example atomic capability package
gh api repos/kotoba-lang/capability-http-fetch/contents/README.md --jq .content | base64 -d | head -30
```

Pick **one** Next item from the plan; finish with tests + inventory/plan update; open minimal PRs; merge only after green.

---

## 6. Related ADRs / docs

| Doc | Role |
|---|---|
| `docs/adr/ADR-capability-repository-semantic-cid-v1.md` | Capability CID + 1-repo policy (this repo) |
| `docs/adr/ADR-w6-migration-inventory-v1.md` | W6 cohort inventory |
| `docs/w6-*.md` + `lang/w6-*.edn` | Path-level inventories |
| compiler `docs/adr/0120`–`0145` (approx.) | W5 deepen trail |
| Superproject `ADR-2607289500` | Workspace-authoritative handoff |

---

## 7. Capability package implementation (2026-07-28)

Contract only → **reference-implemented** (pure allowlist):

| Package | PR | Status |
|---|---|---|
| core-contracts status rules | [#22](https://github.com/kotoba-lang/kotoba-core-contracts/pull/22) | `:reference-implemented` allowlist + sha256/exports rules |
| capability-math-sin | [#1](https://github.com/kotoba-lang/capability-math-sin/pull/1) | wasm core `sin` + JVM `Math/sin` |
| capability-math-cos | [#1](https://github.com/kotoba-lang/capability-math-cos/pull/1) | wasm core `cos` + JVM `Math/cos` |
| capability-hash-sha256 | [#1](https://github.com/kotoba-lang/capability-hash-sha256/pull/1) | wasm core `sha256_hex` + JVM `MessageDigest` |
| capability-clock-monotonic | [#1](https://github.com/kotoba-lang/capability-clock-monotonic/pull/1) | wasm counter + JVM `System/nanoTime` |
| capability-time-now-days | [#1](https://github.com/kotoba-lang/capability-time-now-days/pull/1) | wasm stub + JVM UTC day counter |
| capability-random-bytes | [#1](https://github.com/kotoba-lang/capability-random-bytes/pull/1) | wasm xorshift + JVM `SecureRandom` |
| capability-data-cbor | [#1](https://github.com/kotoba-lang/capability-data-cbor/pull/1) | wasm `cbor_encode` + JVM flat-pair encoder |
| capability-data-json | [#1](https://github.com/kotoba-lang/capability-data-json/pull/1) | wasm `json_encode`/`json_extract_field` + JVM |

**Allowlist pure reference-implemented set is complete** (sin/cos/sha256/cbor/json/clock/random/now-days).  
Network/secret caps stay contract-only until signed production providers exist.

## 8. 2026-07-28 follow-through

| Item | PR |
|---|---|
| QUIC cert path-ref under scoped roots | murakumo#52 |
| kagi-fetch wire | murakumo#52 |
| git kit id 22 (ADR 0148–0150) | provider#29+#31+#32 |
| entropy kit id 23 (ADR 0151) | provider#33 |
| secret name/policy pure oracle | murakumo#60 |
| rebalance + plan + engine + report pure path | murakumo#61–#91 |
| identity JWT + overlay crypto packaging oracle | murakumo#85 |
| product-shell oracle authority (kekkai.gate dual-source) | murakumo#86 |
| live HMAC/AES host adapters | murakumo#87 |
| product-shell oracle authority (token) | murakumo#88 |
| product-shell oracle authority (report pad/header/help) | murakumo#89 |
| product-shell oracle authority (infer.plan) | murakumo#91 |
| product-shell oracle authority (dash.state) | murakumo#93 |
| product-shell oracle authority (infer.schedule) | murakumo#94 |
| product-shell oracle authority (task.plan) | murakumo#95 |
| product-shell oracle authority (infer.engine) | murakumo#96 |
| product-shell oracle authority (fleet.inventory) | murakumo#102 |
| product-shell oracle authority (secret) | murakumo#98 |
| bulk product-shell KIR catalog (32 cores) | murakumo#99 |
| zones query + hostname-match oracle | com-cloudflare#13 |
| clock/time/random reference-impl | capability-*-#1 |
