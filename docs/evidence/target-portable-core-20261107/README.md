# Target-portable core: one-time measurements, 2026-11-07

Evidence for [`ADR-target-portable-canonical-form.md`](../../adr/ADR-target-portable-canonical-form.md).
Linear: JUN-947, JUN-949.

These are one-time local measurements under the workspace's reference-oracle
rule. Nothing here belongs in CI, a gate, a build or an execution path. The values
show what the tools did on this date; they do not prove any implementation correct
and they qualify nothing.

## Environment

| Tool | Version or identity |
|---|---|
| Host | macOS, Apple Silicon |
| `bin/amu` (native baseline) | amu `a4c16e7c4`, behind origin; `amu-cli.macho` sha256 `72e26e48e58d…c0b310` |
| `amu-full-root.macho` (target tables) | amu origin/main `1754ff26f`, sha256 `35c1f9f4105f…d2c0fcc3`, given an FD3 directory root |
| clang | Apple clang 21.0.0 |
| javac / java | 21.0.1 |
| python3 | 3.15.0, run with `-I` |
| deno | 2.7.14 |
| node | 26.0.0, used only to run emitted modules with stub hosts |
| rustc | Not installed, so Rust was **not measured** |

The native baseline is older than the target tables. The origin `amu-cli.macho`
extracted on its own refused with `EPROVENANCE native packager digest mismatch`,
so the baseline could not be retaken on origin.

## Files

| File | What it holds |
|---|---|
| [`spike.py`](spike.py) | Throwaway i64-subset emitter to C, Java, TS and Python, sharing no code with Amu |
| [`spike-results.txt`](spike-results.txt) | Its 15 programs in contract and naive modes against `bin/amu` |
| [`naive/`](naive/) | Hand-written primitives per language, and [`naive/output.txt`](naive/output.txt) |
| [`conformance-compile.tsv`](conformance-compile.tsv) | Target, code, file, first error line for 112 files in `lang/conformance` on 3 targets |
| [`core-head-admission.tsv`](core-head-admission.tsv) | Name, source, result per probed head on js-browser |
| [`pure-form-schema-check.cljs`](pure-form-schema-check.cljs) | Positive and negative forms against `lang/pure-form.ipldsch` |
| [`pure-form-schema-check.txt`](pure-form-schema-check.txt) | Its output |

## Commands

```text
python3 -I spike.py <amu-binary> <empty-output-dir>
<amu-full-root.macho> compile FILE.kotoba --target TARGET --output OUT 3<.
```

## Results

### Independent emitter

| Mode | C | Java | TS | Python |
|---|---|---|---|---|
| Contract (`seed/SIR` rules) | 14/15 | 13/15 | 13/15 | 13/15 |
| Naive (negative control) | 9/15 | 11/15 | 5/15 | 4/15 |

The misses in contract mode are `mutual_tail_1e6` (stack overflow in Java, TS,
Python; no trampoline in the spike) and `deep_nontail_1e8` (Amu native itself exits
139; clang `-O2` turned the recursion into a loop and returned a result).

Limits: `i64`, `loop`, calls and arithmetic only. It reads the surface spelling Amu
accepts today, not the pure core, and has no strings, collections, closures, fuel
or capabilities.

### Conformance admission

Files under a `negative` directory or named malicious count as negative.

| Target | Positive compiled (of 86) | Negative refused (of 26) |
|---|---|---|
| aarch64-macos | 48 | 26 |
| wasm32-browser | 29 | 26 |
| js-browser | 29 | 26 |

wasm32-browser and js-browser agree on 112/112. Their most common refusals on
positive files are E2004 (35) and E7102 (10). Admission only: outputs were not
executed. Some files may have been written for the retired hosted frontend, so a
refusal is a gap between that corpus and selfhost, not necessarily a language bug.

### Execution of small probes

Compiled by `amu-full-root.macho`, run under Node with stub hosts, compared with
`bin/amu run`.

| Probe | wasm32-browser | js-browser | Native |
|---|---|---|---|
| `i64` | 42 | 42 | 42 |
| `string` | 3 | 3 | 3 |
| `vector` | 3 | 3 | 3 |
| `literal_vec` | 2 | 2 | 2 |
| `closure` | 42 | 42 | 42 |
| `hof` | 12 | 12 | 12 |
| `record` | 42 | 42 | 42 |
| `document` | 1 | 1 | 1 |
| `f64` | E7101 | E7101 | 42 |
| `f32` | E7101 | E7101 | 42 |

A constant `main` emits 83,472 bytes and 129 functions on js-browser.

### Float contraction

`(1 + 2^-52)(1 - 2^-52) - 1`, with inputs the compiler cannot fold (bit patterns in
Kotoba, `noinline` in C, ordinary variables in the others):

| Route | Result bits |
|---|---|
| `bin/amu`, Java, JS, Python | 0 |
| clang `-O2` | -5084563979301289984 |
| clang `-O2 -ffp-contract=off` | 0 |

### Sugar test: Definition CIDs

`<amu-full-root.macho> definition-cids FILE.kotoba 3<.`, amu origin/main `1754ff26f`.

| Compared | Result |
|---|---|
| `(-> 1 g g)` and `(g (g 1))` | Same; control `(g (g 2))` differs |
| `(inc 1)` and `(+ 1 1)` | Same |
| `(not= 1 2)` and `(not (= 1 2))` | Same |
| `(cond (< 1 2) 1 :else 0)` and `(if (< 1 2) 1 0)` | Same |
| `defn-` and `defn` | Same, for both definitions |
| `loop` and a tail-recursive `defn` | Same, for both the helper and `main` |
| `(+ 1 2 3)`, `(+ (+ 1 2) 3)` and `(+ 1 (+ 2 3))` | All three differ |
| `(and a b)`, `(if a b false)` and `(let [t a] (if t b t))` | All three differ |

The tail-recursive pure spelling cannot be measured directly, since selfhost refuses
`lam`; this uses `defn`, whose conversion to `def` and `lam` is still to be built.

### Pure form on IPLD Schema

Run once through the hosted reference route under the local-oracle rule, from
`lib/kotoba.ioIpld` in `kotoba-lang/kotoba` at `fc58f414c`, with `kbb` (nbb v1.4.208),
via `ipld.schema-dsl/parse`, `ipld.schema/compile-schema` and `ipld.schema/unify!`.

16 positive forms unify. 8 negative forms are refused, for different reasons
(`unknown-union-kind`, `invalid-keyed-union`, `unknown-union-discriminant`,
`kind-mismatch`, `invalid-struct-tuple`). A `def` form round-trips through DAG-CBOR.

This is Kotoba's own implementation of the spec, and its coverage table says it is not
a conformance claim. The schema has **not** been checked with an independent IPLD
implementation such as go-ipld-prime or js-ipld.

### Seed usage

Counts of forms by head, from `seed` at amu `1754ff26f` with comments and strings
removed: 169,268 forms in 148 files, including `defn-` 6,807, `defn` 76, `inc` 3,757,
`and` 3,668, `or` 2,598, `->` 2,513, `not=` 1,648, `cond` 1,619, `loop` 1,606, `case` 468.
This is a text scan, and it also counts preserved and non-manifest files outside the
excluded directories, so it sizes the migration only roughly.
