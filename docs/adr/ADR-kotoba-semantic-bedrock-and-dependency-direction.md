# ADR — Kotoba semantic bedrock and dependency direction

- **Status**: Accepted
- **Date**: 2026-08-03
- **Scope**: Kotoba language semantics, Kotobase boundary, content identity,
  binary artifacts, and the production dependencies of this repository
- **Related**: `ADR-kotoba-canonical-value-codec.md`,
  `ADR-kotoba-code-identity-and-abilities.md`,
  `ADR-kotoba-content-addressed-codebase.md`,
  `ADR-safe-capability-language.md`,
  `90-docs/adr/2607266000-kotoba-stack-responsibility-split-t0-t3.edn`,
  `90-docs/adr/2607201600-kotobase-merkle-lsm-istore-retirement.edn`

## Context

Kotoba combines a language, a Datomic-shaped database, content-addressed code
and data, Wasm/native execution, and several storage and network providers.
This makes the word *core* ambiguous.  KIR, datoms, IPLD, IPFS, a mutable
database head, and hardware can each appear to be the bottom layer when viewed
from a different direction.

Three different dependency directions must not be collapsed:

1. **Physical causation is bottom-up.** Hardware state changes cause bytes to
   move and instructions to execute.
2. **Design justification is top-down.** Required observations and identities
   determine semantics; semantics determine encodings and mechanisms.
3. **Performance feedback is bottom-up.** Memory capacity, locality, network
   request cost, storage bandwidth, and accelerator alignment constrain the
   physical plan without silently changing the semantics.

The language therefore needs a semantic bedrock that remains stable across
CPU, GPU, Wasm, native code, browser, object storage, and IPFS deployments. It
also needs an explicit rule for large opaque artifacts such as images, video,
checkpoints, and model weights. Treating a storage provider as semantic
authority would make language or database meaning change when that provider is
replaced. Treating a CID as semantic equivalence would confuse exact bytes with
decoded pixels, tensor values, program behavior, or domain meaning.

This ADR also evaluates the actual dependency graph of this repository as of
2026-08-03. The default `deps.edn` production classpath contains Clojure,
`io-multiformats`, `org-ietf-ed25519`, `security`, and
`kotoba-core-contracts`. `compiler` is confined to the
`:pure-product-examples` alias. Source inspection finds that:

- `multiformats.core` is used by definition/package CID validation and the
  package-registry network adapter;
- `ed25519.core` is used only by release-tag verification;
- `kotoba.security.*` is used only by release publication admission;
- the core language namespaces do not depend on Kotobase, IPLD storage, IPFS,
  a database provider, a compiler backend, or a runtime; and
- `kotoba.lang.package-registry-network` is a JVM HTTP/IPFS-gateway adapter in
  the default `src` path.

The workspace role authority already records the production edge
`kotoba-lang/kotoba-lang` (T1 library) -> `kotoba-lang/security` (T4 assurance)
as an unresolved responsibility inversion rather than an allowed edge. This
ADR resolves the architectural question and records the repository split
implemented with it.

## Decision

### 1. Observation and equivalence are the semantic bedrock

The first design questions for a Kotoba subsystem are:

1. Who or what observes the result?
2. Which distinctions must survive?
3. When are two values, states, definitions, or artifacts considered equal?
4. Which state transitions and external effects are permitted?

Bits are the lowest physical representation, but they are not the lowest
useful design abstraction. The lowest stable design contract is the set of
observable distinctions and equivalence relations. Every cache key, datom
identity, definition CID, transaction boundary, merge rule, and capability
check depends on that contract.

### 2. Value semantics precede, but do not dominate, information and computation

Kotoba adopts this partial order:

```text
observation / distinction / equivalence
                  |
          canonical value semantics
             /                 \
information and time          computation and effect
datom/entity/schema/tx        typed KIR/type/fuel/ability
             \                 /
        provenance and authorized publication
                  |
      canonical representation and identity
                  |
       indexes / storage / network / hardware
```

A computation is a relation or function over values, so a minimum value domain
and equality contract logically precede KIR operations. After that point there
is no total order between database semantics and computation semantics:

- information semantics says what is asserted, retracted, related, and known
  at a database value;
- computation semantics says how admitted values are transformed and which
  effects are requested; and
- a transaction itself is a state transition
  `(db-before, tx-data) -> db-after | conflict`.

Kotoba KIR and the Datomic-shaped datom model are therefore sibling semantic
cores over the same canonical value discipline. Neither is implemented in
terms of IPFS, and neither subsumes the other.

### 3. Identity is stratified; CID is not semantic equivalence

The following identities remain distinct:

| Identity | Equality witnessed |
|---|---|
| value identity | equality under the versioned Kotoba value contract |
| source CID | exact authored source representation |
| definition CID | canonical typed KIR plus its versioned semantic closure |
| artifact CID | exact emitted component/file bytes |
| database root CID | exact immutable database snapshot/manifest graph |
| entity/domain identity | equality declared by schema and domain rules |

A CID witnesses the identity and integrity of a chosen representation. It
does not prove that two differently encoded images look the same, two weight
formats contain equal tensors, two programs are extensionally equivalent, a
publisher is trusted, or execution is authorized.

Where a use case requires more than exact-byte identity, it must define a
separate versioned normalization and identity domain. For example, a model may
have an artifact CID for exact safetensors bytes and a model identity over its
architecture, tensor manifest, tokenizer, and numeric representation. General
program behavioral equivalence is not used as an identity test; it is not
decidable in general.

### 4. CID, IPLD, IPFS, and IPNS occupy different layers

| Mechanism | Role in Kotoba |
|---|---|
| CID | representation identity and integrity |
| IPLD/DAG-CBOR | canonical structural/link and traversal boundary |
| IPFS | optional content discovery, transfer, and caching provider |
| IPNS | optional mutable naming/publication mechanism, single-writer unless an external linearizable coordinator is supplied |
| HeadCAS | database publication/serialization mechanism |

IPLD may be the canonical persisted block representation without becoming the
language or database semantic authority. IPFS remains one provider beside
SQLite, PostgreSQL, S3/R2, local files, caches, and future transports. A
multi-writer database must not infer transaction ordering or linearizability
from content addressing or IPNS.

### 5. Large binary artifacts use a metadata/blob split

Images, audio, video, datasets, checkpoints, and model weights are first-class
Kotoba/Kotobase objects, but their large byte payloads do not belong in datom
covering indexes.

```text
datom plane                         immutable artifact plane
identity, CID links, media type     raw/chunked/sharded bytes
shape/dtype/schema/provenance       artifact and chunk manifests
authorization, lifecycle, history   S3/R2/IPFS/local NVMe/cache
```

Publication writes and verifies immutable blocks first, then atomically makes
their manifest CID reachable from a datom transaction/database root. A failed
head CAS leaves unreachable blocks eligible for policy- and retention-aware
garbage collection. Retracting a datom reference is not immediate secure
erasure; reachability, pins, replicas, retention roots, legal holds, encryption
keys, and GC determine physical deletion.

Chunking is a versioned physical-layout contract, not domain identity. It must
balance verification and deduplication against request amplification, range
access, sequential throughput, accelerator alignment, and bounded RAM/VRAM.
Hot model payloads may use local NVMe, mmap/O_DIRECT, object-range reads, or a
direct accelerator I/O path while preserving the same artifact identity and
datom metadata.

### 6. Dependency direction follows semantic authority

For the T1 language/profile repository, production dependencies may point to:

- smaller value, grammar, schema, and contract authorities;
- pure identity/codec primitives required to implement a normative language
  identity contract; and
- host-language libraries used as replaceable implementation substrates,
  provided conformance tests prevent host quirks from becoming semantics.

The T1 language/profile repository must not have a production dependency on:

- Kotobase or a database/storage provider;
- IPFS, HTTP, filesystem, cloud, or accelerator adapters;
- compiler backends, component tenders, execution runtimes, or fleet placement;
- release approval, hardware signing, operational resilience, or deployment
  policy implementations; or
- application/domain actors.

Conformance tooling may depend on the compiler, KIR interpreter, Wasm backend,
or other implementations only through explicit development/test aliases. Such
dependencies test the language contract; they do not define it.

## Current dependency evaluation

| Current edge or source boundary | Verdict | Reason / required action |
|---|---|---|
| `kotoba-lang -> Clojure` | appropriate with constraint | Clojure is the current implementation substrate; JVM behavior must not become normative without CLJ/CLJS/KIR/Wasm conformance evidence |
| `kotoba-lang -> io-multiformats` | appropriate but narrow | required for structural CID validation and definition/artifact identity; it must not grow into an IPFS/storage dependency or define value/KIR semantics |
| `kotoba-lang -> kotoba-core-contracts` | semantically appropriate, mechanically unclear | direction is downward to contracts, but current `src` has no direct namespace use; either make the consumed contract explicit or move the dependency to the conformance alias that needs it |
| `kotoba-lang -> org-ietf-ed25519` | misplaced on the default classpath | only release-tag verification uses it; move `version-policy` signing verification to release/assurance tooling or an explicit tooling alias |
| `kotoba-lang -> security` | inappropriate, known inversion | only release publication admission uses it; move `kotoba.lang.release-admission` out of the T1 language library, then remove the workspace exception |
| `src/kotoba/lang/package_registry_network.clj` | inappropriate source ownership | JVM HTTP and an IPFS gateway are adapter mechanisms; move them to a package-registry adapter/tool repository or an explicit non-production extra path |
| `:pure-product-examples -> compiler` | appropriate | compiler/KIR execution is isolated in an explicit conformance alias and does not define the production language classpath |
| no production edge to Kotobase/IPLD storage/IPFS/runtime/provider/fleet | appropriate and required | preserves the distinction between semantics, representation, execution, and placement |
| default `:paths ["src" "."]` | too broad | the repository root weakens the classpath boundary; converge on explicit `src`/`resources` and explicit tooling paths after callers that rely on root-relative resources are inventoried |

Overall verdict: **the main dependency direction is sound, but the repository
is not yet cleanly T1**. Its semantic core is independent of Kotobase,
storage, IPFS, execution, and placement, which is the most important invariant.
The remaining violations are operational/release mechanisms colocated with the
language contract: `security`, Ed25519 release verification, the HTTP/IPFS
registry adapter, and an overly broad root classpath.

## Responsibility split

1. `kotoba-lang/release` owns release admission, Ed25519 tag verification, and
   its direct dependency on `kotoba-lang/security`.
2. `kotoba-lang/package-registry-ipfs` owns JVM HTTP/IPFS retrieval and consumes
   the pure package registry contract from this repository.
3. This repository retains `lang/version-policy.edn`, the deterministic
   compatibility evaluator, and the pure package registry kernel.
4. The default classpath is narrowed to explicit `src` and `resources` paths;
   compiler/KIR/backend dependencies remain in conformance aliases.
5. Decide whether `kotoba-core-contracts` is a true runtime contract import or
   conformance-only evidence, and declare it accordingly.
6. Keep the repository-role/dependency gate rejecting new T1 production edges
   to assurance, runtime, provider, placement, database, or network adapters.

These are responsibility moves, not changes to Kotoba source semantics.

## Consequences

- Kotoba semantics remain portable across hardware and storage providers.
- Datoms and KIR share a value foundation without collapsing information and
  computation into one model.
- Content addressing can be used pervasively without treating integrity as
  authorization or exact bytes as domain equivalence.
- Large artifacts can participate in transactions, provenance, and Datalog
  queries without polluting covering indexes or forcing database pages to
  carry accelerator-scale payloads.
- Release and network tooling become consumers of the language contract rather
  than authorities imported by it.
- Hardware-specific optimization remains possible below stable manifests and
  semantic identities.

## Non-goals

- Selecting one permanent storage or network provider.
- Claiming that CID equality proves semantic or behavioral equivalence.
- Embedding arbitrary binary payloads directly in datom indexes.
- Replacing benchmark-driven physical planning with a universal chunk size.

## Verification evidence

The assessment is based on:

- `deps.edn` and `clojure -Stree` from this repository on 2026-08-03;
- production namespace references under `src/`;
- `resources/repository-rules.edn`;
- the workspace exception
  `:kotoba-lang-library-depends-on-assurance` in
  `manifest/repository-rules.edn`; and
- the existing KIR, value-codec, content-addressed codebase, storage, and stack
  responsibility ADRs listed above.
