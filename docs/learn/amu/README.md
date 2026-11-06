# Learn the Amu compiler step by step

**English** | [日本語](README.ja.md)

Amu checks Kotoba programs and turns them into artifacts for a selected execution target. This course starts with a program that returns `42`, then gradually adds features while following the path from source to native execution. Along the way, it connects Kotoba forms, IPLD, Unison-inspired content-addressed definitions, the Kototama VM, and performance engineering.

You need to know functions, variables, and conditionals. Each chapter introduces the compiler, Lisp, and IPLD concepts it uses, with an example, an explanation, source references, and an exercise with an answer. Like Simple, the course follows a gradual learning path; it reads the real Amu implementation rather than copying a separate compiler into each chapter.

English is the default. The language links on each page switch between the English and Japanese versions of that page. Both versions share the executable examples and recorded observations.

## Contents

| Chapter | What you learn | What you work with |
|---|---|---|
| [01 The first 42 and the big picture](01-first-program.md) | The roles of Amu, Kotoba, Kototama, and AiueOS | Returning an integer |
| [02 Source and Kotoba forms](02-forms.md) | S-expressions, syntax trees, canonical forms, reader sugar | Reading addition as a tree |
| [03 Reading and name resolution](03-reader-and-names.md) | Lexing, reading, scope, and resolved references | let and an undefined name |
| [04 Type checking and intermediate representations](04-checking-and-ir.md) | Checked KIR, the Seed tree, and SIR | Type errors and conditionals |
| [05 IPLD and content addressing](05-ipld.md) | DAG-CBOR, CIDs, blocks, and handles | Distinguishing source from definitions |
| [06 Unison-inspired definition identity](06-definition-identity.md) | Alpha normalization, dependency CIDs, recursive SCCs | Renaming and dependency updates |
| [07 Effects and authority](07-effects-and-authority.md) | Static effects, grants, invocation admission | Refusing an unsupported operation |
| [08 Machine code and executables](08-code-generation.md) | Lowering, instruction selection, registers, fixups, ABI | Loops and function calls |
| [09 The Kototama VM](09-kototama.md) | Transitions, IPLD state, admission, receipts, typed eval | Describing one execution |
| [10 Making generated code faster](10-runtime-performance.md) | Structural recognition, constants, loops, SIMD, fuel and bounds | Comparing observable behavior |
| [11 Faster builds and selfhosting](11-build-performance.md) | Compiler costs, DefCID caches, self-rebuilds | Designing reuse conditions |
| [12 Reading the implementation and running experiments](12-reading-and-experiments.md) | Source exploration, negative controls, measurements, Simple | Completing a small investigation |

## Run the first example

Use the native CLI on an Apple Silicon Mac. Read the Amu checkout's [public entry scope and instructions](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/docs/native-public-entry.md) first. Replace `/path/to` with the actual locations.

```sh
/path/to/amu/bin/amu check /path/to/this/docs/learn/amu/examples/00-literal.kotoba
/path/to/amu/bin/amu run /path/to/this/docs/learn/amu/examples/00-literal.kotoba
```

The commands print `ok` and `42`, respectively. Linux uses its own entry, target, and format; this Mac observation does not qualify Linux or browser execution. See the [instructions and observations for all examples](examples/README.md).

## Four states to distinguish

A **language or VM contract** defines the behavior an implementation must preserve. **Adopted source** identifies the composition of a particular compiler. An **experiment or candidate** is an implementation being investigated. An **execution result or measurement** is an observation for the recorded artifact, input, and host.

For example, the existence of a DefCID contract does not mean every native build uses a persistent per-definition cache. Running a small native example also does not establish the complete Kototama state, authority, and receipt contract. Each chapter keeps these distinctions beside the feature concerned.

## Reference baseline

The reference date is 6 November 2026. Amu implementation links are pinned to [`a4c16e7c`](https://github.com/kotoba-lang/amu/tree/a4c16e7c47005d2235dc48d3f69569773a1606e7), and Kototama links to [`b7bbcc44`](https://github.com/kotoba-lang/kototama/tree/b7bbcc44d08913fee69074ad70f92d341df2be92). Relative links lead to language contracts in this repository. Pinned links keep the explanation reproducible when files later move. Check the owning contracts, manifests, and execution evidence when using a newer version.

[Back to the language documentation](../../README.md).
