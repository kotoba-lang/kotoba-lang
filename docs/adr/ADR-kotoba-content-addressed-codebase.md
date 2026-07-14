# ADR — Kotoba content-addressed codebase over Kotobase/IPLD

- **Status**: Accepted — C1–C5 reference implementation complete
- **Date**: 2026-07-14
- **Deciders**: Jun Kawasaki
- **Artifacts**: `lang/semantic-code.edn`, `lang/semantic-conformance/`,
  `kotoba/src/kotoba/semantic_code.cljc`, and
  `kotobase/src/kotobase/code_graph.cljc`
- **Related**: `ADR-safe-capability-language.md`, `ADR-kotoba-package-cid-lock.md`, `ADR-kotoba-lang-profile.md`, `ADR-kotoba-rad-git-sovereign-repo.md`

## Context

Kotoba is already a capability-safe Clojure-shaped subset with deterministic
EDN IR, interprocedural effect checks, typed capability values, deny-by-default
Wasm execution, package/component CID locks, and signed execution receipts.
Kotobase already provides the complementary data plane: CID-addressed
DAG-CBOR/IPLD blocks, immutable commit history, Datom indexes, Datalog, and
time-versioned graph state.

The remaining split is that data and packages are content-addressed, while
language definitions are still primarily identified through source files,
namespaces, and symbols. A package lock can prove which source tree or Wasm
component was admitted, but it cannot identify one term or type independently,
reuse an unchanged definition across package versions, or express the exact
definition dependency graph.

Unison demonstrates a useful model: hash a normalized syntax tree whose
dependencies have already been replaced by hashes, and keep human-readable
names as separate metadata. This enables immutable definitions, precise
dependencies, rename-safe references, persistent compilation caches, and
transfer of only missing code. Kotoba should adopt those properties where they
strengthen its existing capability and IPLD architecture, without copying the
Unison language, abandoning Clojure-shaped source authoring, or weakening
Kotoba's package-authority and runtime-security boundaries.

Merely storing `.kotoba` source bytes in IPLD is insufficient. Formatting,
comments, local variable names, import aliases, and source ordering would still
change identity even when program meaning is unchanged. The required boundary
is a versioned **semantic definition CID** computed after safe reading,
resolution, checking, normalization, and dependency substitution.

## Decision

Adopt a staged design for a **Kotoba content-addressed codebase** in which:

1. source files remain the primary human authoring and Git interchange surface;
2. checked top-level terms, types, and recursive definition groups gain semantic
   CIDs derived from canonical IR;
3. resolved references inside stored definitions use definition CIDs, not names;
4. names and namespaces are immutable, versioned mappings to definition CIDs;
5. Kotobase stores and indexes code blocks, data blocks, namespace history,
   artifacts, and execution provenance on one IPLD substrate;
6. CID identity remains distinct from publisher authority and runtime
   capability; and
7. adoption proceeds from pure definitions to capability-bound distributed
   execution, rather than replacing the existing toolchain in one step.

This ADR establishes the architecture and its v1 reference implementation. It
does not change `lang/profile.edn`, remove file/Git authoring, or weaken the
current package contract. Definition CIDs are stable only within the explicitly
versioned `kotoba.semantic-definition.v1` contract and profile CID; a future
contract version produces a distinct identity domain rather than silently
rewriting v1 identities.

## Identity Layers

Kotoba must keep three content identities rather than treating one hash as all
forms of identity:

| Identity | Hashes | Changes when | Purpose |
|---|---|---|---|
| `source-cid` | original source bytes | comments, formatting, or source text changes | audit, editing, exact source recovery |
| `definition-cid` | canonical semantic definition block | checked meaning, dependencies, type/effects, or semantic contract changes | code reference and dependency identity |
| `artifact-cid` | emitted Wasm/component bytes | backend, toolchain, optimization, or binary changes | execution and deployment identity |

The derivation graph is explicit:

```text
source CID
  -- reader/profile + analyzer + semantic-hash contract --> definition CID
  -- backend/toolchain + build policy -------------------> artifact CID
```

A receipt must never claim that equality of source CIDs implies equality of
artifacts, or that equality of definition CIDs implies byte-identical artifacts.

## Semantic Definition Block

The semantic hash input is a canonical DAG-CBOR/IPLD block conceptually shaped
as follows:

```clojure
{:kotoba.code/version 1
 :definition/kind :term
 :definition/ir
 {:op :fn
  :params [0]
  :body {:op :call
         :callee #ipld/link "bafy...plus-definition"
         :args [{:op :local :index 0}
                {:op :literal :value 1}]}}
 :definition/type #ipld/link "bafy...type-definition"
 :definition/dependencies [#ipld/link "bafy...plus-definition"]
 :definition/effects #{}
 :definition/profile-cid #ipld/link "bafy...profile"
 :definition/hash-contract
 {:version 1
  :alpha-normalization :de-bruijn
  :metadata-policy :semantic-only
  :recursive-groups :scc-v1}}
```

The concrete schema is deferred, but its hashing rules must satisfy these
invariants:

- local binder names are alpha-normalized and do not affect identity;
- global term/type references are resolved to definition CIDs before hashing;
- map/set ordering and numeric/string encodings are canonical;
- comments, formatting, source paths, aliases, and non-semantic metadata do not
  affect semantic identity;
- types, effect rows, and capability-typed parameter contracts that affect safe
  execution do affect identity;
- the source-profile and semantic-hashing contract are explicitly versioned;
- unknown semantic metadata is rejected rather than silently omitted; and
- a stored block is accepted only after recomputing and verifying its CID.

### Recursive definitions

Mutually recursive definitions cannot be hashed independently before their
references exist. The analyzer must find strongly connected components and
hash a canonical recursive group. References within a group use stable member
indices; references outside the group use definition or group CIDs. Group
member ordering must be structural and deterministic, not source-order-based.

### Macros and reader behavior

Definition identity is computed after expansion, because hashing an unexpanded
macro call would hide changes in the macro implementation. Safe Kotoba therefore
permits semantic hashing only when expansion is deterministic and closed over
explicit inputs.

- the source block preserves the pre-expansion form;
- the semantic block stores the checked expanded IR;
- the expansion provenance records macro definition CIDs and the reader/profile
  contract CID;
- reader evaluation and expansion that depends on ambient classpath,
  filesystem, network, clock, randomness, or undeclared host state is not
  eligible for a stable definition CID; and
- changing a macro in a way that changes expansion changes all affected
  definition CIDs through ordinary dependency propagation.

## Names and Causal Namespace Commits

Names are human-facing metadata, not definition identity. A namespace commit is
an immutable mapping from names to definition/type CIDs with one or more parent
commits:

```clojure
{:kotoba.namespace/version 1
 :namespace/parents [#ipld/link "bafy...parent"]
 :namespace/bindings
 {"math/increment" #ipld/link "bafy...definition"
  "math/inc"       #ipld/link "bafy...definition"}}
```

Consequences:

- one definition may have multiple names;
- rename changes the namespace commit but not the definition or its dependents;
- separate branches may bind the same name to different definitions;
- ambiguous merged names can be displayed with hash qualification;
- existing `.kotoba`/`.cljc`/`.clj`/`.cljs` namespace resolution remains the
  authoring-time resolver and projection layer; and
- source export remains available so the database never becomes the only way
  to recover reviewable code.

Namespace history is not execution authority. Moving a name to a new CID does
not mutate deployed code or authorize the new target; deployment and execution
must explicitly select a code root and pass admission again.

## Kotobase Code Graph

Kotobase should store code and data on the same IPLD block substrate but under
distinct, versioned schemas. Sharing storage does not collapse their semantics.

The first-class code graph contains at least:

- `CodeDefinition` and `TypeDefinition`;
- `RecursiveGroup`;
- `DependencyEdge`;
- `SourceObject` and source-to-definition derivation;
- `NamespaceCommit`;
- `WasmArtifact` and definition-to-artifact derivation;
- `CompilerContract` / semantic-hash contract;
- `TestResult` and analyzer/audit result; and
- `ExecutionReceipt`.

Their Datom projection must support dependency closure, reverse-dependency and
impact analysis, type/effect search, capability inventory, artifact lookup,
vulnerability propagation, deployment inventory, and code-to-data provenance.

Example questions include:

- which public definitions transitively require `:graph-write`?
- which deployments depend on a vulnerable definition CID?
- which Wasm artifact was derived from this definition under this compiler
  contract?
- which code, policy, grant, and input graph produced a data commit?

## Capability and Authority Boundary

Content identity is not authority:

```text
known CID != permission to read or execute it
valid code CID != trusted publisher
data CID != GraphReadCap or GraphWriteCap
```

The architecture preserves separate responsibilities:

| Mechanism | Responsibility |
|---|---|
| definition/data/artifact CID | content integrity and immutable identity |
| repo RID, publisher DID, signature | provenance and publication authority |
| package lock and admission policy | dependency acceptance |
| CACAO and capability value | scoped runtime authority |
| local/surface/runtime policy | final least-privilege intersection |
| receipt | evidence of grant, denial, execution, and result |

The transitive effect closure of a code root must be computed from verified
definition blocks and checked against the effective capability intersection
before execution. A code CID cannot grant itself a capability.

## Execution Receipt

Every content-addressed execution records or links at least:

```clojure
{:kotoba.execution/version 1
 :execution/code-root-cid #ipld/link "bafy...definition"
 :execution/code-closure-cid #ipld/link "bafy...closure"
 :execution/artifact-cid #ipld/link "bafy...wasm"
 :execution/compiler-contract-cid #ipld/link "bafy...compiler"
 :execution/input-root-cids [#ipld/link "bafy...data-commit"]
 :execution/output-root-cids [#ipld/link "bafy...result-commit"]
 :execution/package-lock-cid #ipld/link "bafy...lock"
 :execution/policy-cid #ipld/link "bafy...policy"
 :execution/grant-cids [#ipld/link "bafy...cacao"]
 :execution/host-receipt-cids [#ipld/link "bafy...host-call"]
 :execution/outcome :success}
```

The closure CID commits to the exact reachable definition/type graph. Host-call
receipts continue to record concrete post-intersection capabilities, resources,
actions, expiry decisions, and outcomes under the existing capability-value
semantics.

This connects, without conflating, **what code ran**, **what data it observed or
produced**, **who authorized it**, and **which host effects actually occurred**.

## Distribution and Caching

A node receiving a code-root CID may:

1. walk its verified dependency closure;
2. request only missing IPLD blocks;
3. verify definition, package, publisher, and compiler contracts;
4. compute the required type/effect/capability closure;
5. reuse a previously verified artifact for the same derivation inputs;
6. execute the artifact in the capability-confined Wasm host; and
7. commit an execution receipt and output graph root.

Immutable definition identity permits persistent caches for parsing,
typechecking, safe analysis, pure tests, dependency audits, and compiled
artifacts. Cache entries must include every semantic or toolchain input they
claim to cover; definition-CID equality alone is insufficient for
backend-specific artifacts or environment-dependent tests.

## Privacy, Retention, and Garbage Collection

IPLD content addressing does not provide confidentiality. Publicly retrievable
blocks can disclose source, dependency topology, names, and small guessable
values. Private code and data therefore use the existing tenant authorization
and sealed/encrypted block-storage boundary. Plaintext CID, sealed-block CID,
and decryption authority remain distinct metadata.

Garbage collection is reachability plus retention policy, not simply
"unreferenced from the latest namespace". GC roots include:

- active namespace and release heads;
- admitted package and deployed service roots;
- actor/code roots referenced by retained data commits;
- execution receipts within audit-retention periods;
- legal holds and explicitly pinned research evidence; and
- compiler/profile contracts required to verify retained derivations.

Deleting a code block referenced by a retained receipt destroys replay and
auditability even if no current namespace names it.

## Delivery Phases

| Phase | Deliverable | Status | Compatibility boundary |
|---|---|---|---|
| C0 | Existing package, source-tree, component, and data CIDs | Existing | no definition identity |
| C1 | semantic definition/type/recursive-group CID contract for the checked subset | Implemented and conformance-tested | files and current namespaces remain authoritative authoring input |
| C2 | Kotobase code blocks, Datom projection, dependency/effect/type queries, persistent analysis cache | Implemented on portable `IStore` | additive read/query integration |
| C3 | immutable namespace commits, hash-qualified names, import/export projection | Implemented | current source resolver remains supported |
| C4 | capability-bound execution receipts linking code, artifact, data, grants, and policy | Implemented | existing admission and host gates remain mandatory |
| C5 | missing-block code sync, artifact reuse, and distributed execution coordination by code-root CID | Reference implementation complete | uses verified C1+ blocks through local or XRPC-backed `IStore`; host execution remains injected |

User-defined macros remain fail-closed in v1. Built-in checked forms are
intrinsics of the semantic contract rather than arbitrary expansion code.
Capability-bearing definitions can be hashed and indexed, but execution still
requires artifact verification, explicit authorization, grant/effect
intersection, local policy, and the existing confined Wasm host.

### Implementation Map

- `lang/semantic-code.edn` is the machine-readable v1 hashing and admission
  contract; `lang/semantic-conformance/` contains positive and negative vectors.
- `kotoba.semantic-code` implements canonical DAG-CBOR blocks, source,
  definition, type, recursive-group, namespace, closure, and receipt CIDs on JVM
  and ClojureScript. `kotoba check --kind semantic-code` exposes the identities.
  Its portable multiformats dependency is pinned to published commit
  `4ec0436938457cd06266d3a3a7e280e1155eade8`; no workspace-local dependency
  override is required.
- `kotobase.code-graph` verifies and stores those blocks, projects Datoms,
  answers dependency/effect queries, manages causal namespaces and analysis
  caches with explicit environment/input identities, reuses verified artifacts,
  performs authorization-gated two-store missing-block sync, coordinates
  authorized execution, records cross-contract migration attestations, and
  computes auditable retention/GC plans over any `IStore` implementation.
- Cross-repository integration tests admit compiler-produced type and definition
  blocks into Kotobase and round-trip namespace, closure, and execution-receipt
  links.

## Consequences

### Benefits

- Code, data, policy, authority, and execution evidence form one verifiable DAG.
- Rename and namespace reorganization no longer rewrite dependent definitions.
- Multiple versions of similarly named terms and types can coexist without
  name-based diamond conflicts.
- Unchanged definitions, analyses, pure tests, and artifacts can be deduplicated
  and cached across packages, branches, actors, and nodes.
- Fleet and edge deployment can transfer only missing dependency blocks rather
  than whole repositories or containers.
- Kotobase Datalog gains exact dependency, capability, vulnerability, deployment,
  and provenance queries.
- Execution receipts bind the exact checked code closure to the exact data roots
  and effective runtime authority.
- Safe Kotoba's restricted, deterministic surface makes semantic hashing more
  feasible than retrofitting unrestricted JVM Clojure.

### Costs and risks

- The semantic-hashing contract becomes a long-lived compatibility surface.
- Deterministic macro expansion, mutual recursion, metadata policy, type/effect
  identity, and language-profile upgrades require precise specifications and
  adversarial conformance tests.
- Tooling must present database-backed code without making ordinary text review,
  Git exchange, or disaster recovery dependent on one codebase manager.
- Namespace merge UX, hash qualification, code GC, pin accounting, encrypted
  blocks, and migration between hash-contract versions add operational cost.
- A compiler or analyzer bug can assign a stable identity to incorrectly checked
  code; independent verification and versioned contracts are required.
- Fine-grained definitions do not remove package/repository trust, licensing,
  release, or vulnerability-management responsibilities.

## Rejected Alternatives

### Hash raw source files only

Rejected as the semantic identity. It is retained as `source-cid`, but it does
not provide rename independence, alpha-equivalence, precise dependencies, or
stable caches across formatting changes.

### Use Wasm artifact CID as the only code identity

Rejected. Artifact bytes include backend and toolchain choices, obscure
source-level type/effect dependencies, and cannot by themselves support
semantic rename or source analysis.

### Replace files, Git, and packages with a Kotoba codebase manager immediately

Rejected. Existing authoring, review, package authority, and repository
workflows remain valuable. The database begins as a verified semantic layer and
exportable projection, not an irreversible tooling migration.

### Treat CID possession as authorization

Rejected as a security violation. Content identity, publisher authority,
package admission, and runtime capability remain separate gates.

### Store code and data under one undifferentiated schema

Rejected. They share IPLD storage, Merkle linking, commit history, and query
infrastructure, but require different validation, privacy, retention, and
execution semantics.

## Resolved Decisions and Version Boundaries

The v1 implementation resolves the original design questions as follows:

- normalized resolved EDN IR, contract/profile links, type/effect/capability
  semantics, and dependency CID links participate in definition identity;
- recursive groups use canonical SCC encoding and member derivation, including
  source-order and binder-name invariance;
- user-defined macros and unsupported semantic metadata fail closed;
- code namespace history uses a narrow immutable namespace-commit codec; and
- Kotobase owns code admission, Datom projection, namespace operations,
  verified artifact/cache lookup, closure transfer, receipt validation, and GC
  planning;
- migrations are separate content-addressed, authority-checked attestations
  linking old/new contract domains; identities themselves are never rewritten;
- globally shared analysis/test caches require code-root, analyzer-contract,
  environment, and input CIDs; and
- sealed/private definition views disclose executable blocks and dependency
  metadata only after injected authorization. Unauthorized views contain only
  the definition identity, kind, visibility, and sealed-envelope CID.

User-defined deterministic macros, new algebraic/polymorphic type forms, an
interactive namespace merge UI, particular encryption/KMS providers, and a
particular HTTP/XRPC client are explicit future contract or host-adapter work,
not incomplete behavior in semantic contract v1. The portable core exposes
fail-closed macro/type admission, pure three-way merge conflicts, sealed-block
authorization seams, and `IStore`; deployments select UI, crypto, transport,
CACAO, retry, and scheduling policy without changing content identity.

## Acceptance Gates for C1

C1 is accepted because conformance fixtures and JVM/CLJS tests prove the
following:

- local binder rename, formatting, comments, import alias, and independent
  top-level source ordering do not change definition CID;
- literal, operator, resolved dependency, type, effect, capability-kind, or
  semantic-profile changes do change definition CID;
- equivalent canonical values encode to identical DAG-CBOR bytes on JVM and
  ClojureScript/Wasm-relevant implementations;
- unresolved names, unknown semantic metadata, user-defined expansion, and
  unsupported constructs fail closed;
- recursive groups are stable under source reordering and binder renaming;
- all referenced definition/type blocks are present and CID-verified before a
  definition is admitted;
- source, definition, and artifact identities remain distinguishable in CLI and
  receipt output; and
- no code CID bypasses package admission, CACAO verification, capability
  intersection, local policy, or Wasm host confinement.

## References

- `lang/semantic-code.edn`: semantic identity/admission contract v1.
- `lang/semantic-conformance/`: language-level conformance vectors.
- `kotoba-lang/kotoba/src/kotoba/semantic_code.cljc`: portable semantic codec.
- `kotoba-lang/kotobase/src/kotobase/code_graph.cljc`: portable code graph.
- `kotoba-lang/kotoba/src/kotoba/runtime.clj`: checked forms and deterministic EDN IR.
- `docs/lang/capability-values.md`: capability values, effect consistency,
  runtime intersection, and receipt requirements.
- `ADR-kotoba-package-cid-lock.md`: source tree, manifest, repo, and component
  identity plus publisher authority.
- `kotoba-lang/kotobase/README.md`: content-addressed Datom plane, Prolly Tree,
  immutable commit DAG, and repository-boundary caveat.
- Unison, *The big idea*: <https://www.unison-lang.org/docs/the-big-idea/>.
- Unison, *Abilities and ability handlers*:
  <https://www.unison-lang.org/docs/language-reference/abilities-and-ability-handlers/>.
