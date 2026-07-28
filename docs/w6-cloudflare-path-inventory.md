# W6 Cloudflare path inventory (first slice)

Status: accepted path-level first slice (2026-07-28)  
Machine-readable: [`lang/w6-cloudflare-path-inventory.edn`](../lang/w6-cloudflare-path-inventory.edn)  
Parent: [`docs/w6-migration-inventory.md`](w6-migration-inventory.md)

## Clarification

W6 parent inventory listed `com-cloudflare` under **route semantics after HTTP
ingress**. Path review shows this repo is the **Cloudflare API v4 client**
(REST + GraphQL Analytics + Stream Live), not a workerd application router.

**“Routes” here** = Workers zone-routes, account Custom Domains, DNS records,
and Pages domain bindings — product answers to *what serves this hostname?*  
Application HTTP ingress remains **provider / workerd** (ADR 0103).

Sibling `com-cloudflare-compat` is a separate L5 clean-room actor (schema-driven
CRUD); parent class keeps it **host-mechanism adapter**, with a pure route
table noted for optional oracle work.

## Vertical map

| vertical | pure / portable product | host mechanism |
|---|---|---|
| **HTTP client** | — | `client.cljc` (`jvm-http-fn`, `rest!`, `graphql!`, token env) |
| **Workers routes** | `workers.cljc` path surface (zone-routes, custom-domains, scripts) | via `client/rest!` |
| **DNS / zones** | `zones.cljc` list/dns path surface | via `client/rest!` |
| **Pages** | `pages.cljc` projects + domain lookup | via `client/rest!` |
| **Analytics** | query builders + parse (`path-query`, `parse-*`) | `daily-report!` / clock ISO helpers |
| **Logpush** | job CRUD path surface | via `client/rest!` (log *fetch* out of scope) |
| **Stream Live** | `validate-output`, `redact-key`, `*-request`, parse/summary | `*!` over `rest!` |
| **compat actor** | `entity-specs`, `routes`, coerce folds | clock/RNG/`*store*` |

## First cutover slice (recommended)

**`cloudflare-pure-request-v1`** — oracle parity only, no live token:

1. `stream.cljc` — `validate-output`, `redact-key`, pure `*-request` builders, parsers  
2. `analytics.cljc` — GraphQL request construction + response parse  
3. Path-string cores for `workers` / `zones` (thin product paths)

Method: existing stubbed `http-fn` tests as oracle → `.kotoba` pure defs →
EDN equality on request maps / parse fixtures → HTTP + token remain host.

## Explicit non-goals (this slice)

- Live `CLOUDFLARE_API_TOKEN` network calls inside guest  
- workerd application router / HTTP ingress product cutover  
- Logpush destination log fetching/parsing  
- Shipping `com-cloudflare-compat` as guest product (adapter keep)

## Blockers by vertical

| vertical | primary blocker |
|---|---|
| http-client-mechanism | provider HTTP transport + secret-custody for API token |
| workers-route / dns / pages | client host only (already pure path construction) |
| analytics | client host; pure parse is unblocked for oracle |
| stream-live | client host + stream-key secret-custody for `*!` |
| compat-clean-room | Datom store host + WASM L5 packaging |

## Progress

- **2026-07-28:** path inventory accepted (this document).
- **2026-07-28 com-cloudflare#1:** `kotoba/stream_core.kotoba` redact-key / validate-flags / destination-url / path builders parity.
- **2026-07-28 com-cloudflare#2:** `kotoba/analytics_core.kotoba` + `workers_path_core.kotoba` GraphQL query text + REST path parity.
- **2026-07-28 com-cloudflare#4:** `kotoba/logpush_path_core.kotoba` + `stream_core` live-input-summary parity.
- **2026-07-28 com-cloudflare#5:** `cloudflare.deploy` pure-plan first slice (Workers put/delete + wrangler argv).
- **2026-07-28 com-cloudflare#6:** `kotoba/deploy_core.kotoba` validators / paths / plan constants / wrangler argv parity.
- **2026-07-28 com-cloudflare#7:** Workers ES-module multipart put plan + live upload (ADR 0005).
- **2026-07-28 com-cloudflare#8:** `deploy_core` module validate + multipart encode oracle (ADR 0006).

## Next

1. Optional analytics parse tallies (map reduce) — guest `map-*` exists; still optional product work.  
2. Optional Pages bulk asset deploy over REST (wrangler argv remains the ops path).  
3. murakumo pure-planner scalar oracles complete (#37–#57); remaining cljc is map/vector/crypto/host shells.
