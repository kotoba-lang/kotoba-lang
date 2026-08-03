# Kotoba syntax quality review

Status: active design review  
Date: 2026-08-03  
Authority inspected: `lang/guest-grammar.edn`, `lang/surface-status.edn`, the
conformance corpus, compiler examples, and the bounded document migration.

## Verdict

Kotoba's core syntax is beautiful: it is small, data-shaped, readable without a
code generator, and keeps effects visible as qualified operations. Control
forms, destructuring, threading, records, and collection literals compose in a
recognizable Clojure-shaped language without inheriting ambient Clojure/JVM
authority.

The syntax is not yet uniformly beautiful at representation boundaries.
Document construction, generic option extraction, and first-class function
invocation expose lowering details that interrupt otherwise direct code. The
right direction is contextual elaboration into existing typed primitives, not
new runtime representations or punctuation-heavy syntax.

## Evidence-based scorecard

| Area | Assessment | Evidence |
| --- | --- | --- |
| Data and control | Strong | literals, nested destructuring, `let`, `if`, `cond`, `case`, threading, bounded HOFs |
| Effect visibility | Strong | qualified catalog operations elaborate to declared abilities; ambient interop remains forbidden |
| Type readability | Strong with one rough edge | signatures read left-to-right; raw descriptors such as `[:option :document]` leak into extraction helpers |
| Collection vocabulary | Mostly coherent | literal/vector/list/set operations are familiar, but source list, pair-chain list, typed `[:list T]`, and document list need careful documentation |
| Document values | Strong | arbitrary bounded EDN map keys, canonical bytes, sets/lists/symbols, contextual `(document {...})` authoring, and KIR/ESM/Wasm/NBB parity are complete |
| Higher-order calls | Adequate, visibly lowered | `fn` is clear; `(invoke f ...)` and `(fn-ref add)` expose static-dispatch machinery |
| Failure/effect semantics | Strong | unsupported syntax, undeclared effects, oversized expansion, and host authority fail closed |

## Preserve

1. Keep ordinary literals and Clojure-shaped control forms as the visual center.
2. Keep qualified capability calls visible at effect boundaries.
3. Keep bounded expansion and static dispatch observable in diagnostics, but
   not necessarily in every source expression.
4. Keep one inert reader and one grammar authority. Do not recover convenience
   by admitting host macros, reflection, reader eval, or ambient imports.

## Improve in priority order

### Completed foundation — one general document map

The remaining bounded-EDN representation gap is closed. A document map now
uses document nodes for both keys and values; keyword source keys are shorthand
for keyword document nodes. General keys round-trip canonically across the
reference evaluator, restricted ESM, JVM Wasm compiler, public NBB fast path,
and browser host. Existing keyword-map canonical bytes, digests, and budgets
remain compatible.

This enabled `(document {...})` to elaborate into the one existing
`document-map` representation without a JSON object escape hatch, a second map
tag, or backend-specific semantics.

### Completed — contextual document literals

Admit a pure elaboration form whose argument must be a closed literal tree:

```clojure
(document
  {:goal "migrate"
   :attempt 3
   :ready true
   :actors #{actor/run}
   :steps [nil :prepare]})
```

This now desugars before KIR to the existing `document-map`, scalar
constructors, `document-vector`, `document-list`, and `document-set`. Everything
inside the form is inert closed data; a nested list becomes a document list and
is never invoked. Dynamic expressions outside the closed form stay explicitly
boxed. This removes representation noise without adding a second value model,
codec, or backend path.

Landed acceptance evidence:

- identical elaborated KIR to the explicit constructor form;
- the existing depth, node, item, UTF-8, duplicate, and canonical-order limits;
- JVM-reader and JVM-free reader parity;
- no capability or host import added;
- source spans in diagnostics point to the literal member that failed.

### P0 — option flow over extraction plumbing

Prefer `if-some`, `when-some`, `some->`, and `some->>` in documentation and
libraries. Keep `option-value-of` as a low-level total extraction primitive,
but stop presenting nested descriptor-bearing extraction as idiomatic source.
Add typed bindings only where inference cannot establish the payload.

### P1 — statically known callable values

Investigate type-directed `(f x)` only when `f` is proven to be a bounded
closure value and cannot be confused with a global operation. Until that proof
and its diagnostics exist, explicit `(invoke f x)` is safer than introducing
ambiguous call syntax. `fn-ref` can then become compiler elaboration for a
top-level function used in value position.

### P1 — vocabulary and module consistency

- Document the four distinct sequence concepts where they first appear rather
  than relying on name prefixes alone.
- Prefer an `ns` form in authored modules and conformance examples; keep
  namespace-free files only as explicit minimal-script fixtures.
- Use predicates with `?`, effecting operations with `!`, and qualified names
  for capability operations consistently in the language-owned catalog.

## Explicit non-goals

- JavaScript-, JVM-, or shell-style interop syntax;
- user macros or reader extensions;
- implicit network/storage/clock authority;
- a second JSON-like object representation alongside `:document`;
- syntax sugar that hides unbounded work or changes fuel semantics.

## Current maturity conclusion

The language already has a coherent aesthetic, rather than merely resembling
Clojure lexically. Its bounded document model and closed authoring syntax are
now complete without representation or backend forks. The highest remaining
syntax debt is option extraction ceremony: make idiomatic `if-some`,
`when-some`, `some->`, and `some->>` absorb common control flow while retaining
visible effects and fail-closed bounds.
