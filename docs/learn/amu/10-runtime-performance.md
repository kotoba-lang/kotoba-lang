# 10 Making generated code faster

**English** | [日本語](10-runtime-performance.ja.md)

Optimization performs the same computation at lower cost. In Amu, correctness includes fuel, traps, effects, and arena observations as well as result values. Removing checks to improve timing is not sufficient grounds for adoption.

## Locate the cost

Repeated expressions, calls, register/stack movement, branches, buffer accesses, dynamic type work, bounds checks, and fuel updates can all cost time. What can be reduced depends on known values, types, aliases, and effects. The constant `40 + 2` in chapter 8 is the smallest example of knowing a result before execution.

## Techniques to look for

| Technique | How it reduces work | Conditions to preserve |
|---|---|---|
| Propagate constants and value facts | Avoid repeated computation and branching; use immediates and suitable instructions | i64 wrap behavior, types, exceptions and traps |
| Inline small functions and use tail calls | Reduce calls, frames, and moves | Argument order, live values, ABI, fuel |
| Recognize structural native kernels | Emit suitable instruction sequences for recognized computations | Structural guards rather than benchmark names |
| Unroll loops and use SIMD | Reduce branch overhead or process multiple elements per instruction | All elements, tails, bounds, CPU support |
| Build tables from constants | Replace repeated decision-tree traversal with lookup | Values fully determined by the original function |
| Hoist or batch checks | Establish conditions earlier and reduce redundant decisions | Matching insufficient-fuel and invalid-index behavior |

`41-a64gen` contains many recognition and emission paths. Read their entry predicates and fallback paths rather than assuming that every listed technique applies to all inputs. Historical candidates are not necessarily the adopted source.

## Turn a decision tree into a table

The scalar threshold-tree experiment recognizes a pure tree that branches on constant thresholds and ends in integer constant leaves. After checking the complete admitted shape, it replaces traversal with a 64-entry table and endpoint handling. Unknown effectful calls and dynamic leaves do not qualify. The table is derived from the original function's information, not selected answers keyed by benchmark name. Read the [experiment record](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/docs/coscientist-scalar-tree-20261005.md) for its verification and measurement scope.

## Preserve fuel and bounds checks

Known iteration counts or index ranges can allow repeated checks to be combined. If the budget expires partway through, execution must still stop at the corresponding point with the appropriate changes already made. A sufficient-fuel fast path plus an original-order path also needs that argument. The [sufficient-fuel region experiment](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/docs/coscientist-fuel-region-20261005.md) passed correctness comparisons but failed its performance criteria and rejected global promotion. Correctness and speed are separate observations.

Fuel semantics are profile-specific. Do not equate ordinary native settings with a metered receipt profile. Reducing CPU instructions does not automatically permit reducing semantic charges.

## Read the public speed figures

The 15 October 2026 Apple M4 observation covered 19 translated Embench ports with one compiler snapshot and one accepted sample per workload. Its geometric mean execution time was about `0.80×` C time. The number includes audited algorithm substitutions and a SHA-instruction difference. It is not a released-toolchain result, certified score, or universal speed ranking. Read the [public article and audit](https://kotoba-lang.org/blog/embench-c-parity-20261015/) with the ratio.

The separate same-algorithm M104 baseline was about `0.8213×`; it is a different measurement. Neither historical result is a new measurement of today's loaderless native compiler. The [compiler lineage](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/compiler-core.json) records the distinction.

## Exercise and answer

If output is unchanged and code is 10% smaller, is it 10% faster? Not necessarily. Dependencies, caches, branches, and timing variability affect execution. Check non-value semantics too, then measure under matching workload, host, and compiler conditions.

[Previous](09-kototama.md) · [Contents](README.md) · [Next: build performance](11-build-performance.md)
