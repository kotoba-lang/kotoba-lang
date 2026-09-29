# ADR: Language semantics are target-independent; wasm32 is one build target

- Date: 2026-09-29
- Status: Accepted (owner decision, 2026-09-29).
- Machine authority for backend reach: `lang/surface-status.edn`.
- Companion in the compiler repository: `kotoba-lang/amu` ADR 0354.

## Decision

Kotoba's semantics are owned here (`kotoba-lang/kotoba-lang`) and are defined
without reference to any single build target. wasm32 (`wasm32-browser`,
`wasm32-wasi`) is one target of the Amu native compiler, alongside the native
AArch64 / x86_64 targets, KIR and the JS route.

1. A language or stdlib feature is admitted on its specified semantics, effects,
   resource bounds and capability requirements, with the KIR interpreter as the
   reference. It does not have to lower on wasm32 (or on any one target) first.
2. A target that cannot lower an admitted feature refuses it by name (a target
   refusal, exit 65) and the head's per-backend status is recorded in
   `lang/surface-status.edn`. The feature is not capped, narrowed or rewritten
   to suit the narrowest target. `guest-grammar.edn`'s `:backend-note` ("Not
   every head above reaches every backend") is the existing form of this rule.
3. No backend has a veto over the language. wasm32 has the same standing as
   every other target.
4. A limit stays only if it bounds a resource or protects an invariant; a limit
   that records only what one backend could do is removed.
5. Unchanged: the shared safety pipeline precedes every backend, fail closed is
   the default, and no JVM / GraalVM / Node fallback is added.

## Reading of existing text

"Desugar to existing wasm-safe ops preferred" (`kotoba-reliability-parity-wbs.md`)
and "desugars to wasm-safe helpers" (`ADR-product-value-abi-v1.md`) describe an
implementation preference for reuse. They are not conditions of admission.
