# Neutral execution and target-profile implementation report

2026-10-10 · ABI ownership refactor, implementation and coordinated dependency integration.

Neutral contracts now live in `kotoba.core.execution` in core-contracts. The
complete 11 neutral exports moved from ABI unchanged; four new exports define
closed v2 identity/lease shapes. The namespace has no imports. Component/WIT/WASI
contracts live in `kotoba.abi.component` (39 exports). `kotoba.abi.contract`
preserves all 46 legacy exports, including v1 identity/lease readers and vectors.

The complete production consumer inventory contains 17 namespaces across AMU,
grant, Kototama, Component generator/executor, Murakumo and Code Graph. Neutral
plan/ability/decision imports point inward; Component admission and v1 mixed
identity readers select the profile explicitly. Parent CLI, OS, storage,
placement and consensus manifests propagate the existing dependency floors.
Language, VM, authority and protocol semantics retain their owners.

## Versioned descriptors and compatibility

`execution-identity/v2` and `capability-lease/v2` use `:profile-binding-cid` and
artifact identity, without Component/WIT or provider-handle fields. Component
binding/v1 binds the target, profile, contract, interface and artifact CIDs with
Component/WIT/WASI fields. Explicit identity projection rejects missing or
mismatched bindings and supports inverse projection for compatible v1 values.
Hosts must verify canonical binding bytes/profile contracts and issue new
identity CIDs/signatures. Shape validation is not authorization or hash checking.
V2 lease issuance is separate work; no automatic lease conversion is supplied.

Legacy v1 wire fields, codec/hash rules, WIT, CIDs and signatures remain unchanged.
Default runtime admission remains v1. Code Graph explicitly accepts either
version and stores the v2 opaque binding index; it does not invent Component
fields for neutral identities. New native/Script/EVM bindings and runtime v2
admission remain profile-specific follow-up work. This CLJK API refactor is not
a whole-component Q9 source port.

## Dependency and architecture evidence

[Consumer inventory](../lang/execution-profile-consumers.edn) records all exports
and direct source consumers. [Immutable manifest observation](../lang/stack-dependency-observation.edn)
reads 17 Git source revisions and records all coordinate descriptors plus alias
extra/replace/override dependencies. [Dependency diagrams](stack-dependencies-current.md)
show 31 production direct edges and 24 alias edges separately. Other libraries
remain in the full coordinate records; this selection is not a transitive lock.
The intended contract DAG remains 22 edges, acyclic with no forbidden direct edge.
Core-contracts repo-wide codec/crypto dependencies do not belong to the zero-import
execution descriptor namespace.

The [architecture guide](stack-architecture-target-neutral.ja.md),
[composition spec](../lang/stack-architecture.edn),
[refactor procedure](stack-refactor-procedure.md) and
[Japanese presentation](presentations/kotoba-lisp-machine.ja.md) use the implemented
entrypoints. Responsibility, library, artifact and runtime/service graphs remain
separate. Holochain-inspired integrity, IPFS/IPLD content identity, protocol
replication and inga ordered-consensus domains retain their distinct contracts.
Required policy/shared-asset/shared-head finality cannot silently fall back to
causal replication; EVM output is separate from external settlement.

AMU compiler pins are in deps.edn and its regenerated 85-dependency lock. Its
existing nbb.edn stdlib bootstrap is deliberately unchanged: adding compiler
pins there starts bb/uberjar before the JVM-free lock resolver. CLI direct
frontend/security floors must not override APIs required by the selected AMU.
The procedure now requires this manifest/bootstrap/alias/lock closure check.

The CLI closure also pins the complete JVM EDN parser correction: the earlier
lexer-only fix left parser/form-span Character comparisons inconsistent. The
owner correction preserves real character literals and does not widen admission.
Security adoption remains fail-closed with its exact selected pin declared.

The target/host closure also includes the authority grammar bytes, actual
`.cljk` compiler resource revisions, the native executor's metered/unmetered
fuel schema, and the reviewed loader source/binary identity. A JVM project
loader fix accepts already resolved `Path` objects while preserving the closed
graph, namespace and real-path containment checks. Native exact-file grants
now open the file without symlink traversal; they never become parent-directory
grants. Directory beneath-resolution and opened-fd containment remain enforced.
The artifact owner binds the repaired source digest and requires explicit new
runtime trust. CLI loader caches use source bytes, flags and host profile,
not checkout timestamps. The shim still enforces its explicit 512 default;
AMU's native default may be unmetered under its own profile. Script budget
exhaustion is checked on the emitted guest and its failure receipt. Qualified
native browse routing includes entry counts and exact wire-34 listing bytes.
The source-bound native fuzz ratchet advances with the loader; all existing
x64/ARM64 coverage, feature and corpus minima and the 20,000-case condition
are retained. A carried-forward threshold is not a new coverage measurement;
Linux CI must produce the new source-bound coverage receipt.

## Verification

| Path | Result and boundary |
|---|---|
| Neutral descriptor API | 4 tests / 91 assertions; closed fields, CID shapes, missing bindings, invalid leases/refusals |
| ABI/profile/v1 compatibility | 22 tests / 187 assertions; v1 vectors, strict binding shape, explicit roundtrip and mismatch refusals |
| AMU portable suite | 81 tests / 576 assertions; all passing |
| AMU native default | 33 checks; generated native artifact executes and returns 42; explicit Wasm override and refusal paths preserved |
| AMU CI | Linux x64/ARM64, macOS, Windows ARM64, Android, server-kind and browser/Safari matrix passed on the integrated refactor |
| grant | 25 tests / 122 assertions; contract, decision and Component admission |
| Code Graph | 16 tests / 66 assertions; v1/v2 storage, opaque binding index and malformed-profile refusal |
| Component generator portable | 9 tests / 32 assertions; WIT capability IDs, host binding and admission |
| Kototama provider portable | 2 tests / 5 assertions; exact provider/grant/ability boundary |
| Host compatibility diagnostics | Kototama 11/40, Component core 3/17, executor 3/6; scratch read bridge, not native/Q9 qualification |
| Murakumo shipped-KIR subset | 3 existing pure authority tests / 14 assertions; real CID fixtures, state/epoch/refusal |
| Murakumo CI | All four repository jobs passed; signing/storage/HTTP qualification is not inferred from the portable subset |
| AMU JVM project seam | 1 regression / 3 assertions; root and transitive module from `Path` inputs; full NBB project integration passes |
| Native file scope | 4 real executions: exact-file and directory reads; sibling and ungranted symlink refusals; compiled with strict warnings |
| Loader sanitizer/fuzz | macOS ASan/UBSan deterministic 20,000-case run passes; Linux x64/ARM64 coverage gates retain their existing minima and bind the repaired loader fingerprint; final receipts in AMU PR 1279 |
| Artifact profile owner | SCI 20 tests / 102 assertions; old-source refusal, explicit trust, untrusted and revoked runtime refusal |
| EDN owner portability | JVM 3 tests / 11 assertions; SCI 34 tests / 420 assertions; parser/lexer agreement, escapes and malformed-input refusals |
| CLI host closure | Required CI: launcher 924 tests / 10,779 assertions and declared qualification 12 tests / 134 assertions, zero failures/errors; signed native + grammar 8 tests / 30 assertions locally |
| Documents and presentation | 53 EDN documents; 48 checked documents / 18 authorities; site locale tests 20,743 assertions; 123 generated pages keep identical script blocks |

The existing artifact full JVM suite has a `kerror/ExceptionInfo` class-resolution failure; its SCI suite is the portability evidence. The existing AMU JVM project suite has host-specific failures; only its new JVM seam regression and full NBB project integration are reported as passing.

The existing full Murakumo SCI authority suite fails at its clj-only signing
entrypoint on both unchanged main and this refactor; it is not reported as pass.
The compatible JVM diagnostics use scratch .cljc/import-metadata/error-class
adaptation and do not certify a product route. AiueOS CLJC CI has the same
`shared security source inventory denied` failure on unchanged main; its UEFI
smoke and docs checks are distinct evidence. No consensus network, physical
hardware, debugger/image restore, selfhost or C-free qualification is upgraded.

## Integration receipts

Normal PR merge state and contained source revisions are the integration
receipts. Related changes are landed in dependency order and checked against
fresh remote main; owner tooling advances only reachable forward west pins and
reconciles the generated register. Worktree retirement preserves unrelated WIP
and reusable caches.

| Owner | PRs |
|---|---|
| kotoba-core-contracts | [26](https://github.com/kotoba-lang/kotoba-core-contracts/pull/26) |
| abi | [23](https://github.com/kotoba-lang/abi/pull/23), [24](https://github.com/kotoba-lang/abi/pull/24) |
| amu | [1278](https://github.com/kotoba-lang/amu/pull/1278), [1279](https://github.com/kotoba-lang/amu/pull/1279) |
| grant | [11](https://github.com/kotoba-lang/grant/pull/11) |
| kototama | [152](https://github.com/kotoba-lang/kototama/pull/152) |
| kotoba-component | [122](https://github.com/kotoba-lang/kotoba-component/pull/122) |
| kototama-component | [16](https://github.com/kotoba-lang/kototama-component/pull/16), [17](https://github.com/kotoba-lang/kototama-component/pull/17) |
| murakumo | [479](https://github.com/kotoba-lang/murakumo/pull/479) |
| code-graph | [2](https://github.com/kotoba-lang/code-graph/pull/2), [3](https://github.com/kotoba-lang/code-graph/pull/3) |
| kotobase | [96](https://github.com/kotoba-lang/kotobase/pull/96) |
| aiueos | [420](https://github.com/kotoba-lang/aiueos/pull/420) |
| sahai | [10](https://github.com/kotoba-lang/sahai/pull/10) |
| inga | [26](https://github.com/kotoba-lang/inga/pull/26) |
| edn | [8](https://github.com/kotoba-lang/edn/pull/8) |
| artifact | [60](https://github.com/kotoba-lang/artifact/pull/60) |
| root | [3554](https://github.com/com-junkawasaki/root/pull/3554) |
| kotoba-lang | [758](https://github.com/kotoba-lang/kotoba-lang/pull/758) |
| kotoba | [639](https://github.com/kotoba-lang/kotoba/pull/639) |

Root legend/ADR and this language composition report retain the canonical
cross-repository explanation. Exact source revisions are in the observation
spec; PR links distinguish API implementation from later dependency-only updates.

## Next complete migration unit

Use the updated procedure: qualify one complete target/host identity + lease
issuance/admission component with a canonical verified binding codec, old-data
compatibility/refusals, generated artifact execution and receipt integrity.
Native/Script/EVM and distributed consistency guarantees require their own
qualified profile; do not enable v2 globally through a namespace or pin update.
Q9 acceptance remains whole-component, native Kotoba and AMU --jvm-free.
