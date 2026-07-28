# ADR: Reliability T5.1 — structural args preferred over arity growth

- Status: Accepted
- Date: 2026-07-28
- WBS: T5.1

## Context

Pure-product / Product Value ABI work repeatedly hit **`max-parameters 5`**,
forcing public base-N packs (`base-65536` seats), `has-*` sentinels, and
intentional bit-packs (e.g. murakumo schedule `eligible?`). Those packs are
hard to read, easy to desync, and block reliable dual-source oracles.

The language already admits **bounded records** and **hetero-vectors** (PVA v1 /
surface-status). What was missing is an explicit **API policy**: prefer
structural arguments over growing arity or inventing pack encodings.

## Decision

### Preference order for new pure-product public APIs

1. **`[:record …]`** (or small typed-map) for multi-field options / configs  
2. **`[:option T]`** for absence (T4.3 guide)  
3. **hetero-vector** for short ordered collections when a record is overkill  
4. **arity ≤5** flat parameters only when the API is truly positional and stable  
5. **Bit-pack / base-N public surface** — **forbidden** for new APIs

### Existing packs

- Historical packs (schedule `eligible?`, rebalance seats, etc.) may remain
  **intentional** until a T5.2/T5.3 host bridge + pilot rewrite lands.
- Do **not** add new public packs. Prefer record exports when rewriting.

### Host bridge (forward pointer, not this ADR)

T5.2 will define how product hosts (murakumo / com-cloudflare) call guest
record exports (`oracle/call-record` or typed args). This ADR only freezes the
**guest API preference**.

### max-parameters policy

**Keep max-parameters = 5** for now (security / simplicity). Raise only with a
separate security ADR (T5.4). Structural args are the escape hatch, not arity
inflation.

## Non-claims

- Does not implement host record bridge (T5.2)  
- Does not rewrite rebalance seats (T5.3)  
- Does not change compiler max-parameters  

## Consequences

| Do | Don't |
|---|---|
| Design new pure exports around records/options | Add `has-foo` / base-N public fields |
| Document residual packs as intentional + T5.x follow-up | Treat pack as the long-term API |
| Use T4.3 option guide for absence | Encode absence as `-1` / `0` sentinels |

## Related

- ADR-product-value-abi-v1  
- T4.3 option/result guide  
- WBS T5.1–T5.4  
- murakumo residual PVA (`eligible?` bit-pack intentional)

## Evidence

- This ADR + pure-product-profile forbidden-pattern alignment  
- WBS T5.1 marked landed  
