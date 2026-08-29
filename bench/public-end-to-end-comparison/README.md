# Public end-to-end developer-loop comparison

This benchmark compares the dependency-free path from a tiny project to a
first correct process result across Kotoba, Rust, C, Zig, TinyGo, Go, Swift,
the JVM, AssemblyScript, .NET IL, and .NET Native AOT.

It measures separately where the toolchain exposes a stable boundary:

- dependency resolution;
- check or admission;
- clean build;
- no-change build;
- process-cold startup and execution;
- paired clean-build-to-first-result and no-change-build-to-result loops.

An unavailable separate phase is recorded as `not-applicable`, never as zero.
Targets and runtime contracts differ: Kotoba, Zig, and AssemblyScript emit Wasm,
most AOT toolchains emit an arm64 macOS executable, javac emits a JVM class,
and regular .NET emits IL. The comparison therefore describes this exact
developer-loop workload; it is not a universal language leaderboard.

Run from the repository root after `npm ci`, with all named toolchains on PATH:

```sh
node scripts/benchmark-public-end-to-end.mjs --runs 7 \
  --output bench/public-end-to-end-comparison/latest.json
```

Every artifact is started in a fresh process and fails unless it computes the
expected result. The report records complete samples, tool versions, artifact
digests, load qualification, and the workloads that remain outside the public
comparison.
