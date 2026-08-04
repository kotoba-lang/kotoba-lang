# ADR — Unison-inspired code identity and typed abilities

- **Status**: Accepted — CI1–CI7 implemented (2026-08-03); see lang/code-identity.edn for per-stage evidence and residual risks
- **Date**: 2026-07-25
- **Artifacts**: `lang/code-identity.edn`, `docs/lang/capability-values.md`,
  `lang/package.edn`
- **Related**: `ADR-safe-capability-language.md`,
  `ADR-kotoba-package-cid-lock.md`, `ADR-kotoba-language-surface-status.md`
- **Execution-boundary authority**:
  `90-docs/adr/2607252500-kotoba-wasm-component-first-execution-boundary.edn`

## Context

Kotoba already has two important but distinct security mechanisms:

1. a package/source/component can be pinned by CID, signed, and admitted by a
   lockfile; and
2. an executing component receives only explicitly granted, resource-scoped
   capabilities.

Those mechanisms prevent mutable dependency substitution and ambient host
authority, respectively.  They do not yet give a *definition* a stable
language-level identity, nor do they make a typed capability value and its
effect row one integrated language contract.

Unison is useful here as a source of two ideas, not as a runtime or syntax to
adopt wholesale: content-addressed definitions and typed abilities. Kotoba
must retain its Clojure-shaped source, **Wasm Component-first** execution
target, CID/EDN package formats, and host-enforced capability boundary. The
authoritative execution boundary is ADR-2607252500: `kototama` is the component
linker/runtime, aiueos policy is a guest control-plane component, and native
code is only the micro-TCB that independently enforces grants and resources.

## Decision

Adopt the following design as a profile extension, named **code identity and
abilities**.

### 0. Abilities are a safety elaboration, not a replacement syntax

Kotoba source remains ordinary Clojure-shaped data and functions. Application
authors normally call a named operation such as `http/get`; they do not write
numeric capability IDs, WIT imports, portable-effect envelopes, or provider
callbacks. The compiler must infer the transitive effect row and elaborate the
operation into typed ability parameters and exact imports before typed KIR.

The normative order is:

```text
closed reader
-> bounded pure desugar
-> type/schema inference
-> interprocedural effect inference
-> implicit ability elaboration
-> typed KIR
-> definition CID
-> component imports and host admission
```

Effect annotations remain useful as public contracts and security ceilings,
but ordinary private functions need not repeat fully inferred rows. Explicit
capability values appear in source when code deliberately attenuates or
delegates resource scope, operations, quotas, or deadlines. This keeps the
Clojure-shaped source small without making authority ambient.

Application, view, route, workflow, and command descriptions should remain
ordinary immutable map/vector/set values governed by schemas. New special
forms are admitted only when deterministic bounded desugaring to the existing
core cannot preserve the required invariant. The workspace migration plan is
[`../kotoba-centered-migration-plan.md`](../kotoba-centered-migration-plan.md).

### 1. Definition identity is separate from source identity

Every safe-buildable exported definition will eventually have a
`definition-cid`: a CID over canonical, typed KIR plus its profile version,
declared interface, and direct definition-CID dependencies.  It is *not* a
hash of source text, a package name, a Git commit, or a Wasm binary.

```text
source tree CID        proves the authored input
definition CID         identifies normalized, typed semantics
component CID          identifies a particular reproducible Wasm output
package manifest CID   binds names, signers, declarations, and dependency policy
```

All four are useful and none substitutes for another.  In particular, a
definition CID must never claim semantic equivalence across profile, type-rule,
or canonical-KIR-version changes.

### 2. Dependencies may be definition-addressed

An importable pure definition may be referenced by `:definition-cid` in a
locked package entry.  Names remain author-facing aliases only; safe linking
resolves an alias to a signed manifest and then to an expected definition CID.
Name resolution cannot silently change the definition that is linked.

Definition-addressing is initially restricted to closed, deterministic,
pure-definition interfaces.  Effectful code remains component-addressed until
its capability interface and effect closure are represented in the typed KIR
**and** its WIT world is admitted by `kototama`. A definition CID is never an
authorization to instantiate code or bind an import.

### 3. Capability values are Kotoba abilities

The existing capability-value proposal becomes the semantic basis for
**abilities**:

- an ability is an unforgeable, resource-scoped capability value;
- a host call consuming an ability requires its corresponding effect in the
  caller's effect row;
- an effectful component declares a narrow aiueos-owned WIT import; broad
  WASI filesystem, HTTP, clock, random, environment, and process imports are
  never ability kinds;
- the ability and the WIT import bind target, operation/method, byte/item
  quota, deadline, and audit identity in addition to resource scope;
- an ability's use is valid only when its value, delegation, local policy,
  manifest, package lock, WIT-world admission, target identity, surface policy,
  and runtime limits intersect;
- capability values are affine at the authority boundary: duplication must not
  create additional authority, and consumption/revocation must be receipted.

This is deliberately narrower than a general-purpose effect-handler language.
Kotoba does not add ambient handlers, arbitrary continuation capture, or a
second dynamic runtime.  Providers remain the concrete effect handlers, linked
by the Wasm tender/host only after policy admission.

### 4. A single safety invariant spans all layers

For an external operation to occur, all of the following must hold:

```text
typed ability/effect admits call
∧ compiler emits declared import
∧ definition/component/package identities match the lock
∧ kototama admits the WIT world, target identity, and resource limits
∧ policy and delegation grant the scoped resource and operation bounds
∧ tender binds the import
∧ provider/native micro-TCB revalidates scope, quota, deadline, and revoke state
∧ provider records a receipt
```

Failure at any stage is deny-by-default.  A CID proves integrity and selected
identity; it never grants authority.  An ability type documents and checks the
required authority; it never replaces host-side policy enforcement.

## Explicit non-goals

- Replacing Clojure-shaped Kotoba source with Unison syntax.
- Treating source or component hashes as proof that code is pure or safe.
- Adopting a global Unison-style codebase namespace before definition identity
  is implemented and independently reproducible.
- Adding general effect handlers, runtime `eval`, dynamic loading, or ambient
  I/O.
- Creating a direct native AOT/OS-syscall route for ordinary Kotoba code.
- Treating a host-language provider callback as the primary execution model;
  normal execution composes admitted Wasm Components through `kototama`.
- Claiming durable/distributed execution semantics from code addressing alone.
  Durable workflows remain an explicit scheduler/component concern.

## Safety consequences

`definition-cid` improves supply-chain integrity, reproducibility, review, and
dependency substitution resistance (S5-style assurance).  Typed abilities
make effect/capability mismatches statically visible and enable more precise
least-privilege policies (T2/T3 support).  Neither is a memory-safety proof nor
a sandbox.  The Wasm runtime, resource-scoped capability intersection, and
provider revalidation remain mandatory TCB elements.

## Delivery stages

| Stage | Deliverable | Admission rule |
|---|---|---|
| CI0 | This ADR and `lang/code-identity.edn` contract | no implementation claim |
| CI1 | Canonical typed-KIR encoding and definition-CID test vectors | byte-for-byte deterministic identity |
| CI2 | Pure definition export/import manifest fields and positive fixtures | alias resolves to expected definition CID |
| CI3 | Negative fixtures for hash/profile/interface/dependency mismatch | mismatch rejects before linking |
| CI4 | Safe-build verifies definition identity against package lock | no fallback to mutable name/version |
| CI5 | Typed ability/effect checking and capability-value **narrow WIT** ABI | ability/effect or WIT-world mismatch rejects before provider call |
| CI6 | Cross-implementation conformance plus target/quota/revocation/receipt evidence | all layers preserve the invariant above |
| CI7 | Friendly source operations elaborate to the same typed ability/effect KIR across project and single-source builds | no user-facing numeric capability or host callback is required |

Until CI4 and CI5 land, the design must be described as **proposed**.  Existing
manifest/tree/component CIDs and current capability gates remain the operative
security mechanisms.

**2026-08-03 — all of CI1–CI7 are implemented**, so the condition above is met
and this ADR moves from Proposed to Accepted.  Per-stage evidence and the
residual risks each stage carries are recorded in `lang/code-identity.edn`;
that file, not this paragraph, is the current state.  Accepted describes the
delivery stages, not a claim that nothing remains — three risks are worth
naming here because they bound what the design now guarantees:

- **The source map exists but nothing consumes it yet.**  `link-source` returns
  a map from linked line to authoring module, so a project-build diagnostic
  *can* be translated back to the file the author wrote — but diagnostics still
  carry linked-source spans until whatever renders them does the translation.
- **The `typed-capability-v3` WIT profile is JVM-only** and is therefore the one
  part of the WIT layer with no cross-implementation evidence.
- **Receipts are compared field-wise, not hashed**, so receipt bytes are not yet
  a cross-implementation contract.
