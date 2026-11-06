# Executable examples for the Amu course

**English** | [日本語](README.ja.md)

Six positive examples gradually add integers, arithmetic, let, if, loops, and function calls. Each expects `ok` from `check`, `42` from `run`, and exit code `0`. Three negative controls expect checking to refuse the input.

| File | Added feature | Observed result |
|---|---|---|
| [00-literal](00-literal.kotoba) | Zero-argument main and i64 | 42 |
| [01-arithmetic](01-arithmetic.kotoba) | `+` | 42 |
| [02-bindings](02-bindings.kotoba) | `let` | 42 |
| [03-branch](03-branch.kotoba) | Comparison and `if` | 42 |
| [04-loop](04-loop.kotoba) | `loop/recur` | 42 |
| [05-functions](05-functions.kotoba) | Typed helper call | 42 |
| [negative-name](negative-name.kotoba) | Undefined variable | E2102, exit 1 |
| [negative-operation](negative-operation.kotoba) | Unsupported `slurp` | E2101, exit 1 |
| [negative-type](negative-type.kotoba) | Bool passed to integer addition | E2104, exit 1 |

## Record your own observations

Point the observer at an existing native Mac ARM64 Amu CLI and choose a new output directory. It copies inputs there without modifying the originals or overwriting old observations. Replace `/path/to` with actual locations.

```sh
python3 /path/to/amu/scripts/seed/native_build_queue.py -- \
  python3 docs/learn/amu/examples/observe.py \
  --amu /path/to/amu/bin/amu \
  --output /path/to/new-observation-directory
```

Python only stages files, starts native processes, and records output and hashes. It does not interpret Kotoba or generate machine code. The supplied native Amu performs checking, compilation, and execution. The shared build queue respects existing concurrency limits.

## Recorded execution

The [raw observations from 6 November 2026](observations-macos-arm64-20261106.json) contain 15 macOS ARM64 observations: six check/run pairs and three refusals, all matching expectations. The CLI SHA-256 is `72e26e48e58d0cb9ce58f081b97877ffc132fdfff35ad371963d87aa97c0b310`. The record also names compiler-image and packager digests and the source revision, corresponding to [Amu's CLI descriptor](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/bootstrap/aarch64-macos/cli.json).

These observations establish that the small examples behaved as expected on that CLI. They do not establish performance, complete language conformance, the DefCID producer, IPLD state, the entire Kototama VM, or Linux/browser execution. Pseudo-IR and identity/admission diagrams in the chapters are explanations, not observer dumps.

[Course contents](../README.md)
