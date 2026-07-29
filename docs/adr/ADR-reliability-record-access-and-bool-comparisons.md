# ADR: record access ergonomics and bool-typed comparisons

- Status: Accepted (measurement + design); implementation not started
- Date: 2026-07-29
- WBS: follow-on to T5.3 / T4.4; touches T1.3 and the T2 profile

## Context

`kotoba-lang/murakumo` T5.3 replaced the rebalance base-65536 seat pack with
`[:record :rebalance/lanes …]`. That landed and is byte-parity green, but the
resulting source is not yet what the migration plan's target DX promises. Two
specific frictions, both measured against `kotoba-lang/compiler@653084f` through
`kotoba.compiler.core/compile-source`:

```kotoba
(defn seats-of-text [total :i64 wt :i64 wm :i64 wp :i64 floor :i64] :i64
  (record-get [:record :rebalance/lanes
               [[:text :i64] [:media :i64] [:postproc :i64]]]
              (seats-record total wt wm wp floor) :text))
```

— 13 inline copies of the schema literal in one module, and

```kotoba
(defn eligible? [flags :i64 free-bytes :i64 min-free :i64] :i64
  (let [has-engine (bit? flags 1) …
        ckpt-ok (if (= has-ckpt 0) 1 (if (= holds 1) 1 (if (= can-fetch 1) 1 0)))
        mem-ok (if (< free-bytes min-free) 0 1)]
    (if (= has-engine 0) 0 (if (= ckpt-ok 0) 0 mem-ok))))
```

— `infer_schedule_core.kotoba` hand-rolls boolean logic on i64 because
comparisons are i64-typed and `not` is not usable on `:bool`.

## Measurements

All probes: `compile-source … :wasm32-kotoba-v1`, executed on KIR.

### What already works (do not re-litigate)

| Form | Result |
|---|---|
| record returned from a `:pure-product` export, read off `ir/execute` | `[[:record :r/s [[:text :i64] …]] 3 4 5]` |
| `[:record …]` as a function **parameter** | OK |
| record flowing return → parameter between functions | OK |
| `let`-bound record projected several times | OK |
| `(def LANES [:record …])` used as a `record-new` / `record-get` **argument** | OK |
| `:bool` parameters through `and` | OK |
| `:bool` through `if` | OK |

### What is rejected

| Form | Error |
|---|---|
| `(:text rec)` | `computed or namespaced calls are forbidden` |
| `(record-get rec :text)` (2-arity) | `record-get requires type, value, and literal field` |
| `def`-named schema in the **return-type annotation** position | body parse breaks |
| `defrecord` / `defprotocol` / `extend-type` | `only ns, def, defn, and defn- are allowed at top level` |
| `(>= x y)` declared `:bool` | `expression type mismatch: expected bool, got i64` |
| `(not b)` where `b : :bool` | `equality operands must have the same value type` |
| `and` / `or` mixing `:bool` params with comparisons | type mismatch |

## Decision

### 1. Comparisons should be `:bool`-typed — as a three-repo slice, not a patch

`(def comparisons '#{= < > <= >=})` are all typed `:i64` in
`infer-call-type`. The frontend change is one line:

```diff
       (contains? (disj comparisons '=) op)
       (do (doseq [[arg type] (map vector args types)]
             (require-expression-type! type :i64 arg))
-          :i64)
+          :bool)
```

Measured consequences of exactly that line:

- `clojure -M:conformance` → **51 / 52** (was 52 / 52). The single regression is
  `:when-ext-kit`, `(when-not (< 2 1) 6)`, failing with *equality operands must
  have the same value type*. Root cause: `not` (and `empty?`, `zero?`, `not=`)
  desugar to `(= x 0)`, so the whole boolean layer is expressed as integer
  comparison against `0`. A bool operand cannot be compared to the i64 literal.
- `desugar-and` / `desugar-or` are already `(let [tmp a] (if tmp … tmp))`, i.e.
  structurally polymorphic — `(and boolA boolB)` already returns `:bool` today.
  They need no change.
- After the frontend accepts it, a `value-type-mismatch` surfaces from a
  **different repo**: that error string is not in `kotoba-lang/compiler`; it
  comes from the `kotoba-kir` / `kotoba-wasm` git deps.

So the slice is: **compiler** (comparison typing + a bool-aware `not`) +
**kotoba-kir** + **kotoba-wasm**, with `kotoba-native` to check, plus new
conformance cases and coordinated pin advances. `=` should follow for coherence
but was not separately measured — leaving `<` bool and `=` i64 would be worse
than either end state.

**Do not land the one-line frontend change on its own.** It typechecks source
the backends then reject, which is precisely the "admits ≠ runs" failure T2.3
(compiler#438) just closed.

### 2. Record field access needs a type-directed rewrite point

`record-get` is 3-arity because lowering needs the schema. A 2-arity
`(record-get r :field)` or a `(:field r)` accessor is resolvable — the type of
`r` is known during inference — but **the compiler has no post-inference,
type-directed rewrite pass** (`annotate-doseq-collection-kinds` runs *before*
`analyze`). Adding either sugar means adding that pass, or resolving the schema
inside `infer-call-type` and rewriting the node there.

This is contained to `kotoba-lang/compiler` — unlike (1) it needs no other repo.
It is the cheaper of the two and should go first.

Interim, available today with no compiler change: name the schema with `def`
and use it in every `record-new` / `record-get` argument position. Only
return-type annotations still need the literal.

### 3. `surface-status.edn` claimed defrecord/defprotocol that does not exist

The entry declared `:disposition :implemented-partial`,
`:implementation #{:compiler :kotoba-wasm :kotoba-cljs}`, `:missing []`, citing
`:conformance :record-protocol-static-dispatch`. Measured: the fixture
`lang/conformance/records/basic.kotoba` — real Clojure-shaped
`defprotocol` + `defrecord` + `extend-type` + `->LocalBox` + `map->ExtendedBox`
— **does not compile through `compile-source` at all**, with or without an `ns`
header, with or without the pure-product profile.

Nor is it executed. `kotoba/lang/conformance_matrix.cljc` says of itself *"Pure
loaders + queries. Dual-backend execution is T1.3 (compiler …)"*, and the
compiler's T1.3 runner reads its own `pilot-manifest.edn`, which does not
contain this case. The compiler's actual records coverage is `:record-kit`,
which uses `record-new` / `record-get` — not `defrecord`.

The entry is corrected to `:not-yet-implemented` with the measurement, the
orphaned-conformance note, and the previous claims preserved under
`:intended-surface`. A new `:record-schema-values` entry records what the
canonical path *does* admit, since that is what production code now uses.

## Consequences

- The authority file no longer claims a Clojure-shaped record surface that the
  canonical compiler rejects. This is the same class of defect as compiler#438
  — a label that no runner enforced — one layer up.
- Ordering for the follow-on work: **(2) accessor sugar → (1) bool comparisons →
  rewrite `infer_schedule_core` / `infer_plan_core` packs**. (1) is what turns
  `eligible?` from bitmask arithmetic into a predicate, but it is a multi-repo
  language change and should not be started as a side effect of a product PR.
- A declared conformance case that no runner executes is worth a general check:
  every case in `lang/conformance/manifest.edn` should either appear in a
  runner's manifest or be marked as not-yet-executed.

## Related

- `docs/kotoba-reliability-parity-wbs.md` T1.3 / T2.1 / T4.4 / T5.3
- compiler `docs/adr/0188-t23-enforce-pure-product-profile-in-conformance-runner.md`
- murakumo `docs/adr/ADR-260729-w6-t53-rebalance-seats-record.md`
- superproject `ADR-2607299400`
