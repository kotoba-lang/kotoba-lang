# Native execution domains in the Kotoba Lisp machine

Status: accepted, owner direction 2026-10-21 (JUN-188).

## Decision

Kotoba, Amu and Kototama describe programs, compilation, execution, authority
and providers as roles in one language-centred machine. Guest/host terminology
describes a selected embedding or target ABI; it is not a mandatory two-world
architecture inside a native program.

A closed, checked native module graph shares one runtime value representation,
execution context and resource budget. Calls inside that graph are ordinary
typed calls. A source namespace, library export or compiler/runtime role does
not by itself require serialization, Canonical ABI lift/lower, an integer-only
summary, an import trampoline or a separate heap/process. A native root export
may name its implementation directly when that preserves name binding and the
exported signature. Multi-arity dispatch and collision avoidance still apply.

Selfhosting alone is not proof that two components share a domain. Different
representations, contexts, principals, authority scopes, arenas or independently
admitted resource budgets still require the appropriate boundary. Programs
cannot declare themselves trusted, choose a common authority domain or obtain
ambient access merely by selecting native output. Removing a forwarding call
does not reset/refill fuel or widen a memory/arena limit.

## Boundaries retained

- Admission verifies actual artifacts, interfaces, runtime identity and grants.
- Effects cross declared provider interfaces. Refresh authorization/revocation,
  enforce scope/expiry/use limits and durably reserve a use before an effect.
- Fuel, frames, allocation/arena bounds, deadlines and output limits remain
  enforced on internal execution as well as provider operations.
- Foreign ABIs, independently admitted contexts, unsafe/kernel/device access,
  external services and cell-to-cell calls keep explicit adapters/validation.
- Wire, persistence, image and receipt boundaries retain canonical encoding,
  digest/CID checks and versioning. Native handles never become portable values,
  provider handles or authority. `:host-object false` remains in force.

An i64 provider transport constrains its imports/exports, not every internal
helper signature. Verified native records/lists can remain context-owned while
the provider still receives and returns only an i64. Unqualified native
operations and raw/ambient calls remain refused.

## Owners and compatibility

| Owner | Responsibility |
|---|---|
| Kotoba | Typed values/policy Forms inside the runtime; existing scalar/text entry adapters |
| Amu | Closed typed linking, native direct calls/exports, target ABI lowering |
| Kototama | Admission, per-effect authority checks, budgets and receipts; target adapters |
| grant / authority | Permission decisions and delegation, independent of implementation language |
| provider / AiueOS | Concrete mechanisms and enforcement at their actual boundary |

The host-guest domain of the boundary draft applies where a selected profile
crosses representations/contexts. It does not require marshalling at each
native call. This refines D1 of
[the Lisp machine contract ADR](ADR-kotoba-lisp-machine-contract-decisions.md).
Existing wire keys, artifact envelopes, CID rules and public scalar APIs remain
compatible. This decision introduces no production cutover or global renaming.
Browser operation consent remains the shared client-runtime contract.

## Verification

Verify the emitted call graph and actual native execution, including internal
typed values followed by an authorized provider effect. Check denial,
revocation/expiry, use exhaustion, shared fuel and allocation limits. A passing
linker/reference test is separate from native execution; a bootstrap compiler
producing a native artifact is separate from a selfhost-built compiler.
Record exact source revisions and runtime evidence per qualified profile.
