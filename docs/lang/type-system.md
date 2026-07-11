# Safe Kotoba type, region, and task contract

Status: **M3 compiler admission adapter; published dependency pin pending**
(2026-07-11).

Safe Kotoba keeps ordinary immutable values freely shareable.  Its type system
only imposes ownership rules where authority or mutable storage makes them
necessary.

```text
ordinary immutable data     freely copyable
[:cap kind resource]        opaque, affine authority
[:region r]                 lexical region token
[:region-ref r value]       mutable reference confined to r
[:task value effects]       result of a child in a structured scope
```

Public signatures are data:

```clojure
{:params  [[:cap :host/fs-read "/app/config"] :string]
 :returns [:result :string :keyword]
 :effects #{:host/fs-read :error}}
```

Attach this map to a `defn` name as `:signature` metadata.  The portable
`kotoba.lang.type-system/validate-forms` validator checks its arity,
capability-parameter metadata, and effect obligations before a host lowers it
to typed HIR.  The annotation is optional during migration; once typed-HIR
admission is enabled it becomes mandatory at public module boundaries.

`[:cap kind resource]` identifies the *shape* of a capability value.  The
runtime still resolves the opaque value against the signed grant and local
policy.  A string resource is therefore a restriction, never a forgeable
authority.

## Region rule

`with-region` will introduce a fresh lexical name `r`.  A `[:region r]` token
and any `[:region-ref r T]` may not escape the body: public return types may
not contain either.  `freeze` is the explicit operation that produces an
ordinary immutable value which may leave the region.

## Structured concurrency rule

`scope` owns its children and joins/cancels them before it returns.  Every
child effect must be included in the parent effect row.  The first language
slice rejects capability capture by a child.  A later `move` form will transfer
an affine capability to exactly one child; shared capabilities require an
explicit capability kind and synchronisation contract.  No implicit sharing is
permitted.

The portable validator is kotoba.lang.type-system.  It is deliberately a
contract validator before a typed-HIR implementation so all hosts can agree on
the syntax and safety invariants first.  lang/type-conformance/ is the M2
fixture suite; adapters must accept and reject the same cases before claiming
this contract. The kotoba-lang/kotoba compiler's check admission gate now
delegates to the validator when the M2 contract is present. Older published
dependency pins reject annotated source rather than treating the annotation as
a comment.
