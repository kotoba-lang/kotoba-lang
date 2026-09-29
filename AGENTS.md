# Agent rules

## Language semantics are target-independent

- wasm32 is one build target of the Amu native compiler, not the definition of
  Kotoba. Do not cap, narrow or defer a language or stdlib feature because
  wasm32 cannot lower it yet: specify the semantics against the KIR
  interpreter, and let each target lower the feature or refuse it by name
  (exit 65, recorded in `lang/surface-status.edn`).
- Keep a limit only if it bounds a resource or protects an invariant.
- Never relaxes the safety pipeline, fail-closed behaviour or the no-JVM rules.
  Decision: `docs/adr/ADR-language-semantics-are-target-independent.md`.

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
