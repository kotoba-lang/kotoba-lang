# ADR: W6 kbb ability gap list v1

- Status: Accepted
- Date: 2026-07-28

## Context

W6 inventory v1 listed a next-action to track kbb ability gaps blocking
kotoba-script ← nbb cutover, without an explicit gap list. Ops shells
(murakumo nbb/bb) and guest product cutovers were being conflated.

## Decision

Adopt a first-slice gap list:

| artifact | role |
|---|---|
| `lang/w6-kbb-ability-gap.edn` | machine-readable gaps + consumers |
| `docs/w6-kbb-ability-gap.md` | human summary |

Separate **ops-host abilities** (process, scoped-fs, ssh, secrets, git, deploy)
from **guest provider kits**. Mark SSH fleet as potentially permanent
host-mechanism.

## Consequences

- nbb/bb remain valid until high-priority gaps close with evidence.  
- Guest `.kotoba` product work is not gated on kbb.  
- Subsequent versions may flip gap `:status` without changing the scheme.

## Related

- `docs/adr/ADR-w6-migration-inventory-v1.md`
- `docs/w6-murakumo-path-inventory.md` (nbb shells blocked-by-language)
- `docs/kotoba-centered-migration-plan.md` § W6 / filesystem-process-git
