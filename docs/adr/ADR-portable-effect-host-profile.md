# ADR — portable effect host profile

- **Status**: Accepted, portable envelope and CLJ/CLJS kernel implemented
- **Date**: 2026-07-25
- **Implementation**:
  `src/kotoba/lang/portable_effect.cljc`
- **First consumer**:
  `kotoba-lang/toshokan`

## Context

Kotoba targets Wasm Components for hostile-code confinement, but pure
application semantics also need Clojure-like portability across JVM, JavaScript
hosts, browsers, and workerd. Portability must not turn host mechanisms into
ambient language facilities.

## Decision

Define `kotoba.portable-effect/v1` as a closed, data-only request envelope.
It carries:

- a stable request and audit identity;
- a declared effect row;
- a component-bound ability containing kind, resource, target, operation,
  byte/item/deadline limits, and audit identity; and
- inert input data.

The envelope cannot carry provider callbacks, credentials, grants, policy, or
host bindings. A host selects a provider only by the admitted
`[target operation]` pair and dispatches through
`guard-component-ability-call`. Missing providers, undeclared effects,
malformed abilities, empty grant intersections, target/operation mismatch,
and broader limits fail before provider invocation.

CLJ and CLJS use the same CLJC dispatch kernel. Application-specific pure
policy should be Kotoba, with exported `test-*` definitions tested once from
the same source across KIR/JVM, restricted ESM, and Wasm. Other hosts implement
the closed wire contract and keep only adapter smoke tests. Wasm Components
use the same semantics but retain Kototama's stronger engine boundary.

Effect implementation is therefore split deliberately:

- Kotoba owns pure admission, state transitions, and deterministic mock
  handlers.
- The host owns physical I/O, credentials, resource metering, final provider
  validation, and confinement.

Putting the latter inside transpiled guest code would make it look portable
but would not make its authority or sandbox portable.

## Security levels

- `portable/trusted`: checked Kotoba or CLJC-generated code plus a conforming
  host adapter. This preserves effect/capability policy but does not claim a
  separate VM sandbox.
- `confined/untrusted`: the same application/effect semantics compiled to an
  admitted Wasm Component and executed by Kototama.

Definition or component identity never grants authority. A successful effect
still requires host-side grant/policy intersection and last-boundary provider
validation.
