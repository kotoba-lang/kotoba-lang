# Security: specify capability-value semantics in the language profile

Architecture review finding: `F-003`
Severity: High
Owner: language profile

## Problem

Safe Kotoba has strong static capability gates, but S4b capability values need a
language-level contract so implementations do not invent incompatible semantics.

## Risk

Without a profile-level contract for capability values, compiler/runtime
implementations may diverge on how resource-scoped capabilities flow through
functions and host calls.

## Required work

- Define capability value vocabulary for graph, model, egress, secret, and
  future host capabilities.
- Define how effect rows relate to capability parameters.
- Define conformance fixtures for dynamic resource calls without wildcard grants.
- Define receipt requirements for concrete capability objects.

## Acceptance criteria

- Language profile docs describe capability-passing style.
- Conformance fixtures distinguish string resource ids from capability values.
- `kotoba-lang/security` risk `R-007` links to profile evidence.

## Local resolution evidence

- Added `docs/lang/capability-values.md` defining capability values, effect
  consistency, runtime intersection, receipt requirements, and conformance
  direction.
- Linked capability values from `docs/lang/README.md`.
- Marked this local issue as `:implemented-local` in `.issues/issues.edn`.

## References

- `kotoba-lang/security/docs/architecture-review-2026-07-01.md` finding `F-003`
- `docs/adr/ADR-safe-capability-language.md`
