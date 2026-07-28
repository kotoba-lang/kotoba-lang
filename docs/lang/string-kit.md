# String kit (pure-product / T4.2)

**Status:** length + from-i64 + concat + **bounded join** landed  
**Split:** deferred (optional)

| Op | Arity | Meaning |
|---|---|---|
| `string-length` / `string-byte-length` | 1 | UTF-8 **byte** length |
| `string-from-i64` | 1 | Signed decimal text (desugars to digit helpers) |
| `string-concat` | 2 | Bounded concatenation (≤64KiB) |
| `string-join` | 1+N (N≤8) | `(string-join sep a b …)` → nested concat with sep |
| `string-split` | — | **Deferred** |
| `string=?` / `string-substring` / `string-contains?` / `string-fold-case` / `string-code-point-at` | … | Existing PVA/search surface |

## `string-join` examples

```kotoba
(string-join ",")           ; => ""
(string-join "," "a")       ; => "a"
(string-join "," "a" "b")   ; => "a,b"
(string-join "-" "x" "y" "z") ; => "x-y-z"
```

More than 8 parts after the separator is a compile-time reject.

## Evidence

- compiler#413 / ADR 0162
- `kotoba.compiler.string-operation-test` dual-backend checks

## Related

- `lang/pure-product-profile.edn` `:string-ops`
- semantics-ssot §9
