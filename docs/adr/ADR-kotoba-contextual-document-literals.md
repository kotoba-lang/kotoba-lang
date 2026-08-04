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

When an enclosing function result or typed capability request already declares
`:document`, the wrapper is optional for closed, unambiguous scalar and
map/vector/set literals:

```clojure
(defn policy [] :document
  {:action :actor/run :attempt 3 :ready true})
```

Simple symbols and lists remain expressions in this type-directed form. This
preserves lexical parameters and calls returning documents; explicit
`(document ...)` continues to mark simple-symbol and list data. A typed
capability request propagates its declared request type during elaboration, but
this semantic document value is not a claim that compiler KIR implements the
kotoba-wasm-only physical `data-host-arg` / `bytes-ptr` / `bytes-len` ABI.

## Evidence

Compiler ADR 0200/0213 and compiler#531 establish explicit-constructor HIR
identity plus type-directed bare-literal identity, typed provider-boundary
identity, KIR, restricted ESM, browser Wasm, and JVM-free NBB execution. The
general-key NBB fixture also covers a vector containing an exact i64 as a map
key.
