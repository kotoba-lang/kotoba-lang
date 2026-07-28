# W6 secret getenv audit (murakumo)

Status: **high-priority secrets cut over** (2026-07-28, murakumo#50)  
Parent: secret-custody ops (murakumo#48, #50; com-cloudflare#3)

## Policy

- **Secrets** (HMAC keys, API tokens, service tokens, private key material)
  must use named fetch (kit-shaped) — no ambient env dump.
- **Config / paths / URLs / binary locations** may stay as exact getenv until
  a broader config kit lands; they are not secret-custody blockers.

## murakumo call-sites

| site | var(s) | class | action |
|---|---|---|---|
| `murakumo.secret` / `cmd-token` | `MURAKUMO_TOKEN_SECRET` | **secret** | **done** `#48` (`murakumo-token`) |
| `infer/relay_server.clj` | `MURAKUMO_SERVICE_TOKEN` | **secret** | **done** `#50` (`murakumo-service-token`) |
| `infer/media.clj` | `MURAKUMO_METRICS_TOKEN` | **secret** | **done** `#50` (`murakumo-metrics-token`) |
| `cloud.clj` | `:overlay/auth-key-env` dynamic | **secret** | **done** `#50` (`resolve-exact-env`) |
| `config.cljc` | operator seed env keys | **secret** | inject-friendly `from-getenv` (leave) |
| `overlay/quic_driver.clj` | `MURAKUMO_QUIC_CERT`, `MURAKUMO_QUIC_KEY` | **secret material** | next: path refs under scoped-fs |
| `overlay/transport.clj` | `MURAKUMO_*_DRIVER` | **config** (binary path) | leave |
| `kekkai.clj` / gate | ledger/dir/`HOME` | config path | leave |
| `infer/gateway.clj` | image ckpt, text backend URL | config | leave |
| `infer/orchestrate.clj` | `MURAKUMO_CLOUD` | config URL | leave |
| `infer/relay_worker.clj` | bin/url/node name | config | leave |
| `overlay/cert.clj` | `MURAKUMO_KAGI_DIR` | config path | leave |

## com-cloudflare

| site | var | class | action |
|---|---|---|---|
| `cloudflare.client/api-token` | `CLOUDFLARE_API_TOKEN` | **secret** | **done** (named `cloudflare-api-token`) |

## Next (medium)

1. QUIC cert/key — prefer path refs under scoped-fs roots over PEM-in-env  
2. Optional live kagi `fn-fetch` for operator seed / overlay keys  
3. Operator seed remains inject-friendly; no ambient dump today
