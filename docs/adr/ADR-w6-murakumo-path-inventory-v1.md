# ADR: W6 murakumo path inventory v1

- Status: Accepted
- Date: 2026-07-28

## Context

W6 inventory v1 classified `murakumo` as `blocked-by-provider` without path
detail. Cutover needs pure planners separated from SSH/nbb shells.

## Decision

Adopt path-level inventory:

| artifact | role |
|---|---|
| `lang/w6-murakumo-path-inventory.edn` | path classes + first cutover slice |
| `docs/w6-murakumo-path-inventory.md` | human summary |

First cutover slice `murakumo-pure-planners-v1`:
`infer/plan`, `infer/engine`, `task/plan`, `kekkai/gate`, `dash/state`, `token`.

## Consequences

- Next implementation work is oracle parity for pure planners, not fleet SSH ports.
- nbb surfaces remain `blocked-by-language` until kbb abilities land.

## Related

- `docs/adr/ADR-w6-migration-inventory-v1.md`
- `docs/kotoba-centered-migration-plan.md` § W6
