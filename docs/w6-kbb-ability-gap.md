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
| `scoped-filesystem` | read/write under declared roots | **dual-runtime OS transport** (provider#25+#28) | high | browser mounts N/A |
| `process` | spawn/await bounded process | **dual-runtime OS transport** (provider#25+#28) | high | — |
| `ssh-or-remote-exec` | remote exec without ambient OpenSSH | **host-forever** | high | murakumo fleet stays on nbb/bb |
| `git` | status/log (read subcommands) | **contract + JVM os-run** (provider#29+#31) | medium | cljs os-run; tooling cutover |
| `secret-custody` | named secret fetch (no dump) | **ops cutover complete** (#48–#53 + CF#3) | high | optional live kagi inject |
| `cloud-deploy` | Workers/Pages deploy verbs | missing | low | scripted publish |
| `clock-and-random` | clock + CSPRNG | partial | medium | compat actor ids |

## Consumers

| repo | surfaces | needs |
|---|---|---|
| **murakumo** | `ops.cljs`, `task/*`, `core.clj`, `ssh.clj` | process, ssh(host), scoped-fs, secrets |
| **kotoba-script** | future kbb driver (not current mjs backend) | fs, process, git, secrets, deploy |
| **kami-engine-script-runtime** | host adapter | process, scoped-fs |

## Policy

1. **SSH fleet exec is host-forever** — see [`w6-ssh-host-forever.md`](w6-ssh-host-forever.md).  
2. Guest product cutovers use **provider kits**, not kbb.  
3. nbb/bb remain authorized ops hosts while gaps are open.  
4. Close a gap only with conformance evidence + inventory status flip.

## Progress

- **2026-07-28 provider#24 / ADR 0143:** `provider.process` (id 20) + `provider.scoped-fs` (id 19) contract first slice (mem/echo transports, pure policy).
- **2026-07-28 provider#25 / ADR 0144:** `process-transport/os-spawn` + `scoped-fs-transport/os-store` (host `:binaries` / `:roots`; no PATH/CWD defaults).
- **2026-07-28 provider#26 / ADR 0145:** `provider.secret` (id 21) get-only allowlist + `env-fetch`/`map-fetch` (no dump).
- **2026-07-28 provider#27 / ADR 0146:** `fn-fetch` (kagi/one-shot) + `keychain-fetch` (single-item `-w` only).
- **2026-07-28 murakumo#48 + com-cloudflare#3:** ops CLI named-secret cutover for token HMAC + `CLOUDFLARE_API_TOKEN`.
- **2026-07-28 provider#28 / ADR 0147:** cljs/nbb `os-spawn` + `os-store` (spawnSync / Node fs sync).
- **2026-07-28:** SSH host-forever decision; secret getenv audit first pass.
- **2026-07-28 murakumo#50:** remaining high-priority secrets (service/metrics/overlay auth-key).
- **2026-07-28 murakumo#52:** cert store scoped path-ref + `secret/kagi-fetch`.
- **2026-07-28 murakumo#53:** quic_driver cert/key path refs (no PEM-in-env).
- **2026-07-28 provider#29 / ADR 0148:** `provider.git` id 22 validate-run + echo-transport.
- **2026-07-28 provider#31 / ADR 0149:** `git-transport/os-run` JVM production transport.

## Next

1. git tooling cutover / cljs os-run (medium).  
2. **cloud-deploy** stays low priority ops.  
3. clock/entropy kit completion (medium).

## 2026-07-28 update

- **git kit** contract first slice: `provider.git` id **22** (ADR 0148) — pure
  subcommand allowlist + echo transport.
- **QUIC cert path-ref**: murakumo overlay cert store accepts scoped-fs shaped
  `:path-ref` + `:root-dirs` (murakumo#52).
- **kagi-fetch**: `murakumo.secret/kagi-fetch` wires name→ref + one-shot getter.
