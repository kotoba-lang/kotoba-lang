# Option & result usage guide (pure-product)

**WBS:** T4.3  
**Status:** accepted  
**Profiles:** `lang/pure-product-profile.edn`  
**Related:** Product Value ABI v1 (`docs/adr/ADR-product-value-abi-v1.md`),  
stdlib freeze T4.1 (`lang/conformance/stdlib/manifest.edn`)

This guide is the **authoritative cookbook** for optional values and result-like
flows on the pure-product surface. Prefer it over inventing `has-*` sentinels
or `ttl = -1` packs.

---

## 1. Two layers (do not conflate)

| Layer | What | When to use |
|---|---|---|
| **Language option** `[:option T]` | Typed optional value; `option-or` for a value default, `if-some` / `when-some` for control flow | Product pure oracles, PVA v1 hosts |
| **Stdlib prelude records** `Some`/`None`/`Ok`/`Err` | Explicit prelude helpers (`option-value`, `unwrap-ok`, …) from `lang/stdlib/core.kotoba` | Programs compiled **with** `--prelude` / conformance `:prelude` |

Pure-product **product oracles** should use `[:option T]` with `option-or` for a
fallback value, or `if-some` when the payload controls a branch.
Prelude helpers are for portable library / stdlib conformance, not a second ABI.

---

## 2. Language option (`[:option T]`)

### Types

- `[:option :i64]`, `[:option :string]`, `[:option :bool]` (pure-product profile)
- Construct on host via Product Value ABI / KIR option encoding (see PVA v1)
- Guest sugar does **not** require `option-some` for language options

### Sugar (single evaluation)

```kotoba
(defn label [name [:option :string]] :string
  (if-some [n name]
    n
    "anonymous"))

(defn claim-exp [now :i64 ttl [:option :i64]] :i64
  (+ now (option-or ttl 2592000)))
```

Rules:

1. `option-or` returns the payload when present and the fallback otherwise; `T` is inferred.
2. `if-some` binds the **payload** when some; else runs the else branch.
3. Do **not** hardcode these forms to `[:option :i64]` — any `[:option T]`.
4. Prefer option over `has-name` / `ttl -1` sentinels (forbidden patterns in pure-product profile).
5. `when-some` is if-some without else; `some->` chains Option-returning steps.
6. `match-option` is the explicit typed branch form; `option-value-of` is lowering-level ABI, not cookbook syntax.

### Golden

| Artifact | Role |
|---|---|
| `examples/product-value-abi-v1/claim_sub.kotoba` | PVA living contract (compiler CI) |
| `examples/option-result/guide_golden.kotoba` | Guide mini-examples (this task) |

Compiler evidence: `kotoba.compiler.pure-product-profile-test`,  
`kotoba.compiler.product-value-abi-v1-test`.

---

## 3. Stdlib prelude option/result (explicit records)

When the program is compiled **with** `lang/stdlib/core.kotoba` as prelude:

| Name | Role |
|---|---|
| `option-some` / `option-none` | Construct record options |
| `option-some?` / `option-none?` | Predicates |
| `option-value` | Payload or default |
| `ok` / `err` | Result records |
| `ok?` / `err?` | Predicates |
| `unwrap-ok` / `unwrap-err` | Payload or default |

Conformance sketch (`lang/conformance/stdlib/basic.kotoba`):

```kotoba
(defn main []
  (+ (option-value (option-some 4) 0)
     (unwrap-ok (ok 5) 0)))
```

Public name freeze: T4.1 `lang/conformance/stdlib/manifest.edn`.

---

## 4. Decision checklist

1. Writing a **murakumo / product pure oracle**? → `[:option T]` + `if-some`.  
2. Need **library-style** option/result without host ABI? → prelude + T4.1 names.  
3. Tempted to add `has-foo :i64`? → stop; use option.  
4. Need multi-field optional config? → prefer small record / hetero-vector (T5), not base-N packs.

---

## 5. Non-goals (tracked elsewhere)

| Item | WBS |
|---|---|
| `string-join` / bounded `split` | T4.2 |
| Record args vs arity packs | T5 |
| Dual-backend expand for more fixtures | T1.3 expand |

---

*T4.3 deliverable: guide + golden pointers; helpers already landed (if-some / prelude).*
