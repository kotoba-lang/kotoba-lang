# Kotoba language surface matrix

**Generated** from `lang/surface-status.edn` — do not hand-edit.
Regenerate: `clojure -M -m kotoba.lang.surface-matrix`
Check: `clojure -M -m kotoba.lang.surface-matrix --check`

| Field | Value |
|---|---|
| surface-status version | 1 |
| profile version | 5 |
| as-of | 2026-08-04 |
| authority ADR | `docs/adr/ADR-kotoba-language-surface-status.md` |

WBS: **T2.2**. Disposition meanings live under `:dispositions` in the EDN source.

## Dispositions

| Keyword | Meaning |
|---|---|
| `implemented-partial` | Usable in at least one current backend, with the recorded semantic or parity limits. |
| `intentional-security-constraint` | Excluded to preserve a named component safety invariant; widening requires an ADR and fail-closed enforcement. |
| `intentional-semantic-simplification` | Deliberately narrower than Clojure for deterministic, portable, or verifiable execution. |
| `not-yet-implemented` | Not a safety prohibition; representation, bounded semantics, conformance, or backend parity is unfinished. |

## Security / language invariants

| Surface | Disposition | Backends | Note / reason |
|---|---|---|---|
| `bool-is-a-type-not-a-number` | `intentional-semantic-simplification` |  | Comparisons and predicates are `:bool`. Profile 4 made them i64 so             `(+ (zero? x) (pos? x))` was admitted; that is a type error in             Clojure and it prevented `and`/`or`/`not` from composing into a             readable predicate. Migration is mechanical: `(if p 1 0)`. |
| `bounded-admission` | `intentional-security-constraint` |  | Untrusted and generated programs must fail closed under finite admission and execution resources. |
| `capability-only-affinity` | `intentional-semantic-simplification` |  | Affine consumption is scoped to capability values; a general ownership/borrow/lifetime system is intentionally absent. |
| `explicit-errors` | `intentional-security-constraint` |  | Component/provider effects use explicit result/error values rather than hidden exception paths. |
| `no-ambient-authority` | `intentional-security-constraint` |  | Components cannot manufacture code or authority from ambient process state. |
| `no-ambient-mutation` | `intentional-security-constraint` |  | External mutable state is provider-owned and capability/policy mediated; component-local state must use an explicitly bounded model. |
| `no-guest-macros` | `intentional-security-constraint` |  | The safe component surface must be statically inspectable before execution. |
| `no-interop` | `intentional-security-constraint` |  | Arbitrary JVM/JS object and method access bypasses capability admission. |
| `no-unbounded-concurrency` | `intentional-security-constraint` |  | Component scheduling and resources must remain tender-controlled and bounded. |

## Collections

| Surface | Disposition | Backends | Note / reason |
|---|---|---|---|
| `contextual-document-literal` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `map-function` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm | Do not diagnose this as lack of map literals; the implemented slice is eager and fuel-bounded. |
| `map-literal` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm | :intentional-persistent-pair-chain-not-hamt |
| `persistent-collection-semantics` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `set-literal` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm | Runtime-equal duplicates are removed; this remains an untagged linear set representation. |
| `vector-literal` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm | :intentional-persistent-pair-chain-not-vector-trie |

## Other surface (gaps & partials)

| Surface | Disposition | Backends | Note / reason |
|---|---|---|---|
| `backend-parity` | `implemented-partial` |  |  |
| `bounded-control-and-sugar` | `implemented-partial` |  |  |
| `data-host-argument` | `implemented-partial` | kotoba-wasm |  |
| `dynamic-arity-apply` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `filter-function` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `first-class-closure-values` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `inline-fn-callbacks` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `lazy-sequences` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `multi-collection-map` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `named-multi-arity-functions` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `nested-destructuring` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm | Collection source expressions are temp-bound and evaluated exactly once. |
| `option-flow` | `implemented-partial` | compiler, kir, kotoba-cljs, restricted-esm, wasm32 |  |
| `portable-source-stdlib` | `implemented-partial` |  |  |
| `portable-value-model` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm | Primary Wasm preserves literal IDs while dynamic string results use allocation-checked descriptors with canonical content IDs; primary CLJS uses native immutable strings and symbols; compiler uses a distinct typed symbol value kind and checked dynamic construction, and its string operations share the UTF-8 byte-boundary contract. |
| `protocol-and-record-dispatch` | `not-yet-implemented` |  |  |
| `record-schema-values` | `implemented-partial` | compiler, kotoba-kir, kotoba-wasm |  |
| `reduce-function` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `release-integration` | `implemented-partial` |  |  |

## Classification rule

See `:classification-rule` in `lang/surface-status.edn` (not expanded here).
