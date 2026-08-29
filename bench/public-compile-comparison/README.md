# Public compile/build startup comparison

This benchmark measures process-cold wall time for four tiny programs with the
same observable source-level result: `42`. Kotoba, Rust, and C emit a WebAssembly
module. `javac` emits a JVM class file, which is validated and executed only
after the timed compiler process exits.

It is a deliberately narrow toolchain-startup workload, not evidence that one
language builds faster in general. The targets, generated artifacts, ABIs,
runtime contracts, and amount of compiler work differ, so artifact sizes are
recorded but not ranked. This is useful as a reproducible developer-feedback
observation, not as a universal language leaderboard.

Run it from the repository root on a machine with the released `kotoba` CLI,
`rustc`, `clang`, a JDK, Node.js, and Rust's `wasm32-unknown-unknown` standard
library:

```sh
node scripts/benchmark-public-compile.mjs --runs 21 \
  --output bench/public-compile-comparison/latest.json
```

The harness performs three unrecorded warmups per tool, rotates all four tools
through each starting position, validates every emitted artifact outside the
timed region, and records all samples, the median, p95, tool versions, host,
commands, source digests, and artifact digests. Re-run on another host rather
than treating `latest.json` as a portable ceiling.

The report also applies the same conservative `load1 <= 1.0` host gate used by
the public native-runtime suite. A failed load gate does not erase the samples
or correctness checks, but it makes the speed ranking explicitly unqualified.
