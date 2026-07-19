# ADR — Intentional constraints versus implementation gaps

- **Status**: Accepted
- **Date**: 2026-07-18
- **Artifacts**: `lang/surface-status.edn`, `lang/guest-grammar.edn`
- **Related**: `ADR-kotoba-lang-profile.md`, `ADR-safe-capability-language.md`

## Context

Kotoba is deliberately a small, capability-confined Clojure-shaped language,
but a small surface can have two different causes:

1. a construct is excluded to preserve a security or semantic invariant; or
2. the construct is useful and compatible with those invariants, but has not
   been implemented or made portable across the current backends yet.

Treating every missing Clojure feature as a security decision makes temporary
implementation gaps look permanent. Treating every omission as a backlog item
is worse: it makes `eval`, ambient namespace loading, interop, and ambient
mutation appear eligible for accidental admission.

Collections exposed this ambiguity. The current CLJC compiler reads map,
vector, and set literals. It lowers map and vector literals to existing pair
primitives, but does not lower a set literal. Separately, the symbol `map`
means the higher-order sequence function and is not the same feature as a map
literal. Documentation that says only "map is unsupported" is therefore
incorrect.

## Decision

Maintain `lang/surface-status.edn` as the machine-readable classification of
the current guest language surface. Every absent or partial feature is assigned
one of these dispositions:

- `:intentional-security-constraint` — excluded to preserve a named safety
  invariant. Admission must remain fail-closed unless a later ADR replaces the
  invariant with an equally enforceable design.
- `:intentional-semantic-simplification` — deliberately narrower semantics,
  chosen for determinism, portability, or a small verifiable runtime. This may
  be widened only by an explicit language decision.
- `:implemented-partial` — usable now with documented backend or semantic
  limits; it must not be advertised as full Clojure compatibility.
- `:not-yet-implemented` — not a safety prohibition. Implementations may add it
  when representation, limits, conformance fixtures, and backend parity are
  defined.

The classification is about the **Kotoba guest surface**, not about Clojure or
ClojureScript used to implement the compiler and host tooling.

## Collection decision

### Map literals

Map literals are implemented in `kotoba-lang/compiler`. They lower
deterministically to a pair-list of key/value pairs. `get` performs a linear
scan; `assoc` constructs a replacement association list. This reuses existing
bounded pair primitives instead of introducing a new GC-managed hash-map
runtime.

This is `:implemented-partial`, not a security exclusion. It is not yet a
Clojure persistent hash map: lookup is linear, map operations are narrow, and
backend parity is incomplete.

### Vector literals

Vector literals are implemented as data in `kotoba-lang/compiler`, using the
same pair-chain representation as lists. The runtime intentionally has no tag
that distinguishes such a vector value from a list value. This is a useful
implemented slice plus an intentional semantic simplification, not full
persistent-vector semantics.

Binding and parameter vectors remain syntax owned by their enclosing forms;
they are not confused with vector-as-data during lowering.

### Set literals

The bounded reader recognizes `#{...}` and both `kotoba-lang/compiler` and the
primary `kotoba` Wasm/CLJS lowering paths lower sets to a bounded unique
pair-chain. Source forms are sorted before lowering,
runtime-equal duplicates are removed, and `contains?`, `conj`, and `disj` use
compiler-synthesized recursive helpers. Set literals are therefore
`:implemented-partial`, with a 16-item admission limit chosen so worst-case
duplicate-removal walks remain inside the existing execution-fuel budget.

This is not yet a persistent hash set. A shared positive conformance fixture
(`:bounded-set-literal-and-operations`) now covers literal construction,
runtime-equal duplicate removal, membership, addition, and removal. The
remaining gaps are tagged collection identity and a persistent-set performance
contract. Reader acceptance alone
would not have been language support; the classification changed only after
lowering, operations, bounds, and executable frontend tests existed.

### The `map` function

The higher-order `map` sequence function is separate from map literals. The
compiler implements an eager, one-to-five-collection, fuel-bounded slice whose
callback may be a statically named matching-arity function or an inline `fn`;
inline callbacks are closure-converted into deterministic helper functions with
explicit, ABI-bounded capture parameters. Traversal stops at
the shortest collection. `filter` has the equivalent
named-predicate shape, and `reduce` supports a named binary callback with an
explicit init and one collection. Each lowers to a deterministic synthesized
recursive helper and is `:implemented-partial`.

The primary Kotoba Wasm and CLJS lowerers now share the eager named-callback
slice of `map` (one to five sources, shortest termination), `filter`, and
init-bearing `reduce`. They use an eight-step bounded unroll rather than the
compiler backend's recursive fuel helpers. Inline callbacks capture lexical
locals by bounded `let` substitution on both primary backends. Inline
`[]`/`[acc value]` no-init reduce is also portable. Stored closure callbacks
and named or stored multi-arity no-init reduce now use the same static closure
dispatchers on all three backends. The shared `:bounded-named-higher-order-collections` manifest case
runs the portable named-callback behavior on the primary implementation and is
also valid compiler input.

No-init `reduce` is implemented with Clojure's empty behavior. Inline callbacks
provide `[]` and `[acc value]` clauses; named multi-arity functions may provide
the same 0/2 arities (and may have additional arities). The empty collection
invokes the zero-arity clause.

Named multi-arity `defn` is lowered across compiler and primary Wasm/CLJS to
deterministic arity-mangled static functions. Direct calls, recursive calls,
higher-order named callbacks, and no-init `reduce` are resolved by arity at
compile time. A zero-arity `main` clause retains the required `main` export.
Variadic calls are statically specialized through the five-parameter ABI limit
and bind the rest arguments as a pair-chain. Arity above five remains an
intentional ABI admission bound.

The compiler and primary Kotoba Wasm/CLJS lowerers implement bounded first-class
closure values. A fixed-arity
`fn` clause with zero to four parameters lowers to a pair containing a deterministic
lambda ID and an explicit capture pair-chain. It may be returned, stored in a
collection, or passed through another function. `(invoke closure args...)`
selects an arity-specific static dispatcher; it never performs a computed host
call or reflection. Captures plus parameters remain inside the five-parameter
ABI bound. Passing a closure value is supported for `map`, `filter`, and
init-bearing `reduce`; because the helper
must also carry the closure value, closure-backed `map` is bounded to four
collections while named/inline callbacks retain five. Multi-arity closure
values share the same ID/capture chain across their arity-specific helpers, so
stored or returned `[]`/`[acc value]` closures also support no-init `reduce`.
Bounded `(apply closure fixed* final-argument-pair-chain)` dispatches runtime
argument counts zero through four through the same static dispatchers and
returns zero when the runtime tail exceeds that bound. Variadic closure
clauses are statically specialized through arity four and bind rest arguments
as a pair-chain. The shared `first-class-closures-invoke-and-apply` case verifies
returned closures, `fn-ref`, `invoke`, and bounded `apply` on the primary path.

Top-level functions can explicitly become first-class values through
`(fn-ref name)`, which creates deterministic arity wrappers for every declared
arity zero through four. An explicit form is used instead of silently treating
value-position symbols as functions, because lexical bindings may shadow a
top-level name. Computed calls remain explicit `(invoke f ...)`; this is an
intentional inspectability simplification rather than an absent call mechanism.

## Intentional constraints

The following remain intentional security constraints:

- no runtime `eval`, dynamic loading, ambient `require`/`use`, or namespace
  resolution;
- no JavaScript/JVM interop or arbitrary object construction;
- no ambient mutable cells, dynamic vars, threads, agents, futures, STM, or
  unrestricted concurrency primitives;
- no arbitrary guest-defined macros in the safe source surface; bounded
  `defdesugar` is the only admitted extension point: templates are pure,
  registered, hygienically temp-bound, node/depth bounded, and cannot call
  host operations or capture free values;
- no exception-based hidden effect path; explicit result/error values are used
  at host boundaries;
- no ambient host authority: effects require explicit, scoped, policy-checked
  capability values;
- bounded source size, nesting, node counts, arities, bindings, collection
  entries, and execution resources, with fail-closed admission.

A general ownership/borrow/lifetime system for every value is intentionally not
part of the language. The affine check is narrowly scoped to capability values;
this is a deliberate safety architecture choice documented by the capability
ADR, not an unfinished promise of Rust compatibility.

## Known implementation gaps

HAMT/vector-trie/hash-trie storage is not a missing language semantic in this
bounded profile: immutable pair-chain operations already return structurally
shared persistent values. Trie storage is an optional performance evolution,
not a prerequisite for calling map/vector/set supported. Remaining semantic
gaps include runtime collection tags/protocols, memoized lazy sequences, and complete
backend parity for current sugar and collection operations. These features may
be implemented without weakening capability confinement, provided their
resource bounds and deterministic lowering are part of their acceptance
criteria.

The compiler now provides a persistent pair-chain operation slice: `count`,
`nth` (with optional default), empty-safe `peek`/`pop`, eager `keys`/`vals`, and
`dissoc`, in addition to the previously recorded map/set operations. These
operations return new values without mutating their inputs. They intentionally
do not claim Clojure's trie-backed class identity or performance contract. The
bounded profile dispatches collection operations by admitted source operation
and pair-chain shape rather than exposing `map?`/`vector?`/`set?` runtime class
predicates. Lookup is bounded linear time and updates preserve structural
sharing. This is the portable collection contract, not hidden unfinished tags
or an unspecified performance promise.

Vector literals now lower to the same persistent pair-chain on the primary
Kotoba Wasm and CLJS paths as well as the compiler backends. All implementations
admit up to 128 literal items. Primary `count`, `nth`, single-source `map`,
`filter`, `reduce`, `keys`, `vals`, and `dissoc` now use fuel-carrying recursive
helpers rather than an eight-step semantic truncation. Helpers that allocate
use tail-recursive accumulators followed by a bounded reverse, avoiding live
allocation state across recursive calls in the primary Wasm backend.
`count`, `nth`, `peek`, and `pop` therefore have
cross-implementation vector coverage, while trie-vector performance and tagged
vector/list runtime class identity is intentionally outside this bounded,
shape-directed profile.

`defrecord` is now implemented across the compiler and primary Kotoba Wasm/CLJS
lowerers. It expands to deterministic
`->Type` and `map->Type` constructors over persistent maps and stores the
record identity under `:kotoba.record/type`. Field lookup and persistent update
reuse ordinary map operations. `defprotocol` declarations and record-local
method implementations now compile to static dispatchers over that tag.
Methods may take one to five parameters; an unknown tag returns the explicit
zero value rather than attempting ambient reflection. `extend-type` and
`extend-protocol` add implementations to the same static dispatcher and reject
duplicate type/method implementations. An `extend-protocol` `default` section
provides the explicit static fallback. `definterface` is supported as the same
safe static method contract—never as ambient JVM/JS interop. The shared
`record-protocol-static-dispatch` conformance case covers constructors,
record-local implementation, and `extend-type`; this bounded static-dispatch
slice now has portable backend parity.

All three backends now provide the explicit lazy-sequence slice. A non-empty
sequence is a resolver closure that yields either zero (empty) or a pair of
zero-arity closures, one for the head and one for the tail. `lazy-first` and
`lazy-rest` force only their respective closure, `lazy-empty?` resolves only
the envelope, and fuel-bounded `take`/`drop` permit
finite observation of infinite generators. These thunks are pure call-by-name,
not memoized: repeated forcing re-evaluates the selected expression. Memoized
Clojure-style lazy seq identity is intentionally outside this pure profile,
not an unfinished hidden cache. To prevent repeated forcing
from duplicating capability effects, transitive effect inference rejects every
lazy thunk whose inferred effect set is non-empty. `lazy-map` supports one to
four sources with named, inline, or stored-closure matching-arity callbacks,
stops at the shortest source, and does not force source tails prematurely.
`lazy-filter` uses the resolver envelope to delay its search and still represent
an all-rejected source as genuinely empty; its search is execution-fuel bounded.
The shared `pure-call-by-name-lazy-sequences` case covers infinite generation,
bounded observation, mapping, and filtering. Primary lowering also rejects
directly or transitively effectful thunks before emission.

Likewise, map destructuring supports keyword identity (`:keys`, `:or`, `:as`,
and explicit keyword patterns), plus `:strs` and `:syms`. Strings, keywords,
and quoted symbols now have deterministic tagged integer identities on all
backends. Strings carry UTF-8 byte length and a bounded hash payload; distinct
literals that collide inside one module are rejected before lowering. This is
an immutable identity/value slice, not Clojure's full string or symbol API.

Nested `let` destructuring is now portable across compiler and primary
Wasm/CLJS lowerers. Vector positional/`&` and map `:keys`/`:or`/`:as` plus
explicit keyword-to-pattern entries expand into sequential temp bindings, so
the source expression is evaluated once. Function-parameter destructuring
now normalizes to generated plain parameters plus the same body-leading `let`,
so it shares the portable semantics and conformance fixture.

## Release and expressiveness priority

The next release gate is integration, not adding more syntax. Portable support
requires the authority grammar, both compiler implementations, shared
conformance fixtures, CLI emit/run paths, deterministic byte output, and the
release qualification suite to agree. A feature implemented in one lowering
path remains `:implemented-partial` until that matrix is green.

Before adding HAMTs or vector tries, replace the primary backend's fixed
eight-step collection-transform unroll with deterministic fuel-carrying helper
functions. The current limit is observable semantics, not merely a performance
detail, and increasing the constant would trade the gap for Wasm code-size
growth. Pair-chain ABI and linear lookup remain acceptable for the bounded
profile; a tagged/trie collection ABI is deferred until representative
workloads show that lookup or allocation dominates.

`lang/performance-workloads.edn` is the authority workload catalog. The
single-source 12-item case is green through fuel helpers. Multi-source `map`
still uses the old eight-step static arity specialization. Adding callback,
source cursors,
accumulator, and fuel to one recursive helper would exceed the five-parameter
ABI. Experiments with 16- and 128-step expansion showed prohibitive compile
time because the current expression expansion grows exponentially across
sources. Therefore this residual remains explicit rather than shipping a
code-size regression or silently claiming fuel-128 parity. A compact internal
helper-state representation is required before widening it.

Clojure-shaped expression growth is selective. Bounded `loop`/`recur`, pure
`match`, and registered `defdesugar` are compatible with the safety model and
belong in the portable profile after compiler parity and conformance. General
macros, runtime code loading, reflection, ambient mutation, exceptions, and
unbounded concurrency remain excluded. Full multimethods, transducers,
metadata-driven dispatch, arbitrary protocols over host objects, and the broad
Clojure sequence/string libraries remain backlog rather than release blockers.

The practical standard library is source-level and portable: immutable
Option/Result records and bounded collection combinators compile through the
same guest surface. `lang/stdlib.edn` versions the source artifact and pins its
SHA-256. Import is explicit through `compile --prelude`; no ambient or default
prelude is loaded. The launcher smoke covers successful composition and
fail-closed unreadable preludes without adding privileged runtime primitives.

Publishing remains a release operation rather than a source-tree fiction:
after these changes are committed and published, consumers must update the
compiler and kotoba-lang git SHAs to those immutable commits. A dirty or
unpublished workspace state must never be recorded as if it were a valid pin.

One pre-final full-suite run observed a `StackOverflowError` in the adversarial
deep-reader corpus. The immediately repeated final integration matrix passed
316 tests and 1,519 assertions. This is recorded as parser-hardening follow-up,
not silently promoted to a deterministic release blocker without reproduction.

### Resolved release blockers

The P1–P3 language changes were not the cause of the final five integration
failures. Ed25519 verification has been restored in
`package-manifest-error`, including real signed positive fixtures; forged
signatures continue to fail closed.

The mesh failure-stage contract now names strict static rejection as canonical:
`mesh_bad_route.kotoba` is rejected before Wasm compilation, preserving the
check-before-emit invariant.

## Consequences

- User-facing diagnostics must distinguish `map` the function from a map
  literal.
- A reader recognizing syntax is not evidence that the language supports its
  runtime semantics.
- New features default to `:not-yet-implemented`, not to "forbidden for
  safety", unless an ADR identifies the invariant they would violate.
- An intentional constraint cannot silently become ordinary backlog work.
- Backend-specific support is recorded honestly and is not promoted to the
  portable profile until conformance evidence exists.
