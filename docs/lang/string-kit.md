# String kit (pure-product / T4.2)

**Status:** length + from-i64 + concat + join + **string-split-count** landed dual-backend (compiler#427); **string-upper** landed as the fold-case mirror (2026-09-04)  
**Full split→collection:** deferred

| Op | Arity | Meaning |
|---|---|---|
| `string-length` / `string-byte-length` | 1 | UTF-8 **byte** length |
| `string-from-i64` | 1 | Signed decimal text (desugars to digit helpers) |
| `string-concat` | 2 | Bounded concatenation (≤64KiB) |
| `string-join` | 1+N (N≤8) | `(string-join sep a b …)` → nested concat with sep |
| `string-split-count` | 2 | Segment count `(haystack sep)` — empty sep rejects |
| `string-split` | — | **Deferred** (collection return) |
| `string=?` | 2 | Equality → i64 1/0 |
| `string-substring` | 3 | Byte-range slice (UTF-8 boundaries) |
| `string-contains?` | 2 | Substring search → i64 1/0 |
| `string-fold-case` | 1 | Unicode case-fold |
| `string-upper` | 1 | Unicode upper-case (the mirror of fold-case) |
| `string-code-point-at` | 2 | Code point at UTF-8 byte offset |

## `string-join` examples

```kotoba
(string-join ",")           ; => ""
(string-join "," "a")       ; => "a"
(string-join "," "a" "b")   ; => "a,b"
(string-join "-" "x" "y" "z") ; => "x-y-z"
```

More than 8 parts after the separator is a compile-time reject.

## Evidence

- compiler#413 / ADR 0162 (`string-join`)
- compiler#414 / ADR 0163 (join + from-i64 pilot)
- compiler#415 / ADR 0164 (contains / eq / substring / fold-case / code-point pilot)
- compiler#425 / ADR 0175 (`string-byte-length` dual-backend kit)
- compiler#427 / ADR 0177 (`string-split-count`; pilot **30**)
- `clojure -M:conformance` dual-green
- `kotoba.compiler.string-operation-test` dual-backend checks

## Related

- `lang/pure-product-profile.edn` `:string-ops`
- semantics-ssot §9
