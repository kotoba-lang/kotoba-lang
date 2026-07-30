# Record & typed structural args cookbook (pure-product / T4.4)

**WBS:** T4.4  
**Status:** accepted (guide + dual-backend pilot evidence)  
**Related:** [T5.1 structural args](../adr/ADR-reliability-t51-structural-args.md),
[option/result guide](./option-result-guide.md), Product Value ABI v1

Prefer **sealed records** for multi-field pure-product exports instead of
base-N packs, `has-*` sentinels, or arity growth past `max-parameters` 5.

---

## 1. When to use a record

| Situation | Prefer |
|---|---|
| 2+ named fields (config, seats, claim body) | `[:record …]` |
| Presence / absence of one value | `[:option T]` (T4.3) |
| Short ordered bag of same-role values | hetero-vector (when admitted) |
| Truly positional ≤5 stable args | flat arity |
| New public base-N / bit-pack | **forbidden** (T5.1) |

---

## 2. Sealed record surface (guest)

Schema shape:

```text
[:record :ns/name [[:field-a :i64] [:field-b :string] …]]
```

Ops (compiler / KIR / wasm32):

| Op | Meaning |
|---|---|
| `(record-new SCHEMA v1 v2 …)` | Construct; arity = field count |
| `(record-get SCHEMA rec :field)` | Project field |

### Golden dual-backend pilot

`compiler` T1.3 case `:record-kit` (`values/record_kit.kotoba`):

```kotoba
(ns kotoba.lang.conformance.record-kit)

(defn main []
  (+ (record-get [:record :demo/point [[:x :i64] [:y :i64]]]
                 (record-new [:record :demo/point [[:x :i64] [:y :i64]]] 3 4)
                 :x)
     (record-get [:record :demo/point [[:x :i64] [:y :i64]]]
                 (record-new [:record :demo/point [[:x :i64] [:y :i64]]] 3 4)
                 :y)))
;; expect 7 on KIR + wasm32-kotoba-v1
```

Evidence: compiler#416 / ADR 0165 — `clojure -M:conformance` includes this case.

### Rules of thumb

1. **Nominal schema** — field list + `:ns/name` identity; wrong nominal fails closed.  
2. **Field types** — pure-product hosts still prefer scalar + option + string; nested recursive records remain a later W4 concern.  
3. **Do not** invent parallel `has-x` i64 fields when an option field works.  
4. **Host bridge** — product CLJ/CLJS hosts call via positional args or murakumo `call-record` (T5.2 partial); native guest record wire still deferred.  
5. **max-parameters 5** stays (T5.4); records are the multi-field escape hatch.

---

## 3. Typed-map (limited)

**Dual-backend pilot landed** (compiler#426 / ADR 0176, case `typed-map-kit`):
bounded `[:map :i64 :i64]` with `typed-map-new` / `assoc` / `count` / `get` +
`if-some` / `contains` / `equal`.

Prefer **records** for public pure-product exports with named fields; use
typed-map when a small homogeneous key→value table is the natural shape.
Keyword keys and large/heterogeneous maps remain out of the pure-product default.

---

## 4. Migration from packs

| Old public pattern | Target | Status (2026-07-30) |
|---|---|---|
| base-65536 seat packs | record export + host `call-record` | **landed** murakumo#193 (`:rebalance/lanes`) |
| schedule `eligible?` flag bits | eligibility record | **landed** murakumo#195 (`:schedule/eligibility`) |
| plan model / lr / residual packs | named records | **landed** murakumo#196–#198 |
| schedule assign `pack3` queue folds | record export | **landed** murakumo#199 (`:schedule/assign2|3|triple|better*`) |
| `has-name` + `name` twin | `[:option :string]` + `if-some` | ongoing (forbidden-pattern) |
| bool-typed comparisons / bool export ABI | opt-in profile + wasm ABI | measured, **not landed** (ADR-reliability-record-access-and-bool-comparisons) |

Do **not** add new packs while rewriting.

---

## 5. Pure-product profile alignment

`lang/pure-product-profile.edn` (2026-07-30):

- `:value-types` includes **`:record`** (kind tag; concrete form
  `[:record :ns/name [[:f T] …]]`) — closes ADR-2607299400 P1
- `:record-ops` = `#{record-new record-get}` (2-arity + schema-ref evidence above)
- `:structural-args` preference still lists `:record` first  
- `:forbidden-patterns` bans new public base-N packs  
- Still **not** free Clojure maps / `defrecord` / keyword field invoke

---

## Related

- compiler ADR 0165 / 0189 / 0190, T1.3 pilot suite  
- T5.2 host bridge / T5.3 murakumo#193–#198  
- `docs/adr/ADR-reliability-record-access-and-bool-comparisons.md`  
- `docs/lang/surface-matrix.md` (T2.2 generated overview)
