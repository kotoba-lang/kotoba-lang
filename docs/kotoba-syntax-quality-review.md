# Kotoba syntax quality review

Status: active design review  
Date: 2026-08-04
Authority inspected: `lang/guest-grammar.edn`, `lang/surface-status.edn`, the
conformance corpus, compiler examples, and the bounded document migration.

## Verdict

Kotoba's core syntax is beautiful: it is small, data-shaped, readable without a
code generator, and keeps effects visible as qualified operations. Control
forms, destructuring, threading, records, and collection literals compose in a
recognizable Clojure-shaped language without inheriting ambient Clojure/JVM
authority.

The syntax is now consistently clean at statically recoverable representation
boundaries. Lexical and computed first-class function calls both reuse a closed
consumer or return context instead of repeating their result descriptor.
Ambiguous computed calls still spell the descriptor, because hiding a real
choice there would be runtime guessing rather than elegance. Document
construction and generic option fallback likewise use contextual/type-directed
elaboration into existing typed primitives rather than new runtime
representations or punctuation-heavy syntax. A declared `:document` result or
typed request now admits the closed literal directly, so the type and the data
shape are each written once.

Nominal record construction follows the same rule. `map->Type` writes the
identity once and propagates it only through total bounded control result
positions; every reachable leaf remains an exact declared-field map. This
removes repeated branch constructors without introducing dynamic record
coercion or runtime shape guessing.

Provider#178 demonstrates that the existing type-directed surface is sufficient
for production recursive data: ordinary `(nth p 0)` retains the exact child
descriptor inferred from the variant arm and lowers to byte-identical Wasm.
Compiler#527 and provider#179 close the final record-specific declaration seam:
a recursive schema may refer to a later same-module `defrecord` without
repeating its generated descriptor, while incompatible collisions and
undeclared references still fail closed. Ordinary construction, record access,
heterogeneous projection, and recursive record declarations no longer expose
raw compiler plumbing in provider source.

Compiler@63bed2b extends that same source/representation separation to the
native host, and compiler@d0af5f0 (compiler#529) records and strengthens its
source-level conformance. Ordinary homogeneous vectors remain `[1 2 3]`, `nth`, and
ordinary destructuring in authored Kotoba; the POSIX and Windows loaders
implement a bounded immutable handle ABI underneath them. The explicit
`vector-*` names remain available at typed or representation boundaries, so
native execution adds backend coverage without adding source punctuation or a
second collection syntax. Sixteen signed-kexe cases now exercise scalar and
composed i64/f64 vector paths on the host ISA.

## Evidence-based scorecard

| Area | Assessment | Evidence |
| --- | --- | --- |
| Data and control | Strong bounded profile | closed mixed/nested literals, recursive typed destructuring including heterogeneous `& rest`, `let`, `if`, `cond`, `case`, threading, and bounded HOFs |
| Records and protocols | Strong bounded profile; recursive declaration and computed-construction seams closed | `defrecord`, `defprotocol`, `definterface`, complete `extend-type`, `->Type`, and exact-map `map->Type` through total bounded control lower to nominal records and static calls; a bounded declaration prepass preserves closed recursive schemas without descriptor repetition |
| Effect visibility | Strong | qualified catalog operations elaborate to declared abilities; ambient interop remains forbidden |
| Type readability | Strong | signatures read left-to-right; idiomatic option fallback infers `[:option T]`, while descriptors remain only in low-level ABI forms |
| Collection vocabulary | Mostly coherent; native homogeneous path preserves the source aesthetic | literal/vector/list/set operations are familiar, and native vectors keep `[1 2 3]`/`nth` over a bounded host ABI; source list, pair-chain list, typed `[:list T]`, and document list still need careful documentation |
| Document values | Strong | arbitrary bounded EDN map keys, canonical bytes, sets/lists/symbols, explicit `(document {...})` plus type-directed bare literals, and KIR/ESM/Wasm/NBB parity are complete |
| Binary values | Coherent bounded foundation | `(bytes)` is the canonical empty value; `:bytes` crosses checked KIR/ESM/Wasm closure boundaries; nonempty payloads remain explicit typed host/provider inputs |
| Higher-order calls | Strong across closed and project-module boundaries | a lexical closure uses ordinary `(f ...)`; a computed head uses visible `invoke` but inherits any closed result context; `[:fn [params result] ...]` is the bounded public contract |
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

Compiler#531 removes the wrapper where the enclosing type already makes the
choice exact:

```clojure
(defn request [] :document
  {:goal "migrate"
   :attempt 3
   :actors #{actor/run}})
```

The automatic form is deliberately narrower than explicit `(document ...)`:
simple symbols and lists remain variable/call expressions, while namespaced
symbols and recursively closed scalar/map/vector/set data elaborate directly.
This is the attractive boundary: ordinary data remains ordinary data, but the
compiler does not guess whether ambiguous syntax is code or data.

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

`invoke` remains intentional for a computed/non-symbol closure expression.
Its result descriptor is omitted when a closed consumer or return annotation
already determines it, and remains explicit only when authored code must make
an otherwise ambiguous result-family choice. `fn-ref` remains
the explicit conversion of a known top-level function into a value. This keeps
the common lexical path visually ordinary without pretending that every form
in call-head position is dynamically callable.

The same contextual rule now covers `:vector-i64`: vector operations and the
collection inputs to `map`, `filter`, and `reduce` select the vector dispatcher
for a lexical call. This admits library-shaped code such as
`(defn call-singleton [f] (vector-at (f 7) 0))`, while a computed closure head
uses `(invoke closure 7)` in that same context. Wrong result families and unknown lambda
ids still trap rather than coerce.

String-consuming operations now provide the corresponding `:string` context,
and declared `:string` function results carry it through the value-producing
tails of `let`, `if`, and `do`. Thus `(string-length (render 42))` stays an
ordinary lexical call, including through a declared library boundary. A
computed closure head remains visible as `(invoke closure 42)`, and a
wrong-family closure still traps closed.

The same closed rule now covers `:document`. Document consumers and
unambiguous document constructor positions select the document dispatcher, so
`(document-count (build 42))` remains ordinary lexical application and a
declared `:document` result carries through `let`, `if`, and `do` tails.
Document-map keys deliberately remain explicit when their type is ambiguous
between keyword and document; contextual computed heads use `(invoke ...)`.

The flat dispatcher profile also covers `:f32`, `:f64`, and `:vector-f64`.
Numeric operations, comparisons, conversions, and vector-f64 consumers provide
the expected result context, so ordinary lexical calls remain `(decode bits)`
or `(samples bits)` rather than spelling a descriptor at each use. Explicit
computed calls remain `(invoke closure bits)` in a numeric context, with a wrong numeric family
trapping before any typed default can escape.

The dispatcher is now keyed by the complete canonical result descriptor for
nominal records and variants, `[:option T]`, `[:result T E]`, heterogeneous
vectors, typed sets, and typed maps. Their constructors, projections,
consumers, updates, equality checks, and match arms give a lexical call enough
context to remain `(f ...)`; declared result boundaries and typed `fn-ref`
values carry the same context. Nested typed closures also seed one another's
requested dispatcher signatures, so an outer closure can call an inner typed
closure without falling back to provisional `:i64` inference. Schema references
are resolved before variant construction, matching, rewriting, and dispatch.
Different nominal or parameterized values never share a dispatcher. At a
computed head the operator stays visible, but the full descriptor is written
only when no surrounding form determines it, for example
`(invoke [:option :string] closure 42)`. That explicitness is useful honesty at
an ambiguous type boundary, not syntax noise on every dynamic call.

Canonical typed lists follow the same rule. In ordinary source the constructor
remains the familiar `(list ...)`; a surrounding `[:list T]` result context
selects the canonical boundary representation without exposing the internal
`typed-list-new` KIR operation. A computed call in the same context is simply
`(invoke closure 42)`; only an uncontextualized call spells
`(invoke [:list :i64] closure 42)`. This keeps construction data-shaped and
makes representation explicit exactly where static inference cannot recover it.

The current descriptor profile is deliberately bounded by fail-closed trap
generation: a result descriptor is admitted only when the compiler can build a
typed fallback value for an unknown or wrong-family closure. Bytes now have the
canonical zero-argument `(bytes)` spelling and an internal empty fallback, so
`:bytes` crosses closure and function boundaries without exposing
`bytes-empty`. Nonempty binary payloads still enter through typed host/provider
boundaries. Linear resources remain excluded because inventing a fallback
handle would violate affinity rather than improve syntax.

Multi-expression `do` is now portable through the restricted ESM path as well
as the reference and Wasm paths. Its non-tail expressions are evaluated in
order and cannot be optimized away, while the final expression keeps its typed
closure result context. The web representation also guards the compiler's
reserved closure dispatcher handle as the physical pair it is, without
weakening ordinary i64 parameter guards.

Closure boundaries now use the same checked representation. A module-local
fixed-point pass follows closure values backward and forward through static
calls, sequential aliases, ordinary parameters, captures, and function
results. It records canonical closure parameter/result metadata in typed KIR;
restricted ESM consequently uses `assertClosure` at those boundaries while
ordinary scalar parameters retain `assertI64`. This keeps higher-order source
ordinary:

```clojure
(defn apply-one [f x] (f x))
(defn identity [f] f)
(defn wrap [f] (fn [x] (f x)))
```

Likewise, `(apply f x (list y z))` keeps the familiar source list spelling;
the compiler records its lowered tail as a bounded i64 pair-chain instead of
leaking that representation into source syntax or weakening scalar guards.

No `invoke`, ABI tuple destructuring, or closure annotation is needed merely
because a closure crosses a boundary inside the module. The inference remains
fail-closed: candidate-specific dispatcher arguments are not globally promoted,
malformed closure values trap, and the nullable zero sentinel used by lazy
sequences is preserved only on the proven nonzero branch.

### Completed — open-module callable signatures

An unconstrained exported higher-order function now declares the same
data-shaped contract used by the rest of Kotoba's value types:

```clojure
(defn apply-one [f [:fn [[:i64] :i64]] x :i64] :i64 (f x))
(defn make-renderer [] [:fn [[:i64] :string]]
  (fn [x] (string-from-i64 x)))
```

Each `[:fn ...]` contains one to five unique arity clauses; a clause is
`[parameter-types result-type]`, and callable arity remains zero through four.
The first profile admits only i64 parameters, matching the real closure ABI,
while every non-linear result family with a safe dispatcher is available.
Callable and linear-resource results are rejected rather than represented by a
synthetic handle.

The descriptor is inert public contract data. It lowers to physical i64 plus
the existing checked closure refinements; KIR and Wasm gain no second function
representation. Project interfaces preserve the descriptor. The linker assigns
disjoint lambda-ID ranges to modules and emits one bounded router for each
arity/result family, so a library closure genuinely executes across the module
boundary. Wrong arity, result family, malformed handles, and dishonest literal
`fn` implementations fail closed.

### Completed — contextual computed calls

A computed head remains visibly dynamic but no longer repeats a result type
already fixed by its surroundings:

```clojure
(string-length (invoke renderer 42))
(record-get Person (invoke factory 42) :name)
```

Consumers, typed constructors, and declared function results select the same
closed dispatcher families used for lexical calls. With no such context,
`invoke` keeps the i64 default or accepts an explicit descriptor such as
`(invoke [:option :string] factory 42)`. Wrong-family closures still trap.
This removes redundant annotation without hiding the computed call itself or
adding runtime type inspection.

### Completed — bounded records and closed protocols

The canonical compiler now accepts the Clojure-shaped surface directly:

```clojure
(defprotocol Value
  (value [this]))

(defrecord Box [x]
  Value
  (value [this] (get this :x)))

(defn main []
  (value (->Box 7)))
```

This is a material aesthetic improvement over repeating a complete
`[:record ...]` descriptor at every construction, projection, parameter, and
result site. The frontend lowers declarations to nominal record operations and
private static functions; protocol call syntax stays ordinary without adding
reflection or runtime type guessing. One `extend-type` may contain multiple
protocol sections, and every section implements every declared method exactly
once.

The visible bounds are coherent rather than accidental. Unannotated fields
default to `:i64`; typed fields reuse the function signature spelling,
`[name :string active :bool]`. A nominal record may contain up to 32 fields.
For records wider than five fields, direct `->Type` and exact-literal
`map->Type` construction remain available, while `->Type` does not pretend to
be a first-class function outside the truthful five-parameter callable ABI.
`map->Type` also carries that nominal context through the result positions of
total `if`/`if-not`/`if-let`/`if-some`, `cond` with `:else`, `case`/`condp`
with defaults, and the final expression of `let`/`do`. Every reachable leaf
must still be an exact literal map. Missing paths, wrong key sets, and arbitrary
map variables are compile errors rather than runtime coercions. Both
`(get record :field)` and the idiomatic `(:field record)` are type-directed.
When an `ns` schema recursively refers to a later `defrecord`, compiler ADR
0210 predeclares the bounded same-module record descriptor before validating
the closed graph. Exact explicit declarations remain compatible under ADR
0209; incompatible collisions and undeclared names are rejected. The authored
schema therefore states only the recursive edge, while `defrecord` owns the
shape exactly once.
`extend-protocol` defaults remain an explicit gap. Arbitrary dynamic-map to
record conversion is deliberately not hidden inside `map->Type`; if needed it
requires a separately named checked operation and a real dynamic-map value
contract.
Legacy primary tag dispatch still returns a zero sentinel for an unknown tag;
that behavior should converge to fail-closed semantics rather than become part
of the language aesthetic.

Acceptance evidence is compiler ADR 0204/0205/0208/0209/0210/0214,
compiler#520/#521/#525/#526/#527/#532, and the
`:record-protocol-static-dispatch` plus `:typed-defrecord-fields` cases
executing on KIR and `wasm32-kotoba-v1` with results `16` and `13`, plus
`:wide-nominal-records` executing with result `8`. The computed constructor
suite also executes on KIR, restricted ESM, and browser Wasm, while its
JVM-free NBB Wasm fixture fixes the portable compiler path. Provider#177/#179 and
provider ADR 0281/0283 additionally prove twelve recursive production packages
against their independent KIR oracles, with byte-identical Wasm after removing
the explicit schema descriptors.

### Completed — type-directed access and nested patterns

Ordinary `get`, keyword lookup, and `nth` now select record, typed-map,
homogeneous-vector, floating-vector, or heterogeneous-vector access from the
inferred receiver. The same decision happens after recursive binding patterns
are expanded, so source stays data-shaped:

```clojure
(let [[id [name active]] row] ...)
(let [{{:keys [name]} :profile} user] ...)
```

The source and every intermediate value are evaluated once. Required record
fields and heterogeneous positions fail closed; typed-map bindings require an
explicit `:or` value because lookup may miss. Homogeneous vector rest keeps its
bounded `vector-drop` behavior. Heterogeneous `& rest` slices its exact suffix
descriptor at compile time and rebuilds the bounded suffix without exposing
that descriptor in source. Closed mixed and nested vector literals infer the
same exact descriptor, so `[1 [2 3] 4 5]` remains ordinary data-shaped syntax.

Compiler ADR 0206/0207/0211 and the `:nested-typed-destructuring` and
`:nested-let-destructuring` cases exercise the completed slice on KIR and
`wasm32-kotoba-v1` with results `26` and `29`.

### Completed — canonical bytes stay below the authored surface

The first real actor adoption confirms the desired boundary shape without
adding source punctuation or representation functions. Actor domain code still
calls ordinary named operations:

```clojure
(delta-to-bytes delta)
(delta-from-bytes bytes)
```

The adapter, not authored Kotoba, owns `:actor-ipc.delta/v1`, explicit Float64
wire tags, canonical map ordering, and the ability's 16 MiB envelope limit.
Legacy `pr-str`/`read-string` text masquerading as bytes is rejected. The same
JVM and real CLJS tests observe actual byte arrays, and the consumer needs only
kotoba-lang org runtime libraries; CID hashing is not loaded on this value-only
path. This is architectural evidence for the syntax rule: physical wire
preparation belongs in generated/provider adapters, while public code names the
semantic operation.

Compiler#533 now applies this layering to canonical compiler hosts for scalar
and `:document` abilities. Authored code keeps the qualified semantic call;
the generated adapter alone translates ordinary canonical values to bounded
`kotoba.value.v1` bytes. The legacy `data-host-arg` spelling remains
kotoba-wasm-only and is not the portable language direction.

The provider ops kits now confirm the same rule across HTTP, secret, process,
git, entropy, and scoped-fs audit paths. Their public operations did not acquire
an `encode`, `parse`, tag, or buffer argument: adapters emit bounded
`:provider.ops-audit/v1` request/reply bytes, while the old W4 EDN is retained
only as compatibility evidence. Real JVM, ClojureScript/Node, and compiler
consumer conformance all exercise that boundary. Provider ABI work still listed
below concerns native record/option/result buffers, not authored wire syntax.

Exact signed i64 follows the same layering. The shared wire now has append-only
code 9 and an explicit adapter wrapper over 8-byte two's-complement data, with
byte-identical JVM and real ClojureScript vectors through both limits. Kotoba's
authored `:i64` type does not gain a suffix or conversion form; compiler/provider
adapters remain responsible for selecting the wrapper when they lower a typed
boundary. Unsafe JavaScript Numbers are rejected instead of being rounded into
a plausible value.

### P0 — remove remaining compiler-shaped source plumbing

- Standardize schema-directed record/variant/option/result ability wire shapes
  and the Wasm component host binding. Compiler#533 already keeps
  `bytes-ptr`/`bytes-len` and codec selection inside generated adapters for
  scalar and `:document` JVM/CLJS hosts.
- Extend contextual callable results to linear resources only when a truthful
  affine trap/result model exists; never invent a fallback handle for elegance.
- Decide a bounded specialization rule for `extend-protocol` defaults and
  remove the legacy zero-sentinel dispatch path.

Compiler#532 closes the former non-literal `map->Type` item with exact map
leaves under total bounded control. It does not relabel arbitrary runtime maps
as records, so nominal identity and heterogeneous field descriptors remain
closed.

After compiler#527 and provider#172–#179 (ADR 0276–0283), the canonical source
scan on 2026-08-04 found zero authored `record-new`, `record-get`, or
`hetero-vector-at` operations in provider `.kotoba` files. Twenty-six distinct
provider packages removed all 274 sites without changing their exports,
capability wires, `main` oracles, or, for the final `nth` migration, even their
Wasm bytes.

More record or projection sugar is therefore not justified by current
production evidence. The latest process and git records intentionally have
separate nominal identities despite sharing a physical shape; the twelve
recursive packages likewise use namespace-local `EdnKeyValue` identities.
This is useful domain meaning rather than ABI-layout coincidence. The
declaration prepass removes only repeated shape text and preserves
exact-collision and closed-schema checks. Compiler#528 closes the separate
heterogeneous-rest seam with an exact bounded descriptor slice rather than
guessing from incomplete runtime evidence.

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
idiomatic, lexical closures use ordinary application across every structured
result family that currently has a safe portable default, and effects remain
qualified and visible. Computed expression heads and explicit top-level
function values still expose `invoke`/`fn-ref`; `invoke` inherits a closed
result context and accepts a complete descriptor only when the type is otherwise
ambiguous, so nominal and parameterized types remain honest at that boundary.
The remaining callable result-family gap is confined to linear resources, for
which typed trap generation cannot construct a truthful portable fallback.
Canonical typed lists retain ordinary `(list ...)` construction and expose
`[:list T]` only at an ambiguous computed-call boundary. Closure-valued
parameters, captures, and results are inferred inside a closed module and
explicitly contracted at an open-module boundary across KIR, restricted ESM,
and Wasm. The `[:fn ...]` spelling is consistent with `[:option T]` and
`[:result T E]`, while ordinary calls remain `(f x)` and the physical ABI stays
hidden. Type-directed bare document literals improve this without closing or
renaming the distinct physical compiler data-host ABI gap. With
multi-expression `do` and recursive typed patterns portable, the
remaining aesthetic friction is concentrated in the intentionally visible
computed-call operator (`invoke`), explicit top-level function conversion
(`fn-ref`), and compiler/provider ABI preparation. Actor Delta and provider
ops audit wire no longer contribute to that friction: canonical bytes, float
tagging, and limits are hidden behind semantic operations.
Record/protocol code is no longer part of that friction: ordinary declarations,
constructors including computed total-control `map->Type`, field access, and
statically resolved calls now form one readable source story.
