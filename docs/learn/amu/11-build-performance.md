# 11 Faster builds and selfhosting

**English** | [日本語](11-build-performance.ja.md)

Generated-program runtime and compiler build time are different performance questions. Repeated AI generation, checking, and repair makes build latency part of the total workflow cost too.

## Reduce the compiler's own work

Native Amu work has investigated repeated label scans over large SIR, inference rounds, and mutation tests that rebuild whole compiler images. An index over immutable SIR can avoid repeated scans, but changing the SIR invalidates it. Equal CODE/FIX counts do not justify caching proofs over mutable emission state.

Explicit parameter types can avoid repeated inference work. This is distinct from a new inference algorithm or faster generated code. Whole-image equality and execution comparisons against an unannotated counterpart check that the annotation did not change behavior.

The [Amu shared rules](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/AGENTS.md) document these conditions under Immutable SIR label proofs and Explicit selfhost parameter types. Longest-first gate scheduling and limits on concurrent builds are operational improvements, separate from compiler algorithms.

## What DefCID-based reuse needs

An unchanged definition suggests reusing an object. A build key must also bind the definition or SCC identity, compiler image, target/ABI, layout, optimization settings, dependency/interface closure, and resource-proof contract.

```text
DefCID/SCC + compiler + target/ABI + options + dependencies + proof contract
    → build key → verified relocatable object → link in the current layout
```

Previously linked addresses may not fit the current layout, so symbolic relocations must remain. Corrupt or mismatched cache entries lead to rebuild or refusal. Current admission, effects, grants, and mutable remaining fuel are not reusable immutable code.

Native identity, SCC, and object-store prototypes each have bounded checks. Complete source-to-checked-KIR production, whole-compiler dependencies, persistent storage, cold/warm mutation builds, and end-to-end CI timing have separate acceptance criteria. Consult the [incremental-build record](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/docs/native-incremental-build.md); content addressing alone does not establish fast builds throughout the system.

## What selfhosting checks

Selfhosting means Amu compiles Amu's source. Its closure is larger than a tiny example: compiler, CLI, build, and packaging dependencies matter too. The current direction uses canonical Kotoba and an Amu-built native compiler without falling back to Node, JVM, or a C compiler/loader.

```text
initial compiler → build source → F1
F1               → build the same source → F2
F2               → build the same source → F3
```

Matching generations are strong observations of stable production for those sources and conditions. They do not alone prove compiler correctness, all language features or targets, or the trustworthiness of an independent initial bootstrap. Semantic corpora, refusals, execution, and resource observations remain necessary. Historical C-loader fixed points and current loaderless native fixed points are separate records.

## Exercise and answer

The DefCID and CPU are unchanged, but the compiler image changed. Can the old object be a hit under this build-key design? No. The compiler's emission rules or fixes may differ. A hit establishes matching reuse conditions, not a completed current safety decision.

[Previous](10-runtime-performance.md) · [Contents](README.md) · [Next: reading and experiments](12-reading-and-experiments.md)
