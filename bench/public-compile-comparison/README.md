# Public Wasm compile comparison

This benchmark measures process-cold wall time for two tiny programs with the
same observable source-level result: an exported `main` returns the integer
`42`. Both commands emit a `wasm32-unknown-unknown` module.

It is a deliberately narrow toolchain-startup workload, not evidence that one
language compiles faster in general. The generated modules use different ABIs
and runtime contracts, so artifact sizes are recorded but not ranked.

Run it from the repository root on a machine with the released `kotoba` CLI,
`rustc`, Node.js, and Rust's `wasm32-unknown-unknown` standard library:

```sh
node scripts/benchmark-public-compile.mjs --runs 21 \
  --output bench/public-compile-comparison/latest.json
```

The harness performs three unrecorded warmups per tool, alternates which tool
runs first, validates every emitted Wasm file, and records all samples, the
median, p95, tool versions, host, commands, source digests, and artifact
digests. Re-run on another host rather than treating `latest.json` as a
portable ceiling.
