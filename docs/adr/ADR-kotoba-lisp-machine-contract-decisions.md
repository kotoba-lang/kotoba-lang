# ADR: Kotoba Lisp machine contract decisions — boundary ownership, image, interaction, live redefinition, cell calls

Status: accepted (owner decision 2026-10-21). This ADR closes the open items in
`docs/kotoba-lisp-machine-design.ja.md` §8 (items 1–5). Item 6 (awai S1 gate
measurement order) is operational and is recorded here as a hand-off, not a
decision. **Nothing here is implementation or qualification evidence.**

## Context

The integrated Lisp machine design (added in PR #796: the distributed Lisp
machine drawing on Holochain, EVM and the awai L1 charter) deliberately left
six decisions to the owner. The owner confirmed the design name ("Kotoba Lisp
machine", descriptive; no metaphor name) and directed that the remaining items
be decided and recorded. The design's section 8 is the list; this ADR is the
record. The draft `docs/kotoba-lisp-machine-boundary.draft.edn`
(`:kotoba.lisp-machine/boundary-draft-v1`) already carries the boundary rules
and five boundary domains; it stays a draft until its owners adopt it.

## Decision

### D1. Boundary encoding: ownership and versioning

The placement proposal in the design's §4.5 is adopted as the ownership
decision for the judgment-free boundary encoding ("boundary"):

| role | owner |
|---|---|
| neutral descriptor (shapes) | `kotoba-core-contracts`, next to `kotoba.core.execution` |
| per-target profile layout | `abi` (the Component profile continues the existing Canonical ABI work: amu ADR 0065 / 0068 / 0070 / 0076) |
| wire codec (canonical bytes) | `kotoba-protocol` |
| kernel profile implementation | the aiueos packer (the existing flat record of ADR-0219 is the kernel boundary implementation) |
| host / guest implementation | amu lower / lift |

Versioning domain: the boundary spec is content-addressed. The adopted schema
name is `:kotoba.lisp-machine/boundary-v1`, superseding the draft
`:kotoba.lisp-machine/boundary-draft-v1`; wire keys and CID rules do not change
until a versioned migration. **No new repository.** Migration of the draft into
the owner repositories follows each repository's own release process and is
**not complete** by this ADR.

### D2. Image

The design's §5.1 definition is adopted as the image contract:

```
image = { world root (IPLD CID)
        × code environment (DefCID bindings)
        × open leases / receipts chain }
```

- **save** encodes the triple through the boundary into one CID; **restore**
  resolves from that CID and, when a dependency is missing, refuses with
  `:unresolved` listing the missing CIDs (the valid / invalid / unresolved shape
  of ADR-2610082200).
- **Restore never revives authority.** An expired lease stays expired: the
  effect is re-authorized or refused after restore.
- Raw heap dumps are still not images ("A Codebase commit is not a running heap
  image", `docs/stack-architecture.md`).
- Continuations are values (`:explicit-continuations` in the VM spec); the
  image holds open continuations as values, not hidden GC references.

Conformance vectors for save / restore / unresolved rejection are attached as a
draft: `docs/kotoba-lisp-machine-image-vectors.draft.edn`. Digests are
`:unmeasured` until an owner codec adopts the vectors into a conformance suite.

### D3. Interactive environment: three contracts, no merged repository

1. **REPL** — an interactive loop over typed eval (`lang/typed-eval.edn`).
   `eval` selects checked code by DefCID and binds AdmissionCID. No host-eval,
   no reflection (kototama `:host-eval false :reflection false`). The REPL's
   only mutating operation is the binding operation of D4.
2. **Editor plane** — stays the kobo editor plane. Unchanged.
3. **Receipt debugger** — a consumer of kototama receipts
   (`state-before`/`state-after`, `fuel-used`, `events`, `child-receipts`) with
   replay for diagnosis. No live heap inspection.

"The integrated environment" means these three contracts together. No
single-repository reorganization is decided here.

### D4. Live redefinition is rebind, not mutation

The 紋 (rule Form / logic Form) of a running cell is immutable, as a Holochain
DNA is. The live operation is a versioned rebinding:

```
rebind(env-slot, old DefCID, new DefCID) -> receipt
```

Running continuations keep the old DefCID to completion; the new binding takes
effect from the next call. The operation is receipted like any other action.
Arbitrary live system modification remains unclaimed.

### D5. Cell-to-cell calls and remote calls are the 機 (hata) API

Owner: `kotoba-lang/hata` (the conductor). ADR-2610082200 §2 already assigns
cell 間呼び出し and remote call to the conductor; this ADR confirms that
assignment as the formal owner for the call API:

- wire shape: the boundary's cell-cell domain (signed action + op, §4.2 of the
  design);
- calls pin their interface by interface DefCID (ADR-2610082200 §8);
- judgment stays with rule Form (validation) and grant — the call API decides
  nothing.

The `hata` part of the hata / mon / ito / nuno open list in
`docs/stack-architecture-target-neutral.ja.md` is therefore resolved **for the
call API only**; the mon / ito / nuno owners stay open.

### D6. awai S1 gate measurement order (hand-off, not decided here)

The S1 gates remain the awai-l1-charter work (ADR-2610092330); naming stays S0
until they pass. The order suggested by dependency, recorded for that work and
tracked in Linear, is:

1. torihiki out of Durable Objects (state-store independence, ADR-2609132007);
2. inga execution hold release;
3. re-genesis;
4. witness set: 7 witnesses across 3+ hosts;
5. the 30-day continuous external height probe last (longest window).

This ADR does not advance the naming stage and does not measure anything.

## Non-claims

No implementation, no qualification, no integrated REPL / editor / debugger,
no image implementation, no live-redefinition implementation, no wire
compatibility with Holochain / EVM / awai, no awai stage change.

## Consequences

- The design's §8 items 1–5 point at this ADR; item 6 is handed off.
- Follow-up work, each on its own repository's process: adopt the boundary
  draft into `kotoba-core-contracts` / `abi` / `kotoba-protocol`; adopt the
  image vectors into a conformance suite; implement the D4 rebind operation in
  an engine; implement the D5 call API in `hata`.
