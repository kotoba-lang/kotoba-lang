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
Computed first-class function invocation still exposes its result descriptor,
while lexical scalar, predicate, vector, document, record, option, and
result-producing calls stay direct in known result contexts. Document
construction and generic option fallback use contextual/type-directed
elaboration into existing typed primitives rather than new runtime
representations or punctuation-heavy syntax.

## Evidence-based scorecard

| Area | Assessment | Evidence |
| --- | --- | --- |
| Data and control | Strong | literals, nested destructuring, `let`, `if`, `cond`, `case`, threading, bounded HOFs |
| Effect visibility | Strong | qualified catalog operations elaborate to declared abilities; ambient interop remains forbidden |
| Type readability | Strong | signatures read left-to-right; idiomatic option fallback infers `[:option T]`, while descriptors remain only in low-level ABI forms |
| Collection vocabulary | Mostly coherent | literal/vector/list/set operations are familiar, but source list, pair-chain list, typed `[:list T]`, and document list need careful documentation |
| Document values | Strong | arbitrary bounded EDN map keys, canonical bytes, sets/lists/symbols, contextual `(document {...})` authoring, and KIR/ESM/Wasm/NBB parity are complete |
| Higher-order calls | Strong for lexical code; explicit at value boundaries | a lexical closure uses ordinary `(f ...)`; `invoke` remains for computed expression heads/result-family selection and `fn-ref` for explicit top-level function values |
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

### Completed surface — option flow over extraction plumbing

Use `option-or` when the intent is “payload or fallback”; its payload descriptor
is inferred from typed locals, constructors, record fields, let bindings, or
function results. Prefer `if-some`, `when-some`, `some->`, and `some->>` when
the intent is control flow. `option-value-of` remains the low-level total
extraction primitive seen after elaboration, not idiomatic authored source.
Existing libraries are being migrated incrementally; completion here describes
the admitted language surface, not zero remaining low-level call sites.

### Completed — statically known callable values

A symbol proven lexical by function parameters, sequential `let`, `loop`, or
destructuring now uses ordinary application: `(f x)`. The frontend lowers it
to the arity-specific static closure dispatcher; boolean positions select the
boolean result family and a wrong-family closure traps. True special heads
(`let`, `if`, `do`, `fn`, `loop`, `recur`) remain non-shadowable, while an
unknown global call head still fails closed instead of becoming a host lookup.

`invoke` remains intentional for a computed/non-symbol closure expression or
when authored code must select a result family explicitly. `fn-ref` remains
the explicit conversion of a known top-level function into a value. This keeps
the common lexical path visually ordinary without pretending that every form
in call-head position is dynamically callable.

The same contextual rule now covers `:vector-i64`: vector operations and the
collection inputs to `map`, `filter`, and `reduce` select the vector dispatcher
for a lexical call. This admits library-shaped code such as
`(defn call-singleton [f] (vector-at (f 7) 0))`, while a computed closure head
uses `(invoke :vector-i64 closure 7)`. Wrong result families and unknown lambda
ids still trap rather than coerce.

String-consuming operations now provide the corresponding `:string` context,
and declared `:string` function results carry it through the value-producing
tails of `let`, `if`, and `do`. Thus `(string-length (render 42))` stays an
ordinary lexical call, including through a declared library boundary. A
computed closure head remains explicit as `(invoke :string closure 42)`, and a
wrong-family closure still traps closed.

The same closed rule now covers `:document`. Document consumers and
unambiguous document constructor positions select the document dispatcher, so
`(document-count (build 42))` remains ordinary lexical application and a
declared `:document` result carries through `let`, `if`, and `do` tails.
Document-map keys deliberately remain explicit when their type is ambiguous
between keyword and document; computed heads use `(invoke :document ...)`.

The dispatcher is now keyed by the complete canonical result descriptor for
nominal records, `[:option T]`, and `[:result T E]`. A record constructor,
projection, update, equality check, option/result constructor or match arm gives
a lexical call enough context to remain `(f ...)`; declared result boundaries
and result-typed `fn-ref` values carry the same context. Different nominal
records and differently parameterized option/result values never share a
dispatcher. At a genuinely computed head, the full descriptor remains visible,
for example `(invoke [:option :string] closure 42)`. That explicitness is a
useful honesty at the dynamic boundary, not syntax noise on the common path.

The current descriptor profile is deliberately bounded by fail-closed trap
generation: a result descriptor is admitted only when the compiler can build a
typed fallback value for an unknown or wrong-family closure. Descriptors whose
members have no such inhabitant yet (notably bytes, linear resources, and some
list shapes) remain a maturity gap rather than silently weakening dispatch.
Also, frontend result context propagates through a `do` tail, but the
`kotoba-script` path still rejects a multi-expression `do` KIR operation. Until
that backend gap closes, use `let` sequencing in portable authored code.

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

The language has a coherent aesthetic rather than merely resembling Clojure
lexically. Bounded documents are authored as inert data, option flow is
idiomatic, lexical closures use ordinary application across flat and structured
result types, and effects remain qualified and visible. Computed expression
heads and explicit top-level function values still expose `invoke`/`fn-ref`;
the former now accepts a complete descriptor so nominal and parameterized types
remain honest at that boundary. The highest-value callable gap is no longer
record/option/result dispatch itself, but extending typed trap fallbacks to the
remaining descriptor families. The separate multi-expression `do` backend gap
is now the clearest blemish in otherwise ordinary sequencing syntax.
