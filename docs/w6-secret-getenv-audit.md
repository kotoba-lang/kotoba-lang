# W6 secret getenv audit (murakumo first pass)

Status: audit first slice (2026-07-28)  
Parent: secret-custody ops cutover (murakumo#48, com-cloudflare#3)

## Policy

- **Secrets** (HMAC keys, API tokens, service tokens, private key material)
  must use named fetch (kit-shaped) — no ambient env dump.
- **Config / paths / URLs / binary locations** may stay as exact getenv until
  a broader config kit lands; they are not secret-custody blockers.

## murakumo call-sites (`origin/main` 2026-07-28)

| site | var(s) | class | action |
|---|---|---|---|
| `murakumo.secret` / `cmd-token` | `MURAKUMO_TOKEN_SECRET` | **secret** | **done** (named `murakumo-token`) |
| `infer/relay_server.clj` | `MURAKUMO_SERVICE_TOKEN` | **secret** | next: named fetch |
| `infer/media.clj` | `MURAKUMO_METRICS_TOKEN` | **secret** | next: named fetch |
| `overlay/quic_driver.clj` | `MURAKUMO_QUIC_CERT`, `MURAKUMO_QUIC_KEY` | **secret material** | next: path refs or named fetch |
| `cloud.clj` | `:overlay/auth-key-env` dynamic | **secret** | next: inject via secret kit |
| `overlay/transport.clj` | dynamic env-name | **secret** | next: inject via secret kit |
| `config.cljc` | operator seed env keys | **secret** | already inject-friendly `from-getenv` |
| `kekkai.clj` / gate | ledger/dir/`HOME` | config path | leave |
| `infer/gateway.clj` | image ckpt, text backend URL | config | leave |
| `infer/orchestrate.clj` | `MURAKUMO_CLOUD` | config URL | leave |
| `infer/relay_worker.clj` | bin/url/node name | config | leave |
| `overlay/cert.clj` | `MURAKUMO_KAGI_DIR` | config path | leave |

## com-cloudflare

| site | var | class | action |
|---|---|---|---|
| `cloudflare.client/api-token` | `CLOUDFLARE_API_TOKEN` | **secret** | **done** (named `cloudflare-api-token`) |

## Next cutover candidates (priority)

1. `MURAKUMO_SERVICE_TOKEN` / `MURAKUMO_METRICS_TOKEN` — same pattern as token secret  
2. overlay auth-key env inject — wire through `murakumo.secret`  
3. QUIC cert/key — prefer path refs under scoped-fs roots over raw getenv of PEM
