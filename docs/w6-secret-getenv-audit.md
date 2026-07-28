# W6 secret getenv audit (murakumo)

Status: **ops secret cutover complete** (2026-07-28)  
Parent: secret-custody ops + provider kit + kagi-fetch

## Policy

- **Secrets** must use named fetch (kit-shaped) — no ambient env dump.
- **Path refs** are absolute files, never PEM bodies in env.
- **Config** URLs/bins may stay exact getenv until a config kit lands.

## Done

| site | var / mechanism | evidence |
|---|---|---|
| token CLI | `MURAKUMO_TOKEN_SECRET` | murakumo#48 |
| relay-server | `MURAKUMO_SERVICE_TOKEN` | murakumo#50 |
| media push | `MURAKUMO_METRICS_TOKEN` | murakumo#50 |
| cloud overlay auth | `:overlay/auth-key-env` | murakumo#50 |
| cert store | scoped path-ref + roots | murakumo#52 |
| kagi wire shape | `secret/kagi-fetch` | murakumo#52 |
| quic_driver | `MURAKUMO_QUIC_CERT/KEY` path refs | murakumo#53 |
| com-cloudflare | `CLOUDFLARE_API_TOKEN` | com-cloudflare#3 |

## Progress (config inject)

| site | var / mechanism | evidence |
|---|---|---|
| orchestrate / relay-server cloud | `MURAKUMO_CLOUD` via `config/cloud-url` | murakumo#137 |
| media model-map / gateway text+ckpt | `MURAKUMO_API_URL` / text / ckpt | murakumo#137 |
| relay-worker join | kotoba bin / local url / node name | murakumo#137 |
| core pin git | `MURAKUMO_GIT_BIN` | murakumo#137 |
| overlay adapter drivers | `MURAKUMO_*_DRIVER` | murakumo#137 |

## Remaining (optional / low)

- Operator seed: already inject-friendly; optional live kagi getter inject
- Config leave: `MURAKUMO_CLOUD`, bins, driver paths, `HOME`, …
