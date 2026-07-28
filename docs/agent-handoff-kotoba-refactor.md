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
| W6 murakumo pure-planners **medium** | partial (fleet/schedule/join/gc/config/identity/deploy…) |
| W6 cloudflare pure-request string cores | stream + analytics/paths |
| W6 process / scoped-fs / secret kits | provider ADR **0143–0145** |

### Plan Next (priority order)

1. **Ops CLI secret cutover** — stop ambient `getenv`; use `provider.secret` (id 21)
2. **cljs/nbb OS transports** for process spawn + scoped-fs roots
3. **Remaining medium murakumo pure planners** — e.g. credits, reconcile/plan
4. **kbb** high gaps still open beyond contract-first (ssh/git/cloud-deploy policy)
5. **Capability Wasm implementations** — atomic repos are still `contract-only`

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
| C. Compiler/provider kit ids | Application capability kits | 1–21 (secret=21, process=20, scoped-fs=19) |

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
| scoped-fs | 19 | pure resolve-path + mem-store + **os-store** (`:roots` required) |
| process | 20 | pure validate-spawn + echo-transport + **os-spawn** (`:binaries` required) |
| secret | 21 | get-only allowlist + map-fetch / env-fetch (no dump) |

Still open: ops CLIs using these kits end-to-end; cljs parity for OS transports; ssh forever-host decision per gap list.

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

Next pure targets on the allowlist: `data/cbor`, `data/json`, `clock/monotonic`, `random/bytes`, `time/now-days`.  
Network/secret caps stay contract-only until signed production providers exist.

## 8. 2026-07-28 follow-through

| Item | PR |
|---|---|
| QUIC cert path-ref under scoped roots | murakumo#52 |
| kagi-fetch wire | murakumo#52 |
| git kit id 22 (ADR 0148) | provider#29 |
