# ADR: Reliability T5.4 — keep max-parameters = 5 (records for multi-field)

- Status: Accepted
- Date: 2026-07-28
- WBS: T5.4

## Context

T5.1 preferred structural args over arity growth. The open T5.4 choice was:
raise `max-parameters` with a security ADR, **or** keep 5 and rely on records.

Raising arity expands ABI surface (wasm/native calling conventions, verification,
admission tables) for little gain once sealed records and options exist.

## Decision

1. **Keep `max-parameters = 5`** as the long-term pure-product / guest ABI default
   (`guest-grammar.edn`, compiler `frontend` / `verifier`).
2. Multi-field public APIs use **`[:record …]`** (T4.4 cookbook) or options
   (T4.3), not arity inflation.
3. Raising the limit requires a **new** security ADR with dual-backend evidence
   and threat review — not a silent bump.
4. Compiler T2.4 suite asserts arity 6 rejects with
   `:kotoba.error/max-parameters` (compiler#417 / ADR 0166).

## Non-claims

- Does not implement T5.3 pack rewrites  
- Does not change historical intentional packs still awaiting T5.3  

## Related

- T5.1 structural args ADR  
- T4.4 record cookbook  
- compiler ADR 0166 (ambient + max-parameters reject codes)
