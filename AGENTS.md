# Agent rules

## Q9 source migration is whole-component and JVM-free

The machine authority is `lang/q9-migration.edn`; the accepted decision is
`docs/adr/ADR-q9-whole-component-build-migration.md`.

- Migrate a complete namespace/deployable component: every public export and
  its transitive Kotoba source closure. A predicate, decision core or
  caller-precomputed scalar shadow is not migration progress.
- Keep native mechanisms behind explicit capability/provider imports. Do not
  shrink the component to avoid a missing language or backend feature; mark
  that migration blocked.
- Every target must pass the verified native Kotoba CLI (`kotoba check`,
  `kotoba compile`, and package-level `kotoba rad build`) and Amu
  `check`/`compile` with `--jvm-free`.
- Q9 build, test, parity and soak gates must not require a JDK, Java process or
  Clojure CLI. Deny/trace `java`, `javac`, `clojure` and `clj`; unsupported
  routes and lock failures fail closed instead of falling back to the JVM.
- Run portable retained `.cljc` oracles through nbb/CLJS, native or Wasm, or
  use content-addressed golden vectors. JVM observations are diagnostic only.
- Existing JVM/Clojure compiler and test paths are compatibility surfaces;
  they cannot satisfy or weaken Q9 acceptance.
