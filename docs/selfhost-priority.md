# Selfhost priority: the product does not depend on nbb, Node or the JVM

Decision recorded 2026-10-01. The full priority document is
`docs/selfhost-priority.md` in `amu-measure` (rules 1-11); this file carries the
rules that bind `kotoba-lang` sources. The goal sentence in
`docs/selfhost-native-binary.md` stands: `kotoba` is a native executable
produced by Kotoba, and building it needs no JVM, no GraalVM and no Node.

nbb, Node, the JVM and GraalVM are bootstrap references. They may build and
measure until the self-built compiler exists. They are not where the product
lives.

## Rules

8. **No nbb/Node/JVM dependency in the product.** The end state is `kotoba` and
   `amu` built by Amu that run `check`, `refactor`, `compile` and `kotoba ...`
   with no node/nbb/JVM process. "It runs on nbb" is a bootstrap result.
9. **New product-path code is Kotoba.** `.cljk`/`.kotoba` with a `:kotoba`
   reading that passes `amu check`. `lang/compat/*.kotoba` twins are Kotoba
   readings of host modules and must be real implementations, not refusals.
   Host-only code (`node:*`, `js/*`, `java.*`, `:import`) lives behind
   `#?(:cljs ...)` / `#?(:clj ...)` in files flagged `;; bootstrap-tooling`
   (first five lines) or under `scripts/`, `test/`, `bench/`.
10. **Refuse new host dependencies.** A PR or commit that adds an nbb, Node or
    JVM dependency to the product path is refused, including a new
    `#?(:kotoba nil ...)`. Check with `scripts/selfhost-wall/bootstrap-boundary.sh`
    in `amu-measure`; PRODUCT counts must not grow.
11. **What 100% means.** The `amu` binary built by `amu` itself runs the full
    check, refactor and compile on its own sources with zero node/JVM/nbb
    processes, verified by `scripts/selfhost-wall/no-host-processes.sh --
    <command>` (strace/dtruss execve trace; a missing tracer is a failure).

The inventory and the removal stage (S6) are in `amu-measure`:
`docs/selfhost-bootstrap-boundary-20261001.md` and
`docs/selfhost-core-rewrite-plan-20260930.md`.
