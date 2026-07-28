# ADR: W6 cloudflare path inventory v1

- Status: Accepted
- Date: 2026-07-28

## Context

W6 inventory v1 classified `com-cloudflare` as `blocked-by-provider` with a
note about route semantics after HTTP ingress soak, without path detail.
Cutover needs pure request/parse/product surfaces separated from JVM HTTP and
token env.

## Decision

Adopt path-level inventory:

| artifact | role |
|---|---|
| `lang/w6-cloudflare-path-inventory.edn` | path classes + first cutover slice |
| `docs/w6-cloudflare-path-inventory.md` | human summary |

Clarify that **routes** mean Workers/DNS/Pages product discovery, not workerd
app routing. First cutover slice `cloudflare-pure-request-v1`: stream
validate/redact/request builders, analytics parse, thin workers/zones path
cores.

## Consequences

- Next implementation work is oracle parity for pure request builders, not live
  API token cutover or workerd router ports.
- `com-cloudflare-compat` remains adapter; pure `routes`/`entity-specs` may get
  a later oracle slice.

## Related

- `docs/adr/ADR-w6-migration-inventory-v1.md`
- `docs/w6-murakumo-path-inventory.md` (pattern)
- `docs/kotoba-centered-migration-plan.md` § W6
