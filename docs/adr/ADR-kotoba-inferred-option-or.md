# ADR: Type-inferred Option fallback

Status: accepted

Date: 2026-08-03

## Decision

`(option-or option fallback)` is the idiomatic total value-with-default form for
`[:option T]`. The compiler infers `T` and elaborates to the existing
`option-value-of` operation. `if-some`, `when-some`, `some->`, and `some->>`
remain the idioms for control flow.

The explicit payload descriptor stays in the lowered ABI and `match-option`,
but is not repeated at ordinary fallback call sites. This adds no value
representation, KIR operation, backend path, effect, or capability.

## Evidence

Compiler ADR 0201 and compiler PR 483 cover typed locals, constructors, record
fields, let bindings, function results, unannotated result inference,
JVM/NBB parity, and fail-closed type errors.
