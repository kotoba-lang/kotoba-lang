# Kotoba language reference

This is the reader-oriented map of the language. Normative details live in the
[semantics SSoT](../lang/semantics-ssot.md), while exact admission is decided by
[`lang/guest-grammar.edn`](../../lang/guest-grammar.edn) and
[`lang/surface-status.edn`](../../lang/surface-status.edn).

## Source and modules

- `.kotoba`: canonical Kotoba component source.
- `.cljk`: CLJ Kotoba source; it is not a JVM compilation target.
- `.cljc`: common source across Clojure, ClojureScript, and Kotoba readers.
- `.clj` / `.cljs`: compatibility inputs retaining their host-language reader
  meanings; they do not imply runtime compatibility.
- A module uses `ns`, declares exports, and obtains effects only through
  declared capability imports.

Source-file classification is owned by
[`kotoba-core-contracts/lang/profile.edn`](https://github.com/kotoba-lang/kotoba-core-contracts/blob/main/lang/profile.edn).
The broader admitted component grammar is owned here.

## Values and types

The typed profile covers bounded scalar and structured values. Start with the
[value model](../lang/semantics-ssot.md#3-value-model), then use the focused
guides for [option/result](../lang/option-result-guide.md),
[records](../lang/record-cookbook.md), [strings](../lang/string-kit.md), and
[collection costs](../lang/collections-costs.md).

Do not infer Clojure collection implementation details. Kotoba specifies
observable operations and bounds; several current portable collections use
persistent pair-chain representations with linear bounded operations.

## Evaluation and errors

- Evaluation order is deterministic and defined by the semantics SSoT.
- Hidden exception paths are excluded from safe components; fallible work uses
  explicit option/result values.
- Execution is fuel-bounded. See the [fuel model](../lang/fuel-model.md).
- Diagnostics are phase-tagged. The bounded stable registry is in the
  [generated diagnostic-code reference](../generated/diagnostics.md); compiler
  and host-specific codes outside it must not be presented as covered.

## Effects and capabilities

Effects are inferred and checked against declared capabilities. A resource
name is not authority. The host must receive a scoped capability value, narrow
it through grant and policy intersection, and leave an auditable receipt. See
[capability values](../lang/capability-values.md).

## Deliberately absent

The safe component surface excludes ambient code loading, host interop,
unbounded concurrency, ambient mutation, guest macros, and hidden exceptions.
These are language constraints, not missing documentation. The exact set and
rationale are in the generated [surface matrix](../lang/surface-matrix.md).

## Portability

Conformance is case-class and backend specific. A backend must report an
unsupported target explicitly; silent fallback is non-conforming. Executable
fixtures live under [`lang/conformance/`](../../lang/conformance/), with
additional type, capability, identity, and malicious-source suites beside it.

## Versioning

Language profile, package contract, implementation release, and individual
stdlib semver are separate axes. Read [language versioning](../lang/versioning.md)
and [stdlib versioning](../lang/stdlib-versioning.md), then check the generated
[release binding](../generated/release.md) before making a compatibility claim.
