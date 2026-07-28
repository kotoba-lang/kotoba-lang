# W6 kbb ability gap list (first slice)

Status: accepted gap-list first slice (2026-07-28)  
Machine-readable: [`lang/w6-kbb-ability-gap.edn`](../lang/w6-kbb-ability-gap.edn)  
Parent: [`docs/w6-migration-inventory.md`](w6-migration-inventory.md)

## Scope

This list tracks **ops-host abilities** that keep scripts on **nbb / babashka /
JVM** rather than a future **kbb** (Kotoba-hosted scripting host under grants).

It does **not** block guest product `.kotoba` cutovers (those use provider kits:
http, object, state, llm, …).

| term | meaning |
|---|---|
| **kbb** | Grant-scoped Kotoba ops host (not production yet) |
| **nbb** | Current Node-adjacent ops scripts (e.g. murakumo `ops`/`task`) |
| **guest product** | Portable pure/effectful `.kotoba` — out of this gap list |

Note: `kotoba-script` today is the **checked KIR→mjs backend**, not nbb. Parent
inventory grouped “keep nbb until kbb …” under that name; this document
separates **ops shells** from the JS backend.

## Gap table

| id | ability | status | priority | blocks |
|---|---|---|---|---|
| `scoped-filesystem` | read/write under declared roots | **OS transport landed** (provider#25 os-store) | high | cljs mounts still open |
| `process` | spawn/await bounded process | **OS transport landed** (provider#25 os-spawn) | high | cljs spawn / SSH still open |
| `ssh-or-remote-exec` | remote exec without ambient OpenSSH | missing | high | murakumo fleet SSH (may stay host forever) |
| `git` | status/log (+ optional worktree) | missing | medium | repo tooling scripts |
| `secret-custody` | named secret fetch (no dump) | **contract first slice** (provider#26 id 21) | high | kagi transport + ops CLI cutover |
| `cloud-deploy` | Workers/Pages deploy verbs | missing | low | scripted publish |
| `clock-and-random` | clock + CSPRNG | partial | medium | compat actor ids |

## Consumers

| repo | surfaces | needs |
|---|---|---|
| **murakumo** | `ops.cljs`, `task/*`, `core.clj`, `ssh.clj` | process, ssh, scoped-fs, secrets |
| **kotoba-script** | future kbb driver (not current mjs backend) | fs, process, git, secrets, deploy |
| **kami-engine-script-runtime** | host adapter | process, scoped-fs |

## Policy

1. **Do not** move murakumo SSH fleet shells into guest until process+ssh are
   qualified — W6 murakumo inventory may keep them host-mechanism permanently.  
2. Guest product cutovers use **provider kits**, not kbb.  
3. nbb/bb remain authorized ops hosts while gaps are open.  
4. Close a gap only with conformance evidence + inventory status flip.

## Progress

- **2026-07-28 provider#24 / ADR 0143:** `provider.process` (id 20) + `provider.scoped-fs` (id 19) contract first slice (mem/echo transports, pure policy).
- **2026-07-28 provider#25 / ADR 0144:** `process-transport/os-spawn` + `scoped-fs-transport/os-store` (host `:binaries` / `:roots`; no PATH/CWD defaults).

## Progress (continued)

- **2026-07-28 provider#26 / ADR 0145:** `provider.secret` (id 21) get-only allowlist + `env-fetch`/`map-fetch` (no dump).

## Next

1. **Ops CLI cutover** — murakumo/cloudflare use secret kit instead of ambient getenv.  
2. **cljs/nbb** OS spawn + root-mount transports (sync contract).  
3. Decide **ssh forever-host** vs kit.  
4. Optional **kagi** one-shot getter as `:fetch`.
