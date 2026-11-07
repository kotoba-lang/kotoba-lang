# 12 Reading the implementation and running experiments

**English** | [日本語](12-reading-and-experiments.ja.md)

You have followed source, forms, checking, IR, identity, artifacts, and execution. Next, choose one behavior and connect its input to an observable result.

## Read in this order

Start with `seed/MANIFEST` and `FULL-MANIFEST` to determine whether a file belongs to the adopted closure. Follow `90-drv` through `lex → read → names → check → lower → gen → layout → out`. Read module contracts, success/refusal behavior, and corresponding unit fixtures before attempting the entire backend.

| Question | Where to look | What to observe |
|---|---|---|
| Name errors | `20-names`, `21-check` | Check refusal and source position |
| Loop semantics | `30-lower`, `seed/SIR` | Parallel recur updates, result, fuel |
| Constant emission | `41-a64gen`, `40-a64enc` | Artifact bytes, instructions, execution |
| Branch distances | `42-layout` | Fixups and out-of-range refusals |
| Definition identity | code-identity and standalone native producer | Canonical payload bytes and DefCIDs |
| Execution permission | Kototama `eligible?` and `admit` | Refusals for missing origins |

## Connect this course to Simple

[Simple](https://github.com/SeaOfNodes/Simple) grows a complete working compiler chapter by chapter, explaining Sea-of-Nodes IR, SSA, types, optimizations, and backends. You can borrow its progression from integers through arithmetic, locals, conditionals, loops, calls, and code generation. That does not mean Amu's native core uses the same SoN representation throughout.

Amu has a [rooted-rewrite diagnostic referencing Simple chapter 9](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/docs/coscientist-rewrite-proof-20261006.md). Its reverse-use worklist illustrates how a changed node causes dependent users to be reconsidered. It does not claim a complete SoN/SSA conversion or a faster product emitter.

## Complete a small experiment

1. State a hypothesis, such as: renaming a local does not change the result.
2. Fix the inputs and expected outputs first. Keep the complete original case instead of shrinking it to a convenient fragment.
3. Include an incorrect change, such as renaming the binding but not its use.
4. Check and run with the same compiler, recording exit, stdout, stderr, input hashes, and artifact hashes.
5. State a conclusion within the hypothesis's scope. Without observing DefCIDs, you have not demonstrated identity equality.

For performance, establish correctness first, then pin compiler and source, target and CPU, optimization settings, runtime profile, input, warm/cold conditions, trial ordering, host load, and exclusion rules. Keep accepted and rejected samples, not just aggregates. Differences below noise and smaller code alone do not establish a speed improvement.

## Final exercise and answer

Explain four things about the `double` example:

- How does renaming its local argument affect the result and normalized definition?
- How does changing `double`'s computation affect a `main` that seals its dependency?
- Why can the same definition generate machine code for a different target?
- Why does retrieving cached machine code not authorize an operation under an expired grant?

The answers connect binding-preserving alpha normalization, dependency-CID propagation, separation of meaning from artifacts, and separation of immutable code identity from current admission. Explaining them together connects the compiler, IPLD, and Kototama by their responsibilities.

[Previous](11-build-performance.md) · [Contents](README.md) · [Example observations](examples/README.md)
