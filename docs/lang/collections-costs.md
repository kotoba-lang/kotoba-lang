# Collections O-costs (pure-product / portable surface) — T4.5

**Status:** accepted documentation of **current** pair-chain bounds  
**Related:** `lang/surface-status.edn` `:collections`, fuel-model, surface-matrix

Kotoba’s portable collections are **eager (or fuel-bounded) pair-chains**, not
HAMT / vector-trie. That is intentional (deterministic, small code). Authors
must not assume amortized O(1) assoc or log-time random access.

## Cost table (implementation choice today)

| Operation / form | Representation | Typical cost | Bound |
|---|---|---|---|
| Map literal `{…}` | Sorted pair-list of pairs | Build O(n log n) sort + O(n); `get` **O(n)** | Linear lookup; untagged runtime |
| `assoc` (map) | Rebuild pair-chain | **O(n)** | Same limits as map literal |
| Vector literal `[…]` | Pair-chain | Build O(n); index via walk **O(n)** | Backend max **128** items |
| Set literal `#{…}` | Pair-chain (set semantics at lower) | Membership **O(n)** | Same family as vector |
| `map` / `filter` / `reduce` (sugar) | Fuel-bounded helpers | O(n) per pass | Helper fuel often **128** (surface-status `:primary-transform-fuel`) **and** module fuel (default 512 calls) |
| `count` / `nth` on pair-chain | Walk | **O(n)** | Fuel-bounded where applicable |
| Hetero-vector / typed-map | Sealed structured values | Projection O(1)–O(fields) | Admission node/depth limits |
| Sealed `record-new` / `record-get` | Nominal record | O(fields) construct; get O(1)–O(fields) | Prefer for multi-field APIs (T4.4) |

## What “bounded” means

1. **Admission** — `max-list-items` 128, expression node/depth limits (`surface-status` invariants).  
2. **Transform fuel** — desugared map/filter helpers stop within helper budget (commonly 128).  
3. **Module fuel** — each **function call** costs 1 (T7.2); deep recursion exhausts 512.  
4. **No silent O(n²) API** — nested full scans in guest code are still possible if the
   author nests linear walks; the language does not rewrite them. Document and avoid.

## Pure-product guidance

| Do | Don't |
|---|---|
| Prefer records / options for public multi-field APIs | Grow large maps for hot `get` loops |
| Keep collection sizes small (≤ tens for pure oracles) | Assume Clojure persistent-hash performance |
| Use dual-backend pilot + goldens when adding surface | Add unbounded reduce without fuel talk |

## Evidence / authority

- `lang/surface-status.edn` — `:map-literal` / `:vector-literal` limits  
- `docs/lang/fuel-model.md` — call fuel vs helper fuel  
- `docs/lang/surface-matrix.md` — generated disposition table  

## Dual-backend pilot (T4.5 vector-i64)

`vector-i64` constructor + mut ops + **`reduce` / `map` / `filter` over vector-i64**
(compiler#433–#434 / ADR 0183–0184) are dual-green.

| Prefer | Notes |
|---|---|
| `(reduce + 0 v)` / `(reduce (fn [a x] …) 0 v)` / `(reduce named-binary 0 v)` | Folds; empty → init; named = arity-2 module `defn` |
| `(map (fn [x] …) v)` / `(map inc v)` / `(map named-unary v)` | Builds new vector-i64; named = arity-1 module `defn` |
| `(map (fn [x y] …) a b)` / `(map named-binary a b)` | 2-source; stops at **shortest** collection |
| `(map (fn [a b c d e] …) a b c d e)` / named equivalent | Named/inline callbacks scale through 5 sources; 3–5 vector handles share one bounded typed state value; stops at **shortest** collection |
| `(map stored-closure a …)` | Stored closure callbacks scale through 4 sources so callback + helper state remain inside the five-parameter ABI |
| `(filter (fn [x] pred) v)` / `(filter named-pred v)` | pred used as `if` test |

Still gated: five-source stored-closure `map` and typed-map transform sugar.
`inc`/`dec` desugar to arithmetic.

## Follow-ups

- T4.5 residual: typed-map transform sugar and closure result families beyond i64/bool
- T1.3 full matrix still progressive for collection fixtures
