# Kotoba language surface matrix

**Generated** from `lang/surface-status.edn` — do not hand-edit.
Regenerate: `clojure -M -m kotoba.lang.surface-matrix`
Check: `clojure -M -m kotoba.lang.surface-matrix --check`

| Field | Value |
|---|---|
| surface-status version | 1 |
| profile version | 6 |
| as-of | 2026-08-30 |
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
| `explicit-errors` | `intentional-security-constraint` |  | Ambient throw/try/catch is untracked non-local control flow: it exits             scopes the inferred effect row never mentions and skips unwind             obligations (dataspace facet retraction has no checked unwind yet).             The ban is on the ambient form. Since 2026-09-02 the typed abort             ability admits the heads by elaboration: the effect appears in the             inferred row as :abort and the function lowers to [:result T E], so             the ambient form never exists post-elaboration. Slice 2 (2026-09-02)             made :abort propagate through calls and A-normalized an aborting             operand or test into a let binding; neither widens the invariant,             because a propagated abort is on the caller's row and an             A-normalized one is the same elaboration in a different position.             Where the unwind precondition would matter, the abort stays refused             -- for a CALL now as well as for a throw. |
| `no-ambient-authority` | `intentional-security-constraint` |  | Components cannot manufacture code or authority from ambient process state.             Source strings, reader forms, loaded namespaces, and compiled host             objects were never effect-inferred and are not part of a definition             CID. The admitted `(eval request)` operation is therefore separate:             it selects already checked KIR by CID through :code/eval and is             re-admitted by the host. |
| `no-ambient-mutation` | `intentional-security-constraint` |  | External mutable state is provider-owned and capability/policy mediated; component-local state must use an explicitly bounded model.             The invariant is AMBIENT, not mutation itself: the :state capability             kit is the admitted non-ambient model, and the :widening-path heads may             later desugar to it (effect row shows :state, a grant is required at             instantiation, and capability handles are rejected as stored values so             affine provenance tracking survives). Until that desugar and its             conformance vectors land, every head here stays rejected fail-closed.             binding / var / alter-var-root / set! have no ability model decided             and carry no widening path. |
| `no-guest-macros` | `intentional-security-constraint` |  | The safe component surface must be statically inspectable before execution.             Unrelaxable: expansion executes code inside the compiler (build time),             and the definition CID hashes post-desugar typed KIR, so unbounded             macros both run early and make source identity unreviewable.             defdesugar (bounded pure desugar) remains the admitted alternative. |
| `no-implicit-numeric-conversion` | `intentional-semantic-simplification` |  | Mixed numeric operand types (:i64 / :f64 / :f32) are refused naming             both types and the explicit conversions (i64-to-f64-checked /             -rounded, f64-to-i64-checked / -truncating, i64-to-f32-checked /             -rounded, f32-to-i64-checked / -truncating, f32-to-f64-exact,             f64-to-f32-rounded). A silent promotion would make `(+ a b)` mean             different things on different backends and hide the rounding the             author never wrote. |
| `no-interop` | `intentional-security-constraint` |  | Arbitrary JVM/JS object and method access bypasses capability admission.             Unrelaxable by grant dispatch: interop never reaches             guard-component-ability-call, so grant intersection, receipts and             revocation cannot see the call. Vacuous on the wasm32 ABI (no such             path exists); load-bearing at portable/trusted, where the subset gate             is the only boundary (no separate VM sandbox is claimed there). |
| `no-unbounded-concurrency` | `intentional-security-constraint` |  | Component scheduling and resources must remain tender-controlled and bounded.             Neither definition CIDs nor delegated grants meter CPU or scheduling;             fuel is per-instance and ambient threads would escape it. A             structured-spawn ability with sub-budgeted fuel is designable but             undecided; no widening path yet. |

## Collections

| Surface | Disposition | Backends | Note / reason |
|---|---|---|---|
| `contextual-document-literal` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `map-function` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm | Do not diagnose this as lack of map literals; the implemented slice is eager and fuel-bounded. |
| `map-literal` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm | :intentional-persistent-pair-chain-not-hamt |
| `native-homogeneous-vectors` | `implemented-partial` | compiler-native-host | This records the homogeneous native value path. It does not claim native parity for the separate source vector literal entry, whose portable pair-chain representation remains unchanged. |
| `persistent-collection-semantics` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `set-literal` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm | Runtime-equal duplicates are removed; this remains an untagged linear set representation. |
| `vector-literal` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm | :intentional-persistent-pair-chain-not-vector-trie |

## Other surface (gaps & partials)

| Surface | Disposition | Backends | Note / reason |
|---|---|---|---|
| `backend-parity` | `implemented-partial` |  |  |
| `bounded-control-and-sugar` | `implemented-partial` |  |  |
| `data-host-argument` | `implemented-partial` | compiler-host-cljs, compiler-host-jvm, kotoba-wasm |  |
| `dataspace` | `implemented-partial` | compiler | Source forms make coordination native without making assertion data authoritative. Explicit facet enter/leave is the safe current lifecycle surface. |
| `dynamic-arity-apply` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `filter-function` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `first-class-closure-values` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm | Public [:fn [parameter-types result-type] ...] contracts cross project-module boundaries; parameters are intentionally i64-only in the first ABI-neutral profile. Computed heads remain explicit, while their result descriptor is inferred from a closed consumer or return context and stays explicit only when ambiguous. |
| `inline-fn-callbacks` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `lazy-sequences` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `multi-collection-map` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `named-multi-arity-functions` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `native-binary32-arithmetic` | `implemented-partial` | compiler, kotoba-kir |  |
| `nested-destructuring` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm | Source and intermediate collection expressions are each evaluated exactly once. |
| `option-flow` | `implemented-partial` | compiler, kir, kotoba-cljs, restricted-esm, wasm32 |  |
| `portable-source-stdlib` | `implemented-partial` |  |  |
| `portable-value-model` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm | Primary Wasm preserves literal IDs while dynamic string results use allocation-checked descriptors with canonical content IDs; primary CLJS uses native immutable strings and symbols; compiler uses a distinct typed symbol value kind and checked dynamic construction, and its string operations share the UTF-8 byte-boundary contract. |
| `protocol-and-record-dispatch` | `implemented-partial` | compiler, kir, kotoba-cljs, kotoba-wasm, wasm32 |  |
| `record-schema-values` | `implemented-partial` | compiler, kotoba-kir, kotoba-wasm |  |
| `reduce-function` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm |  |
| `release-integration` | `implemented-partial` |  |  |
| `string-predicate-typing` | `implemented-partial` | compiler, kotoba-cljs, kotoba-wasm | `string=?` and `string-contains?` infer `:bool`, like every other predicate, so     they compose under `and`/`or`/`not` and can be returned from a `:bool` function.     A string predicate no longer sits in an `:i64` position -- convert explicitly. |
| `typed-eval` | `implemented` | compiler, kir |  |

## Classification rule

See `:classification-rule` in `lang/surface-status.edn` (not expanded here).
