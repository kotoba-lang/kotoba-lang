# Record & typed structural args cookbook (pure-product / T4.4)

**WBS:** T4.4  
**Status:** accepted (portable compiler + production provider evidence)
**Related:** [T5.1 structural args](../adr/ADR-reliability-t51-structural-args.md),
[option/result guide](./option-result-guide.md), Product Value ABI v1

Prefer **sealed records** for multi-field pure-product exports instead of
base-N packs, `has-*` sentinels, or arity growth past `max-parameters` 5.

---

## 1. When to use a record

| Situation | Prefer |
|---|---|
| 2+ named fields (config, seats, claim body) | nominal `defrecord` |
| Presence / absence of one value | `[:option T]` (T4.3) |
| Short ordered bag of same-role values | hetero-vector (when admitted) |
| Truly positional ≤5 stable args | flat arity |
| New public base-N / bit-pack | **forbidden** (T5.1) |

---

## 2. Sealed record surface (guest)

Prefer the nominal declaration and ordinary access surface:

```kotoba
(ns demo.point)

(defrecord Point [x :i64 y :i64])

(defn main []
  (+ (:x (->Point 3 4))
     (get (map->Point {:x 3 :y 4}) :y)))
;; expect 7 on KIR + wasm32-kotoba-v1
```

Unannotated fields default to `:i64`. Records admit up to 32 unique fields.
Direct `->Type` and exact-map `map->Type` construction work for wide records.
The nominal context may cross total bounded control while each result leaf
keeps the exact declared fields:

```kotoba
(map->Point
  (if enabled
    {:x 3 :y 4}
    {:y fallback :x 0}))
```

This covers `if`/`if-not`/`if-let`/`if-some`, `cond` with `:else`,
`case`/`condp` with a default, and final `let`/`do` results. An arbitrary map
variable is not coerced to a record. Only first-class constructor values remain
bounded by the truthful five-parameter callable ABI. `record-new` and
`record-get` are low-level representation operations, not the default authored
style.

Recursive schemas state the edge once and let `defrecord` own the shape:

```kotoba
(ns demo.tree
  (:schemas {:tree/node
             [:variant :tree/node
              [[:entry [:ref :demo.tree/Entry]]]]}))

(defrecord Entry [k :string v :string])
```

Compiler ADR 0210 predeclares eligible same-module records before validating
the closed schema graph. Exact explicit descriptors remain compatible, while
undeclared references and incompatible same-name descriptors fail closed.

### Rules of thumb

1. **Nominal schema** — namespace plus record name owns identity; equal physical fields do not make records interchangeable.
2. **Field types** — use canonical admitted value descriptors; recursive W4 records are production-proven.
3. **Do not** invent parallel `has-x` i64 fields when an option field works.  
4. **Host bridge** — product CLJ/CLJS `call-record` cutovers are landed; W4
   guest modules encode nominal records while live host I/O remains explicitly
   injected.
5. **max-parameters 5** stays (T5.4); records are the multi-field escape hatch.

---

## 3. Group implementations by protocol

Use `extend-protocol` when one protocol is the natural unit of organization:

```kotoba
(defprotocol Value
  (value [this]))

(defrecord Special [x])
(defrecord Ordinary [x])

(extend-protocol Value
  Special
  (value [this] (+ 100 (:x this)))

  default
  (value [this] (:x this)))
```

The canonical compiler does not install a runtime fallback. It specializes the
`default` body to each otherwise-unimplemented record in the sealed module;
named sections and record-local implementations take precedence. Every
receiver remains statically nominal, every method is checked against its
record descriptor, and an unknown receiver is a compile error. Use
`extend-type` when the record is the clearer unit of organization.

## 4. Typed-map (limited)

**Dual-backend pilot landed** (compiler#426 / ADR 0176, case `typed-map-kit`):
bounded `[:map :i64 :i64]` with `typed-map-new` / `assoc` / `count` / `get` +
`if-some` / `contains` / `equal`.

Prefer **records** for public pure-product exports with named fields; use
typed-map when a small homogeneous key→value table is the natural shape.
Keyword keys and large/heterogeneous maps remain out of the pure-product default.

---

## 5. Migration from packs

| Old public pattern | Target | Status (2026-07-30) |
|---|---|---|
| base-65536 seat packs | record export + host `call-record` | **landed** murakumo#193 (`:rebalance/lanes`) |
| schedule `eligible?` flag bits | eligibility record | **landed** murakumo#195 (`:schedule/eligibility`) |
| plan model / lr / residual packs | named records | **landed** murakumo#196–#198 |
| schedule assign `pack3` queue folds | record export | **landed** murakumo#199 (`:schedule/assign2|3|triple|better*`) |
| credits `share-pack-2` | `:credits/shares2` | **landed** murakumo#200 |
| reconcile `pick-targets` pack | `:reconcile/targets` | **landed** murakumo#201/#202 |
| task assign pack3 | `:task/pair|triple|assign2|3` | **landed** murakumo#203 |
| rebalance demand / seat-order packs | `:rebalance/demand|order` (+ lanes) | **landed** murakumo#204 — **T5.3 packs complete** |
| `has-name` + `name` twin | `[:option :string]` + `if-some` | ongoing (forbidden-pattern) |
| bool-typed comparisons / bool export ABI | language profile 5 (compiler ADR 0191) | typed predicates landed; external Wasm exports retain the recorded Canonical ABI boundary |

Do **not** add new packs while rewriting.

---

## 6. Pure-product profile alignment

`lang/pure-product-profile.edn` keeps the low-level representation contract:

- `:value-types` includes **`:record`** (kind tag; concrete form
  `[:record :ns/name [[:f T] …]]`) — closes ADR-2607299400 P1
- `:record-ops` = `#{record-new record-get}` after frontend elaboration
- `:structural-args` preference still lists `:record` first  
- `:forbidden-patterns` bans new public base-N packs  
- Authored nominal source uses `defrecord`, `->Type`, exact-map `map->Type`
  (including total bounded control), `(get record :field)`, or
  `(:field record)`; free ambient host maps and reflection remain excluded

---

## Related

- compiler ADR 0165 / 0189 / 0190 / 0204 / 0208 / 0210 / 0214 / 0217
- compiler#520/#525/#527/#532/#535 and provider#172–#179
- T5.2 host bridge / T5.3 murakumo#193–#206  
- `docs/adr/ADR-reliability-record-access-and-bool-comparisons.md`  
- `docs/lang/surface-matrix.md` (T2.2 generated overview)
