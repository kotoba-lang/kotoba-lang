# ADR: Contextual document literals

Status: accepted
Date: 2026-08-03

## Decision

`(document literal)` is the language-owned convenience form for authoring a
bounded document. Its one argument is a closed EDN-shaped tree. Forms nested
inside it are inert document lists, not calls, so the form adds no evaluation
or ambient authority.

The compiler elaborates it before KIR into the existing `document-*`
constructors. It preserves the document depth, node, item, UTF-8, duplicate,
and canonical-order rules and introduces no second object model, wire tag,
codec, capability, or backend operation.

## Evidence

Compiler ADR 0200 and `document-edn-test` establish explicit-constructor HIR
identity plus KIR, restricted ESM, browser Wasm, and JVM-free NBB execution.
The general-key NBB fixture also covers a vector containing an exact i64 as a
map key.
