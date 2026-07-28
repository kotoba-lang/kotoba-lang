# ADR: W6 migration inventory v1

- Status: Accepted
- Date: 2026-07-28

## Context

W5 host-kit deepen (capabilities, streams, linear move typing, product verticals
through conditional get) is landed through compiler ADR 0142. Design-system
Delivery-6 document cutover is complete 5/5. The migration plan’s W6 step
requires each candidate file/namespace to be classified before cutover.

## Decision

Adopt a first-slice inventory:

| artifact | role |
|---|---|
| `lang/w6-migration-inventory.edn` | machine-readable cohort + classification |
| `docs/w6-migration-inventory.md` | human summary |

Classes: `portable-pure`, `portable-effectful`, `host-mechanism`,
`operational-command`, `blocked-by-language`, `blocked-by-provider`.

### Cohort boundaries

1. **Design system** — document layer migrated; not re-opened for bulk port.
2. **Language platform** — compiler/kir/component/abi/wasm/provider remain
   host-mechanism authority (not product file ports).
3. **Next product verticals** — murakumo / cloudflare / kotoba-script inventory
   only; blocked by provider or language as recorded.

## Consequences

- W6 work picks from Cohort C path-level refinements, not from design-system
  re-ports or host-kit rewrites.
- Guest product `.kotoba` examples under compiler remain evidence of portable
  effectful slices, not substitutes for product repo cutovers.
- Subsequent inventory versions may refine path-level entries without changing
  the classification scheme.

## Related

- `docs/kotoba-centered-migration-plan.md` § W6
- Design-system Delivery-6 cutovers (css/html/shitsuke/liquid-glass-ui/kotoba-ui)
- Compiler ADR 0120–0142 (W5 deepen)
